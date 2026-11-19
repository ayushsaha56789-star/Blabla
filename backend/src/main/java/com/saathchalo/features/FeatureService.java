package com.saathchalo.features;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.features.internal.FeatureFlag;
import com.saathchalo.features.internal.FeatureFlagRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Decides whether a feature is on for a given viewer, following these steps:
 * maintenance mode → parent switch → the switch itself → the viewer's restrictions → the switch's audience.
 * States are kept in memory and reloaded after every change (and every 30 s, for when several servers run).
 */
@Service
public class FeatureService {

	private record State(boolean enabled, Audience audience, List<String> cities, Set<String> cityKeys,
			UUID updatedBy, Instant updatedAt) {
	}

	private final FeatureFlagRepository flags;

	private final AuditService audit;

	private final ObjectProvider<AudienceProfileProvider> profiles;

	private final Clock clock;

	private volatile Map<String, State> states = Map.of();

	public FeatureService(FeatureFlagRepository flags, AuditService audit, ObjectProvider<AudienceProfileProvider> profiles,
			Clock clock) {
		this.flags = flags;
		this.audit = audit;
		this.profiles = profiles;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		syncCatalog();
	}

	/** Creates a row (with the default state) for every catalogue switch that has none yet. */
	@Transactional
	public void syncCatalog() {
		for (FeatureDefinition def : FeatureCatalog.all()) {
			if (!flags.existsById(def.key())) {
				flags.save(new FeatureFlag(def.key(), def.defaultOn(), clock.instant()));
			}
		}
		reloadAfterCommit();
	}

	@Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
	public void reload() {
		Map<String, State> loaded = new LinkedHashMap<>();
		for (FeatureFlag flag : flags.findAll()) {
			List<String> cities = splitCities(flag.getCities());
			Set<String> keys = new LinkedHashSet<>();
			cities.forEach(c -> keys.add(c.toLowerCase(Locale.ROOT)));
			loaded.put(flag.getKey(), new State(flag.isEnabled(), flag.getAudience(), cities, keys, flag.getUpdatedBy(),
					flag.getUpdatedAt()));
		}
		states = Map.copyOf(loaded);
	}

	public boolean isMaintenance() {
		State s = states.get(FeatureCatalog.MAINTENANCE);
		return s != null && s.enabled();
	}

	public boolean signupsOpen() {
		State s = states.get(FeatureCatalog.SIGNUPS);
		return s == null || s.enabled();
	}

	public boolean isOnFor(String key, UUID userIdOrNull) {
		return isOn(key, profileOf(userIdOrNull));
	}

	public boolean isOn(String key, AudienceProfile viewer) {
		FeatureDefinition def = FeatureCatalog.find(key).orElse(null);
		if (def == null) {
			return false;
		}
		if (def.global()) {
			State s = states.get(key);
			return s != null && s.enabled();
		}
		if (isMaintenance()) {
			return false;
		}
		if (def.parent() != null && !isOn(def.parent(), viewer)) {
			return false;
		}
		State s = states.get(key);
		if (s == null || !s.enabled() || viewer.restrictedFrom(key)) {
			return false;
		}
		return switch (s.audience()) {
			case EVERYONE -> true;
			case VERIFIED -> viewer.idVerified();
			case WOMEN -> "FEMALE".equals(viewer.gender());
			case BETA -> viewer.betaTester();
			case CITIES -> viewer.city() != null && s.cityKeys().contains(viewer.city().strip().toLowerCase(Locale.ROOT));
		};
	}

	/** Member-facing switches that are on for this viewer (global switches are reported separately). */
	public List<String> enabledKeys(UUID userIdOrNull) {
		AudienceProfile viewer = profileOf(userIdOrNull);
		List<String> keys = new ArrayList<>();
		for (FeatureDefinition def : FeatureCatalog.all()) {
			if (!def.global() && isOn(def.key(), viewer)) {
				keys.add(def.key());
			}
		}
		return keys;
	}

	public List<FeatureView> list() {
		return FeatureCatalog.all().stream().map(this::view).toList();
	}

	public long countOn() {
		return FeatureCatalog.all().stream().filter(d -> !d.global()).filter(d -> {
			State s = states.get(d.key());
			return s != null && s.enabled();
		}).count();
	}

	public FeatureDefinition definition(String key) {
		return FeatureCatalog.find(key)
			.orElseThrow(() -> ApiException.notFound("unknown_feature", "There is no feature called \"" + key + "\"."));
	}

	@Transactional
	public FeatureView update(String key, boolean enabled, Audience audience, List<String> cities, Actor actor) {
		FeatureDefinition def = definition(key);
		Audience newAudience = def.global() || audience == null ? Audience.EVERYONE : audience;
		List<String> newCities = newAudience == Audience.CITIES ? cleanCities(cities) : List.of();
		if (newAudience == Audience.CITIES && newCities.isEmpty()) {
			throw ApiException.badRequest("cities_required", "Add at least one city, or choose another audience.");
		}
		FeatureFlag flag = flags.findById(key).orElseGet(() -> new FeatureFlag(key, def.defaultOn(), clock.instant()));
		Map<String, Object> before = describe(flag.isEnabled(), flag.getAudience(), splitCities(flag.getCities()));
		flag.update(enabled, newAudience, String.join(",", newCities), actor.id(), clock.instant());
		flags.save(flag);
		Map<String, Object> after = describe(enabled, newAudience, newCities);
		if (!before.equals(after)) {
			audit.record(actor, "feature.updated", "feature", key, summary(def, enabled, newAudience, newCities),
					Map.of("before", before, "after", after));
		}
		reloadAfterCommit();
		State s = new State(enabled, newAudience, newCities, Set.of(), actor.id(), clock.instant());
		return view(def, s);
	}

	private FeatureView view(FeatureDefinition def) {
		return view(def, states.get(def.key()));
	}

	private static FeatureView view(FeatureDefinition d, State s) {
		boolean enabled = s != null && s.enabled();
		Audience audience = s == null ? Audience.EVERYONE : s.audience();
		List<String> cities = s == null ? List.of() : s.cities();
		return new FeatureView(d.key(), d.parent(), d.area(), d.label(), d.description(), d.phase(), d.built(),
				d.critical(), d.global(), enabled, audience, cities, s == null ? null : s.updatedAt(),
				s == null ? null : s.updatedBy());
	}

	private AudienceProfile profileOf(UUID userId) {
		if (userId == null) {
			return AudienceProfile.ANONYMOUS;
		}
		AudienceProfileProvider provider = profiles.getIfAvailable();
		return provider == null ? AudienceProfile.ANONYMOUS : provider.profileOf(userId);
	}

	private void reloadAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reload();
				}
			});
		}
		else {
			reload();
		}
	}

	private static String summary(FeatureDefinition def, boolean enabled, Audience audience, List<String> cities) {
		if (!enabled) {
			return "Turned off " + def.label();
		}
		String who = switch (audience) {
			case EVERYONE -> "everyone";
			case VERIFIED -> "ID-verified members";
			case WOMEN -> "women";
			case BETA -> "beta testers";
			case CITIES -> "members in " + String.join(", ", cities);
		};
		return "Turned on " + def.label() + (def.global() ? "" : " for " + who);
	}

	private static Map<String, Object> describe(boolean enabled, Audience audience, List<String> cities) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("enabled", enabled);
		m.put("audience", audience.name());
		m.put("cities", cities);
		return m;
	}

	private static List<String> cleanCities(List<String> cities) {
		if (cities == null) {
			return List.of();
		}
		Map<String, String> unique = new LinkedHashMap<>();
		for (String c : cities) {
			String city = c == null ? "" : c.strip().replaceAll("\\s+", " ");
			if (city.length() > 60 || !city.matches("[\\p{L} .'-]*")) {
				throw ApiException.badRequest("invalid_city", "City names can contain letters only.");
			}
			if (!city.isEmpty()) {
				unique.putIfAbsent(city.toLowerCase(Locale.ROOT), city);
			}
		}
		if (unique.size() > 50) {
			throw ApiException.badRequest("too_many_cities", "Choose at most 50 cities.");
		}
		return List.copyOf(unique.values());
	}

	private static List<String> splitCities(String stored) {
		if (stored == null || stored.isBlank()) {
			return List.of();
		}
		return Arrays.stream(stored.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
	}
}
