## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 09a — `docs/plans/2026-09-26/discovery-repair-09a-orcid-query.md`
Boundary: `9c6d84430ec0f5f25e3fa06468ad7dcde260fe67..f9caa0b6fa5a1893aa37a6dd8612c8b930635eaf`
Verifier: OrcidQueryVerifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Product commit `f9caa0b6` changes precisely the three approved production files and three approved tests; intervening commits `ac69b49`/`ff38050` contain fast-p evidence/preparation only. No schema, pipeline, or product file outside the six-file list changed. |
| Plan and invariants | PASS | `OrcidDataSource.kt:97-102` retains UTF-8 parameter encoding and calls URI overload; real RestTemplate/MockRestServiceServer tests `OrcidDataSourceTest.kt:60-96` verify one decode for engineering, computer science, Chinese and literal +/%/quotes, independent `start`/`rows`, and unchanged ORCID-ID expression. `DiscoveryCheckpointCodec.kt:54-80` applies only the ORCID suffix; `ExpertDiscoveryService.kt:1496-1502` shares it with queue hash while `:930-958` loads/persists the new sync key, and `:1516-1537` seeds queue only from new source keys. `ExpertDiscoveryServiceTest.kt:1995-2082` starts with old `3|9000`, observes first request `start=0`, then `start=1`, persists `0|1` then `0|2`, keeps both old rows, checks distinct old/new ORCID keys/hash and invariant OpenAlex key/hash. Fresh `target/discovery-plan-acceptance/09a.json` contains captured URI/decoded q, cursors, hashes, two old rows remaining and zero deleted; neither old payload/stream/job nor global pipeline hash/epoch was changed. |
| Required commands | PASS | Fresh after `f9caa0b6`: `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=OrcidDataSourceTest,DiscoveryCheckpointCodecTest,ExpertDiscoveryServiceTest` exited 0 (`BUILD SUCCESS`, `artifact://741`): JUnit 146+10+11=167, 0 failures/errors/skips; Maven Node phase 1194 pass, 0 fail/skipped. Baseline `artifact://697`: JUnit 164, 0 failures/errors/skips, exit 0. Existing failed-page replay test remains in the selected ExpertDiscoveryServiceTest class. |
| Downstream interfaces | PASS | `queueQueryHash(sourceName, criteria)`, `sourceKey(sourceName, criteria)`, `canonicalCriteria(criteria)`, cursor envelope and existing query/filter/cursor APIs retain signatures; only `sourceCanonicalCriteria(sourceName, criteria)` was added. 09b's single `researchFieldIds` field/mapping contract is untouched; no model, ES mapping, or 09b admission/classification files changed. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Existing `DiscoveryPipelineService.kt:704-708,793-843` iterates all retained ACTIVE streams, not only current-query-hash streams. Consequently a previously ACTIVE ORCID stream can still be collected separately at its old offset despite the new stream/hash starting at zero. This is not inheritance into the new stream and the approved 09a plan expressly preserves old streams/jobs; changing legacy-stream operation needs an unauthorized pipeline change/policy decision, so it is outside automatic repair.

### Required Action
- COMPLETE_CHILD