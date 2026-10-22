# Child 01 verification log

## Light Verification: LIGHT_PASS
Child: 01 / docs/plans/2026-10-22/batch-demand-01-classification.md
Boundary: d403caeb5dcc4810067293c10600ee57f94e276b..756e8acaccc4675759152b4973e6b451a58f0a00
Verifier: Fast01LightVerifier epoch2 attempt1 2026-10-22T20:34:57+08:00

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Non-doc diff in the review range is exactly `ExpertSearchService.kt`, `ExpertDiscoveryService.kt`, `ExpertSearchServiceTest.kt`, `ExpertDiscoveryServiceTest.kt`, and new `ClassificationWireFormatTest.kt`; `OperatorStatusWriteSeamGuardTest.kt` intentionally unchanged because pinned `ExpertSearchService.kt:505` is above the inserted parser branch. All five changed files are in the six-file authorized table; commit `756e8ac` contains only these five files (300+/7-). |
| Plan and invariants | PASS | I-1: `ExpertSearchService.kt:566-590` adds the strict 5/6/7 integral/`canConvertToInt`, `LocalDateTime.of` array branch with `DateTimeException -> null`, no current-time fallback; legacy string/date/epoch paths untouched. I-2: `ExpertDiscoveryService.kt:3275` now writes `expertIndexWriterService.classificationNode(expertClassificationService.classify(enrichedProfile))` into the shared `doc`, which feeds both ordinary `doc` and discovered `params.doc`; no other classification write was changed. I-3: only the read parser and this one write assignment changed; identity CAS script, HEAD-404 ABSENT behavior, null-fact omission, no-upsert assertion, and other classification fields are unchanged/asserted. Wire test `ClassificationWireFormatTest.kt:58-139` uses `RestTemplateConfig().restTemplate()` + `MockRestServiceServer` for real converter bytes (asserts string `classifiedAt`, all fields equal writer node, no upsert, no identity keys), then replays through public `searchExpertsWithEmail` and real `RecipientScope.matchesExpertType`; malformed type/evidence/date fail closed. Search tests `ExpertSearchServiceTest.kt:2028-2072` cover 5/6/7 arrays, nanos 0/999999999, leap day, legacy formats, and empty/4/8/floating/overflow/nonintegral/invalid arrays in every layer. |
| Required commands | PASS | Fresh `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest,ExpertDiscoveryServiceTest,ClassificationWireFormatTest,ExpertIndexWriterServiceTest,ExpertClassificationServiceTest,OperatorStatusWriteSeamGuardTest` exited 0 with BUILD SUCCESS: 370 JVM tests, failures 0, errors 0, skipped 0 (93+194+2+51+29+1); Maven-bound Node run 1792 tests, 334 suites, fail 0, cancelled 0, skipped 0, todo 0; both `node --check` steps passed. 370 = controller baseline 365 excluding the then-absent `ClassificationWireFormatTest` + 5 added tests, with the same zero failure/error/skip baseline. Converter smoke is executed by the fresh `ClassificationWireFormatTest` run (2 tests passed). |
| Downstream interfaces | PASS | Child 02 depends on 01 but only consumes existing `ExpertSearchService` public query/cursor surfaces; this patch changes no public signature or constructor. `ExpertSearchService` diff is confined to private `parseClassifiedAt`; `ExpertDiscoveryService` diff is one private update-body assignment. No interface required by later children is altered. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

### Controller identity receipt

- Actual isolated dispatched verifier: Demand01LightVerifier (reviewer). The report's Fast01LightVerifier label denotes this same dispatch; writer identities were Demand01Implementer (blocked epoch1) and Demand01Epoch2Writer (epoch2). All distinct. No product checks were rerun for this identity annotation.
