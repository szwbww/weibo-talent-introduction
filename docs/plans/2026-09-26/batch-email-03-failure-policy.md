# 子计划 03：局部验证暂缓与全局故障停止

状态：DRAFT，仅创建开发计划，未实施、未运行测试、未部署。
目标工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，审计分支 `main`，HEAD `d6f54c25b228ee2e9e0317d053957ae3f56984b5`。当前已有其他未提交改动，见证据 E-00；执行前重查，禁止覆盖。
证据：[源码快照](batch-email-reliability-evidence.md)。E-n 均包含读取命令/原始输出或带原文件行号的摘录；下文“拟改”是设计决策，不是现状事实。

可独立发布；建议顺序为 01→02→03→04，减少共享文件冲突。

## 需求描述

单个邮箱验证未完成、超时或响应无法判定时，暂缓该邮箱并继续候选；明确的验证服务依赖故障才停止本次整批。保留真实发送计数与错误原因，前端区分暂缓和确认不可投递。

必须保持：249 最多两次请求、500ms 重试间隔与原 HTTP 错误映射；undeliverable 仍跳过/标邮箱异常；risky/unknown 按现策略放行；审计失效仍停止；已成功发送和未处理 remaining 不伪造；取消/账号故障/额度/原一轮模式不变。

范围外：增加连续失败阈值、断路器、自动重试队列、后台重验、自动断点续发；停止未来 cron 或自动修改 autoEnabled；把验证局部异常当可投递而直接发信。

## 关键不变量

### Invariant I-1：显式失败分类
- Rule：仅下表中的三个目标级码继续后续候选；四个已存在的依赖级码停止整批。分类是本次业务决策，不声称从截图证明了服务商整体宕机。
- Applies to：ServiceFailure 分支、BatchEmailVerificationErrorCodes helper；不修改 HTTP 客户端或供应商请求参数。
- Violation consequence：单邮箱阻塞整批，或依赖不可用仍扫完整批。
- 来源：original；实际现状映射见 E-05:209–248。

| 已有错误码 | 源码触发条件 | 拟定动作 |
|---|---|---|
| EMAIL_VERIFY_INCOMPLETE | 两次 HTTP 249 | 暂缓该邮箱，继续 |
| EMAIL_VERIFY_TIMEOUT | HttpTimeoutException | 暂缓该邮箱，继续 |
| EMAIL_VERIFY_BAD_RESPONSE | 无法解析/邮箱不匹配/无合法 state，或未单列的 4xx | 暂缓该邮箱，继续 |
| EMAIL_VERIFY_AUTH_ERROR | HTTP 401/403 | 停止整批 |
| EMAIL_VERIFY_NO_CREDITS | HTTP 402 | 停止整批 |
| EMAIL_VERIFY_RATE_LIMITED | HTTP 429 | 停止整批 |
| EMAIL_VERIFY_SERVICE_ERROR | IOException，或未单列 HTTP 状态（含 5xx） | 停止整批 |
| EMAIL_VERIFY_AUDIT_FAILED（E-29:63） | 审计写入失败 | 停止整批 |

SERVICE_ERROR 指固定 Emailable 端点的传输/服务调用失败；这是本系统按依赖故障停止的策略，不是“全部邮箱已验证失败”的证据。BAD_RESPONSE 只说明本次结果不可信，暂缓不意味着放行。新出现的未知错误码不得静默当 PASS；按协议不受支持的依赖故障停止并保留原码，测试须覆盖。

### Invariant I-2：暂缓有明确审计，不能伪装不可投递
- Rule：暂缓行保留 decision=ERROR、原 errorCode/requestCount/checkedAt；send_status=SKIPPED、send_reason=EMAIL_VERIFICATION_DEFERRED，tag_status=NOT_REQUIRED。不改变为 SKIP，不标邮箱异常，不缓存为 undeliverable。
- Applies to：新暂缓分支及控制台读取；复用既有 VARCHAR 列，不新增 schema/decision 枚举。
- Violation consequence：单邮箱错误被下一次过滤永久排除，或历史报表失真。
- 来源：original；E-04/E-05/E-14 证明现有状态字段可承载。

### Invariant I-3：暂缓不耗成功额度
- Rule：严格落审计后 accumulator.recordSkipped(DEFERRED)，processedTotal/roundProcessed/roundRejected 各加 1，roundPassed 不变，continue 补后续目标；failed 不加。取消随时仍可终止。暂缓审计写失败按原审计故障路径停止，不用吞异常的 Quietly helper。
- Applies to：runIntroductionFromSnapshot；新原因在 BatchOutcomeReasonCodes.LABELS 显示“邮箱验证暂缓”。
- Violation consequence：50 封任务因一个暂缓少发，或失去审计仍继续。
- 来源：original；E-06 成功配额循环、E-10 OutcomeAccumulator。

### Invariant I-4：终态与暂停语义分开
- Rule：全局故障继续保留 sent>0→PARTIAL_SUCCESS，否则 FAILED，remaining 保持未处理量。旧 manageRuntimeStatus 路径无论有无成功都转 PAUSED，保留 stopReason；现代配置/手动快照入口只结束当前 execution，不修改 autoEnabled/cron。没有持久化收件人游标，后续执行是新 execution。
- Applies to：发送循环返回、ManualOutreachResult.taskFinalStatus、applyResultToRuntimeStatus。
- Violation consequence：部分发送后故障却显示空闲，或把一次停止误做全站调度停用。
- 来源：K-batch-paused-final-status-is-not-resume；E-11/E-13。

### Invariant I-5：按记录解释历史
- Rule：只有已落 `sendReason=EMAIL_VERIFICATION_DEFERRED` 的 ERROR 行显示“验证暂缓”（warn）；其他历史 ERROR 仍显示原“验证服务异常”（error）。不得仅见旧 INCOMPLETE 就改写成当时已继续发送。ERROR 总计中文改“验证异常”，计数仍来自原 summary.errors；不新增聚合字段。
- Applies to：emailVerificationDecisionText/BadgeClass/SendText、batchEmailVerificationMetricsHtml；HTML 与 JS 的提示常量同时更新。
- Violation consequence：历史执行被误解释；数字口径与标签矛盾。
- 来源：original；E-19。

## 样式契约

### S-1：提示与暂缓展示复用
- 复用 styles.css:9708–9740 的 batch-gate-hint；1068–1085 的 badge.ok/warn/error；9848 的 batch-log-metric.is-failure。本子计划只改文案/既有状态 class，styles.css 不在修改清单。
- 提示字号 11px、line-height 1.5、色 #94a3b8；暂缓 badge 用现有 warn（warning token #d97706），全局错误保留 error；完整规则在证据 E-17/E-26。不得新加 CSS 或 inline style。
- DOM 结构仍为 `<span class="batch-gate-hint" id="batchConfigEditorEmailVerificationHint">…</span>` 及 manual 对应 id；验证结果仍为 `<span class="badge warn">验证暂缓</span>`，发送结果用既有 td 文本。
- 提示固定为：“仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。单邮箱验证未完成、超时或响应异常时暂缓该邮箱；鉴权、额度、限流或服务故障停止本次执行。会消耗 Emailable 额度。”手动版追加“仅影响本次执行。”
- sendReason 映射固定为“邮箱验证暂缓，本次未发送”；不承诺“稍后自动重试”。除提示文本、badge 值、汇总标题外不改 DOM 层级。

## 现状审计

### 验证明细及结果
- Schema、写/读/删除全集：01 现状审计与 E-01/E-04/E-15。当前 verify 先落 PENDING，再写 ERROR，返回 ServiceFailure；loop 所有 ServiceFailure 均 break（E-06:737）。
- 单邮箱 ERROR 已有明确错误码，故可在编排层分类，不需要改 HTTP 协议/新增结果类型。E-05 还证明网络超时与 IOException 是不同码；249 重试间隔固定。
- IP-1：verify(ERROR)→loop 暂缓收尾 recordSend→controller 映射 decision/errorCode/sendStatus/sendReason→前端；IP-2：recordSkipped→OutcomeAccumulator→进度/结果 JSON→跳过原因展示。
- 数据库 ERROR 聚合仍是 ERROR 总数（含目标级异常），前端标题必须同步，不能继续把总数解释为“全局故障数”。

### 执行终态与旧 runtime
- E-06:737/1757：global 故障返回 FAILED/PARTIAL_SUCCESS，taskFinalStatus 从结果推导；E-11:516 原 runtime switch 未包含 PARTIAL_SUCCESS，落到 IDLE。
- E-11:312 manageRuntimeStatus 默认 false；只有旧 startAuto/startManual/oneRound 路径显式 true。现代 startScheduled(configId)/startManual(request) 不是同一个 runtime 状态存储。此前对“部分成功会回空闲”的判断只适用于旧 runtime，不能泛化。
- runtime 写方为 BatchSendControlService 调 BatchSendSettingService.setRuntimeStatus→三个 KV upsert；getRuntimeStatus/getStatus 为读方，命中全集 E-24。既有状态存储不加字段。任务持久化读写 E-13，均不改 schema。
- IP-3：loop 的 stopReason/finalStatus→旧 runtime，及 TaskExecutionService 的 taskFinalStatus→现代执行日志。

### 前端样式盘点
- E-16 是两个提示 span 的逐字 DOM 基线；E-17/E-26 是完整可复用规则。E-19:20051 当前所有 ERROR 固定“验证服务异常”与 error badge，summary.errors 标题“服务异常”；这里只做 S-1 指定替换。
- 样式类不改定义，因此其他使用点无行为变更；源文件全局 class 引用核对纳入 E-27。

## 实现方案

### T1：集中分类和原因码（I-1/I-2/I-3）
文件：BatchEmailVerificationRepository.kt（其已有 ErrorCodes object）、BatchExecutionModels.kt。

在 ErrorCodes object 增可单测的 `isRecipientFailure(code)`/`isGlobalFailure(code)`，上表显式集合；编排层只认 recipient 白名单，其他 ServiceFailure 停止。未知验证错误码作为协议边界错误终止；旧runtime仅对以 EMAIL_VERIFY_ 前缀的非recipient错误及AUDIT_FAILED执行全局暂停判定，普通SMTP错误不被吞入该规则。BatchOutcomeReasonCodes 新增 `EMAIL_VERIFICATION_DEFERRED` 及中文标签。无新类、无新 decision、无新列。

### T2：暂缓继续且保留停止边界（I-1/I-2/I-3/I-4）
文件：ManualInitialOutreachService.kt。

在 ServiceFailure 分支先分流目标级：调用已有严格 recordSend(rowId, SKIPPED, DEFERRED)；捕获 EmailVerificationAuditException 使用现有 AUDIT_FAILED 终止语义，然后才累加跳过/处理量并更新进度，继续下个目标。禁止借用 recordVerificationSendQuietly，该方法会吞审计失败（源码 E-29）。原全局分支维持 stopReason 与 FAILED/PARTIAL_SUCCESS；不计 SMTP 失败、不标邮箱异常。

全部候选暂缓时：任务遍历完成、sent=0/failed=0/skipped=N/remaining=0，execution 为 SUCCESS（表示处理完成），无全局 stopReason；前端能看到全部暂缓，不能暗示成功投递。原 oneRoundOnly 的正常暂停仍保留；“只有全局失败停整批”仅指验证错误分类，不取消用户暂停/账号额度等原控制。

### T3：旧 runtime 精准修正（I-4）
文件：BatchSendControlService.kt。

applyResultToRuntimeStatus 保留 current.status!=RUNNING 保护；在正常完成/oneRound 分支前，仅当 result.stopReason 是全局验证故障/审计故障时转 PAUSED 并 return。不得把所有 PARTIAL_SUCCESS 都暂停；普通 SMTP 混合结果继续原行为。现代入口不调用该 runtime 更新，不改 scheduler/autoEnabled。

### T4：展示/提示及测试（I-5/S-1）
文件：app.js、index.html、三个测试文件。

按 I-5/S-1 实施；原始 errorCode 保留在详情。测试应从真实 index.html 断言提示节点存在，再执行 JS 函数，不能只依赖永远返回元素的 DOM stub。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` | ErrorCodes 中显式局部/全局分类，不改 HTTP 映射 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 新增 EMAIL_VERIFICATION_DEFERRED 原因及中文标签 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 局部错误跳过并继续；全局错误继续终止 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 旧 runtime 全局错误即使 PARTIAL_SUCCESS 仍暂停 |
| `src/main/resources/static/app.js` | 按落库暂缓原因展示文案；验证异常总计 |
| `src/main/resources/static/index.html` | 两个发送前验证提示语同步 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 局部继续/全局停止/审计失败矩阵 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 旧 runtime 与现代执行入口区别 |
| `src/test/js/batchEmailVerification.test.js` | 暂缓与历史 ERROR 展示回归 |

共 9 个文件；验证编排和其控制台展示两个紧密关联部分。

## 验收标准

- I-1：表中全部已有错误码参数化测试；INCOMPLETE 连续两次实际模拟响应仍返回 ServiceFailure，但编排继续；不改已有 BatchEmailVerificationServiceTest 249 行为；BAD_RESPONSE 不发该地址；三个目标级码都不得进入全局停止路径。
- I-2/I-3：候选 A 暂缓/B 通过/C 通过，roundSize=2 → sent=2/failed=0/skipped=1/remaining=0；A 为 ERROR+SKIPPED+DEFERRED+NOT_REQUIRED，原 errorCode 保留；SMTP 仅 B/C；A 无联系/绑定/标签副作用。全暂缓同样完成；暂缓审计写失败立刻停止、不再验证 B。
- I-4：第一个目标 NO_CREDITS → FAILED、sent=0、remaining 未处理数；已发一个后 NO_CREDITS → PARTIAL_SUCCESS、sent=1、其后无 HTTP/SMTP；旧 runtime 两者都 PAUSED 且原因保留；普通 PARTIAL_SUCCESS 原行为不变；现代入口不改 config.autoEnabled；取消优先行为回归。
- I-5/S-1：新暂缓为 warn、历史 ERROR+NOT_SENT 仍 error；未通过和暂缓不混为同一数；summary.errors 仍原值、标题“验证异常”；两个静态提示与动态提示一致；CSS 文件无 diff。
- 命令：
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest test`
  - `node --test src/test/js/batchEmailVerification.test.js`
  - `git diff --check`

## 人工验收清单

### A-1：单邮箱暂缓后补足额度
- 前置条件：本地测试 fixture/FakeEmailableVerifyClient；第一邮箱返回 249 两次，后两邮箱 deliverable；隔离 SMTP sink；每轮成功目标=2。
- 操作步骤：1. 打开发送前验证，执行一轮。2. 查三行验证明细及 SMTP sink。
- 预期结果：第一行“验证暂缓”，请求次数 2，标签“无需处理”、本次未发；后两行已发送；成功2/跳过1/失败0，无 INCOMPLETE 整批停止原因。重试仍间隔500ms。
- 覆盖：I-1/I-2/I-3/I-5/S-1，IP-1/IP-2；请求成本不扩大。

### A-2：全局故障与部分成功
- 前置条件：同一 fixture 分别设置“首个402”和“第一个通过、第二个402、第三个通过”；准备旧 runtime 与现代 API 的测试入口。
- 操作步骤：1. 分别运行两个序列。2. 看执行终态、remaining、后续请求。3. 对照旧流程状态与现代定时配置。
- 预期结果：分别 FAILED/PARTIAL_SUCCESS；故障之后无验证/发信，remaining 保留；旧流程 PAUSED；现代 execution 已停止，配置 autoEnabled 没被自动关闭。
- 覆盖：I-1/I-4，IP-3；不改变未来调度。

### A-3：审计与历史回归
- 前置条件：测试中令暂缓 recordSend 失败；另备旧 ERROR+NOT_SENT、现有 undeliverable、risky/unknown 的日志 fixture。
- 操作步骤：1. 执行审计失败场景。2. 查看历史和本次日志。3. 观察 badge/提示及标签写入。
- 预期结果：审计失败整批停止；旧 ERROR 文案不伪装已暂缓；undeliverable 仍“未通过”并标异常；risky/unknown 按策略放行；暂缓不标异常；仅现有样式值，未增加 CSS。
- 覆盖：I-2/I-3/I-5/S-1、IP-1；原标签/验证策略不变。
