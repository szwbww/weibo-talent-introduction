# Child 09 — Verification log

## Epoch 4 — Attempt 1 — fix_round 0 — 2026-10-25

## Light Verification: LIGHT_PASS
Child: 09 — docs/plans/2026-10-24/discovery-recheck-09-frontend.md (commit:47150fa045e7109ba60fad0a8ef19d7ae54d51a5)
Boundary: 59c34a1b6f04af0669a9276dae7c043bca4164f5..306664e25738d78e2587e175c05bb0724e778d71
Verifier: Recheck09Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-only 59c34a1b6f04af0669a9276dae7c043bca4164f5 306664e25738d78e2587e175c05bb0724e778d71 -- src` compared programmatically with the exact approved plan table: authorized40, changed40, outside[]. `git show 47150fa045e7109ba60fad0a8ef19d7ae54d51a5:docs/plans/2026-10-24/discovery-recheck-09-frontend.md` equals the read plan byte-for-byte. Interleaved governance excluded. Direct28-test diff contains only obsolete17-count retirement and actual script insertion in ChatBehavior/GroupPush; functional/shared-key/order assertions remain. |
| Plan and invariants | PASS | Exact S-1 substring and whole S-2 CSS suffix equality independently true, including literal terminal blank; production HTML18 versioned resources all `20261024-discovery-recheck`. Component lines24–57,75–163,183–345; host app.js2456–2471 and review lifecycle/selection/list bridges; fixed reader discovery-papers.js273–307; read-only counts Repository461–481/1144–1147. Fresh tests and independently viewed actual Chromium production surfaces below supply behavioral evidence. |
| Required commands | PASS | Fresh chain bg_185 exited0, full output artifact://1312; focused56/56, reader13/13, fullJS1840/1840, selectedJVM48/48; zero failures/errors/skips. Docker MySQL8.0.36 actually started, not skipped. Detailed commands below. |
| Downstream interfaces | PASS | Child10 plan43–49/65–70 inspected only for interface compatibility: frozen source record/attempt/SHA, AUDIT/REUSE and explicit ENFORCE, retained receipts/history and separate proposed/actual/projection remain; child09 adds only counts DTO defaults/read-only SQL and narrow existing-reader bridge. No replay/consumer/admission/schema APIs or child10 files changed. No aggregate review performed. |

### Requirement evidence
- S-1/S-2: independent in-memory comparison extracted the approved HTML/CSS fenced blocks: `domExact=true`, `cssExactSuffix=true`; no CSS normalization or EOF trimming. Fresh focused tests include real-ID existence, exact DOM/CSS, allowed class subset/no-new-inline-style and script-before-app contracts. Host retains seven columns and original manual actions/protected selection; actual browser third protected row had disabled checkbox/manual actions with usable flow button.
- True08 adapter: Controller mappings18–76 match module prepare/start/cancel/sync-retry/runs/latest/run/items/steps/manifest paths. QueryService139–164 forwards original run/count model and accepts latest1..100. Component125–129 distinguishes AUDIT wouldAdmit from ENFORCE actualAdmitted, pending/processing, cancellation/protection overlapping diagnostic, and separate sync failures. Repository changes only append pending/processing/protected read-only aggregates and defaulted DTO fields; real SQL tests verify partition and overlapping protection without business writes.
- Frozen authority: component75–118/152–156 preserves defaults AUDIT/REUSE, spec signature, READY-only start and frozen hash/revision. Radio changes invalidate old preparation; host forwards real selected IDs/filter and selection/filter changes. Independent actual-host HTTP receipts observed two selected docIds, fixed count2, disabled pre-READY, readiness GET, and start body exactly `{rosterHash:"independent-fixed-hash",revision:2}`. ENFORCE change reset hash to 名单未固定 before another prepare.
- Lifecycle: component43–55/65–73/135–150/219–226/327–344 provides abort+epoch+per-lane sequence, one2000ms timeout, visibility pause/resume, native dialogs/focus restoration and teardown. Browser Tab remained within config; Esc restored `data-rr=open` and kept parent task visible. Delayed person1 request was closed/switched; after its delay the visible detail remained independent-2, not person1. Simulated hidden visibility input yielded0 run polls over2.4s; restoring input yielded1 immediate GET. Review→pipeline unmount yielded0 feature requests over2.4s. Close/Esc produced0 cancellation requests; explicit cancel produced1. Fresh unit tests additionally exercise terminal stop, listeners/timer cleanup and stale promises. Visibility was simulated via a temporary document.hidden getter, not claimed as OS background-tab proof; getter removed before cleanup.
- Truthful detail/history: actual production module rendered8 steps, blocker3, INSUFFICIENT and SKIPPED(nonfailure), reasons/version/time/attempt and separate before/proposed/current/applied. Run→items→detail and person switch used actual mounted host. Code234–280 displays missing old steps/time as 历史未记录, never success; source names/SHA/page/versions/attempts remain explicit. Page contracts use20, current-page latest is one ≤100-ID batch, external content uses textContent and safeUrl. No preview people/storage or alternate manual-approve implementation introduced.
- Existing reader: independent strict HTTP fixture returned attempt11 first and frozen attempt12 second. Source click used production openPaper/reader, selected12, checked frozen SHA, fetched `/api/expert-discovery/papers/7/attempts/12/content`, and displayed “已核对固定原文 SHA；页码未记录，不猜测定位。” Esc restored 查看留存原文 focus. Fresh reader tests cover wrong SHA/attempt and cancellation/dispose stale transport. No provider/analyze POST was used.
- Original manual confirmation actually opened via 通过所选 and displayed fixed2-person roster, note, cancel and confirm controls; no manual apply performed. Existing host regression tests preserve old endpoint/refresh behavior.
- A10–A14 chronology: reviewed execution24–28/85–106 and exact direct-test diff. GroupPush's second full-script-list insertion preceded its precise A14 clarification and is honestly disclosed; exact epoch4 plan covers that already-listed file before product commit. This closed clarification is not reopened or recast as a new violation.

### Fresh mandatory command receipts
All ran once in the assigned worktree against the final unchanged product head. Chain exit0; no failure was rerun merely to confirm.
| Command | Observed result |
|---|---|
| `node --check src/main/resources/static/discovery-recheck.js` | exit0 |
| `node --check src/main/resources/static/app.js` | exit0 |
| `node --check src/main/resources/static/discovery-papers.js` | exit0 |
| `node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | exit0;56 tests/pass56/fail0/skipped0; artifact://1312:75–82 |
| `node --test src/test/js/discoveryPapers.test.js` | exit0;13 tests/pass13/fail0/skipped0; artifact://1312:96–103 |
| `node --test src/test/js/*.test.js` | exit0;1840 tests/pass1840/fail0/skipped0; artifact://1312:2621–2628 |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationQueryServiceTest -DmysqlIt=true` | exit0 BUILD SUCCESS; RepositoryIT29 + QueryServiceTest19 =48, failures0/errors0/skipped0; artifact://1312:3626–3634/6168. Docker discovery3101–3119 and second MySQL3375–3379 show fresh real mysql:8.0.36 containers/JDBC connections. Maven-bound JS also1840/pass1840/skipped0 at6155–6160. |

Baseline execution failures were historical only: the final fresh commands do not reproduce those failures. No WAR build, full JVM suite or diff-whitespace command was claimed; the approved literal CSS EOF blank is deliberately preserved.

### Independent Chromium proof and limits
Fresh actual Chromium, logical1280×720 and640×900, loaded unchanged production index/app/discovery-recheck/discovery-papers/CSS. Independent strict loopback Bun HTTP fixture served actual APIs/assets; no copied preview/static DOM, weak DOM stub, in-page api/fetch replacement, provider or production writes. Synthetic `.invalid` experts distinguish fixture data from product data.

Screenshots were actually surfaced/viewed, not merely saved:
| Retained external screenshot | Observation |
|---|---|
| `/tmp/child09-independent-detail-wide.png` | Opaque white dialog and readable detail; computed background rgb255,255,255; reason rgb71,85,105 (#475569)/12px; grid235px 783px;8 server steps and blocker3. |
| `/tmp/child09-independent-detail-narrow.png` | Narrow one-column detail grid598px, steps289px+289px; document horizontal overflowfalse; white dialog/fixed footer and independently scrollable body. |
| `/tmp/child09-independent-config-wide.png` | Actual frozen ENFORCE/REUSE configuration, footer hash/count/start, original review host/manual controls behind opaque dialog. |
| `/tmp/child09-independent-config-narrow.png` | Narrow config/manifest; computed local table client576/scroll900 and document overflowfalse. Expanded roster extends within its own local scrolling region, not whole document. |

Fresh wire receipts retained at `/tmp/child09-independent-browser-receipts.json`. Original writer receipts/screenshots were setup context only, never counted as independent proof.

Harness chronology/limits: initial fixture list used `items` instead of actual host `experts`, then corrected fixture only; shell initially lacked auth/me and correctly blocked pointer input with login overlay, then isolated authenticated fixture session supplied. One narrow source-link interaction timed out with detached element while active poll re-rendered; no production defect was established or investigated beyond this bounded UI check. Fixture run was subsequently moved to terminal state for stable original-reader interaction. One attempted manual-close helper did not exist; original confirm cancel button closed it instead. These are harness observations, not fabricated successful actions or AUTO_FIX findings. Fixture outcomes do not prove Spring HTTP serialization, real provider/ES, qualification/admission transactions, actual1001-person execution or human acceptance. Real SQL proof is the fresh MySQL IT above; sync-retry and old-history edge conditions additionally rely on direct code/fresh tests, not invented browser writes.

Cleanup: managed Chromium tab closed, both isolated HTTP servers stopped, temporary visibility override removed. Fixture lived in eval memory only; no scaffold in product tree. Screenshots/receipts intentionally retained externally. No product/tests/index/branch/commits/plans edited by this verifier; only this append-only log written.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
