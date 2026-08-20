# Aggregate Machine Verification — workbench-repair-00-execution-order

## Epoch 1 — 2026-08-20T01:31:04Z

- Master plan: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (sha256: 1baab881a3dcffb0297810b3ecf97927d6d6d5df5dbc0626a9884a9f43178226)
- Governing master identity: sha256 1baab881a3dcffb0297810b3ecf97927d6d6d5df5dbc0626a9884a9f43178226; recorded identity commit b830ec208e9fe51bd693436f92158f1fde76622b
- Master identity state: CONSISTENT; amendments N/A.
- Boundary: 3bd132cb429a6928aa0eaa7c9f72d733d6905a15..8ee03a9b207227890bca01da272207ff9a22f943
- Reviewer: /root/aggregate_reviewer
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A; review-p did not invoke repair-p because verification passed.

### Review-P Output

#### Boundary and artifact verification

- Base/code ancestry is valid. `8ee03a9b207227890bca01da272207ff9a22f943` precedes evidence head `7e01f1cacbe2c702028d3c325ebd7e04a1051a83`.
- Governing master, fast-p ledger, and fast-p handoff SHA-256 values exactly match their supplied identities.
- Product tree and index are clean against the final code head; the only pending path before this evidence update was the review-evidence directory.

#### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | 55 pass, 0 fail |
| `node --test src/test/js/*.test.js` | PASS | 663 pass, 0 fail |
| `node --check src/main/resources/static/trust-reply-workbench.js` | PASS | exit 0 |
| `mvn test -Dtest=TrustReplyWorkbenchItemFlowTest` | PASS | 35 tests, 0 failures/errors |
| Required targeted Maven class suites, comma-equivalent | PASS | 430 tests, 0 failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | 2611 tests, 0 failures/errors, 4 skipped |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | WAR produced; all test reports green |
| `git diff --check 3bd132cb429a6928aa0eaa7c9f72d733d6905a15..8ee03a9b207227890bca01da272207ff9a22f943` | PASS | exit 0, no output |

Surefire 2.22.2 rejects literal `+` class lists with “No tests were executed”. The exact child briefs prescribe comma-separated equivalent lists; the reviewer reran those equivalents fresh. This is not a product failure.

#### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | Exact master identity; four ordered children terminal; base/code/evidence ancestry valid. |
| 01-I1–I4 | PASS | `trust-reply-workbench.js:1678-1695`; role/data selector after render; shared-mount focus test passes. |
| 01-unchanged | PASS | Tab/panel ARIA markup at `:1726-1734`; no bootstrap path in page switch. |
| 02-I1–I5 | PASS | Paragraph constant at `AiReplyDraftService.kt:2345`; generation, canonicalization, materialization use it; trust boundary remains single-space; composer unchanged. |
| 03a-I1–I3 | PASS | Per-request subset versioning at `TrustReplyWorkbenchService.kt:1640-1666`, `:2070-2099`; assemble validates each locked item at `:1149-1211`. |
| 03a-I4–I7 | PASS | Partial restore at `:582-677`; schema v4/v3/v1 at state store `:122-130`, `:181-188`; no migration diff. |
| 03a frontend | PASS | Preserve/reconcile path in JS `:649-744`; changed-item-only reset; stale hint rendered. |
| 03b-I1–I3 | PASS | Seven-part source identity at `:1564-1581`; research evidence only for research-context items at `:1648-1659`; regression tests green. |
| 03b-I4/I6 | PASS | Context fingerprint observational only at `:1584-1589`; stale-item prompt/button/rerun at JS `:1036-1072`, `:1798-1803`, `:2159-2165`. |
| 03b must-not-change | PASS | Profile prompt composition preserved; `AiReplyContextBuilder`, intent gate, and auto-reply service untouched; guard tests pass. |
| Scope/style | PASS | Combined source/test diff is within union of child authorization; no migration, CSS, index, or prohibited runtime edits. |

#### Findings

No P1 or P2 findings. No prior aggregate finding lineage.

#### Observations

- 01: textual `instanceId`/`tabId(` counts are 5/7 rather than 4/6 only because the plan-mandated explanatory comment contains the tokens; selector removal and `panelId(` count 6 hold.
- 02: fixed line anchors shifted because the plan itself required an explanatory comment; content and invariant hold.
- 03a: JS retains assemble `expectedEvidenceSetVersion` on wire because the unchanged controller DTO requires it; server global pre-check is removed and per-item validation is correct.
- 03a: legacy v1 locked states safely restore as STALE under per-request semantics; no silent acceptance occurs.
- 03b: `sha256Hex(mailHistory)` acceptance grep hits required `contextVersion()` implementation; source identity excludes it.
- 03b: `resetVersions()` textual grep has a comment-only fourth hit; three functional sites remain.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1/O-2 | Fix numeric-leading `instanceId` selector without deleting tab/panel identity behavior. | Not a violation. | Functional selector removal, focus test, and panel identity checks pass; extra token hits are plan-mandated comments. |
| 02 O-1 | Standardize claim paragraph separator while preserving composer and trust boundary. | Not a violation. | Required comment shifts line anchors; constant propagation and single-space trust boundary pass. |
| 03a O-1 | Assemble must not reject unchanged locked items due to a whole-draft evidence check. | Not a violation. | DTO remains unchanged; assemble's global pre-check is gone and per-item validation passes. |
| 03a O-2 | Never silently accept legacy locks under incompatible per-request semantics. | Not a violation. | v1 locks degrade safely to STALE; fresh state-store and item-flow checks pass. |
| 03b O-1 | `sourceVersion` excludes training knowledge/mail history; context changes are item-scoped. | Not a violation. | Hash text resides only in required `contextVersion()`, outside the seven-part `sourceVersion()`. |
| 03b O-2 | Changing request facts resets only affected item state. | Not a violation. | Fourth `resetVersions()` hit is comment-only; three functional sites and no-call invariant pass. |
| Infrastructure | Run mandatory targeted test suites. | Not a violation. | Fresh comma-equivalent Surefire suites pass; literal plus syntax is rejected by Surefire 2.22.2. |

#### Manual status

Manual UI, mail-delivery, and operational checks are pending exactly as listed in the four ordered child plans.

Repair planning: N/A.

No product code was modified.
