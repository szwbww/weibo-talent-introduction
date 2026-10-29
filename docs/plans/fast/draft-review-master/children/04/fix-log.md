# Child 04 — fix-log

## Epoch 2 — No repairs at initial verification

Fix round: 0. Fix commits: —. Initial implementation `ae148e46ce143a73046c648d0a19500b37230c60` includes the recorded obsolete-contract retirement amendment; not a post-verifier repair. Fresh DraftReview04Verifier dispatched; no fixer acquired.

## Epoch 2 — Round 1/3

- Findings: F-01,F-02
- Before: ae148e46ce143a73046c648d0a19500b37230c60
- Fix commit: a02477be34b3308899820fcc9bd3060ca5bccb70
- Authorized files changed: `src/main/resources/static/mailbox-readonly.js`; `src/test/js/mailReviewReader.test.js`
- Commands:
  - `node --check src/main/resources/static/mailbox-readonly.js` -> exit0.
  - `node --check src/main/resources/static/app.js` -> exit0.
  - `node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js` -> exit0;131 tests,22 suites,131 pass,0 fail/cancelled/skipped/todo;976.495541ms. All three exact planned commands ran freshly in one `&&` chain after the final product/test edits.
  - `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft04-fix1-classpath -DincludeScope=test` -> exit0; runtime dependency preparation only, not a build/test claim.
  - `docker run --rm -d --name fast-draft04-fix1-mysql -p 127.0.0.1:33375:3306 -e MYSQL_ROOT_PASSWORD=review04-fix1 -e MYSQL_DATABASE=fast_review04_fix1 mysql:8.0.36 --log-bin-trust-function-creators=1` -> exit0; disposable migrated MySQL8.0.36.
  - `git diff --cached --check` -> exit0; staged paths exactly the two authorized files above. Local commit subject `fix(fast-p): repair 04 round 1`; HEAD/target-branch ancestry check exit0. Reports excluded.
  - `docker stop fast-draft04-fix1-mysql && rm /tmp/fast-draft04-fix1-classpath` -> exit0; --rm container, fixture DB and temporary classpath removed. `proc://fast-draft04-fix1-live/kill` stopped the application; `browser.close(name='reader04fix1')` released the logged-out browser.
- Result: FIXED
- Notes: Plan-bound F-01/F-02 only; exact plan SHA256 remains `2eadefbae3db8205e028ce2cebad46b66f7912ce5fe465d33a7947496ed3c97a`. Plan/worktree identity rechecked before staging and commit; root/branch/git-dir enforced. F-01 retains the explicitly requested detail identity across network failure, clears it on reset/revocation, and preserves confirmation before discarding local input. F-02 fallback emits canonical v4-form UUIDs while reusing the existing createRequest retry identity; backend admission/API unchanged. Four added deterministic behavioral cases cover initial-detail recovery/cancel, local conflict preservation and revoked reload, and retry/new-identity behavior with crypto absent or randomUUID absent. No source-text/wiring regression tests added.

  Fresh real-browser/backend smoke used production static assets from the target worktree and existing compiled production backend classes (frontend-only repair), authenticated through the real user1 login form at `http://127.0.0.1:18095/`; no fake backend/session/localStorage prototype. Exact launch expression in Python Eval:

  ```python
  cpfix = read('/tmp/fast-draft04-fix1-classpath').strip()
  cmdfix = "env DB_URL='jdbc:mysql://127.0.0.1:33375/fast_review04_fix1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=review04-fix1 AUTH_ENABLED=true MAIL_SCHEDULING_ENABLED=false MAIL_QUEUE_ENABLED=false LLM_ENABLED=false LLM_AUTO_REPLY_ENABLED=false /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp 'target/classes:" + cpfix + "' com.weibo.talentintroduction.TalentIntroductionApplicationKt --server.port=18095 --spring.main.lazy-initialization=true --spring.rabbitmq.listener.simple.auto-startup=false --spring.web.resources.static-locations=file:/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/src/main/resources/static/"
  ```

  Launch ready pid91313; disposable fixture contact/campaign/account90411, fixture.test addresses only. Browser disabled `crypto.randomUUID` with `Object.defineProperty(window.crypto, 'randomUUID', {value: undefined, configurable: true})`, clicked actual New Draft, and captured real POST. Initial fixture lacked a SENT reply target: real service422 REVIEW_TARGET_NOT_FOUND (not a UUID failure); after completing fixture send_status=SENT, UI retry sent the exact same canonical id `d5bf8463-a4ad-4783-a8c2-1727fd66162a` and real backend returned200/id1/version1/DRAFT with editable editor. After page reload, opened real history; intercepted and aborted only the first actual GET `/api/mail/mailbox/review-drafts/1` with `request.abort('connectionfailed')`, without substituting any response. Actual Reload action appeared with no loaded draft; accepted its native confirmation; second actual GET returned200/id1/DRAFT, editor editable, reload hidden, zero page errors/TypeErrors. Interception removed in finally.

  Final exact DB receipt command `docker exec fast-draft04-fix1-mysql mysql -uroot -preview04-fix1 fast_review04_fix1 -e "SELECT id,expert_contact_id,client_request_id,version,state FROM mailbox_review_draft; SELECT COUNT(*) AS mail_record_count FROM mail_record;"` -> exit0; exactly draft1/contact90411/canonical id above/version1/DRAFT and one seeded mail_record, no actual SMTP sends. Real logout returned login form before tab cleanup. Transcript receipts: “Running final planned covering commands”, “Confirming canonical UUID admission and retry stability”, “Exercising initial failure recovery in actual UI”, “Recording real smoke persistence receipt”; retained backend launch `artifact://1109` and commit/identity `artifact://1126`. Machine smoke only; no human acceptance claim. Remaining in-scope concerns: none known; independent light re-verification remains controller-owned.

