# Fast-P Child Brief — p1 · 发送侧硬闸门

- Master: `docs/plans/2026-08-09/personalization-gate-master.md` (sha256 cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324)
- Plan: `docs/plans/2026-08-09/personalization-gate-p1-send-gate.md` (sha256 ae3f7909427ce17880574f126967f3c967c8edf669e8cba21facc23d4c1c3cb7) — **the complete approved contract; read it in full before editing.**
- Depends on: none
- Child base: `ab5dcbb7fbb58f5e8a9b13b7e54022effd270b77`
- Branch: `fast/personalization-gate`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate`
- Execution report: `docs/plans/fast/personalization-gate/children/p1/execution.md` (append)

## Global constraints

1. JDK 11 only: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command. Bare `mvn` fails.
2. Modify ONLY the plan's authorized files (below). The plan's production file count is at the hard limit of 10 — if an 11th production file becomes necessary, STOP and return `BLOCKED` (plan defect per plan §变更文件清单).
3. Preserve every invariant I-1..I-8 of the plan and master invariants I-M1..I-M5.
4. Do not touch: `ExpertDiscoveryService.kt`, `ContentVariantService.kt`, `QaFactBodyPolicy.kt`, `ReplySnippetService.kt`, `PendingMailOperationService.kt`, `MeetingInvitationMailComposer.kt`, `AutoMailReplyService.kt`, `AutoReplyPreviewService.kt`, `MeetingScheduleService.kt`.
5. No push, merge, rebase, amend, squash, or history rewrite. One local implementation commit.
6. Exclude all fast-p report/log files under `docs/plans/fast/` from the implementation commit; the controller commits evidence separately.
7. Do not implement anything from P2 or the master "Out of scope" list (block-level conditional rendering, auto-enrichment, subject rewriting, multipart unification, postmaster compliance).
8. Do not repair unrelated behavior; do not run formatters or linters beyond what the plan requires; do not run project-wide test suites beyond the named required commands.

## Authorized files (exactly)

Production (10):
1. `src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt` (new)
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
8. `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`
9. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
10. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

Tests (5, not counted in the limit):
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt` (new)
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` (new)
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`

## Required commands (verbatim from master plan; do not rewrite)

```bash
# P1 新增/受影响测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

Pass criteria: mvn exit 0, output contains `Tests run: N, Failures: 0, Errors: 0`, no Node test `fail 1`+; `git diff --check` empty. The plan's 验收标准 additionally lists per-invariant assertions (I-1..I-8) — implement tests that defend them per the plan's test-file list.

## Downstream interfaces P2 will consume (must exist exactly as specified)

- `MailComposeTemplateService.effectiveRequiredKeys(templateId: Long): List<String>` — returns effective required variable keys; empty when `required_keys` is NULL/empty/invalid (I-4).
- `MailComposeTemplateService.requiredEsFields(templateId: Long): List<String>` — maps keys via `ES_FIELD_BY_KEY`, de-nulled, deduplicated, stable order.
- `MailPlaceholderService.ES_FIELD_BY_KEY["primaryResearchField"] == "researchFields"` (I-7).
- V84 migration adds `required_keys VARCHAR(500) NULL` with the plan's exact COMMENT.

## Verification notes

- The controller dispatches a fresh verifier after you finish; do not self-verify beyond running the required commands.
- Return exactly: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the commit SHA, command summary (commands + exit codes + test counts), and report path.
