#!/usr/bin/env python3
"""
Builds app/src/main/assets/drugs.csv from the openFDA NDC directory.

Source: https://open.fda.gov/apis/drug/ndc/  (openFDA data is CC0 1.0, see https://open.fda.gov/terms/)
Scope : human prescription drugs in oral solid forms. Output is name data only:
        generic name, brand names, strengths, active ingredients. NO dosing, frequency or limits.
Run   : python3 tools/build_drug_dataset.py [out.csv]      (needs network; ~10 minutes, rate-limited)
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

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        for k in sorted(rows):
            w.writerow(rows[k])
    print(f"wrote {len(rows)} rows to {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/drugs.csv")
