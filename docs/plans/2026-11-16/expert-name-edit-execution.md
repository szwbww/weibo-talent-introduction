# Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-16/expert-name-edit.md

Plan SHA-256: 9767eb7978d5711d250be33a47fdaf49c23805bfb2f59c42983134706407560f

Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-16/expert-name-edit.md@9767eb7978d5711d250be33a47fdaf49c23805bfb2f59c42983134706407560f

Execution epoch: NEW

Approval basis: 用户要求集成既有“身份基本信息→编辑”，随后明确“嗯 你直接在main分支上修改吧”。这项人工修订优先于计划下方保留的独立姓名弹窗/独立 PATCH 旧设计及其文件清单；执行统一人工档案接口和事务，未修改原计划。

Executor: /root

Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction

Target branch: main

Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction@main@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git

Pre-execution code SHA / current HEAD: 9d042fd93c666163173f08f3d02f09537ebe0c88

Post-execution code SHA / Evidence HEAD: N/A（未提交）

Implementation boundary: main 当前工作区内下述姓名相关文件；保留执行前已有附件、发布记录等无关改动。

## Task Status

| Requirement | Status | Evidence |
|---|---|---|
| 人工修订：统一编辑与原子保存 | IMPLEMENTED | 原身份弹窗添加姓名；一个 PATCH；人工档案及模板覆盖共用事务，分别校验版本 |
| I-1 单源 | IMPLEMENTED | 姓名仍存 expert_template_override；资料、顶部与变量页共享覆盖；未改邮件变量生产解析 |
| I-2 局部更新 | IMPLEMENTED | 仅替换/删除 expertName，保留其他键；版本冲突回滚；校验权限、长度、控制字符和占位符 |
| I-3 身份/历史隔离 | IMPLEMENTED | 不写 ES、联系人快照或历史邮件；expertFamilyName 保持原值 |
| I-4 异步边界 | IMPLEMENTED | 保留打开时版本与草稿；迟到读/保存响应不能覆盖较新姓名；切专家和预览 owner 隔离 |
| S-1 按人工修订复用既有弹窗 | IMPLEMENTED | 无新增独立入口、无 CSS 修改；权限不足姓名禁用，原资料编辑权限保留 |

## Commands

执行目录均为上述 Target worktree，最终状态下执行：

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS，exit 0 | 语法检查 |
| `node --check src/main/resources/static/expert-mail-settings.js` | PASS，exit 0 | 语法检查 |
| `node --test src/test/js/*.test.js` | PASS，exit 0 | 2181 tests，0 fail；/tmp/expert-name-js-all.log |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailVariableServiceTest` | PASS，exit 0 | 后端 86 tests，0 failures/errors；Maven 同时执行前端 2181 tests；BUILD SUCCESS；/tmp/expert-name-maven.log |
| `git diff --check` | PASS，exit 0 | 无空白错误 |

初次新增 JS 用例按预期暴露原名未覆盖；全量 JS 曾因旧 DOM stub 不支持复合选择器失败，改为等价分步查询后通过。首次后端运行有两项新增 fixture 未填 company_match_status，补齐数据后重跑全部上述后端用例成功。

## Changed Files

- src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertManualProfile.kt — 姓名变更命令与覆盖快照 DTO。
- src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt — 统一请求解析、精确版本类型校验、管理员写权限及可编辑标记。
- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileService.kt — 读取姓名覆盖、双版本事务保存和回滚。
- src/main/resources/static/app.js — 统一弹窗、姓名展示、保存及预览/异步隔离。
- src/main/resources/static/expert-mail-settings.js — 变量保存后通知当前详情。
- src/main/resources/static/index.html — 已版本化资源统一更新为 20261117-expert-name。
- src/test/js/expertManualProfile.test.js — 统一提交、权限、冲突草稿、标题同步、迟到响应。
- src/test/js/expertMailSettings.test.js — 当前 owner 保存通知，旧 owner 响应不通知。
- src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt — 请求解析与管理员权限。
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileServiceTest.kt — 其他变量保留、事务提交/回滚、冲突、校验与恢复。
- src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt — plain/HTML/preview、无 profile/contact 场景与姓氏隔离。

此前预览/计划阶段生成的 docs/mockups/expert-name-preview、计划及知识记录仍保留，不作为生产功能的运行证据。

## Deviations

- 按用户修订，后端测试目标从旧独立模板覆盖接口调整为 ExpertManualProfileServiceTest / ExpertManualProfileControllerTest；原有 MailVariableServiceTest 保留。
- 事务证据为真实 TransactionTemplate 配合 mock transaction manager 的提交/回滚断言；未运行真实数据库集成验证。
- 旧方案的独立姓名弹窗/恢复按钮不实施；覆盖删除仍由既有变量编辑器或统一接口 nameChange.value=null 支持。
- 未部署、未连接真实 SMTP、未改真实专家数据；尚未以运行中的新后端完成浏览器端到端验收。

## Freshness

- Plan identity rechecked: YES，hash 不变。
- Worktree identity rechecked: YES，main/HEAD/路径不变。
- Reported commits reachable from target branch: N/A，无新提交。
- Required commands run this invocation: YES，按人工修订调整测试类。
- Historical evidence used only as baseline: YES。

## Remaining Blocker

None for implementation handoff. 本记录为执行证据，不是独立验收 PASS。

## Next Action

READY_FOR_VERIFICATION → verify-p；运行新后端后的浏览器验收与部署未执行。
