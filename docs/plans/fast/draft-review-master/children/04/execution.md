# Child 04 — execution


## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-04-reader.md`
Current plan SHA-256: `2eadefbae3db8205e028ce2cebad46b66f7912ce5fe465d33a7947496ed3c97a`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-04-reader.md@2eadefbae3db8205e028ce2cebad46b66f7912ce5fe465d33a7947496ed3c97a`
Execution epoch: NEW, epoch2. Original epoch1 identity was `6f7bca6a57737934f983bd69ecc4834b0267fb109fc0b49f7e89c6b9cf3feae8`; its failed gate is retained below, not reused as final proof.
Approval basis: current child04 assignment and brief; user continuous authority; controller-recorded A7/A8 bounded amendment in plan-only commit `c17c9013ece5bf7967d6233cd97f5bfed47d1cab`, recorded before the eighth-file edit. The amended plan was reread completely and identified afresh, then the already-applied seven-file work was reconciled without replay.
Executor: `DraftReview04Implementer`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`
Target branch: `fast/draft-review-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master@fast/draft-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-draft-review-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA / child_base_sha: `f7b24c9c7364ea64adc4fa1013083343399756d7`
Pre-execution evidence HEAD: `a32b4659daf647474ec59a72ecaf3a3b89a214c7`
Epoch2 pre-commit evidence/plan HEAD: `c17c9013ece5bf7967d6233cd97f5bfed47d1cab`
Post-execution code SHA: `ae148e46ce143a73046c648d0a19500b37230c60`
Evidence HEAD at handoff: `ae148e46ce143a73046c648d0a19500b37230c60`; this report is separately uncommitted for controller-owned evidence persistence.
Product commit: `feat(fast-p): implement 04`; exactly eight amended-authorized product/test paths, no fast-p evidence.
Implementation boundary: `f7b24c9c7364ea64adc4fa1013083343399756d7..ae148e46ce143a73046c648d0a19500b37230c60`; intermediate commits are controller evidence/plan-only, not child04 product code.

### Task / Invariant Mapping

| Requirement | Status | Files and evidence |
|---|---|---|
| T1 / I-16 | IMPLEMENTED | app.js exact method/path reader whitelist, positive integral ID paths; independent reader module, no admin mount/account/attachment/translation/review-send access. Named guard tests exercise allowed pairs and forbidden suffixes/methods before fetch. Actual browser denied send/approve/translate/attachment with four403 and zero additional resource-fetch entries; raw HTTP reader send also403. |
| T1 / I-17 | IMPLEMENTED | Reader handle owns flushDrafts/hasPendingDrafts. Expert/draft/new/search/refresh/logout wait on current dirty content; submit first saves and uses returned version; save failure/409 retains local content and blocks navigation. beforeunload only dirty/saving. Final tests cover late save revisions, failure, logout and submit freeze. Actual offline expert-switch/logout remained in甲, restored save worked, refresh/logout persisted DB subject. |
| T1 / I-18 | IMPLEMENTED | Instance owner, generation, selected contact, draft id/version/revision, independent read/history/event/navigation sequences; stale callbacks discarded. Any401/403 clears timeline/editor/comments/history/pending state and aborts requests. Unmount clears memory/listeners/requests. Explicit post-revoke refresh starts at the authorized list, not revoked selected details; cleared state may logout. Actual delayed real draft request could not enter乙; real revocation cleared all body state and explicit refresh recovered甲. |
| T2 / S-1 / I-17 | IMPLEMENTED | Persistent composer follows the unchanged text-only timeline in mc-scroll; one editor. Explicit POST creation with retry-stable clientRequestId; history includes deleted/pagination, opens fresh detail, events use id cursor. Server-returned state controls four-state writes; approved no mutation/comment inputs; deleted only events. Browser created distinct1/2, selected/reloaded1, submitted/withdrew/resubmitted, read both comments/versions, returned/revised/resubmitted/approved, read deleted2 history. |
| T2 / I-18 / HTML | IMPLEMENTED | HTML writes pass only existing MailboxMeeting.sanitizeDraftHtml; unavailable module is text-only/read-only. Reasons/comments/name via textContent, safe URL protocols, no editor library/uploads. Native browser bold/list/HTML clipboard paste preserved caret and removed image/event-handler/javascript URL capabilities; final canonical save stripped browser-generated style attributes. Historical literal b markup remained text with zero child elements. |
| S-1 CSS / DOM | IMPLEMENTED | styles.css appended literal approved CSS block; new behavioral test compares that exact block. Existing globals/mailbox-chat.css unchanged. Finite DOM harness verifies actual nodes/action-state matrix and class/inline-style restrictions. Real desktop editor100px/240px, border rgb(220,228,239), radius7px;390px page/compose no horizontal overflow. |
| S-2 | IMPLEMENTED | Existing panel title 收发邮件 and badge 可查看 · 可提交草稿; original name DOM only h2, no email properties. Actual desktop only mailbox data-view tab (logout is not a view), mobile only mailbox option; no management/send/attachment controls. Errors stay editor mc-error/mc-note, no new toast. |
| T3 / A7-A8 | IMPLEMENTED | Unified existing versioned resource key20261029-review-reader, no new resources. Original cache-key grep returned no matches in src/test. Retired only obsolete global-innerHTML prohibition, fixed-copy test and old-title assertion in authorized eighth file; preserved unrelated styles/layout/permissions, did not re-pin replacement wording there. |

### Fresh Commands and Results

All commands ran in the exact target worktree. No build/test/lint/formatter ran during initial editing. Named checks were run at implementation candidates; failures and later fixes are disclosed. The final required gate ran after every product/test edit.

```sh
# C1/C2/C3, final invocation (one shell chain, each command completed exit0)
node --check src/main/resources/static/mailbox-readonly.js &&
node --check src/main/resources/static/app.js &&
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js

# Runtime dependency preparation, not a test or build substitute
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft04-classpath -DincludeScope=runtime
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft04-classpath -DincludeScope=test

# Disposable real database
docker run --rm -d --name fast-draft04-mysql -p 127.0.0.1:33374:3306 -e MYSQL_ROOT_PASSWORD=review04-local -e MYSQL_DATABASE=fast_review04_smoke mysql:8.0.36 --log-bin-trust-function-creators=1

# Cleanup
docker stop fast-draft04-mysql && rm /tmp/fast-draft04-classpath
```

| Command | Observed result / receipt |
|---|---|
| Final C1/C2 | Exit0 each, syntax accepted. Same final chain as C3. |
| Final C3 | Exit0;127 tests,127 pass,0 fail/cancel/skip/todo;22 suites,839ms. Tool receipt “Running final syntax and regression gates”. Includes18 new reader tests plus existing reader/auth/style cases. |
| Epoch1 candidate C1/C2/C3 | Syntax exit0; C3 exit1,126 tests/123 pass/3 obsolete-style-contract failures. Controller was sent exact file/line evidence before unlisted edit; A7/A8 authorized retirement. |
| Intermediate epoch2 candidate gate | Exit0,126/126,0 failures/skips; before the final busy-editor regression was added. Not substituted for final127 evidence. |
| Classpath runtime / test scope | Both exit0. Initial runtime-scope launch exited1 (provided ServletContext absent); test-scope third-party classpath included required provided Tomcat jars and startup succeeded. No target/test-classes on application classpath. |
| Real production app startup | Ready, embedded Tomcat18094, JDK11, fresh MySQL8.0.36/Flyway throughV169, existing auth/JDBC/services. `artifact://1049`. |
| Explicit-whitelist git add; git diff --cached --check | Exit0; eight paths printed, no evidence files. `artifact://1092`. |
| Plan/worktree identity before staging and before commit; commit, HEAD, ancestry, status | Exit0; current plan hash unchanged; expected root/branch/git-dir enforced; code commit is target HEAD and branch-reachable. `artifact://1092`. Only controller brief/ledger dirty before writing this report. |
| Runtime cleanup | Exit0; service fast-draft04-live stopped through proc kill, disposable --rm MySQL container removed, temporary classpath removed; browser tab reader04 released after logout. |

Exact launch construction used in the Python Eval kernel (cp04 is the freshly generated file contents, not a fabricated classpath):

```python
cp04 = read('/tmp/fast-draft04-classpath').strip()
runcommand04 = "env DB_URL='jdbc:mysql://127.0.0.1:33374/fast_review04_smoke?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=review04-local AUTH_ENABLED=true MAIL_SCHEDULING_ENABLED=false MAIL_QUEUE_ENABLED=false LLM_ENABLED=false LLM_AUTO_REPLY_ENABLED=false /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp 'target/classes:" + cp04 + "' com.weibo.talentintroduction.TalentIntroductionApplicationKt --server.port=18094 --spring.main.lazy-initialization=true --spring.rabbitmq.listener.simple.auto-startup=false --spring.web.resources.static-locations=file:/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/src/main/resources/static/"
```

Production predecessor backend classes were reused because child04 changes frontend/tests only. The actual current production static directory was served by the real Spring application via the standard static-locations override; there was no API mock, fake session, localStorage preview, custom product server or substituted browser UI. SQL fixture setup existed only in the disposable DB: campaign90401, contacts90401/90402, local account review04-local, two existing OUTBOUND timeline rows. No SMTP sends were invoked; backend reader-send denial happened before delivery. Fixture addresses were exclusively fixture.test.

### Actual Browser / Runtime Observations

Browser tool documentation was read before opening managed Chromium reader04 on `http://127.0.0.1:18094/`. User1 logged in through the production form/session. A separate requests.Session authenticated real admin, performed mandatory initial password change (204), then used real comment/return/context/approve/grant/revoke APIs. Neither session bypassed auth. These are machine smoke observations, not human acceptance.

| Scenario | Observed proof |
|---|---|
| Native contenteditable bold/list | Native keyboard selection + toolbar B produced b/ul/li; two list items survived explicit server save and full page reload. The final current-source smoke repeated native typing/bold/list on draft6 after an explicit browser reload. |
| Native safe HTML paste/caret | Initial clipboard permission attempt failed and clipboard helper returned shim; these were not claimed as paste proof. Later real page focus + CDP clipboard-write grant + navigator.clipboard.write(ClipboardItem html/plain) + native Meta+V succeeded. On final-source draft6: b/ul/li retained, unsafe marker null, unsafe node count0 (img/script/on* and javascript href), selection anchor remained inside the same editor. Browser execCommand introduced benign computed-color style attrs; canonical save removed them. |
| Actual multi-draft persistence | Draft1 and2 distinct ids. Reader history selected1 via GET; full production-page reload then history reopen retained exact bold/list HTML and subject. History/comments default closed and mutually exclusive. |
| Real workflow / approval lock | Draft1 DRAFT→SUBMITTED→DRAFT→SUBMITTED; user1/admin comments both visible. Admin return reason补充时间 visible; reader changed subject and submit flushed latest version; admin approve200 version9. Reader refresh showed已审核, contenteditable=false, zero visible save/submit/withdraw/delete/comment controls and zero comment textarea. |
| Offline failure | Browser network offline caused genuine ERR_INTERNET_DISCONNECTED on PUT; expert乙 click and logout did not leave甲, local subject retained and inline保存失败，内容尚未同步 shown. Network restored, explicit save succeeded. |
| Real stale request isolation | Network interception delayed an actual review draft GET, without substituting its response; switched to乙, released request.乙 identity rendered, subject/editor remained empty, historical乙 message present. An initial click occurred while refreshed list was temporarily cleared; corrected the automation to await the actual list node. |
| Genuine conflict | A second actual reader HTTP PUT advanced draft4 server version. Current editor then saved stale version→409, retained Local conflict preserved/body, disabled submit and showed conflict. Explicit reload confirmation adopted current server data only after user-copy warning. |
| Refresh/logout flush | Dirty draft4 refresh saved subject Refresh flush final; real GET confirmed it. Later dirty logout returned actual login form; DB confirmed Logout persisted final/version6, not a local-only value. |
| Real grant revocation | Admin DELETE grant200 while乙 dirty. Reader refresh PUT403 cleared timeline/editor/subject/discussion/history and hid composer. Initial smoke discovered repeated refresh re-requested revoked selection; corrected within listed module and added regression. Retested real regrant/revoke: clear remained total, next explicit refresh recovered authorized甲 list. |
| Deleted history | Deleted2 appears已删除, no open-editor action; events visible,3 canonical version bodies (CREATE/SAVE/DELETE), no comment input, no second editor. Service intentionally returns a minimal deleted tombstone, hence no invented timestamp. |
| In-flight submit on final source | After last code edit, browser was explicitly reloaded. Real POST submit was temporarily held at network boundary after latest PUT save. Observed editor contenteditable=false, subject disabled=true, submit disabled=true; released actual request, state已提交. The preceding non-reloaded page still had older runtime JS and is not used as final busy-lock proof. |
| Desktop1440 | Actual computed editor min100/max240, border rgb(220,228,239), radius7px; editor scrollWidth=clientWidth844. Name identity exactly div.mc-identity/h2甲教授, no email attribute. Historical literal b string textContent with0 child nodes. |
| Mobile390 | Actual viewport390, document scrollWidth390, compose width=scrollWidth288. One mobile option mailbox/收发件箱; new draft button usable. Saved mobile draft4, final-source draft6 submitted surface photographed after scrollIntoView; editor count1, no page horizontal overflow. |
| Forbidden capabilities / storage | app.api send/approve/translate/attachment each403 before fetch,0 new API resource entries; raw fetch review send403 from server. No management/attachment controls. Browser storage scan found none of the fixture bodies/subjects; module source tests prohibit Storage writes. |

Retained screenshot evidence (tool-generated files, outside product scope):

- Desktop rich saved draft: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b9654918ee702a.webp`
- Approved read-only/comments: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b96585d1ee702b.webp`
- Desktop deleted-version history: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b965f301ee702c.webp`
- Mobile deleted history after actual scroll: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b965fba8ae702e.webp`
- Mobile editable saved draft: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b965fc0aee702f.webp`
- **Final reloaded-source desktop1440**: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b966959a6e7031.webp`
- **Final reloaded-source mobile390**: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b96695bc6e7032.webp`

Final real DB receipt before teardown:

| id | contact | version | state | subject | deleted |
|---|---|---|---|---|---|
|1|90401|9|APPROVED|A revised time|0|
|2|90401|3|DRAFT|B unsaved offline|1|
|3|90402|2|DRAFT|Beta revoked draft|0|
|4|90401|6|DRAFT|Logout persisted final|0|
|5|90401|3|SUBMITTED|Final native draft|0|
|6|90401|3|SUBMITTED|Final current-source native|0|

Events: APPROVE1, COMMENT2, CREATE6, DELETE1, RETURN1, SAVE11, SUBMIT5, WITHDRAW1. mail_record remained exactly the two seeded timeline rows; no outbound review delivery. An initial diagnostic used nonexistent contact_id and exited1; corrected to the actual expert_contact_id before the successful receipt. Fixture Chinese name encoding was corrected with mysql --default-character-set=utf8mb4; no product/schema change.

### Changed Files

1. `src/main/resources/static/mailbox-readonly.js` — restricted review composer, explicit persistence, history/comments/native editing, scoped lifecycle/isolation.
2. `src/main/resources/static/app.js` — exact reader requests, panel title, reader-owned logout flush.
3. `src/main/resources/static/styles.css` — literal appended S-1 block only.
4. `src/main/resources/static/index.html` — synchronized existing versioned asset keys only.
5. `src/test/js/mailboxReadOnly.test.js` — finite DOM model support/current reader shell expectations, retained text-only history/security/lifecycle tests.
6. `src/test/js/authFlow.test.js` — exact permission matrix plus logout flush/error behavior.
7. `src/test/js/mailReviewReader.test.js` —18 finite-node behavior/CSS/resource/lifecycle/concurrency cases.
8. `src/test/js/mailboxChatStyle.test.js` — A7/A8-only retirement of obsolete HTML/copy/title assertions, no replacement-wording pin.

### Deviations and Freshness

- Only deviation is the controller-authorized eighth test file and resulting new immutable plan identity. No ledger/handoff/plan/verifier/later05 edits by this executor.
- No shared table/schema/backend-send/admin-draft/attachment capability changed.
- No extra worktree, deployment, push, merge, history rewriting or full verification skill. Required command set is frontend-only; no JVM tests/build/package claim.
- Plan identity rechecked: YES, epoch2 hash unchanged before commit.
- Worktree identity rechecked: YES, before staging/commit, expected root/branch/git-dir.
- Product commit is HEAD and reachable on target branch: YES.
- Required commands fresh after final implementation: YES,127 pass.
- Historical results used only as baseline: YES.
- Product/index clean at handoff: YES, commit receipt; only controller-owned evidence changes and this separately authorized report remain unstaged.
- Runtime/browser/temporary classpath cleaned: YES; screenshots retained only as requested evidence.

### Remaining Blocker

None. READY_FOR_VERIFICATION is execution readiness, not independent PASS or human acceptance.

### Next Action

Controller dispatches the isolated child04 verifier against the amended plan/current code boundary.

## Controller Terminal Checkpoint — Epoch 2

Independent DraftReview04Verifier2 final `LIGHT_PASS` / `COMPLETE_CHILD` covers `f7b24c9c7364ea64adc4fa1013083343399756d7..a02477be34b3308899820fcc9bd3060ca5bccb70`. Initial implementation ae148e46ce143a73046c648d0a19500b37230c60; round1 fix a02477be34b3308899820fcc9bd3060ca5bccb70 closed F-01/F-02. Fresh planned node131/131 pass, no skips; browser evidence identified as historical executor proof, not human acceptance. AUTO_FIX/RECORD_ONLY N/A at terminal attempt. Controller product verification performed: none.

