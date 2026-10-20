# Apollo matching V2 / country supplement release

Authorization: current conversation explicitly authorizes implementation on `main`, production publication/restart, and enabling country supplement. No historical admission rewrite or mail sending is part of this change.

## Approved behavior

| Matching evidence (compatible name required) | Label | Admission |
|---|---|---|
| Current institution subject | 已明确 / 当前任职匹配 | Yes |
| Historical institution subject | 已明确 / 历史任职匹配 | Yes |
| Professor title | 已明确 / 教授职称匹配 | Yes |
| Specific academic specialty | 未明确 / 专业辅助匹配 | Yes |
| Name alone / unresolved multiple qualifying candidates | 待核验 | No |

Qualification facts remain separate: matching clarity does not fabricate a doctorate, birth date or institution-issued title. Existing protected/manual/contacted records and unrelated hard blockers remain protected.

Implementation boundaries:
- New discovery runs freeze `settings_json.apolloMatchingVersion=2`. Existing runs without it retain V1; saved search cursors and completed outcomes are not upgraded.
- V2 adds bound ORCID formal-name queries, alongside original name and aliases. V1 in-flight queries remain in their original sequence.
- Apollo masked surnames use variable hidden spans only to choose detail requests; a V2 admission requires a full compatible name.
- Candidates already supported by current institution / professor evidence are checked first. When such a candidate survives, unrelated name-only hits do not require paid details. Multiple supported candidates still block admission.
- Otherwise inspect historical employment and specific specialty. A historical institution requires explicit `current=false` and a nonfuture end date. Institution comparisons reuse subject extraction; no invented translations.
- Specialty matching uses whole normalized phrases from the available academic research fields against title/headline/current employment major; broad labels such as research, engineering, education and scientist are excluded. Missing research fields do not invent a match. No claim of semantic/translation coverage.
- Search and detail calls share existing SQL reservation budgets/retries/leases. Acknowledged detail results and completed IDs are saved together; resume does not repeat acknowledged detail calls.
- Matching fields and response hashes are retained; contact email/phone from Apollo detail are excluded.
- Statistics retain existing outcome buckets. New labels and evidence show matching basis and version; old records display the original qualification semantics.
- Existing academic enrichment pipeline is unchanged. V2 freezes academic fields available at decision time for replay.
- Country supplement uses the existing service before initial RAW/snapshot freezing. Production flag `talent-introduction.discovery-verification.country-supplement-enabled=true`; existing frozen profiles are not silently rewritten. It represents institution country, not nationality/residence.

## Actual API observations (2026-10-20)

Using the already authorized production API key on the production host (key not printed/copied):
- Duncan `61382888870a120001be1c7e`: GET person detail HTTP 200; name `Duncan Sutherland`, title `Professor`, current `Aarhus University`; previous `Chalmers University of Technology`, 1995-10-01–2006-02-01.
- Search `Robert Hadfield`: HTTP 200, total 11; Glasgow candidate `54a418c97468692cf02b022c`, masked surname `Ha***d`, title `Professor of Photonics`.
- Robert detail: HTTP 200; full name `Robert Hadfield`, current `University of Glasgow`, title `Professor of Photonics`; historical records include Heriot-Watt University, NIST and University of Cambridge.
- These probes prove endpoint access and these returned fields, not whole-population accuracy or new production admissions. Frozen-shape tests exercise the shared rules with both examples.

Official endpoint reference: https://docs.apollo.io/reference/get-complete-person-info (GET /api/v1/people/{id}).

## Validation / deployment

Build PASS: backend 5287 tests / 0 failures / 0 errors / 21 pre-existing opt-in skips; frontend 1725 passed. Included real isolated MySQL Apollo flow (31) and country supplement flow (8). Local and production JVM timezone probes PASS (10/10 offsets). `build.json` records command, source hashes and WAR checksum. Production receipt pending.
