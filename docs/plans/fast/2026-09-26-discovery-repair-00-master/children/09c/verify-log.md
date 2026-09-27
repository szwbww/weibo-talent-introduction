## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 09c — `docs/plans/2026-09-26/discovery-repair-09c-scope-classification.md` (approved plan SHA-256 `df4280873bfb0df5b468b92f3a2c4649cda8a6014a35d3c52ee2ebd8e111131b`)
Boundary: `3df3d79a1806081639ad318fb6b638c59f5bca85..5724b4ff34619acf8abd8ee68c724ea25669742c`
Verifier: ScopeClassificationVerifier (independent of ScopeClassificationImplementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Product commit changes exactly the nine files in the brief: three production Kotlin, five focused tests, one JSON fixture; `git diff --check` clean. No 09d eligibility/revalidation/writer, migration, send configuration, scheduler enablement or production-data edit. Controller-owned documentation changes outside the product boundary were not treated as product edits. |
| Plan and invariants | PASS | `OpenAlexDataSource.kt:521-531,616,654-665` uses the existing count-ranked five topic nodes, only textual `field.id` matching numeric/exact HTTPS field URI, all-or-null on invalid/missing selected node, normalized unique numeric-ascending IDs; single and batch author paths share it with no new request. `ExpertDiscoveryService.kt:2644-2690,2703-2725` writes a nonnull list explicitly to each existing layer, omits null, copies the effective fact into classification and retains identity CAS, real `_id`, no upsert, and partial failure reporting. `ExpertClassificationService.kt:34-75,98-113,186-201,232-243` preserves clinical/medical precedence and scores, then applies six-ID discovery-only unknown/outside/target decisions, with normalized IDs/discovery in the fingerprint, new version and old non-discovery thresholds. Five audited real fixture records match their source IDs, original hashes, input fields and scores (Gebeyehu 0/80 and medical rejection); none has invented field IDs. Constructed six-positive, mixed, four outside, no-ID, parser-invalid, clinical cases are separate. Old JSON round-trip and version-gate guard pass. |
| Required commands | PASS | Fresh `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertClassificationServiceTest,ExpertClassificationSchedulerTest,ExpertClassificationAdminControllerTest,ExpertClassificationVersionGateGuardTest` exited 0, `BUILD SUCCESS` (`artifact://858`): 265 JVM tests, 0 failures/errors/skips; Maven-bound Node 1,194 pass, 0 failures/skips. Prior boundary baseline `artifact://803`: 260 JVM tests, 0 failures/errors/skips and exit 0; Node 1,194 pass. |
| Downstream interfaces | PASS | 09b `ExpertProfile.researchFieldIds: List<String>?` remains the sole trusted fact, `SubjectScopeCatalog` six existing IDs remain authoritative, and `classify(profile)` exposes the versioned decision for 09d to consume. `target/discovery-plan-acceptance/09c.json` contains 18 real-vs-constructed decisions with scores, negativeEvidence, fingerprint and version; parser cases, six three-layer updates across two emails/one lookup, null omission, identity CAS, partial failure and old JSON flag. Its sections equal the freshly regenerated test outputs `09c-classification.json`, `09c-layers.json`, `09c-partial.json`. Candidate admission/removal is deliberately deferred to 09d. |

### AUTO_FIX
- N/A; no proven unique authorized repair.

### RECORD_ONLY
- Release observation: existing disabled-by-default scheduler uses `onlyPending=true` and the current `VERSION`; if enabled, previously classified `rnd-v2-2026` records become pending under `rnd-v3-20260927`. No automatic backfill was triggered. Unmodified comments in scheduler/backfill still mention the previous literal version; executable paths use `VERSION`. This is not a 09c product defect or authorization for broad edits.

### Required Action
- COMPLETE_CHILD