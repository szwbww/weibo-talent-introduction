# Fast-P Ledger — master: docs/plans/2026-09-25/mail-open-tracking-00-master.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-25/mail-open-tracking-00-master.md (commit ab2dda0f86c52d8bd9570d994fa895f087931df3)
- Amendments: A1, A2
- Master base: f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5
- Branch: fast/mail-open-tracking-00-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-25T12:27:24.289943+00:00
- Current child: 04
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline
- Original committed main start f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5; plans seeded at 7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce, approved amendments at ab2dda0f86c52d8bd9570d994fa895f087931df3.
- JDK 11 available; V140 highest baseline migration.
- Existing candidate implementation was completed in a separate retained worktree. Recovery replays committed product patches in ordered stages without rewriting that prior history; each child reruns required commands and receives fresh independent light verification.
- Docker-backed MySQL integration previously NOT_RUN, must be attempted freshly and accurately classified.

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-09-25/mail-open-tracking-01-storage-api.md | commit:7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce | none | 1 | LIGHT_PASS_WITH_NOTES | f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5 | c39198962a728e620bf0648e71021c49601778d2 | 1 | 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd | 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd | c8e755e0ba88ce81257c505ecdd42951f60e2560 | RecoveryStorageWriter and RecoveryStorageVerifier; O-01 Docker/MySQL NOT_RUN |
| 02 | docs/plans/2026-09-25/mail-open-tracking-02-reply-context.md | commit:7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce | 01 | 1 | LIGHT_PASS | 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd | a8ed5f80b67ac7b4f97f9de8ca78084b521e1c94 | 0 | — | a8ed5f80b67ac7b4f97f9de8ca78084b521e1c94 | b97ff08ef15016f10843ce8cf22f5997ca547d03 | RecoveryReplyWriter and RecoveryReplyVerifier; 153 scoped tests passed |
| 03 | docs/plans/2026-09-25/mail-open-tracking-03-smtp-integration.md | commit:ab2dda0f86c52d8bd9570d994fa895f087931df3 | 02 | 1 | LIGHT_PASS_WITH_NOTES | a8ed5f80b67ac7b4f97f9de8ca78084b521e1c94 | 0a20ee338f3abfa327e945405e22fe22fb2aa2c2 | 1 | 439031c5c815de8a49a3b6fb7dfc72e58326db1a | 439031c5c815de8a49a3b6fb7dfc72e58326db1a | N/A | RecoverySmtpWriter and RecoverySmtpVerifier; O-01 dependency MySQL NOT_RUN |
| 04 | docs/plans/2026-09-25/mail-open-tracking-04-monitoring-ui.md | commit:7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce | 03 | 1 | PENDING | N/A | N/A | 0 | — | N/A | N/A | N/A |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-25/mail-open-tracking-00-master.md | commit:7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce | commit:ab2dda0f86c52d8bd9570d994fa895f087931df3 | G-6 / 每阶段不得超10文件 | Child 03 requires two legacy Mockito test migrations; approved 11-file exception and corrected aggregate counts | HUMAN:2026-09-25T11:20:27Z selected 扩大测试白名单并干净切换; 批准03扩为11文件 |
| A2 | docs/plans/2026-09-25/mail-open-tracking-03-smtp-integration.md | commit:7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce | commit:ab2dda0f86c52d8bd9570d994fa895f087931df3 | G-2 / 03→01查询 | Remove seven-argument compatibility overload and authorize both existing outreach tests for clean recordSuccess cutover | HUMAN:2026-09-25T11:20:27Z selected 扩大测试白名单并干净切换; 批准03扩为11文件 |
