## Light Verification: LIGHT_PASS
Child: a3 (plan docs/plans/2026-08-16/a3-expert-list-rename-and-entry-move.md, identity commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3)
Boundary: bb07586b758357ad21794e17b7e99f200abeed5b..e1ce1cbf1eeaba87e670771f23c25f2d2293a768
Verifier: VerifyA3

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|Implementation commit e1ce1cb touches exactly the brief's 4 authorized files (git show --stat: app.js 2+-/1 line, index.html 14±, batchEntryRelocation.test.js +65 new, batchSendTaskConsoleVisualFix.test.js 6±). No styles.css change (boundary diff for styles.css = 0 lines), no Flyway migration (db/migration diff = 0 lines). Doc/report files in the boundary diff come from separate docs(fast-p) commits (6c772b5, 8d497b0, a5a7dc1, 5f361ed, 0ee8642) interleaved between base and head; they are excluded from the implementation commit per constraint 5. Commit message exactly `feat(fast-p): implement a3`. |
|Plan and invariants|PASS|S3-1: app.js diff is exactly 1 line — app.js:514 `contacts: ["专家列表", "查看联系状态、邮件时间线和人工处理。"],` (key + subtitle untouched). I3-1: nav span 专家列表 at index.html:106; four `contacts` registration points intact — data-view="contacts" (index.html:101), id="view-contacts" (index.html:448), viewMeta.contacts key (app.js:514), refreshCurrentView `state.view === "contacts"` (app.js:1648); 专家联系 residual 0 in index.html+app.js; contacts-list-width still present (2 hits in app.js). I3-2/M-4: id="bulkOutreachBtn" exactly once (grep -c = 1, index.html:731); four app.js references (674, 682, 5124, 5626) untouched by the 1-line diff. I3-3/S3-2: button at index.html:731 sits in `.panel-head` (index.html:729) immediately after `<h2>已激活账号收发邮件记录</h2>` (index.html:730), between id="view-mailbox" (688) and id="view-inbound-summary" (747); markup verbatim `<button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>`; no bulkOutreachBtn between view-contacts (448) and view-mailbox (688). S3-3/M-2: cache triad 20260817-v3-expert-list-entry-move exactly 3x in index.html and 3x in batchSendTaskConsoleVisualFix.test.js; v2 residual 0 in both (chain authority honored). T3-C3: new batchEntryRelocation.test.js asserts count=1, mailbox-region placement, h2-sibling order, verbatim class/onclick/text, no button in contacts fragment, nav span 专家列表, viewMeta.contacts[0] + quad registration (7 tests, all pass). |
|Required commands|PASS|node --test src/test/js/batchEntryRelocation.test.js -> exit 0, tests 7, pass 7, fail 0. node --test src/test/js/batchSendTaskConsoleVisualFix.test.js -> exit 0, tests 17, pass 17, fail 0. node --test src/test/js/expertTagBatchFix.test.js -> exit 0, tests 31, pass 31, fail 0. node --check src/main/resources/static/app.js -> exit 0. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -> exit 0, BUILD SUCCESS, Tests run: 2459, Failures: 0, Errors: 0, Skipped: 4 (surefire XML aggregate cross-checked: 2459/0/0/4; 187 report files, 0 stale). git diff --check base..head -> exit 0, clean. |
|Downstream interfaces|PASS|b1: all three index.html cache keys already at v3 (styles.css?v=, trust-reply-workbench.js?v=, app.js?v= all = 20260817-v3-expert-list-entry-move, index.html:8/2031/2032 region) matching the three test assertions — ready for b1's v4 bump. B4: #bulkOutreachBtn exists exactly once, verbatim in the mailbox panel title bar (index.html:731, inside panel-head at 729, right-aligned via .panel-head flex) — S2b-3 locatable; no count-1 regressions (expertTagBatchFix.test.js green). |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Surefire JVM aggregate at head = 2459 vs baseline ledger 2456 (+3), Skipped 4 in both. Not an a3 change: JVM test sources are byte-identical base..head (git grep @Test count = 2472 on both sides; a3's commit touches only static assets + JS tests; JS tests run via exec-maven-plugin, not surefire). Count delta is a run/reporting artifact of the fresh run, not a regression; all failures/errors 0. Out of light-gate scope.

### Required Action
- COMPLETE_CHILD
