# Fast-P Child Brief — p1-expert-profile-absence

- Master plan (global authority): `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` — 执行顺序 ②。
- Child plan（唯一权威文本）: `docs/plans/2026-08-06/expert-profile-absence-not-error.md` —— 全部阶段 1/2/3 与验收标准、验证命令均以该文件为准。
- child_base_sha: 由 controller 在启动本 child 时写入（= 前一 child 的 code head）。
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`（分支 `fast/mail-reliability`）。

## Authorized Files（排他，M-1）

1. `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`（任务 1.1/1.2）
2. `src/main/resources/static/app.js`（任务 2.1-2.6）
3. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt`（任务 1.3）
4. `src/test/js/expertTagBatchFix.test.js`（任务 3.1）
5. `src/test/js/expertProfileAbsence.test.js`（新增，任务 3.2）

`styles.css` **零改动**；`ExpertSearchService.kt`、`AiReplyContextService.kt`、任何 migration、任何 mail 侧文件不得触碰。

## Key Invariants

- **I-1**：`getExpertProfile()` 画像缺失 → HTTP 200 + `found=false` + `tags=[]`；`findByOrcidId()` 异常**继续上抛**（无 try/catch）；前端 `api()` 抛错走 `showStatus(..., "error")`。
- **I-2**：标签读层与写层同源（`data-level`），禁止读路径加 APPLICATION→CANDIDATE 回退。
- **I-3**：`found === false` 时产出的标签区**不含** `data-action="expert-add-tag-open"` / `data-action="expert-remove-tag"`；`handleContactAction()` 该分支 showStatus 后 return，不打开 `#actionDialog`。
- **I-4**：前端判定一律 `found === false`（`undefined`/`null` 视同有画像）；禁止 `if (!found)`。
- **I-5**：`showUnmatchedDetail()` 标签获取失败不阻断面板渲染；`#mailboxList`（:11489）与 `#monitoringActivityTable`（:10499 一带）监听器以 `.catch((error) => showStatus(error.message, "error"))` 收口（写法与 `#unmatchedDetailPanel` :11013 逐字一致）。
- **S-1/S-2**：DOM 契约逐字（见计划 S-1/S-2 代码块；含 `data-profile-missing="true"`；有画像分支逐字不变）；无新增 class、无 inline style。
- **M-1**：本 child 文件集合与其余三份零交叉（`app.js` 是本批次唯一前端文件，归 P1）。
- **M-3**：不新增任何 Flyway migration。
- 交互点：`fetchExpertTagsFromEs` 返回类型由数组变对象 → **5 个消费点全部适配**（6585/6952/8347/8766/9325）+ `mutateExpertTag`（:4046）取 `.tags` + 既有 JS 测试桩改 `{ found: true, tags: [] }`。

## Required Commands（全部运行并记录退出码）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest
node --test src/test/js/expertProfileAbsence.test.js
node --test src/test/js/expertTagBatchFix.test.js
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：全量退出码 0 且含 `Tests run: N, Failures: 0, Errors: 0`；`node --test` 退出码 0 且 `pass N`/`fail 0`；`node --check` 无输出；`git diff --check` 无输出。

## Downstream Interfaces

- 无（P2/P3/P4 均不触碰本 child 文件；`app.js` 无下游消费方）。
- 本 child 无知识库写回（P1 无 Phase 6 节）。

## Deliverables

1. 实现提交：`feat(fast-p): implement p1-expert-profile-absence`（只含上述 5 文件）。
2. 执行报告：`docs/plans/fast/mail-reliability/children/p1-expert-profile-absence/execution.md`（不提交）。
3. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`，commit SHA，命令摘要，报告路径。
