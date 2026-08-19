# Execution Report — 03b-source-version-split (fast-p child)

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order/docs/plans/2026-08-19/workbench-repair-03b-source-version-split.md
Plan SHA-256: 7b36d9aff1a81a75eef407a3ebe254499c3249d84b1b8b61202b2ebb144f1ed2
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order/docs/plans/2026-08-19/workbench-repair-03b-source-version-split.md@7b36d9aff1a81a75eef407a3ebe254499c3249d84b1b8b61202b2ebb144f1ed2
Execution epoch: NEW
Approval basis: fast-p child brief (03b-source-version-split/brief.md) + approved plan bytes
Executor: Impl03bSourceVersionSplit (fast-p child 03b implementer, safety-critical last child)
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
Target branch: fast/workbench-repair-00-execution-order
Pre-execution code SHA: e2ad440157017fb6ced066fe63ad2d5e104a8296 (child 03a terminal Code head)
Post-execution code SHA: 8ee03a9b207227890bca01da272207ff9a22f943
Implementation boundary: e2ad440..8ee03a9 (8 files, 807 insertions, 49 deletions)

### Research checkpoints (plan-mandated, run before editing)
| Checkpoint | Result | Evidence |
|---|---|---|
| `grep -rn "sourceVersion" --include=*.kt src/test \| wc -l` | 151 (2026-08-19 baseline 137) | baseline recorded; delta = tests added by children 02/03a, not an unidentified read path — no PLAN_CONFLICT |
| `grep -rn "sourceVersion" src/test/js/*.js \| wc -l` | 104 (baseline 124) | 03a refactored JS coverage/version assertions; delta is structural, not a read path — no PLAN_CONFLICT |
| 03a landed: `grep -n "requestEvidenceVersion" TrustReplyWorkbenchService.kt` | HIT (20+ sites incl. def :2067, resolver :1658) | 03b proceeds |
| Guard-test class existence (`AiReplyIntentCatalogTest`, `GroundedAutoReplyDecisionServiceTest`, `AiTrainingQaServiceTest`) | ALL 3 EXIST (llm/service + mail/service) | no class dropped from the guard run; no 修正记录 entry needed |

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 `AiReplyContext` + `expertProfileText`/`trainingKnowledgeText` (I-5); profileText construction byte-unchanged | IMPLEMENTED | AiReplyContextService.kt | `buildExpertProfile` result extracted to local `expertProfileText`; `profileText = appendKnowledgeToProfile(expertProfileText, trainingKnowledge)` unchanged; 2 new defaulted fields; builder untouched |
| T2 `ResolvedTrustReplySource` + `expertProfileText`/`trainingKnowledgeText`/`contextVersion`; resolveWithContact fills them | IMPLEMENTED | TrustReplyWorkbenchService.kt | data class fields (defaulted); resolveWithContact passes `context.expertProfileText`/`context.trainingKnowledgeText` and `contextVersion(context.trainingKnowledgeText, context.mailHistory)` |
| T3 `sourceVersion()` narrowed to 7 identity components; new `contextVersion()` (I-1/I-6) | IMPLEMENTED | TrustReplyWorkbenchService.kt | sourceVersion() body grep-verified: 7 components only; contextVersion() at :1588, never in requestKey/versionId/requestEvidenceVersion/aggregateEvidenceVersion (I-6 grep clean at identity-fn lines) |
| T4 research evidence only into research items' per-request evidence (I-2/I-3) | IMPLEMENTED | TrustReplyWorkbenchService.kt | `requestEvidenceVersion(..., researchEvidence: String? = null)`; null path byte-identical to 03a; resolver, FULL_DRAFT, buildInitialItemVersions all mix `sha256Hex(expertProfileText) + " " + researchProfileSufficient` only when `item.requiresResearchContext` |
| T5 responses carry contextVersion; item/locked versions record it; restore never drops on context | IMPLEMENTED | TrustReplyWorkbenchService.kt | Bootstrap/Assemble responses + `TrustReplyItemVersion` + `TrustReplyLockedItemRequest` (round-trip) carry it; materializeVersion fills it from resolved.contextVersion; restoreSavedStateWithFrame doc updated — context mismatch never drops (sourceVersion is identity-only), restored locks carry their fingerprint for client-side comparison |
| T6 frontend per-item prompt + one-click rerun (I-4/S-1/S-2) | IMPLEMENTED | trust-reply-workbench.js | state.contextVersion; per-request contextStale; verbatim S-1 span beside 03a's evidence-stale span; verbatim S-2 button at end of status area only when ≥1 context-stale item; `regenerate-context-stale` onClick → shared runItemSequence (skipResolved:false) + one final durable save; never resetVersions/handleStaleGeneration/re-bootstrap |
| Tests (4 Kotlin + 1 JS files) | IMPLEMENTED | 4 Kotlin + 1 JS test files | I-1 (a/b/c), must-NOT-change-1, I-2/I-3, I-5, I-4 (JS) acceptance tests added; targeted 155 + guard 57 + JS 663 + full 2611 green |

### Commands
| Command | Result | Evidence |
|---|---|---|
| Research checkpoint greps (2) + 03a presence grep | PASS | exit 0; baselines 151 / 104 recorded; requestEvidenceVersion hit |
| `JAVA_HOME=…zulu-11… mvn test -Dtest='AiReplyContextServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,PendingMailOperationServiceTrustWorkbenchTest'` | PASS | exit 0; Tests run: 155, Failures: 0, Errors: 0; node-test execution ran; BUILD SUCCESS (comma-form; surefire 2.22.2 rejects '+'-lists) |
| `JAVA_HOME=…zulu-11… mvn test -Dtest='AiReplyIntentCatalogTest,GroundedAutoReplyDecisionServiceTest,AiTrainingQaServiceTest'` | PASS | exit 0; Tests run: 57, Failures: 0, Errors: 0; all 3 classes exist — none dropped; BUILD SUCCESS |
| `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | exit 0; tests 55 / pass 55 / fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 663 / pass 663 / fail 0 |
| `node --check src/main/resources/static/trust-reply-workbench.js` | PASS | exit 0, SYNTAX_OK |
| `JAVA_HOME=…zulu-11… mvn test` (full) | PASS | exit 0; Tests run: 2611, Failures: 0, Errors: 0, Skipped: 4 (pre-existing); node-test ok; BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn clean package` | PASS | exit 0; Tests run: 2611, Failures: 0, Errors: 0, Skipped: 4; node-test ok; BUILD SUCCESS |
| `git diff --check` | PASS | exit 0, no output |

### Changed Files
- src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt — T1: AiReplyContext +2 defaulted fields; build() extracts buildExpertProfile local (I-5)
- src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt — T2–T5: identity-only sourceVersion, contextVersion(), research-split per-request evidence, contextVersion on responses/versions/locked items, restore semantics doc
- src/main/resources/static/trust-reply-workbench.js — T6: state.contextVersion, contextStale per request, S-1 span, S-2 button, regenerate-context-stale via shared runItemSequence, contextVersion round-trip in lockedToVersion/serializeResolvedVersion
- src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt — I-5 new-field assertions (existing profileText assertions untouched)
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt — I-1 (a)(b)(c) + must-NOT-change-1 STALE regression
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt — I-2/I-3 research-split fixture + assertions; requestEvidenceVersion researchEvidence unit test; trailing-lambda call sites converted to named baseSnapshotOf
- src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt — archive re-assembly accepts locks under an older context fingerprint (context observational, auto-reply path unchanged)
- src/test/js/trustReplyWorkbenchSharedMount.test.js — I-4: stale hint + rerun button + exactly-N stream calls + no re-bootstrap/resetVersions

### Deviations
1. **I-1 file-wide grep acceptance vs T3 formula (plan-internal contradiction)**: T3 mandates `contextVersion` return `sha256Hex(listOf(sha256Hex(trainingKnowledgeText), sha256Hex(mailHistory)).joinToString(" "))` — the literal `sha256Hex(mailHistory)` appears inside contextVersion(). The I-1 acceptance `grep -n "sha256Hex(mailHistory)\|sha256Hex(profileText)"` (file-wide, expected no output) therefore cannot both hold. Resolved in favor of T3's explicit implementation formula: run as written (literal `\|` pipe) the grep yields no output; under ERE alternation it yields exactly 1 hit, line 1589, inside the plan-mandated contextVersion() — the designed location. The semantic invariant holds: sourceVersion() body contains only the 7 identity components (verified by function-body read), and `sha256Hex(profileText)` appears nowhere in the service. I-6 grep (contextVersion lines vs identity functions) is clean.
2. **`TrustReplyLockedItemRequest` gains `contextVersion: String = ""`** (same authorized file, T5 scope): required for the saved-state round-trip — without it the frontend could not compare a restored lock's context fingerprint with the current one (I-4), and the A-1/A-2/A-3 flows would be impossible. Observational only: never validated, never enters versionId/requestKey/evidence.
3. **Kotlin trailing-lambda call sites**: adding the defaulted 4th param to `requestEvidenceVersion` changes trailing-lambda binding (lambda binds to the new last param). The 4 sites (3 pre-existing test assertions + the `perRequest` helper, all in authorized TrustReplyWorkbenchItemFlowTest.kt) were converted to named `baseSnapshotOf = { … }` — identical hash inputs, byte-identical outputs.
4. **Line numbers**: children 02/03a shifted TrustReplyWorkbenchService.kt/trust-reply-workbench.js vs the plan's original-base anchors (plan's D-1 join separator is `" "`, actual `"\u0000"`; :1441/:1875/:521/:1712 etc. all moved). All anchors located by content and shifts recorded: sourceVersion() now :1544-1572, contextVersion() :1588, requestEvidenceVersion companion :2067, materializeVersion :1398, applyBootstrap :535+, renderFactSection staleMarkup :1750+, renderStatus :2120+, runItemSequence :790+. Existing separator `\u0000` preserved.
5. **restoreSavedStateWithFrame needs no structural change for context**: context staleness never dropped locks pre-03b either (contextVersion is new); the behavior change comes from narrowing sourceVersion (context no longer flips identity → locks restore), and the "which items differ" information travels on the restored locked items' contextVersion for the client-side comparison (T5/T6).
6. **Pre-03b legacy locks (no contextVersion) are flagged context-stale once** after deploy: `"" !== <current sha256>` — the conservative reading of "generated under old training knowledge/history"; cleared by regeneration or the one-click rerun, which persists fresh fingerprints durably.
7. **S-2 button renders in the interactive host only**: the read-only AUTO_PREVIEW host's status div uses a plain message and never calls renderStatus — no button there, consistent with its display-only role and S-2's "status area" contract.
8. **Surefire syntax**: plan's `-Dtest='A+B+C'` rejected by surefire 2.22.2; comma-form used as instructed in the brief.

### Freshness
- Plan identity rechecked: YES (sha256 unchanged 7b36d9aff1a81a75eef407a3ebe254499c3249d84b1b8b61202b2ebb144f1ed2)
- Worktree identity rechecked: YES (root/branch/git-dir matched before commit)
- Reported commit reachable from target branch: YES (8ee03a9 is HEAD of fast/workbench-repair-00-execution-order)
- Required commands run this invocation: YES (all 10, freshly, after final state)
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None.

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`
