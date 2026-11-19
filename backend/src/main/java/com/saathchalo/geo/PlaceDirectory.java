package com.saathchalo.geo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Finds Indian cities and towns by any name people use: the official name, old names ("Bombay", "Madras"), everyday
 * nicknames ("Vizag", "Ooty") and names in Indian scripts ("दिल्ली", "ಬೆಂಗಳೂರು"). The data — GeoNames (CC BY 4.0),
 * every town with 5,000+ people — is read into memory at start-up, so searching needs no database.
 */
@Service
public class PlaceDirectory {

	static final String DATA = "geo/places-in.tsv";

	public static final int MAX_RESULTS = 20;

	/** Places farther than this from every known town are "not in India" for {@link #nearest}. */
	private static final double NEAREST_MAX_KM = 60;

	/** Shown before anything is typed: the busiest carpooling cities. */
	private static final List<Long> POPULAR = List.of(1273294L, 1275339L, 1277333L, 1269843L, 1264527L, 1259229L,
			1269515L, 1270642L, 1279233L, 1274746L, 1264733L, 1275004L, 1269743L, 1273313L, 7279746L);

	/** How a typed name matched: the main name, another name, or a later word of the main name ("Delhi" in "New Delhi"). */
	private enum Kind {

		MAIN(3.0, 1.2), ALIAS(2.2, 0.6), WORD(1.0, 0.4);

		final double exact;

		final double prefix;

		Kind(double exact, double prefix) {
			this.exact = exact;
			this.prefix = prefix;
		}
	}

	private static final Kind[] KINDS = Kind.values();

	private record Key(String key, int place, Kind kind) {
	}

	private record Row(Place place, List<String> aliases) {
	}

	private final List<Place> places;

	private final Map<Long, Place> byId;

	private final String[] keys;

	private final int[] keyPlace;

	private final byte[] keyKind;

	public PlaceDirectory() {
		this(read());
	}

	private PlaceDirectory(List<Row> rows) {
		List<Row> sorted = new ArrayList<>(rows);
		sorted.sort(Comparator.comparingInt((Row r) -> -r.place().population()).thenComparingLong(r -> r.place().id()));
		this.places = sorted.stream().map(Row::place).toList();
		Map<Long, Place> ids = new HashMap<>();
		List<Key> all = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Row row = sorted.get(i);
			ids.put(row.place().id(), row.place());
			Set<String> seen = new HashSet<>();
			addKey(all, seen, row.place().name(), i, Kind.MAIN);
			for (String alias : row.aliases()) {
				addKey(all, seen, alias, i, Kind.ALIAS);
			}
			String[] words = row.place().name().split("[\\s-]+");
			for (int w = 1; w < words.length; w++) {
				addKey(all, seen, String.join(" ", Arrays.copyOfRange(words, w, words.length)), i, Kind.WORD);
			}
		}
		all.sort(Comparator.comparing(Key::key));
		this.byId = Map.copyOf(ids);
		this.keys = new String[all.size()];
		this.keyPlace = new int[all.size()];
		this.keyKind = new byte[all.size()];
		for (int i = 0; i < all.size(); i++) {
			keys[i] = all.get(i).key();
			keyPlace[i] = all.get(i).place();
			keyKind[i] = (byte) all.get(i).kind().ordinal();
		}
	}

	/** Best matches for what someone typed, most likely first. An empty query lists the biggest cities. */
	public List<Place> search(String query, int limit) {
		int n = Math.clamp(limit, 1, MAX_RESULTS);
		String q = fold(query);
		if (q.length() > 40) {
			q = q.substring(0, 40);
		}
		if (q.isEmpty()) {
			return POPULAR.stream().map(byId::get).filter(p -> p != null).limit(n).toList();
		}
		Map<Integer, Double> scores = new HashMap<>();
		for (int i = lowerBound(q); i < keys.length && keys[i].startsWith(q); i++) {
			Kind kind = KINDS[keyKind[i]];
			scores.merge(keyPlace[i], keys[i].length() == q.length() ? kind.exact : kind.prefix, Math::max);
		}
		return scores.entrySet()
			.stream()
			.sorted(Comparator.comparingDouble((Map.Entry<Integer, Double> e) -> -(e.getValue() + size(places.get(e.getKey()))))
				.thenComparingInt(Map.Entry::getKey))
			.limit(n)
			.map(e -> places.get(e.getKey()))
			.toList();
	}

	public Optional<Place> find(long id) {
		return Optional.ofNullable(byId.get(id));
	}

	/** The known town closest to a position, if the position is in or near India. */
	public Optional<Place> nearest(double lat, double lng) {
		Place best = null;
		double bestKm = Double.MAX_VALUE;
		for (Place p : places) {
			double km = GeoMath.straightKm(lat, lng, p.lat(), p.lng());
			if (km < bestKm) {
				best = p;
				bestKm = km;
			}
		}
		return bestKm <= NEAREST_MAX_KM ? Optional.ofNullable(best) : Optional.empty();
	}

	/**
	 * Towns near the straight line between two places, in travel order — suggested stops for drivers. Bigger towns come
	 * first, with a growing penalty the further they are from the line (highways bend, so up to 30 km is allowed).
	 * Places within 15 km of the start or end, and suburbs of big cities, are skipped; suggestions are spread out.
	 */
	public List<Place> along(Place from, Place to, int limit) {
		double total = GeoMath.straightKm(from, to);
		if (total < 40 || limit < 1) {
			return List.of();
		}
		double maxOffKm = Math.clamp(total * 0.15, 10, 30);
		double minGapKm = Math.max(15, total * 0.12);
		double cosLat = Math.cos(Math.toRadians((from.lat() + to.lat()) / 2));
		double bx = (to.lng() - from.lng()) * cosLat * 111.32;
		double by = (to.lat() - from.lat()) * 110.57;
		double length = Math.sqrt(bx * bx + by * by);
		record Candidate(Place place, double atKm, double score) {
		}
		List<Candidate> candidates = new ArrayList<>();
		for (Place p : places) {
			if (p.population() < 30_000) {
				break;
			}
			if (p.id() == from.id() || p.id() == to.id() || isSuburb(p, from) || isSuburb(p, to)) {
				continue;
			}
			double px = (p.lng() - from.lng()) * cosLat * 111.32;
			double py = (p.lat() - from.lat()) * 110.57;
			double t = (px * bx + py * by) / (length * length);
			double offKm = Math.abs(px * by - py * bx) / length;
			if (t > 0.06 && t < 0.94 && offKm <= maxOffKm) {
				double off = offKm / maxOffKm;
				candidates.add(new Candidate(p, t * total, size(p) - off * off));
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
		List<Candidate> picked = new ArrayList<>();
		for (Candidate c : candidates) {
			if (picked.size() == limit) {
				break;
			}
			if (picked.stream().noneMatch(o -> Math.abs(o.atKm() - c.atKm()) < minGapKm)) {
				picked.add(c);
			}
		}
		picked.sort(Comparator.comparingDouble(Candidate::atKm));
		return picked.stream().map(Candidate::place).toList();
	}

	public int size() {
		return places.size();
	}

	/**
	 * The form names are compared in: lower case, Latin accents removed ("Manāli" → "manali"), and only letters, digits
	 * and Indian-script vowel signs kept, so "Naini Tal", "Nainital" and "naini-tal" are the same.
	 */
	static String fold(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		String d = Normalizer.normalize(s, Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder(d.length());
		for (int i = 0; i < d.length();) {
			int cp = d.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 0x0300 && cp <= 0x036F) {
				continue;
			}
			int type = Character.getType(cp);
			if (Character.isLetterOrDigit(cp) || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) {
				b.appendCodePoint(Character.toLowerCase(cp));
			}
		}
		return b.toString();
	}

	/** Too close to a route's start or end to be worth a stop (searches already look 30 km around each stop). */
	private static boolean isSuburb(Place p, Place city) {
		double km = GeoMath.straightKm(p, city);
		return km < 15 || (km < 30 && p.state().equals(city.state()) && city.population() >= 1_000_000);
	}

	private static double size(Place p) {
		return Math.log10(Math.max(1000, p.population()));
	}

	private int lowerBound(String q) {
		int lo = 0;
		int hi = keys.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (keys[mid].compareTo(q) < 0) {
				lo = mid + 1;
			}
			else {
				hi = mid;
			}
		}
		return lo;
	}

	private static void addKey(List<Key> all, Set<String> seen, String name, int place, Kind kind) {
		String key = fold(name);
		if (!key.isEmpty() && seen.add(key)) {
			all.add(new Key(key, place, kind));
		}
	}

	private static List<Row> read() {
		try (InputStream in = new ClassPathResource(DATA).getInputStream()) {
			return parse(in);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read the place list " + DATA, e);
		}
	}

	/** Columns: id, name, state, lat, lng, population, aliases ("|"-separated). The first line is a header. */
	static List<Row> parse(InputStream in) throws IOException {
		List<Row> rows = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		reader.readLine();
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.isBlank()) {
				continue;
			}
			String[] c = line.split("\t", -1);
			Place place = new Place(Long.parseLong(c[0]), c[1], c[2], Double.parseDouble(c[3]), Double.parseDouble(c[4]),
					Integer.parseInt(c[5]));
			rows.add(new Row(place, c[6].isEmpty() ? List.of() : List.of(c[6].split("\\|"))));
		}
		return rows;
	}
}
