# Child 04 — Verification log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04 — `docs/plans/2026-10-24/discovery-recheck-04-qualification.md`
Boundary: `bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a..6884eb7f31b1763dbb14422e91dfd63dc022fb94`
Verifier: Recheck04Verifier (independent of writer Recheck04Implementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --stat bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a 6884eb7f31b1763dbb14422e91dfd63dc022fb94` and product diff: six authorized production files and three authorized test files; other boundary entries are preceding child03/ledger governance only. No additional product/test path. |
| Plan and invariants | PASS | Direct evidence detailed below; required regression tests freshly passed. |
| Required commands | PASS | Exact prescribed JDK11 Maven command freshly exited0:62 JVM,1827 JS, zero failures/errors/skips; both bound syntax checks succeeded. Independent standalone extractor→real evidence→Rules and actual Registration/Worker fixture runtime exited0 outside JUnit. |
| Downstream interfaces | PASS | Rules exposes optional `evaluatedAt: Instant? = null`, accepts0..3; Registration freezes3; Worker accepts frozen0..3/rejects4. Qualification cache implementation key advances independently. No later-child06 production paper bridge or child07 end-to-end time propagation implemented/reviewed. |

### Direct requirement evidence

- Extractor `PaperQualificationEvidenceExtractor.kt:55–146,207–245`: unique full metadata owner, paragraph-local subjects/pronouns, unknown/new subject and heading/reference cutoffs, sentence-scoped completion/student/negation handling, distinct conflicting roles, explicit valid full birth dates bounded by complete publication date or year-end, source quote/page/paragraph/year/SHA retention. Paper facts retain PUBLICATION_TIME.
- `PaperQualificationEvidenceExtractorTest.kt:114–159`: Q01–Q13 and N01–N04. Read-only private reviewed-facts context independently confirmed thirteen positive-person fact sets in the same order, including title-conflict and unresolved abbreviation treatment, plus one next-person negative and three illustrative third-person birth negatives. No private identities/raw quotations copied here. Additional student, negation, conflict, heading and source-boundary tests are at161–189,220–225.
- Rules `DiscoveryVerificationRules.kt:41–110,393–414,454–466`: optional frozen instant used for age/date/trace, local bound ORCID endpoint/hash/fingerprint compatibility only, full-token rotations or complete unique surname/initial alignment, version3-only compound role parsing, prior title branch retained for0/1/2. Ordinary Apollo/sameName behavior not expanded.
- `DiscoveryVerificationRulesTest.kt:488–585`: Agbo ordering removes NAME_CONFLICT only under bound evidence; wrong provider/endpoint/ORCID/hash/fingerprint, spelling, omitted initials and arbitrary permutations rejected. R.Krishnamoorthy remains UNKNOWN/PENDING. Brendan becomes MET but SEARCH_INCOMPLETE/PENDING. Retired/emeritus/student/assistant-to and historical job negatives retained. Exact complete decision equality for legacy0/1/2 and frozen age/trace across different clocks pass freshly. This verifier does not claim the implementer's separate36-case baseline replay as fresh evidence.
- `DiscoveryPaperAnalysisService.kt:28–51,63–68` and extractor test194–216: independent qualification RULE_VERSION3 in parserVersion; old qualification2 NOT_FOUND reparsed; first outcome JSON unchanged; no network calls. Existing AnalysisService tests39–84 freshly cover body/hash/first immutability and cache force behavior. Analysis schema remains2; queue/proof compatibility versions not modified by this boundary.
- `DiscoveryVerificationRegistrationService.kt:98` freezes3; Worker supported range at221. `DiscoveryVerificationWorkerTest.kt:196–310` exercises actual Registration→Worker→isolated SQL, frozen0/1/2/3, unsupported4, missing-setting default1, and unchanged resumed settings.

### Fresh commands and runtime evidence

All repository command cwd: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`. No product/test edits by verifier.

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PaperQualificationEvidenceExtractorTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryPaperAnalysisServiceTest -DmysqlIt=true`
   - Exit0, BUILD SUCCESS,4m48s; finished2026-10-25T02:29:48+08:00; output `artifact://983`.
   - JVM62: extractor15, analysis8, Worker16, Rules23; failures0/errors0/skips0.
   - Bound JS1827 passed, failed0/cancelled0/skipped0; node-check-app and node-check-task-modal-runtime succeeded.
   - Compared with execution.md final baseline62/1827/zero skips: same passing totals; no failure requiring baseline classification.
2. Standalone temporary Java adapter compiled and ran via Python subprocess, not a JUnit runner:
   - `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac --release 11 -cp <CP> /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/recheck04-verifier-ne2j_vi3/RecheckSmoke.java` — exit0.
   - `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/recheck04-verifier-ne2j_vi3:<CP> RecheckSmoke /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/recheck04-verifier-ne2j_vi3` — exit0.
   - `<CP>` is the verbatim `java.class.path` property from the freshly generated Worker Surefire XML, passed as one subprocess argument; adapter source SHA256 `45062f7f59b63522232b35f7933f006029d6fc4114979ecaca458d079b305868`.
   - Synthetic raw biography fed the actual extractor: completed Ph.D., assistant professor and full birth date for author0/paragraph1; doctoral student for author1/paragraph2. All four original excerpts were observed, contained in input, with PUBLICATION_TIME and identical supplied body SHA.
   - Adapter constructed real VerificationEvidence from extracted kinds/terms/quotes, mapping completed degree to existing completed education semantics and publication employment to HISTORICAL. Historical employment alone UNKNOWN; extracted doctorate+birth MET. Four raw false-positive controls produced no facts. Actual Rules compound title rule2 UNKNOWN versus3 MET; explicit frozen evaluation identical under2026 and2035 clocks.
   - Independent launcher directly invoked the existing isolated version-selection scenario method (without JUnit discovery/runner), with its temporary replay root supplied explicitly: actual Registration/Worker/SQL and strict mock HTTP. Observed new run frozen3, Worker3, alias2, APOLLO_CLEAR/projected,5 fixture requests; frozen0/1 COMPLETED/PENDING,2/3 COMPLETED/APOLLO_CLEAR,4 ERROR; cached-version paths externalHttp0; legacy missing-settings default1 assertion passed.
   - Testcontainers MySQL8.0.36 was disposable. The adapter did not bootstrap a production application context or contact paid/live providers. Fixture SQL/projection activity is not production activity. No actual future production paper-evidence bridge is claimed.
   - Temporary Java/classes/replay directory removed after execution; absence observed. No temporary scaffold retained in repository.

### AUTO_FIX
- N/A — no proven four-gate defect.

### RECORD_ONLY
- O-04-01: Fresh compile/runtime output contains existing compiler/Jackson and disposable MySQL/Flyway deprecation warnings; standalone harness uses the test classpath and fixture helpers. No demonstrated child04 production defect; no unrelated warning investigation or repair.

### Required Action
- COMPLETE_CHILD

This is child04 four-gate light verification only, not whole-system PASS, private full-batch replay, production end-to-end acceptance, future child06/07 verification, WAR deployment or human acceptance.

## Epoch 1 — Attempt 1 closing metadata

- Reviewed boundary: bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a..6884eb7f31b1763dbb14422e91dfd63dc022fb94.
- Verifier: Recheck04Verifier, distinct from Recheck04Implementer.
- Fresh required command completion: 2026-10-25T02:29:48+08:00; exact observed results and runtime limitations above.
- Controller acceptance: COMPLETE_CHILD; fix_round0. This closing metadata adds no new test, runtime, or review claim.
