# Child 03 fix log

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery/docs/plans/2026-09-25/mail-open-tracking-03-smtp-integration.md`
Plan SHA-256: `462a812858550dc350a8fa38b51705b21240c538479340af342076896820650b`
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery`
Branch: `fast/mail-open-tracking-00-master`
Implementation commit: `0a20ee338f3abfa327e945405e22fe22fb2aa2c2` (source `a2d1df800b8f989a3db0ecac5246b11d63cfc40f`).

## Epoch 1 — Round 1/3
- Finding: original helper compatibility overload hid seven-argument callers rather than completing the clean-cutover eight-argument `recordSuccess` migration.
- Fix: remove the compatibility overload, propagate the delivered ID (including null) via named arguments from InitialOutreachService and ManualInitialOutreachService, and update the two caller-test files' Mockito verifications.
- Fix commit: 439031c5c815de8a49a3b6fb7dfc72e58326db1a
- Source patch: `62b012aaa1422cddb797cc793f53d3d1762b8a71`; its replayed product/test tree matches this new fix commit.
- Fresh focused JDK11 test: PASS, exit 0; 238 tests, 0 failures, 0 errors, 0 skipped (`artifact://476`).
- Independent verification: pending; these reports are intentionally uncommitted until the controller's evidence commit.
