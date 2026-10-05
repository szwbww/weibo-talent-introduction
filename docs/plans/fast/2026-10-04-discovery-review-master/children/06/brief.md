# Fast-P Child Brief — 06（深度发现审核前端）

## 身份与边界

- Master plan：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:60d97bbe1425c429b4e6e66409a5585fcd08b3f7`（A8）。
- 本 child 完整批准合同：`docs/plans/2026-10-04/discovery-review-06-ui.md`，identity `commit:60d97bbe1425c429b4e6e66409a5585fcd08b3f7`（A7）；全部章节生效，冲突以计划原文为准。epoch 2，fix_round=0，保留 epoch1 报告并追加新执行结果。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = b699750b9a84ed56224541e3137cdf1c79f77e7e`（= child 05 code head）。
- 依赖：01–05 API 契约稳定。D1 未定案：不得写"所有黑盒已清除"等断言式承诺；未定案文案按计划 I-4 保留"待定/未定案"表述。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-evidence/frontend-baseline.md`、`cache-key.txt`、`docs/plans/2026-10-04/discovery-review-audit.md`（E6/X2/X5/X6/X7/X9）。
- 基线命令结果：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`（全量 JS 1434/1434 通过，0 失败）。
- 真实浏览器验收（S-1/S-2/S-3、1440px/390px、真实交互）属人工验收，不在本片自动化命令内；不得以 node 测试宣称 UI 验收通过。

## 全局约束

1. 只允许修改 Authorized Files 内 10 个文件；其余文件只读。接续既有六文件未提交工作，不删除/重做已完成 child。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/06/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 06`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网、连线上环境、发信、部署；不得新增依赖。
6. 保持：采集启动/暂停/定时/日志；其他任务弹窗 700px；现有批量配置保存与手动快照字段；键盘可操作；刷新后以数据库审核为准；不改既有 class 规则、无 inline style、无新路由框架。
7. 缓存键：11 项已有资源统一 bump 至 `20261004-discovery-review`（同值同时）；执行前重查当前键；若发现固定旧键测试需要改动而文件不在授权清单，返回 PLAN_CONFLICT（证据见 cache-key.txt：当前测试按动态键读取，预期无需改测试）。
8. 新增 DOM id 只按计划 S-1/S-2/S-3 契约；动态事实一律 textContent，禁止未转义 HTML；错误/空态/加载态使用同一契约。
9. 不得新增审核筛选控件到批量任务；批量页文案只显示"准入通过/待审核/显式条件排除/本次目标"。
10. 人工预授权（2026-10-04，适用 04–06）：若既有测试的**精确计数/集合断言**仅因本计划合法新增/变更的枚举、目录或 taskType 条目而失败，你可以在本 child 内对该测试文件做**最小重同步**（只改计数/集合/样例字面量；不弱化、不删除断言、不改无关语义），并在执行报告中逐条列出文件与旧/新断言；控制器据此记录 amendment 行并同步主计划文件数上限。超出该类别（行为断言、产品语义、其他文件）仍必须返回 PLAN_CONFLICT。
11. 全量 `mvn test` 既有 19 个 `ExpertContactLocationServiceTest` 错误为 master base 既有（祖先 ab8e4cb，时区目录 America/Coyhaique），不得改动该测试或其配置来"修复"。

## Authorized Files（10）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 包装原采集 DOM、增 nav/panes、两处文案、统一版本键 |
| 2 | `src/main/resources/static/app.js` | 生命周期（两个打开路径）、真实接口、范围确认、请求序号、轮询 |
| 3 | `src/main/resources/static/styles.css` | 仅追加 S-1/S-2/S-3 逐字规则 |
| 4 | `src/test/js/discoveryReview.test.js` | 新增交互/HTML/请求测试 |
| 5 | `src/test/js/taskModalLifecycleIntegration.test.js` | 其他任务与双打开路径回归 |
| 6 | `src/test/js/gateTemplateFilter.test.js` | 两入口开关文案/快照一致 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryReview.kt` | A7 initialized DTO |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | A7 初始化来源/筛选 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` | A7 真实扫描边界回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` | A7 直接关联回归（必要时） |

A7 合同详见计划步骤6：initialized 表示 admission 行存在，revision=0/identityChanged 独立；保留原有效 decision 和 ALL_MATCHING 范围。追加必跑：JDK11 `mvn -DskipTests test-compile` 和 `mvn -Dtest=DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest test`，及真实扫描 API smoke。原 JS/浏览器约束不变。修改 exported DTO 前先 LSP references。

## 关键不变量（计划 I-1～I-4 与 S-1～S-3；逐字以计划为准）

- I-1：审核只挂 EXPERT_DISCOVERY；自动通过不显示待审；数量/状态来自 API；确认后"处理中"，持久 APPLIED 才更新成功数；未初始化/同步失败与待审分开。
- I-2：所选=所选 IDs；当前页=当前展示可审核 IDs；所有页=服务端当前筛选全部 NEEDS_REVIEW；确认框列范围/固定总数/原因命中/备注；所有页等 READY；筛选变化使未确认快照作废；全选当前页≠全选所有页。
- I-3：modal generation + 独立 review 请求序号；切 tab/关弹窗/换任务取消或丢弃旧响应；关闭弹窗不取消后台审核；错误保留名单与具体原因。
- I-4：前后端词义一致；无新增审核筛选控件；模板门禁关闭文案明确不按个性化缺项排除；D1 未定案不写断言式承诺；已批准缺项继续显示、不用绿勾伪装补齐。
- S-1/S-2/S-3：CSS 逐字、DOM 层级/ID 对齐、复用既有 class、不改旧规则、无 inline style、`#taskProgressModal[data-discovery-review="true"]` 仅标记触发宽度；其他任务仍 700px。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
node --check src/main/resources/static/app.js
node --test src/test/js/discoveryReview.test.js
node --test src/test/js/taskModalLifecycleIntegration.test.js
node --test src/test/js/gateTemplateFilter.test.js
node --test src/test/js/*.test.js
```

（若实现涉及其他已改 JS 文件，逐一对 `node --check`；新增 id 的渲染函数须在测试中断言 id 出现在 index.html 源文本，避免 DOM stub 掩盖悬空引用。）

## 下游接口/上游产出（01–05 已交付；code heads：05=b699750b9a84ed56224541e3137cdf1c79f77e7e）

- 审核 API（02/03，前端直接消费）：`GET /api/discovery/review/experts`（level/tag/from/size/q/issue/decision，服务端过滤）；`POST /api/discovery/review/batches/prepare`（scope=IDS|ALL_MATCHING；ALL_MATCHING 返回 202 后轮询）；`POST /batches/{batchKey}/confirm`（仅 batchHash）；`POST /batches/{key}/retry`；`POST /batches/{key}/cancel`；`GET /batches/{key}?afterId&limit`；`GET /history?docId=`；`POST /items/{id}/revoke`。
- 批处理状态：`DiscoveryReviewBatchPhase` = PREPARING/READY/PREPARE_FAILED/APPLYING/APPLIED/CANCELLED；item 状态 STAGED/READY/APPLYING/APPLIED/STALE/FAILED/CANCELLED；24h 未确认过期；重试只重领 FAILED/未处理；STALE 需重新 prepare。task type：`DISCOVERY_REVIEW_PREPARE`（"发现审核名单固定"）、`DISCOVERY_REVIEW_APPLY`（"发现审核应用"），均有中文名与进度白名单；计数 prepare success=inserted、apply success=applied / failure=failed+stale。
- 批量页数据源（05 交付）：预览响应 `PendingOutreachSummary` 已含 `template: PreviewTemplateVersion{templateId,versionToken,enabled,mailType}`、`admission: RecipientAdmissionCounts{admitted,needsReview,explicitFilterExcluded,target}`、`reasonHits: Map<String,Int>`、`excludedRecipients: List<PreviewExcludedRecipient{docId,orcidId,admissionState,filterKeys,reasonKeys}>`；`BatchSendConfigController.previewRecipients` 原样返回。启动期过期预览令牌 → 409；账号变量缺项 → 422 预启动配置错误。
- 准入状态词义（05 selector）：`RecipientAdmissionState`（含 UNINITIALIZED/IDENTITY_CHANGED/MANUAL_REJECTED/ADMITTED/NOT_DISCOVERY 等）；`RecipientFilterKeys`/`RecipientAdmissionReasonKeys` 为原因 key 单一来源。
- D1 未定案：前端不得写"所有黑盒已清除"等断言式承诺；缺项事实继续显示。
- 05 终态：全量 `mvn test` 4653/0F/19E（仅 master base 既有 ExpertContactLocationServiceTest 19E，见 brief 规则 11）；JS 1434/1434。01–05 均 `LIGHT_PASS_WITH_NOTES`（O-1…O-6 见各 verify-log）。

## 交付物

- 产品提交：`feat(fast-p): implement 06`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/06/execution.md`，包含 execute-p 规定字段。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
