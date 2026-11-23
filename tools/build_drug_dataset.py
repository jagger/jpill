#!/usr/bin/env python3
"""
Builds app/src/main/assets/drugs.csv from the openFDA NDC directory.

Source: https://open.fda.gov/apis/drug/ndc/  (openFDA data is CC0 1.0, see https://open.fda.gov/terms/)
Scope : human prescription drugs in oral solid forms. Output is name data only:
        generic name, brand names, strengths, active ingredients. NO dosing, frequency or limits.
Run   : python3 tools/build_drug_dataset.py [out.csv]      (needs network; ~30 minutes, rate-limited)
Clean : python3 tools/build_drug_dataset.py --clean raw.csv out.csv   (re-apply cleaning to a raw file, no network)
Output: semicolon-separated: generic;brands(|);strengths(|);ingredients(|)  — sorted, deduplicated.
"""
import csv, json, re, sys, time, urllib.parse, urllib.request
from collections import defaultdict

API = "https://api.fda.gov/drug/ndc.json"
FORMS = [
    "TABLET", "TABLET, FILM COATED", "TABLET, COATED", "CAPSULE", "TABLET, EXTENDED RELEASE",
    "CAPSULE, EXTENDED RELEASE", "CAPSULE, DELAYED RELEASE", "TABLET, DELAYED RELEASE",
    "TABLET, ORALLY DISINTEGRATING", "CAPSULE, GELATIN COATED", "TABLET, CHEWABLE",
    "TABLET, FILM COATED, EXTENDED RELEASE",
]
MIN_PRODUCTS = 3          # drop one-off oddities
DELAY = 0.35              # seconds between calls (anonymous limit is 240/min)


def get(params, tries=5):
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:           # no results for that query
                return {"results": []}
            time.sleep(2 * (attempt + 1))
        except Exception:
            time.sleep(2 * (attempt + 1))
    return {"results": []}


def norm_strength(s):
    s = s.strip().lower()
    s = re.sub(r"/1$", "", s)            # "10 mg/1" -> "10 mg"
    s = re.sub(r"\s+", " ", s)
    return s


# ---- cleaning -------------------------------------------------------------------------------------

LIQUID_RE = re.compile(r"/\s*[0-9.]*\s*(ml|l|g|actuat)\b|\[|%")      # solutions, injectables, topicals, vitamin units
MAX_NAME_LEN = 60
MAX_INGREDIENTS = 4                                        # drops multivitamin / kit style rows


def tidy_name(name):
    name = re.sub(r"\s+", " ", name.strip())
    return name.title() if (name.isupper() or name.islower()) else name


def clean_strength(st):
    st = st.strip().lower()
    st = re.sub(r"(?<![0-9])\.(\d)", r"0.\1", st)          # ".25 mg" -> "0.25 mg"
    st = re.sub(r"\bug\b", "mcg", st)
    st = re.sub(r"\s+", " ", st)
    return st


def first_number(st):
    m = re.search(r"[0-9]+(?:\.[0-9]+)?", st)
    return float(m.group()) if m else float("inf")


def clean_row(row):
    """row = [generic, brands(|), strengths(|), ingredients(|)] -> cleaned row, or None to drop it."""
    generic = tidy_name(row[0])
    ingredients = [i for i in row[3].split("|") if i]
    if not generic or len(generic) > MAX_NAME_LEN or len(ingredients) > MAX_INGREDIENTS or re.search(r"[0-9]", generic):
        return None   # too long, kit-like, or a strength baked into the name ("Metformin ER 500 mg")
    base = re.split(r"[^a-z]+", generic.lower())[0]
    brands, seen = [], set()
    for b in (x.strip() for x in row[1].split("|")):
        low = b.lower()
        # repackager labels like "Sertraline HCl" or "Zolmitriptan OD" repeat the generic name: not brands
        if not b or low in seen or low == generic.lower() or (base and base in low):
            continue
        seen.add(low)
        brands.append(b.title() if b.isupper() else b)
    strengths = []
    for st in row[2].split("|"):
        st = clean_strength(st)
        if st and not LIQUID_RE.search(st) and st not in strengths:
            strengths.append(st)
    if not strengths:                                       # liquid/injectable-only drugs are out of scope
        return None
    strengths.sort(key=lambda x: (first_number(x), x))
    return [generic, "|".join(sorted(brands, key=str.lower)), "|".join(strengths), "|".join(sorted(set(ingredients)))]


def clean_file(in_path, out_path):
    with open(in_path, newline="", encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter=";") if len(r) >= 4]
    out = {}
    for r in rows:
        c = clean_row(r)
        if c and c[0].lower() not in out:
            out[c[0].lower()] = c
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        for k in sorted(out):
            w.writerow(out[k])
    print(f"cleaned {len(rows)} -> {len(out)} rows: {out_path}", file=sys.stderr)


def main(out_path):
    counts = defaultdict(int)
    for form in FORMS:
        data = get({
            "search": f'product_type:"HUMAN PRESCRIPTION DRUG" AND dosage_form:"{form}"',
            "count": "generic_name.exact", "limit": 1000,
        })
        for r in data.get("results", []):
            counts[r["term"]] = max(counts[r["term"]], r["count"])
        time.sleep(DELAY)
    names = [n for n, c in counts.items() if c >= MIN_PRODUCTS]
    print(f"{len(names)} candidate generic names", file=sys.stderr)

    rows = {}
    for i, name in enumerate(sorted(names)):
        data = get({
            "search": f'product_type:"HUMAN PRESCRIPTION DRUG" AND generic_name.exact:"{name}"',
            "limit": 100,
        })
        generic = name.title() if name.isupper() else name
        brands, strengths, ingredients = set(), set(), set()
        for rec in data.get("results", []):
            b = (rec.get("brand_name") or "").strip()
            if b and b.lower() != name.lower() and b.lower() not in name.lower():
                brands.add(b.title() if b.isupper() else b)
            acts = rec.get("active_ingredients") or []
            if acts:
                strengths.add(" / ".join(norm_strength(a.get("strength", "")) for a in acts if a.get("strength")))
                for a in acts:
                    if a.get("name"):
                        ingredients.add(a["name"].lower())
        strengths.discard("")
        key = generic.lower()
        if key not in rows:
            rows[key] = [generic, "|".join(sorted(brands, key=str.lower)), "|".join(sorted(strengths)), "|".join(sorted(ingredients))]
        if i % 100 == 0:
            print(f"  {i}/{len(names)}", file=sys.stderr)
        time.sleep(DELAY)

    raw_path = out_path + ".raw"
    with open(raw_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        for k in sorted(rows):
            w.writerow(rows[k])
    print(f"wrote {len(rows)} raw rows to {raw_path}", file=sys.stderr)
    clean_file(raw_path, out_path)


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "--clean":
        clean_file(sys.argv[2], sys.argv[3])
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/drugs.csv")
