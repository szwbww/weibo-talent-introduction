# 01b 执行报告 — 人工处理沿用当前登录身份

## Execution Result: READY_FOR_VERIFICATION

- Plan: `docs/plans/2026-10-03/mailbox-suspension-01b-processing-identity.md`
- Plan identity (control): `commit:c486c5c44806b5b4c4db654606c358efb94fec5a`
- Execution epoch: NEW
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`
- Target branch: `fast/2026-10-03-mailbox-suspension`
- Pre-execution HEAD: `e06f226` (docs-only evidence commit 01b transition)
- child_base_sha（派发记录）: `79fb15d350621ab2c39b79217d9a4b7a28b9cb0d`
- Post-execution code SHA（唯一实现提交）: `94378f6` — `feat(fast-p): implement 01b`
- Implementation boundary: `e06f226..94378f6`，仅 2 authored 文件

## Task Status

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| T1 接口最小调整（I-1/I-2/I-3） | IMPLEMENTED | `UnmatchedInboundMailController.kt` | markResolved 增加必需 `HttpServletRequest`，Session 身份缺失/空白抛 401；`MarkResolvedRequest` 三属性默认 null；新增 `MarkResolvedResponse`；成功返回 `{id,processStatus:"PROCESSED",resolvedBy}` |
| T2 身份与兼容验证 | IMPLEMENTED | `UnmatchedInboundMarkResolvedIdentityTest.kt`（新） | standalone MockMvc + MockHttpSession 真实 JSON 解析/序列化，6 用例 |

## 实现说明（对照不变量）

- I-1：`servletRequest.sessionUsernameOrNull()?.takeIf { it.isNotBlank() } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录")`；身份只取 `AuthSessionKeys.USERNAME`；body 中 `resolvedBy/operatorName` 不再读取；`note` 仍透传。AuthInterceptor/AuthWebConfig 未修改。
- I-2：仅调用现有 `PendingMailOperationService.markResolved` 一次，`resolvedBy` 与 `operatorName` 均传同一 Session username；未新增 repository/IP 查询/第二个 save；未写 `expert_mailbox_suspension`；服务异常沿原链路返回，不返回成功 JSON。
- I-3：`MarkResolvedRequest(resolvedBy=null, operatorName=null, note=null)` 三属性全 nullable 且默认 null，`{note:null}` 合法；旧完整 payload 仍 200，identity 取 Session；POST 路径与 200 语义不变。

## Commands

| 命令（cwd=worktree 根，`JAVA_HOME=.../zulu-11.0.32.jdk/Contents/Home`） | 结果 | 证据 |
|---|---|---|
| `mvn -DskipNodeTests=true -Dtest=UnmatchedInboundMarkResolvedIdentityTest,PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest test` | PASS | exit 0；`Tests run: 55, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn -Dtest=UnmatchedInboundMarkResolvedIdentityTest,PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest test` | FAIL（仅 node 阶段） | exit 1；Kotlin `Tests run: 55, Failures: 0, Errors: 0, Skipped: 0`；失败来源为 `exec-maven-plugin:3.1.0:exec (node-test)` 前端用例 `fail 18` |

Kotlin surefire 明细（两次运行一致）：

| 类 | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| UnmatchedInboundMarkResolvedIdentityTest（新增） | 6 | 0 | 0 | 0 |
| PendingMailOperationServiceTest | 37 | 0 | 0 | 0 |
| UnmatchedInboundTrustWorkbenchTest | 12 | 0 | 0 | 0 |

### 基线对照（`children/01b/baseline.md`）

| 类 | 基线 | 本片 |
|---|---|---|
| PendingMailOperationServiceTest | 37/0/0/0 | 37/0/0/0（一致，未回归） |
| UnmatchedInboundTrustWorkbenchTest | 12/0/0/0 | 12/0/0/0（一致，未回归） |
| UnmatchedInboundMarkResolvedIdentityTest | 不存在（新文件） | 6/0/0/0 |

### 已知范围外失败（非本片引入）

- 精确命令在 Kotlin 阶段全绿后，`node-test` 阶段 `node --test src/test/js/*.test.js` 报 `tests 1385 / pass 1367 / fail 18`，全部为 `index.html` 版本化资源缓存键不统一（`20261003-mobile-core-03` vs `20261003-mobile-core-03-generic-followup`）。
- 与 `children/01b/baseline.md` 记录、派发说明一致（worktree 基线 18 个前端缓存键失败，计划由 child 02 修复）。本片未改动任何 JS/静态文件。

## 验收标准逐条对照

- I-1：匿名 401 + `verifyNoInteractions`；空白 Session 401 + 零调用；`admin` + body 伪造 `other` 实际服务两参数均 `admin`（ArgumentCaptor 断言）。✔
- I-2：成功仅调用原 service 一次（`times(1)`），响应 `id/processStatus/resolvedBy` 准确；服务抛异常时无 `PROCESSED` 且非 200（`GlobalExceptionHandler` 映射），服务调用一次。✔
- I-3：最小 `{"note":null}` 与旧完整 payload 均 200，`note` 正确透传。✔
- `PendingMailOperationService.kt`、`AuthInterceptor.kt`、`AuthWebConfig.kt`、`UnmatchedInboundTrustWorkbenchTest.kt` 字节未改（git status 仅 2 文件）。✔

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` — Session 身份、DTO 缺省、成功回包
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMarkResolvedIdentityTest.kt` — 新增身份/兼容/失败回包测试

## Deviations

- None（实现范围与授权 2 文件一致；未新增依赖/表/字段/API 路径；未修改计划与范围外文件）。
- 说明：测试 401 用例使用无 advice 的 standalone MockMvc（依赖 `ResponseStatusExceptionResolver` 映射 401）；服务失败用例使用挂载 `GlobalExceptionHandler` 的第二套 MockMvc（避免 catch-all `@ExceptionHandler(Exception)` 抢占 `ResponseStatusException`）。两套均为测试内部构造，不改变生产行为。

## Not Run / 未覆盖

- 未执行 `docs/plans/2026-10-03/mailbox-suspension-01b-processing-identity.md` 的人工验收清单 A-1/A-2（需独立环境已实施 01 与真实 DB/登录态，属人工验收，非本片自动化范围）。
- 未运行全量 `mvn test` 全绿结论（受 18 个既有前端失败阻塞，已按约束不改范围外文件）。

## Freshness

- Required commands run this invocation: YES（两条命令均本会话新鲜执行）
- Historical evidence used only as baseline: YES
- 产品提交仅一个 `94378f6`，恰含 2 个授权文件，位于目标分支 HEAD；工作区除本报告外干净。
