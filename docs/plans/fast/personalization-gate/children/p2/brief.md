# Fast-P Child Brief — p2 · 运营可见性（列表按模板门禁筛选）

- Master: `docs/plans/2026-08-09/personalization-gate-master.md` (sha256 cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324)
- Plan: `docs/plans/2026-08-09/personalization-gate-p2-operator-visibility.md` (sha256 611523e002ea2c4bb579b6c4fc2cc5e451fd04f81a046c982c0ab4f8a4a49ef6) — **the complete approved contract; read it in full before editing.**
- Depends on: p1 (its interfaces are implemented and verified — see below)
- Child base: `07a77f3e15da0d56317ec413412a5ca15ece913b` (p1 terminal code head; the interleaved evidence commit `4488b86` precedes this run in ancestry but is not the product base)
- Branch: `fast/personalization-gate`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate`
- Execution report: `docs/plans/fast/personalization-gate/children/p2/execution.md` (append)

## Global constraints

1. JDK 11 only: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command. Bare `mvn` fails.
2. Modify ONLY the plan's authorized files (5 production + 3 test files, below). The production file count is at the limit of 5 — if a 6th production file becomes necessary, STOP and return `BLOCKED`.
3. Preserve every invariant I-9..I-12 of the plan and master invariants I-M2, I-M3; obey the style contract S-1..S-3 verbatim (byte-exact CSS/DOM where the plan says so).
4. Do not touch any P1 delivery file (all of `src/main/kotlin/.../mail/**`, `.../campaign/**`, `.../template/domain/MailComposeTemplate.kt`, `MailComposeTemplateService.kt`, migrations) or `ExpertDiscoveryService.kt`, `.tag-chip` CSS rules, or the existing five `#hasFieldTagSelect` buttons.
5. No push, merge, rebase, amend, squash, or history rewrite. One local implementation commit.
6. Exclude all fast-p report/log files under `docs/plans/fast/` from the implementation commit; the controller commits evidence separately. Do not commit `docs/plans/2026-08-09/**`.
7. Do not implement anything from the master "Out of scope" list or P1's remaining phases (阶段 5 copy is operator data work).
8. Do not repair unrelated behavior; no formatters/linters; no project-wide suites beyond the brief's required commands.

## P1 interfaces already delivered and verified (consume, never re-derive)

- `MailComposeTemplateService.effectiveRequiredKeys(templateId: Long): List<String>`
- `MailComposeTemplateService.requiredEsFields(templateId: Long): List<String>`
- `MailPlaceholderService.ES_FIELD_BY_KEY["primaryResearchField"] == "researchFields"`
- V84 migration adds `required_keys VARCHAR(500) NULL`.

If any signature differs from this table, that is a p1 delivery defect: STOP and return `BLOCKED` — do not compensate inside p2.

## Authorized files (exactly)

Production (5):
1. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt`
3. `src/main/resources/static/index.html`
4. `src/main/resources/static/styles.css`
5. `src/main/resources/static/app.js`

Tests (3):
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/template/controller/ComposeTemplateGateControllerTest.kt` (new)
- `src/test/js/gateTemplateFilter.test.js` (new)

## Required commands (verbatim from master plan; do not rewrite)

```bash
# P2 新增/受影响测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'

# 前端 JS 测试单文件（P2 快速迭代用）
node --test src/test/js/gateTemplateFilter.test.js

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

Pass criteria: mvn exit 0, `Tests run: N, Failures: 0, Errors: 0`, Node `fail 0`; `node --test` single-file exit 0; `git diff --check` empty. Note: full `mvn test` already includes the Node suite (exec-maven-plugin) — run the single-file command only for fast iteration, but both are named above; run at least the targeted mvn test class pair, the full `mvn test`, and `mvn clean package`. The plan's 验收标准 additionally asserts I-9..I-12 and S-1..S-3 (grep/diff assertions included).

## Verification notes

- The controller dispatches a fresh verifier after you finish; do not self-verify beyond running the required commands.
- Return exactly: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the commit SHA, command summary (commands + exit codes + test counts), and report path.
