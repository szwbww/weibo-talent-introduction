# Child 04 — Execution log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-04-qualification.md`
Plan SHA-256: `04b07f58de1d6e9199e0919047cfffde967896f1bd4cfcad526b40a7d412dd0d`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-04-qualification.md@04b07f58de1d6e9199e0919047cfffde967896f1bd4cfcad526b40a7d412dd0d`
Execution epoch: NEW
Approval basis: current child04 invocation, exact approved brief, explicit local commit authority.
Executor: Recheck04Implementer; `execute-p`, without independent verification skills.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`
Target branch: `fast/discovery-recheck-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master@fast/discovery-recheck-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA / child base: `bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a`
Pre-execution governance HEAD: `3a7dac56a3b8978c2b2178ef3b70a452ecbda533`
Post-execution code SHA: `6884eb7f31b1763dbb14422e91dfd63dc022fb94`
Evidence HEAD: N/A — controller owns the subsequent evidence commit; this report is intentionally uncommitted.
Implementation boundary: `bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a..6884eb7f31b1763dbb14422e91dfd63dc022fb94`; child04 product commit `feat(fast-p): implement 04`.

### Task Status

| Requirement | Status | Files / implementation evidence |
|---|---|---|
| I-1 /方案1: unique biography subject and audited grammar | IMPLEMENTED | Extractor accepts inline full-name biographies, IEEE parentheticals, uniquely owned same-paragraph pronouns/surname continuations, parallel degrees, holds/awarded/dr.philos/Doctor of Philosophy, compound biography employment and Since YYYY. Ownership stops at new authors, initials-bearing new subjects, headings/references or paragraph boundaries. Sentence-scoped negatives do not erase separate completed degrees. Role conflicts retain distinct conflicting original terms; unresolved Ass.Prof/header-versus-lecturer does not become a positive professor assertion. |
| I-1 acceptance: positive/negative structures | IMPLEMENTED | Authorized extractor tests cover Q01–Q13 and N01–N04 derived from recovered reviewed classifications, additional doctoral-student/negation/duplicate/ambiguous author controls, title conflict, wrapped/long source quotes, and real PDF page locators. |
| I-2: historical facts and explicit birth dates | IMPLEMENTED | Added BIRTH_DATE, canonical ISO value with original excerpt; valid full dates only, <= optional complete publication date or known-year Dec31. Missing/invalid/future/year-only dates and graduation-based inference rejected. Paper facts retain PUBLICATION_TIME; runtime historical employment alone remains UNKNOWN. |
| 方案2: independent qualification analysis version | IMPLEMENTED | qualification RULE_VERSION=3; parserVersion qualification key advances independently of identity extraction/layout/format versions. Analysis cache test replaces an old qualification=2 NOT_FOUND cache, observes reparse and completed fact, and asserts original outcome JSON unchanged. Analysis schema/analysisVersion and saved body SHA contracts retained. |
| I-3 /方案3: bound ORCID local name compatibility | IMPLEMENTED | Rule3-only full-token rotation/unique surname+initial reconciliation requires bound ORCID person/other-names URL plus matching identityHash/factFingerprint. Agbo counterfactual, unbound/wrong-provider/wrong-endpoint/hash/fact negatives, spelling and missing-initial negatives covered. Ordinary sameName/Apollo matching is not broadened. R counterfactual remains UNKNOWN/PENDING. |
| I-4 /方案4: compound qualification and frozen evaluation | IMPLEMENTED | Rule3-only local title parsing accepts Lecturer/Assistant Professor in Marketing while preserving assistant-to, retired/emeritus/student and historical-job negatives. Explicit evaluatedAt drives both age/date and trace; repeatability tested across different clocks. Brendan stays MET/PENDING with SEARCH_INCOMPLETE. |
| I-4: old and new frozen versions | IMPLEMENTED | Registration freezes3 for new runs; Worker accepts0..3, preserves stored payload version and old missing-settings default1, rejects4. Actual Registration→Worker→SQL→Gate/Query fixture exercised in JUnit and a standalone runtime. Legacy Rules comparison against child-base source produced36 exact full-decision equalities across versions0/1/2. |
| Global boundary | IMPLEMENTED | Product commit contains exactly the nine authorized paths. No migrations, production admission/ES writes, proof/queue-version changes, mail/contact/outreach changes, historic re-signing/backfill, or later-child implementation. Existing Worker→Rules→Gate/Commit route remains in use. |

### Recovered fixture provenance and privacy

The retained worktree did not contain the approved audit report linked from the evidence README. This was reported to the controller rather than treated as a waived acceptance. The controller recovered the exact report and reviewed-facts JSON read-only into internal local artifacts. No other worktree or server file was changed by this worker. Recovered JSON SHA-256: `90876f6ebad74722964679de33ffb5738b94e22e8da278f08cab38c6232d7b54`.

Only short anonymized structures are committed. Q01–Q13 follow reviewed positive-person order, not incidental neighboring matches:

| Synthetic ID | Reviewed positive facts / conservative treatment |
|---|---|
| Q01 | Completed doctorate + full birth date; title conflict is not positive professor evidence |
| Q02 | Completed doctorate, dr.philos |
| Q03 | Completed doctorate + assistant professor |
| Q04 | Completed Doctor of Philosophy + professor |
| Q05 | Assistant professor after earlier non-doctoral degrees |
| Q06 | Completed doctorate, holds |
| Q07 | Completed doctorate + associate professor |
| Q08 | Completed doctorate; unresolved Ass.Prof not classified |
| Q09 | Completed doctorate after IEEE annotation and earlier degree |
| Q10 | Assistant professor with discipline suffix |
| Q11 | Associate professor, inline biography |
| Q12 | Associate professor, inline biography |
| Q13 | Completed doctorate after B.Sc. plus Since YYYY associate professor |

N01 is the adjacent-student ownership control; N02–N04 preserve the illustrative third-person birth/QA pattern against different author contexts. Additional controls cover paragraph, heading, unknown full-name and unknown-initial subject cutoffs. No private person list, email, item IDs or long original quotations were copied into the repository or this report.

### Commands

All repository shell commands used the explicit target worktree as cwd.

| Exact command / invocation | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-04-qualification.md` | PASS | Exact approved hash above at initial and pre-handoff gates. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py <absolute-plan-path> --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master` | PASS | Correct root/branch/Git directories and starting HEAD; rerun before staging/commit with exact `--expect-root`, `--expect-branch`, `--expect-git-dir`. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PaperQualificationEvidenceExtractorTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryPaperAnalysisServiceTest -DmysqlIt=true` — first implementation command | FAIL, exit1 | 61 JVM tests,2 failures,0 errors/skips. A conflict deduplication key collapsed two original titles, and a synthetic conflicting ORCID fixture used an invalid checksum. Both repaired within authorized files; not counted as final proof. Output `artifact://959`. |
| Same exact Maven command — intermediate state | PASS, exit0 | 61 JVM tests and1827 JS tests passed; subsequently strengthened initial-bearing ownership, lossless wrapped quotes and associated regressions, so this is not final-state proof. Output `artifact://962`. |
| Same exact Maven command — final product/test state | PASS, exit0 | 62 JVM tests: extractor15, AnalysisService8, Worker16, Rules23;0 failures/errors/skips. Bound complete JS1827 passed,0 failed/skipped, plus node-check-app and node-check-task-modal-runtime succeeded. BUILD SUCCESS,4m45s. Output `artifact://965`. |
| `python3 /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/child04-qualification-smoke-2cowdt34/run_smoke.py` — disposable baseline setup | Initial scaffold FAIL, then compiler/Javac PASS | First compiler pass could not smart-cast nullable public properties across the new compilation module. Added only two guarded `!!` assertions to disposable baseline source. No product edit. The subsequent runtime exposed that an original degree token alone is not the existing education-role completion contract; corrected only the disposable adapter to derive `completed` role semantics from the extracted COMPLETED_DOCTORATE kind. |
| `python3 /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/child04-qualification-smoke-2cowdt34/run_smoke.py 2` | PASS, exit0 | Recompiled changed disposable Java adapter, then actual extractor→source-derived VerificationEvidence→Rules and actual Registration/Worker/SQL fixture outside JUnit. Final runner26.84s, details below. Successful baseline compiler setup was retained rather than repeated. |
| `git diff --check && git diff --stat && git diff --name-only` | PASS, exit0 | Nine child04 product/test paths plus the pre-existing controller ledger modification; no whitespace error. Ledger was never edited/staged by this worker. |
| Exact nine-path `git add -- ...`, then `git commit -m 'feat(fast-p): implement 04'` | PASS, exit0 | Worktree identity helper checked before each mutation. Commit6884eb7 contains9 files,393 insertions/63 deletions; reports/logs excluded. |
| `git rev-parse HEAD fast/discovery-recheck-master && git merge-base --is-ancestor 6884eb7 fast/discovery-recheck-master && git show --format=fuller --stat HEAD && git status --short` | PASS, exit0 | HEAD and target branch both6884eb7f31b1763dbb14422e91dfd63dc022fb94; ancestor check exit0. Only controller ledger and child04 evidence directory remain dirty/untracked; product/index clean. |

### Standalone runtime evidence

The removed disposable launcher used JDK11, the final Surefire classpath, Kotlin K2JVMCompiler1.9.25, `javac --release 11`, and `java QualificationSmoke`. For baseline comparison, it loaded Rules source from the child-base SHA into a separately named class, sharing the real production decision/evidence types; adaptations were only the class name, removal of duplicate shared data-class declarations, and the two guarded cross-module smart-cast assertions. Original source SHA-256: `2116431276c584366d6128d33a136548f43203d7abee4f0c318ef983c01b5469`.

The qualification input was synthetic raw biography text, not desired-result data. The actual extractor emitted:

| Fact | Source locator / original term |
|---|---|
| COMPLETED_DOCTORATE | authorIndex0, paragraph1,2023, `Ph.D.` |
| ASSISTANT_PROFESSOR | authorIndex0, paragraph1,2023, `Assistant Professor` |
| BIRTH_DATE | authorIndex0, paragraph1,2023, `1989-07-23` |
| DOCTORAL_STUDENT | authorIndex1, paragraph2,2023, `PhD student` |

Every fact retained PUBLICATION_TIME and body SHA `221ba371f619fedae6b9a9d294023a4f540db6eb28f59f8dfc6782918563e7b8`. Literal excerpts were observed and asserted in the original body. The first paragraph said the subject received B.Sc./M.Sc./Ph.D. in2019, was assistant professor, and was born on the complete date; the second subject was explicitly a student. The disposable adapter populated real VerificationEvidence with PAPER provider, source SHA/excerpt, completed-degree semantics derived from the extracted kind, and historical employment. It did not pretend the future production paper-evidence bridge already exists.

Observed final output:

```text
QUALIFICATION_RUNTIME extractorFacts=4 falsePositiveControls=4 historicalOnly=UNKNOWN completedPlusBirth=MET frozenTraceAndAge=identical AgboRule2=NAME_CONFLICT AgboRule3=DIRECT_CLEAR R=UNKNOWN Brendan=MET/PENDING baselineFieldComparisons=36 productionWrites=0 externalRequests=0
QUALIFICATION_VERSION_SMOKE run=2001 workerRule=3 alias=2 sql=APOLLO_CLEAR gate=eligible projection=PROJECTED admission=0 requests=5
QUALIFICATION_VERSION_SMOKE frozenPayloadRule=0 state=COMPLETED outcome=PENDING externalHttp=0
QUALIFICATION_VERSION_SMOKE frozenPayloadRule=1 state=COMPLETED outcome=PENDING externalHttp=0
QUALIFICATION_VERSION_SMOKE frozenPayloadRule=2 state=COMPLETED outcome=APOLLO_CLEAR externalHttp=0
QUALIFICATION_VERSION_SMOKE frozenPayloadRule=3 state=COMPLETED outcome=APOLLO_CLEAR externalHttp=0
QUALIFICATION_VERSION_SMOKE frozenPayloadRule=4 state=ERROR outcome=null externalHttp=0
QUALIFICATION_RUNTIME actualRegistrationAndWorkerVersionSelection=PASS isolatedMySQL=true strictMockHTTP=true productionContext=false providerCosts=0
```

The Worker portion invoked the existing real registration/worker scenario directly outside the JUnit runner, using disposable MySQL and strict mocked HTTP, including Gate/Commit/Query and frozen older payloads. Its5 requests were fixture HTTP requests, not provider requests. Isolated test SQL/projection activity is not production business activity. The absent frozen setting still retained default1. No application production context, production query/write or live provider cost occurred.

Disposable Java source SHA: `2e51c36d5b0a4e3eb832bfea7f63715145ba920502ab0b7414803630400687c3`; adapted baseline SHA: `e767d624c981d62a722f5a979c53d7c59722198c7106ac9da605546ea9f16a47`; launcher SHA: `03e0d33c0b177c6db84d5161541be6a84bfb8dcf7d427d3817ba6e695e1b6971`. All disposable sources/classes/replay output were removed with the created temporary directory; removal was observed. No scaffold was added to the repository.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` — ownership-scoped grammar, explicit birth-date validation, lossless assertions and qualification implementation version3.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt` — BIRTH_DATE kind.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` — explicit qualification RULE_VERSION analysis-cache key.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` — bound local name reconciliation, versioned compound title qualification, optional frozen evaluation instant.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — frozen rule versions0..3 accepted.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — new runs freeze3.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt` — audited structural fixtures, false-positive/date/conflict/wrapped-source/cache regressions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` — three counterfactuals, anchor/negative controls, old full-decision contract, frozen age/trace.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — new version3 and frozen0..3/unsupported4 SQL/runtime coverage.

### Deviations and limitations

- Required commands ran only after implementation checkpoints, as explicitly required by the child brief; no failing pre-implementation TDD run or full verification skill was executed.
- Missing audit context was recovered by the controller; it is no longer a blocker. Coverage is anonymized reviewed structural fixtures, not a private full-corpus replay or a claim of13 production admissions.
- This child exposes Rules evaluatedAt; end-to-end Gate/Worker frozen-time propagation remains the explicitly later07 contract. The disposable bridge does not introduce the later06 production evidence bridge.
- No WAR package/publish, browser/manual acceptance, full Kotlin suite, private full-batch replay or production migration was requested/executed. The final prescribed test command includes compilation, the complete bound JS suite and the bound syntax checks.
- Existing compiler/Jackson deprecation and disposable MySQL/Flyway warnings were observed; no unrelated warning repair was attempted.
- The controller's pre-existing ledger modification and child evidence files were preserved and excluded from the implementation commit.

### Freshness

- Plan identity rechecked: YES — unchanged approved SHA.
- Worktree identity rechecked: YES — exact target root/branch/Git directory.
- Reported product commit reachable from target branch and equal to target HEAD: YES.
- Required commands run freshly after last product/test edit: YES.
- Historical/intermediate outputs used only as baseline, not final proof: YES.
- Product/index clean apart from separately owned uncommitted evidence: YES.

### Remaining Blocker

None.

### Next Action

Controller obtains the independent child04 light verifier required by fast-p. READY_FOR_VERIFICATION is execution handoff, not a PASS or human acceptance claim.


## Epoch 1 — Controller closing record

- Product boundary: bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a..6884eb7f31b1763dbb14422e91dfd63dc022fb94.
- Distinct fresh verifier Recheck04Verifier completed independent four-gate verification; required action COMPLETE_CHILD, verdict LIGHT_PASS_WITH_NOTES, O-04-01 record-only warnings/harness limits.
- Fix round0; no product repairs. Controller records this evidence without rerunning product work or claiming whole-system verification.
