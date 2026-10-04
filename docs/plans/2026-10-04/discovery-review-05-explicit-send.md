# 05：批量目标同源与模板开关真实生效

依赖：01–04。**D1未定案，本子计划可开发纯计算能力，但不得启用发送切换，不得声称“所有过滤已清零”。**

## 需求描述

发信消费已经完成的准入结论；只按当前页面的显式条件做本次任务筛选。预估、ES新目标、DB重试、手动与定时执行语义一致；模板门禁关闭后真正关闭。保持任务节奏、取消、账号配额、投递失败记录及非批量邮件行为。

范围外：新增审核状态过滤开关；新增业务过滤项；改变自动回复/会议内容；未经D1确认开放历史重发、退订或重绑。

## 关键不变量

### Invariant I-1：发送只消费准入结果
- Rule：发现专家使用04批量读取的持久结果，AUTO_PASSED/MANUAL_APPROVED/LEGACY_APPROVED同等准入；同身份批准后不调用基础资格、机构/国家存在性或凭证验签来再次拒绝。未初始化/身份变化/人工拒绝是明确展示的准入状态，不混在“发送失败”中；非发现专家保留原准入入口。
- Applies to：候选与有效层、预估、执行、NEW重试、旧首发。
- Violation consequence：仍有隐藏学术门槛。
- 来源：用户澄清；K-batch-send-filter-retry-parity。

### Invariant I-2：每个过滤有明确控件来源
- Rule：本次仅使用funnelLevel、tags、regions、emailDomains、discipline、operatorStatuses、expertTypes、researchDirectionFilter、gateFilterEnabled、excludeVerifiedUnavailableEmails、emailVerificationEnabled/AllowedStates。发送账号/节奏是执行配置。状态空集合不限；类型空集合仍零人；模板门禁关闭不能再因变量缺项产生PERSONALIZATION_INCOMPLETE。
- Applies to：RecipientScope、ES query builder、重试、composer。
- Violation consequence：页面开关失效、默认黑盒。
- 来源：E3；用户要求。

### Invariant I-3：选择及解释同源
- Rule：统一selector返回逐人admission结果、所有不匹配filterKeys、进入目标与否；同一记录多原因可重复计“原因命中”，总排除人数按人去重。preview无写入；执行与重试使用同一selector，不再二次近似过滤。实时供应商验证未运行时只显示“待执行验证”，不能提前标通过。
- Applies to：countBySnapshot、INTRODUCTION/MATERIAL目标构造、统计。
- Violation consequence：预估与执行不一致、失败人数无法对账。
- 来源：K-recipient-count-preview-parity、K-batch-send-round-loop-symmetry。

### Invariant I-4：模板筛选与发送使用同一内容
- Rule：开关开启时用同一模板ID/版本、专家seed、实际选中变体和MailVariableService实际值做完整变量判定，不能只靠ES字段白名单。关闭时不检查个性化缺项，已有`${key|fallback}`正常生效，裸变量缺值按当前renderText变为空串，不伪造专家信息；语法错误/残留占位符是明确模板错误，不静默改成专家不合格。
- Applies to：selector、IntroductionMailComposer、材料提醒调用ManualExpertMailService。
- Violation consequence：关了仍挡、预估人数不实、发送虚假内容。
- 来源：E3/E6；共享composer审计。

### Invariant I-5：共享组件不改变其他发送路径
- Rule：composer新增显式内部参数enforcePersonalizationGate，默认true保持人工单发/旧调用；批量传快照开关。不得修改全局PersonalizationGateService语义或一律关闭SMTP抑制检查。D1业务判断仍列待定，不能重命名为系统错误掩盖。
- Applies to：INTRODUCTION/MATERIAL、人工单发、旧首发调用、SMTP。
- Violation consequence：修批量却破坏回复/会议，或未经授权放开退订。
- 来源：K-dual-outreach-paths、K-suppression-check-call-sites。

## 现状审计

公共审计E3/E4/E5的所有发送分支属于本子计划审计范围；精确读点：ManualInitialOutreachService.resolveScope:474/countBySnapshot:497/buildRetryableTargets:1456/countEsTargets:1750/fetchEsPage:1814/buildEsFiltersForLevel:1839，循环:705–971，材料快照:1668和循环:313–375。InitialOutreachService:58/187重复学术门禁。

`RecipientScope.matchesExpert:211`及matchesEsTarget再次调用matchesDiscoveryOutreach；保留isDiscoveryOutreach给CandidateEligibility的国籍语义使用，不顺手删掉。`IntroductionMailComposer:29`不看开关；`ManualExpertMailService:242`也做门禁，材料提醒经该服务发送。SMTP:22还会拦退订。`OutreachTargetIterator`按orcid去重、offset归零依赖目标变化；此计划不改其算法，但测试全页过滤后仍继续读下一页，不能以空过滤页当扫描结束。

涉及存储均为读：ES三层（全部写路径E5）、admission表（02/04写）、contact/mail历史/验证记录（原写路径保留）。新增selector不得create campaign/contact、写验证审计或发HTTP验证请求来计算预估。结果明细使用现有任务结果/日志承载，完整名单可分页只读计算，不新增统计表。

## 实现方案

1. **I-1/I-3**：新增`BatchRecipientSelectionService.kt`，集中规范配置、批量准入lookup、显式条件匹配、原因输出。现有方法成为适配层；preview先按真实_id去重，再与重试合并去重计数；不重复计候选/有效层同人。同一数据下count=selector最终目标数，不是ES hits。每500名批量查准入，杜绝N+1。
2. **I-2/I-3**：移除候选首发且statuses空时的notContacted隐式基座。ES只做不会错误排除的已配置粗筛，内存同一语义收口。发现地区按country；非发现按现有country OR nationality，修正重试不一致。邮箱大小写/规范值使用同一归一，不能仅ES或仅内存变规则。研究方向PRESENT维持当前exists且非空串语义，模板变量另按实际trim值判断。
3. **I-1**：计划最终切换删除各发送点对`matchesDiscoveryOutreach`的调用；可以保留旧纯函数供历史兼容测试，但生产发送不得使用。人工通过后`filterResult=REJECTED`也不再二次拒绝；模板/地区/类型的显式选择仍生效。
4. **I-4/I-5**：IntroductionMailComposer新增纯`evaluateForBatch`或等价共用方法，使用实际render及rawTexts，返回缺key；compose以参数控制evaluate。ManualExpertMailService增加内部批量调用策略参数，默认保持人工单发；材料提醒显式传开关，不再漏这一条。不要把参数暴露为任意前端“跳过所有限制”。
5. **I-4**：resolveScope不再用白名单删掉familyNames等字段后假称检查完成。预估冻结模板版本摘要；执行开始读取同模板版本，发现模板已变则更新预估提示/拒绝使用过期预估令牌，不能沿用旧人数。运行中模板变化不混用：复用本次内存模板内容快照，必要快照信息写已有request_payload，不加数据库列。账号变量缺项按所选账号集合在任务启动前显示模板/账号配置错误；不能用account=null把全部专家筛掉。
6. **I-3/I-5**：NEW重试与ES同源；历史sent/permanent/bound/suppressed处置必须接后续D1政策，再移除或替换原分支。材料提醒缺contact同样在D1明确前保留为待决冲突，不以“结果为空”蒙混验收。旧cron路径没有页面快照，应接入同一可见配置或明确退出批量入口；具体由D1子计划一并给出，不继续保留独立隐式默认。
7. **I-1–I-5**：同一个fixture跨count、manual、scheduled、retry运行；新selector能列出未准入及显式条件排除人。结果类型定义在新selector文件中，避免为DTO额外拆文件。两发送循环都覆盖，不改发送节奏。

## 变更文件清单

共12文件；批量选择与共享组装2子系统；数据库/ES新字段0。D1尚不在此表授权内。（A3 修正：新增 2 个文件用于启动期模板版本/令牌校验与内存模板快照渲染 seam；预览响应 DTO 字段扩充在已授权的 ManualInitialOutreachService 内完成。）

| 文件 | 操作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt | 移出隐式学术门禁、解释显式条件 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt | 新增统一选择/解释 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | 预估/两循环/重试接入；A3：预览响应 DTO 补齐准入计数与逐人原因 key |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt | A3：启动时模板版本/过期预览令牌校验；请求快照信息写 request_payload |
| src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | A3：按内存模板内容快照渲染的 seam（运行中模板变化不混用） |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt | 最终切换共用准入，保持未切换行为 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt | 精确模板判定与参数 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt | 材料批量参数，默认保持人工行为 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt | 新增真值表 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | preview/new/retry/material |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | 旧入口回归/准入 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchTemplateGateParityTest.kt | 新增跨两composer/非批量回归 |

## 验收标准

- I-1：批准后缺机构/国家/凭证、filterResult非PASSED样本进入目标；不再调用旧凭证门禁；未初始化状态单独显示，不谎称待审核。
- I-2：逐项控制开关，未选择地区不判国家、状态空不限、研究方向ANY不判断、有显式模板门禁才检查；类型空仍零人。
- I-3：人工/定时/重试同样配置相同名单；标签/状态变更导致名单变化须给具体key；preview无数据库写与验证外呼；500名每批lookup有界；预览 HTTP 响应含准入计数与逐人原因 key（06 数据源）。
- I-4：裸变量空+关→不阻断；开→精确缺key；fallback不阻断；变体seed一致；模板变更有版本不一致提示；占位符残留有明确错误。
- I-5：人工单发/自动回复/会议相关原测试回归；材料提醒开关也生效；SMTP全局策略没被意外改动。
- **整体验收前置**：D1闭合后重新运行batch-gates检索，逐项记录全部分支来源；任何未解释continue/filter意味着主计划FAIL。

## 人工验收清单

### A-1：开关真的控制行为
- 前置条件：已准入A/B，A缺primaryResearchField、B有值；模板裸变量，两者标签类型符合；使用模拟投递。
- 操作步骤：1. 门禁关闭预估/执行。2. 开启预估/执行。3. 改模板为带fallback再开启。
- 预期结果：步骤1目标2，A正文对应变量为空串而非伪造研究方向；步骤2目标1、A显示模板变量缺失；步骤3目标2且A使用明确fallback。
- 覆盖：I-2/I-4、X6。

### A-2：状态与多入口一致
- 前置条件：2名已准入、不同联系状态、其中1人为NEW重试；D1政策已确认且允许本用例。
- 操作步骤：1. 状态留空预估。2. 选NOT_CONTACTED。3. 相同快照分别手动/定时/重试模拟执行。
- 预期结果：空值不自动追加NOT_CONTACTED；选择后只匹配所选状态；三个入口名单与对应预估一致。
- 覆盖：I-1/I-2/I-3、X7。

### A-3：共享组装回归
- 前置条件：现有人工单发、自动回复、会议、材料提醒各一份测试数据；账号配额故障可注入。
- 操作步骤：1. 执行旧非批量用例。2. 材料提醒开关开/关各测一次。3. 给批量注入配额不足并取消。
- 预期结果：非批量原门禁保持；材料提醒遵循开关；配额/取消显示执行原因，不把已批准专家改成不合格。
- 覆盖：I-5、必须保持项、X8/X9。
