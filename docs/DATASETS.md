# Bundled datasets

## `app/src/main/assets/drugs.csv` (prescription name list)

| | |
|---|---|
| Source | openFDA National Drug Code (NDC) directory, `https://api.fda.gov/drug/ndc.json` |
| Retrieved | 2026-11-23 (openFDA reported `last_updated` 2026-11-20) |
| License | openFDA data is public domain under CC0 1.0 (https://open.fda.gov/terms/, https://open.fda.gov/license/). Attribution is not required but given here. |
| Disclaimer | openFDA: "Do not rely on openFDA to make decisions regarding medical care." jPill uses it only to suggest names and strengths; it never supplies doses, frequencies or limits from this file. |
| Scope | Human prescription drugs in oral solid forms (tablets, capsules, ER/DR/ODT/chewable variants). |
| Rows | 826 generic names (about 60 KB). |
| Columns | `generic;brands(\|);strengths(\|);ingredients(\|)`, semicolon-separated, RFC-4180 quoting. |

**How it is built:** `python3 tools/build_drug_dataset.py` (needs network, about 30 minutes, rate-limited). It first lists the 1,000 most frequent generic names per oral dosage form, fetches up to 100 NDC records for each, then cleans the result (`--clean` re-applies the cleaning offline).

**Cleaning rules:** drop names over 60 characters, with digits, or with more than 4 active ingredients (multivitamins/kits); drop brands that merely repeat the generic name (repackager labels such as "Sertraline HCl"); drop liquid/injectable/topical strengths; normalise strengths (`.25 mg` to `0.25 mg`, `ug` to `mcg`) and sort numerically; drop drugs left with no solid-form strength.

**Known limitations**
- Brand coverage is partial: at most 100 NDC records are read per generic name, and brand products are a small share of them. The curated `RxCatalog` supplies brands for the most common drugs.
- NDC directory entries are typed by manufacturers, so a few names contain typos (for example "Lamotirigine"). Curated entries rank first in search, so common drugs are unaffected.
- Salt-form variants ("Sertraline" and "Sertraline Hydrochloride") both appear; search shows only the first per active ingredient.
- Strengths are product strengths from labels, not recommendations. The prescriber's instructions always govern the dose.
