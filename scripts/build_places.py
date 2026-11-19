"""Builds backend/src/main/resources/geo/places-in.tsv, the place list behind SaathChalo's "From" and "To" boxes.

Source: GeoNames (https://www.geonames.org, CC BY 4.0 — keep the attribution in the website footer):
  - cities5000.zip        every town with 5,000+ people
  - admin1CodesASCII.txt  state names
  - alternatenames/IN.zip names by language, with historic and colloquial flags

How to rebuild (downloads are untrusted data: keep them in their own empty folder, outside the project):
  mkdir -p /tmp/geonames && cd /tmp/geonames
  wget https://download.geonames.org/export/dump/cities5000.zip \
       https://download.geonames.org/export/dump/admin1CodesASCII.txt \
       https://download.geonames.org/export/dump/alternatenames/IN.zip
  unzip cities5000.zip && unzip IN.zip
  python3 -I scripts/build_places.py /tmp/geonames /tmp/geonames backend/src/main/resources/geo/places-in.tsv
Then run the backend tests (PlaceDirectoryTest checks well-known names still resolve).

Output columns (tab-separated): id, name, state, lat, lng, population, aliases ("|"-separated).
Aliases: English and unlabelled Latin names (incl. old names like Bombay or Madras), plus one name per Indian
script (Hindi, Marathi, Bengali, Gujarati, Punjabi, Tamil, Telugu, Kannada, Malayalam, Odia).
"""
import difflib
import re
import sys
import unicodedata

cities_dir, alt_dir, out_path = sys.argv[1], sys.argv[2], sys.argv[3]

INDIC = ["hi", "mr", "bn", "gu", "pa", "ta", "te", "kn", "ml", "or"]
MAX_LATIN = 10

# Display names people use most, where GeoNames prefers another spelling.
DISPLAY = {1260607: "Panaji", 1253993: "Ooty", 1254388: "Tiruchirappalli", 1262117: "Nainital", 1258744: "Ramanagara"}
# GeoNames gives a few towns their district's population, which would push them above real cities in search.
POPULATION_FIX = {1258393: 60000, 12165956: 60000, 1261162: 30000, 1254745: 100000, 12501153: 50000}
# Everyday nicknames missing from the data. "Goa" means Panaji here (and is removed from other places).
EXTRA = {
    1260607: ["Goa", "Panjim"],
    1254388: ["Trichy"],                        # Tiruchirappalli
    1253993: ["Udhagamandalam", "Ootacamund"],  # Ooty
    1265873: ["Calicut"],                       # Kozhikode
    1254187: ["Trichur"],                       # Thrissur
    1264588: ["Margao"],                        # Madgaon
}
EXCLUSIVE = {"goa": 1260607}

states = {}
with open(f"{cities_dir}/admin1CodesASCII.txt", encoding="utf-8") as f:
    for line in f:
        code, name, ascii_name, _ = line.rstrip("\n").split("\t")
        if code.startswith("IN."):
            states[code[3:]] = {"NCT": "Delhi", "National Capital Territory of Delhi": "Delhi"}.get(ascii_name, ascii_name)

places = {}
with open(f"{cities_dir}/cities5000.txt", encoding="utf-8") as f:
    for line in f:
        c = line.rstrip("\n").split("\t")
        if c[8] != "IN":
            continue
        places[int(c[0])] = {
            "name": c[1], "ascii": c[2], "state": states.get(c[10], ""), "lat": c[4], "lng": c[5],
            "population": POPULATION_FIX.get(int(c[0]), int(c[14] or 0)), "en": [], "latin": [], "indic": {},
        }

latin = re.compile(r"^[A-Za-zÀ-ɏḀ-ỿ][A-Za-zÀ-ɏḀ-ỿ .'-]{1,39}$")
with open(f"{alt_dir}/IN.txt", encoding="utf-8") as f:
    for line in f:
        c = line.rstrip("\n").split("\t")
        gid, lang, name = int(c[1]), c[2], c[3].strip()
        p = places.get(gid)
        if p is None or not name:
            continue
        preferred, historic, colloquial = c[4] == "1", c[7] == "1", c[6] == "1"
        if lang in ("", "en") and latin.match(name) and any(ch.islower() for ch in name):
            # English names first (preferred, then current, then historic/colloquial), unlabelled ones after.
            rank = (0 if lang == "en" else 1, 0 if preferred else 1, 1 if (historic or colloquial) else 0)
            p["latin"].append((rank, name))
            if lang == "en" and not historic and not colloquial:
                p["en"].append((0 if preferred else 1, name))
        elif lang in INDIC and lang not in p["indic"] and len(name) <= 40 and not re.search(r"[A-Za-z0-9]", name):
            p["indic"][lang] = name


def fold(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(ch for ch in s if not unicodedata.combining(ch) or ord(ch) > 0x036F)
    return re.sub(r"[^\w]", "", s.lower())


def display_name(gid, p):
    """A curated name, else GeoNames' preferred English name, else the current English name closest to the official one."""
    if gid in DISPLAY:
        return DISPLAY[gid]
    preferred = [n for flag, n in p["en"] if flag == 0]
    if preferred:
        return preferred[0]
    if p["en"]:
        official = fold(p["ascii"])
        return max((n for _, n in p["en"]),
                   key=lambda n: (difflib.SequenceMatcher(None, fold(n), official).ratio(), -len(n)))
    return p["ascii"]


rows = []
for gid, p in places.items():
    name = display_name(gid, p)
    seen = {fold(name)}
    aliases = []

    def add(alias):
        key = fold(alias)
        if key and key not in seen and (key not in EXCLUSIVE or EXCLUSIVE[key] == gid):
            seen.add(key)
            aliases.append(alias)

    for alias in [p["name"], p["ascii"]] + EXTRA.get(gid, []):
        add(alias)
    for _, alias in sorted(p["latin"]):
        if len([a for a in aliases if latin.match(a)]) >= MAX_LATIN:
            break
        add(alias)
    for lang in INDIC:
        if lang in p["indic"]:
            add(p["indic"][lang])
    rows.append((gid, name, p["state"], p["lat"], p["lng"], p["population"], aliases))

rows.sort(key=lambda r: (-r[5], r[0]))
with open(out_path, "w", encoding="utf-8", newline="\n") as f:
    f.write("id\tname\tstate\tlat\tlng\tpopulation\taliases\n")
    for gid, name, state, lat, lng, pop, aliases in rows:
        fields = [str(gid), name, state, lat, lng, str(pop), "|".join(aliases)]
        assert not any("\t" in x or "\n" in x for x in fields)
        f.write("\t".join(fields) + "\n")

missing = [gid for gid in list(DISPLAY) + list(EXTRA) if gid not in places]
print(f"{len(rows)} places; {sum(len(r[6]) for r in rows)} aliases; "
      f"no state: {sum(1 for r in rows if not r[2])}; curated ids not found: {missing}")
