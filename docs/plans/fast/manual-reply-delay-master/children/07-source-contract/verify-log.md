# Fast-P Verify Log — 07-source-contract

No independent verification performed at plan seed. Append each fresh four-gate verdict; do not substitute prior child evidence.

## Light Verification: LIGHT_PASS_WITH_NOTES

Child: 07-source-contract — epoch 1 / attempt 1 / fix 0  
Boundary: `4e64c1f4da4eb5acdac282731f58f2798ab9ff5d..a9c09c9abf8cf08707d86514b168318341716126`  
Verifier: **Verify07SourceContract**, independent of **Impl07SourceContract** and **Resume07SourceContract**  
Date: 2026-11-05  
Worktree / branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master` / `fast/manual-reply-delay-master`  
Approved master + child identity: `525aeeb9e97241d5ad044f5662c75194db1a5944`  
Implementation/code: `a9c09c9abf8cf08707d86514b168318341716126`

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Product/test boundary is the authorized eleven paths; master/child bytes match the approved identity and current source/test bytes match implementation before commands and after runtime. `mailboxChatStyle.test.js` uses A25/A26 solely to retire the dynamic old06-plan-key binding, without repinning a new literal/plan. No06 plan/log, CSS, migration, activation/configuration, product, test, branch or commit edits by this verifier. `identity.json`, `patch.diff`, `unchanged-source-after-runtime.json`; this report is the sole repository append. |
| Explicit requirements / invariants | PASS | C1–C7, S-C1/S-C2 and T1–T4 checked against the approved child. Original nullable/blank raw request remains separate from canonical UUID and actual frozen MAIL_RECORD anchor; LEGACY INBOUND/OUTBOUND are strict mutually exclusive typed sources. No payload/safety/schema/fingerprint authority change. Source matching uses actual source/account, not UUID/null bypass. Restore checks current owner/admin FULL and actual SENT non-simulator source identity. Local restoration does not fabricate/persist an ordinary draft; conflict preserves current input, higher persisted version and CANCELLED snapshot. Stable UUID for unchanged implicit→explicit old anchor, new UUID for actual text/HTML/attachment changes, text+HTML raw baseline, ordered real attachments and stale-owner/target/epoch/revision guards exercised. `LIGHT-AUDIT.md`, focused tests, `mime-runtime-proof.json`, `frozen-snapshot-byte-proof.json`, API/browser/DB evidence below. |
| Required commands — fresh | PASS | All six exact current-plan commands ran freshly after analysis, all exit0. JVM **82/82** (Snapshot14, Controller16, Worker9, Pending43), real MySQL Lifecycle **44/44**, focused JS **86/86**, full JS **2076/2076**, syntax exit0; test skipped0. Maven-bound full JS also2076/2076 in each Maven test command. No fresh baseline exclusion; historical failures/interrupted runs are not current evidence. `required-commands.json`, complete `command-1.log`–`command-6.log`, `grep-receipts.txt`. |
| Downstream interfaces | PASS | Traced production preparation→factory/codec→03 lifecycle/source lock→restore controller→explicit INBOUND host adapter or OUTBOUND local marker→existing conversation transport→Pending rehydrate/LEGACY worker, plus READ rendering/FULL delegation. Fresh actual INBOUND host mounting and unchanged-body/text/format SMTP proof recheck the closed F06-01 boundary. Real old-anchor/UUID/raw/attachment POSTs, permissions/source matrix, held-response races and1440/390 surfaces exercise the changed consumers. No public enqueue or07 activation; component evidence is not whole-plan acceptance. |

### Fresh Required Commands

Common Maven environment: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.

| Command | Terminal result |
|---|---|
| `mvn -DskipTests test-compile` | exit0 / BUILD SUCCESS; compilation only, not a test-pass claim |
| `mvn test -Dtest=DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,PendingMailOperationServiceTest` | JVM82/82, failure0/error0/skipped0; bound Node2076/2076; exit0 |
| `mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true` | real MySQL8.0.36 IT44/44, failure0/error0/skipped0; bound Node2076/2076; exit0 |
| `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` |86/86,12 suites, failure0/cancelled0/skipped0; exit0 |
| `node --test src/test/js/*.test.js` |2076/2076,356 suites, failure0/cancelled0/skipped0; exit0 |
| `node --check src/main/resources/static/mailbox-chat.js` |exit0 |

Fresh command timestamps: 2026-11-05T13:00:28Z–13:11:06Z; expanded commands, exact timestamps and complete output are archived. Historical providerDNS/stale31vs30, smart-cast compilation, old-cache2073/2074 and interrupted checks were not rerun merely to confirm them and are not claimed as fresh baseline exclusions.

### Actual Changed-Runtime Evidence

- Reused infrastructure/baseline, **not old results**. Isolated real MySQL33387 / app18087 / SMTP25287; current compiled preparation/factory/codec/03 service creates new optional-anchor fixtures, actual HTTP cancellation and restore, then later real SENT anchors. Eight fresh LEGACY snapshots remain byte-identical to their persisted final snapshots. Fourteen actual upload/download byte proofs; full current startup, fixture source, API/network, final DB dump and source hashes retained.
- Actual browser/HTTP200-SENT→SMTP250→MIME→DB correlation: **six deliveries** — OUTBOUND text, format-only and ordered attachment removal; INBOUND unchanged body, edited text and format-only after actual restored host mount. Actual old In-Reply-To anchors retained, edited UUIDs change, no restored draftRef, and old raw/template is not substituted over edited text/HTML. The INBOUND unchanged-body case changes the subject to obtain a new synchronous fingerprint; it is not an unchanged-cancelled requeue claim. ASCII attachments use existing JavaMail7bit LF→CRLF SMTP transfer: original byte-exact downloads and both raw-wire/LF-normalized MIME hashes are retained, not falsely equated.
- Real permissions/source matrix: current FULL owner/admin200, FULL nonowner403, owner downgraded READ403 with public projection200; changed account/contact/Message-ID/direction/status/simulator and deleted old anchor422 even with a newer SENT anchor. `permission-source-api.json`.
- Ordinary input and separate real-session v2 draft both survive restore conflicts; durable cancelled raw input remains available. Final DB: zero ordinary draft rows for restored701–704, no active WAITING/SENDING/UNKNOWN jobs. `browser2/ordinary-conflict-browser.json`, `browser2/higher-version-api.json`, `browser2/higher-version-browser.json`, `final-db-projections.json`.
- Actual held HTTP200 response races cover expert switch, native editor input, actual03 requeue/revision change followed by cancellation before due, applied sender2 scope verified by real scoped request/list, and admin→user1 session change on the same706 target. No response replacement/fake fetch/business response. Session change invalidates the held interception; new-owner editor remains unchanged. No completed late callback or unobserved AbortController cause is claimed. Root and `browser2/*race*.json` retain details.
- Actual1440/390 restored editor/cards/footer and READ task screenshots inspected. At390: document390/editor288/send44px, no horizontal overflow. READ visible CANCELLED task has no restore/send/task mutation controls or raw placeholders. Existing20 resource references retain collection/order and share `20261105-manual-reply-source`; specialized grep receipt is archived.
- Harness startup/stdin-control, native-select/input-delivery/viewport timeouts and evidence-parser attempts remain archived, not relabeled PASS. Fresh owned Chrome plus stable native hit testing and actual filter Apply/request/list checks resolved browser instrumentation. Final SQL evidence uses explicit utf8mb4; 7bit MIME line-ending semantics are explicit. These are not product findings or required-command baseline exclusions. No fake UI/DOM, application business state or API responses supplied.
- Durable archive created before cleanup; the final controlled app/MySQL/SMTP/browser/control services subsequently exited0 (earlier abandoned startup attempts remain recorded), all owned ports closed; final archive includes cleanup and graceful app exit proof. No production access or early07 activation.

### AUTO_FIX

- N/A — no confirmed in-scope implementation defect; no product/test/plan repair performed.

### RECORD_ONLY

- **O07SC-01:** Actual unchanged cancelled701 POST preserves original UUID/raw/ordered files/blank scope and explicitly submits verified old anchor7010, but the still-synchronous compatibility guard rejects it: observed HTTP500 containing422, SMTP0. This is **not a new public requeue PASS**. Do not change exception mapping out of scope. Public enqueue activation, real60 seconds, application restart and complete master acceptance remain mandatory in07-activation; none is waived here.
- **F06-01 remains closed.** Fresh actual INBOUND restoration and text/format SMTP proof recheck the prior boundary; no old06 plan/log rewrite or reopened finding.
- Four-gate child result only. No master/whole-plan PASS; controller records the checkpoint.

### Evidence Archive

- Current independent archive: `local://verify07-source-contract-epoch1attempt1fix0-evidence.tar.gz`
- SHA256: `7ade55b53a4d13311efed5dfb6aea08f285327b5c033b81557e5649d668beabd`
- Bytes:4339486; prefix:`verify07-source-contract-epoch1attempt1fix0-evidence/`.
- Prior infrastructure provenance only: `local://resume07-source-contract-epoch1-evidence.tar.gz`, SHA256:`4a94100ee5ba7e01dbece25e6e386de9c1aeefc54ff9704c29d4e5912c8c5eb8`.

### Required Action

- **COMPLETE_CHILD** — bounded `LIGHT_PASS_WITH_NOTES`; controller may record this child checkpoint and continue the approved order, without treating later07 activation/integrated acceptance as complete.

### Artifact formatting clarification

The verifier's emphasized COMPLETE_CHILD above is transcribed below in the required machine-readable form. Exact child plan: `docs/plans/2026-11-04/manual-reply-delay-07-source-contract.md`. Verdict, reviewed boundary, verifier identity and evidence are unchanged; this controller formatting append is not a new verification or rerun.

### Required Action
- COMPLETE_CHILD
