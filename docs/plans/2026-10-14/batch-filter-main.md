# 批量介绍邮件过滤修复：MAIN 主计划

状态：计划完成，未实施。2026-10-14。本文件约束三个子计划的范围、依赖、共享语义和联合验收；本次只编写计划，不执行代码修改、部署、线上数据更新、Emailable 或真实发信。

|顺序|子计划|交付|执行文件数|进入下一阶段条件|
|---|---|---|---:|---|
|01|[地区筛选修复](batch-filter-01-region.md)|未知国家按“其他”筛选，ES与精确判断一致|4|01 的 I-1～I-3 机器验证通过|
|02|[统一预检查与过滤解释](batch-filter-02-diagnostics.md)|完整母集、独立原因、只读诊断、预估与执行同源|10|02 的 I-1～I-6 通过，真实SQL测试已执行；01回归通过|
|03|[页面解释与提示修正](batch-filter-03-ui.md)|两个表单展示相同事实，模板提示与开关一致|5|03 的 I-1～I-3、S-1/S-2 通过，再做联合验收|

## 需求描述

1. “其他”勾选对新发现专家实际生效；每个过滤结果可解释，页面与执行共用确定性判断。
2. 数据准入、任务条件、发送保护、待执行验证、任务运行状态分别表达，不能把“已准入”或“符合任务条件”承诺为必然可发送。
3. 不得改变：非新发现专家的既有地区规则；人工准入与身份保护；退订、已发送、永久失败和绑定保护；真实docId/ORCID去重；执行快照；邮箱/模板开关本身；页面交互和样式；未涉及的旧首发及材料催办流程。
4. 范围外：发现自动准入政策调整、姓名邮箱证据采集、人物多邮箱合并、主邮箱选择、历史名单回填、国家推断、解绑/重发、自动任务节奏和账号策略调整。历史46,884条人工审核名单不因本组代码计划自动获得准入。

## 关键不变量

### Invariant M-1: 顺序与文件边界
- Rule: 严格01→02→03；后一个基于前一个已验证产物实施。子计划各自的变更清单是代码修改边界：4/10/5文件，去重16文件（9业务/页面、7测试）；MAIN不把它们合成一个超限实施步骤。新增文件或改变共享语义，先同步修订MAIN和受影响子计划。
- Applies to: 实施、验证、修复、合并。
- Violation consequence: 后续覆盖已有修复，或借主计划扩展业务范围。
- 来源: K-master-plan-shared-file-sequential-gates。

### Invariant M-2: 同一快照、同一范围、完整原因
- Rule: discovered 的空/空白/Other/未映射国家只在Other选中或地区不限时通过；明确国家按选中地区判断，非discovered地区规则不变。02在01基础上建立仅含层级/标签的诊断母集，不能重新用地区粗筛后的子集冒充完整母集；预估、新ES目标、NEW重试沿用同一快照与最终preflight。既有docId/ORCID去重保留，不新增人物合并规则。
- Applies to: 01 matcher/ES读取、02母集与执行读取、03两个表单。
- Violation consequence: Other再次被漏掉、原因缺失或不同入口口径漂移。
- 来源: K-recipient-count-preview-parity；K-filter-baseline-reason-independence。

### Invariant M-3: 共享响应和计数只有一套口径
- Rule: 02输出、03直接消费下表；持久准入事实独立于本次排除原因。某人已MANUAL_APPROVED但地区不合，仍保留该准入事实。每个原因按目标去重，多个原因允许重叠；完整统计满足baseline=excludedUnique+eligibleBeforeLiveVerification，awaitingVerification是eligibleBeforeLiveVerification的子集。partial结果不能套用完整总数承诺。旧响应字段含义保持兼容，旧响应缺新字段时UI显示详细解释不可用，禁止补0。
- Applies to: preflight DTO、预估/诊断接口、运行日志、页面、跨计划fixture。
- Violation consequence: 多个页面各算人数、重复原因被当成人数、待验证被当已验证。
- 来源: K-filter-baseline-reason-independence。

|共享字段/概念|唯一含义|
|---|---|
|baseline|所选层级/标签母集中按既有规则去重后的目标数，不等同已归并的自然人数|
|excludedUnique|确定性条件/准入/保护未通过的目标去重数|
|eligibleBeforeLiveVerification|确定性检查通过数，仍可能等待真实验证或发送前复核|
|awaitingVerification|实时验证开启但无有效可复用结果的通过目标数|
|reasonHits / 样本|每个原因的命中数；每类样本最多20条，超限明确truncated|
|asOf / partial|统计时点及是否完整；不宣称跨ES/MySQL原子快照或持续实时保证|
|verificationPhase|DISABLED、CACHED_ALLOWED、CACHED_REJECTED、AWAITING_VERIFICATION；不新增持久状态|

### Invariant M-4: 解释不能变成写入或绕过保护
- Rule: 预估/指定docId诊断只读，不创建campaign/contact/task/Pending验证/发送尝试/准入；供应商、SMTP调用为0。邮箱缓存判据沿用现有仓储，按当前两开关和allowedStates解释；无缓存不等同供应商unknown。执行仍保留发送前的抑制、发送历史、永久失败、绑定与身份/人工决定保护；额度、自检、供应商故障归运行状态。模板gate关闭不因个性化缺项排除，模板不存在仍是配置错误。
- Applies to: 02全链路、03提示和只读交互；01不得改变这些事实。
- Violation consequence: 看预估触发付费/发信，或“修复黑盒”变成重复发送与误准入。
- 来源: K-filter-baseline-reason-independence；三个子计划的保护约束。

### Invariant M-5: 验证与发布范围相匹配
- Rule: 每个子计划有独立机器验证；03完成后执行MAIN联合机器检查及人工清单。测试使用本地fixture、无外发stub/捕获器，不能靠生产发送或Emailable验证本功能。01可独立交付，02向旧UI兼容；独立交付只代表相应子集完成，三个计划及联合验收完成前不能宣布整体问题已解决。代码验证、人工验收、部署分别记录，本次写计划不将任何一项标为已完成。
- Applies to: 阶段状态、联合验收、交付与回滚。
- Violation consequence: 只修后端就声称页面可解释，或把未执行/跳过测试记为通过。
- 来源: K-master-plan-shared-file-sequential-gates；子计划01/02独立交付边界。

## 样式契约

### S-M1: 完整继承03，不增加另一套界面设计
- 复用：[03的S-1/S-2](batch-filter-03-ui.md#样式契约)为唯一DOM与样式契约；保留batchConfigEditorRecipientHint、batchManualRecipientHint及两个gate说明ID。复用batch-config-editor-hint、batch-gate-excluded、batch-gate-warnline、batch-gate-hint。
- DOM结构：逐字使用03的S-1内部骨架；两个gate说明逐字使用S-2。MAIN不新增控件或结构。
- 新增CSS：无。容器margin-top=10px、padding=8px 12px、font-size=12px、line-height=1.6；gate说明11px/1.5。styles.css差异必须为零。
- 禁止项：inline style、新class、改变原控件/绑定提示。500ms防抖、旧响应丢弃、HTML转义保持。03中16个资源引用统一新key；实施前若资源基线已变，先按实际引用同步修订计划，不覆盖其他功能资源。

## 现状审计

### 已核对的存储与跨计划读写链

审计依据：[报告](../../audits/2026-10-14-batch-filter-transparency/report.md)、[路径检索](../../audits/2026-10-14-batch-filter-transparency/grep-evidence.txt)、[16文件清单](../../audits/2026-10-14-batch-filter-transparency/scope.json)及三个子计划现状审计。MAIN新增治理关系，无新业务存储/写入点。

|存储/状态|既有写入方|本组消费方与跨计划交互|
|---|---|---|
|RAW/CANDIDATE/APPLICATION，dynamic=false|ExpertIndexService、ExpertIndexWriterService及发现/投影/晋级调用方|X1：01读取国家和标签；02以层级/标签取母集并共用matcher；03解释结果。mapping与写入方不变|
|持久准入、任务配置|DiscoveryReviewService人工/自动决定、重验；配置服务与旧适配入口|X2：02分别读取身份绑定准入与执行快照；03仅展示，不重写人工决定/原任务|
|contact/mail_record/attempt/suppression|联系人服务、SenderAccountBindingService、ManualOutreachTxHelper、收发件/退订/退信路径|X3：02新增参数化批量SELECT，解释已有保护；执行前动态复核仍保留；03显示原因|
|batch_email_verification|BatchEmailVerificationService.verify的Pending/复用/供应商结果写入|X4：02只读解释现有有效记录与当前开关；03呈现缓存通过/拒绝/待验证；预估不调用verify|
|模板内容、页面内存及DOM|模板服务；原表单事件与预估渲染|X5：02按实际模板变量判断；03提示跟随开关、忽略过期响应；保存和发送handler不改为诊断入口|

各存储schema与全部既有路径索引继承01/02审计，frontend基线继承03审计；本轮重新检索确认matchesRegion仍拒绝Other、selector仍先筛条件再加载准入、count与执行分支和两个UI函数均存在。已发表审计数量是冻结证据，不能写成部署后的固定人数断言。

### 共享文件与接续要求

|精确共享路径|顺序|接续要求|
|---|---|---|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|01→02|保留Other最终匹配语义；02诊断扫描取消提前丢人的粗筛，不可复制01之前旧函数覆盖|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|01→02|01地区/快照/重试用例保留；追加preflight/只读/动态保护用例|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|01→02|01地区矩阵保留；追加准入事实独立与去重用例|

02→03为接口依赖，没有共享实现文件。03不能在前端重算地区、准入、历史邮箱有效期或原因人数。技术兼容允许01/02独立部署，不能套用其他MAIN的强制原子发布要求。

## 实现方案

### G-0：授权后实施前检查（M-1～M-5、S-M1）
- 核对实际工作树、三个子计划版本、上述共享文件、16文件集合和页面资源引用；保留现存无关改动。只按各子计划精确清单修改产品代码。
- 冻结下列联合fixture和响应契约；发现差异先修订受影响计划。历史名单、人物合并、自动准入政策不作为顺手修复项。

### G-1：01→02→03，各自留验证记录（M-1～M-5、S-M1）
1. 01仅按其4文件清单执行和验证I-1～I-3；地区改变不产生ES或准入写入。
2. 02仅按其10文件清单执行I-1～I-6；读取上表现有写方的数据，不新建业务写路径。JDK11定向测试、真实SQL IT必须有执行结果；跳过IT不能通过本阶段。检查每批500、每原因20样本、指定docId最多200；重新验证01。
3. 03仅按其5文件清单执行I-1～I-3/S-1/S-2；直接消费02实际响应。运行三个JS测试、关联批量页面契约测试和语法检查；核对真实DOM与资源键，不能只信DOM stub。

### G-2：共同fixture与联合验收（M-2～M-5、S-M1）

测试母集固定10名不同docId/ORCID的discovered专家，全部MANUAL_APPROVED；排除下表特例外均满足任务条件、模板、保护检查。开实时验证，allowedStates=deliverable/unknown；缓存使用仓储认定有效的原始记录。

|目标|唯一特例|排除贡献|通过/待验证|
|---|---|---:|---|
|D1、D2|各自地区和任务所选状态同时不匹配；不存在实际发送/绑定/抑制事实|2人；地区2次+状态2次|0/0|
|D3|仅缺模板实际必填字段，gate开启|1人；模板1次|0/0|
|D4|仅邮箱有有效risky缓存，当前不允许risky|1人；邮箱1次|0/0|
|D5～D7|有效deliverable缓存|0|3/0|
|D8～D10|无有效验证缓存|0|3/3|

统一断言：baseline=10，excludedUnique=4，eligibleBeforeLiveVerification=6，awaitingVerification=3；原因命中合计 **6次**（2+2+1+1），不是排除6人。02/03原稿的“5次”是算术错误，随本MAIN同步修正。

- 对同一fixture和snapshot核对API、两个页面、执行器进入实时验证前的目标集合；实际邮件投递数量不能拿来替代eligibleBeforeLiveVerification。
- 再执行保护动态变化、地区矩阵、两类开关、错误/partial/旧响应、样式与限量场景。失败归回所属子计划，修复后重跑受影响阶段和联合断言。

### G-3：交付与回滚（M-1、M-5）
- 各阶段记录实施版本、机器验证命令/结果、尚未完成人工项。最终必须具备三个子计划门禁结果和MAIN人工结果，才能称整体完成；文档完成不等于实现完成。
- 沿用01/02可独立交付及向旧UI兼容的约束；发布动作仍以届时用户授权为准。回滚可整体回退，分步按03→02→01；若暂时出现新UI配旧响应，必须只显示“详细解释不可用”，不得伪造0或发送保证。本组无schema/数据迁移，不用数据反写回滚代码。

## 变更文件清单

本MAIN编制只涉及以下4份计划文档；未来代码实施严格继承各子计划文件清单，合计16个唯一文件，清单不扩大。

|序号|精确路径|本次文档变更|
|---|---|---|
|1|docs/plans/2026-10-14/batch-filter-main.md|新增主约束、依赖、联合验收|
|2|docs/plans/2026-10-14/batch-filter-01-region.md|增加MAIN约束链接|
|3|docs/plans/2026-10-14/batch-filter-02-diagnostics.md|增加MAIN链接；共同fixture原因5→6，明确3人待验证|
|4|docs/plans/2026-10-14/batch-filter-03-ui.md|增加MAIN链接；同步共同fixture原因5→6|

知识命中计数维护不构成产品执行授权；本次无新存储路径或通用规则，不另新增知识条目。

## 验收标准

- M-1：三个阶段执行顺序和独立门禁有记录；子清单4/10/5、交集三文件、并集16；无未声明产品文件，后续保留前序回归。
- M-2：Other矩阵和非discovered基线一致；相同snapshot预估、新ES和NEW目标集合一致；层级/标签外目标显式OUTSIDE_BASELINE、不存在为NOT_FOUND；母集未被地区/模板提前缩小。
- M-3：共同fixture API与两个页面均10/4/6/3；原因合计6且只排除4；准入仍MANUAL_APPROVED；旧字段兼容，partial不伪称完整。
- M-4：预估/诊断业务写与供应商/SMTP调用均0；两开关四组合、gate开关、有效/过期/错误缓存和动态抑制通过；保护策略不放宽。
- M-5：SQL IT实际通过；三个子计划机器结果与联合机器结果齐全；MAIN人工清单尚未执行时不能标整体已验收；无生产验证动作。
- S-M1：03的DOM/CSS契约、真实ID、防抖/旧响应/转义检查通过；CSS改动为0；资源版本统一。

## 人工验收清单

本节为联合验收权威清单，补充而不替代各子计划A项。开始人工验收时再导出同目录`batch-filter-main-acceptance.md`，逐项带验收人、日期和结果；本次不生成空验收记录冒充完成。

### A-1：共同计数与解释闭环
- 前置条件：隔离测试环境通过fixture导入接口/脚本建立G-2的D1～D10；供应商为拒绝调用stub，SMTP为本地捕获器；打开同一个任务快照。
- 操作步骤：1. 打开任务编辑预估；2. 打开同快照手动执行预估；3. 展开原因；4. 请求D1的诊断；5. 查看业务行数和调用记录。
- 预期结果：两处10/4/6/3；地区2、状态2、模板1、邮箱1，排除仍4；D1仍MANUAL_APPROVED；新增业务记录0、供应商/SMTP调用0。
- 覆盖：需求1/2；M-2～M-4；X1/X2/X4/X5；S-M1。

### A-2：Other和非新发现回归
- 前置条件：按01 A-1/A-2准备未知国家四例、Japan/Morocco两例及非discovered基线；其余检查均通过，验证两开关关闭。
- 操作步骤：1. 仅选Other；2. 改选Japan/Korea；3. 对比API与两处页面；4. 比较国家、标签、准入原值和非discovered结果。
- 预期结果：Other时未知四例地区通过、Japan/Morocco均不通过；Japan/Korea时明确Japan通过、未知四例不通过；无档案/准入变化，非discovered与原基线相同。
- 覆盖：需求1/3；M-2/M-4；X1。

### A-3：保护、重试和快照
- 前置条件：按02 A-3准备已发送、永久失败、绑定、抑制、人工拒绝及可发者；可发者同时出现在新ES/NEW来源，SMTP仅本地捕获；旧首发及材料催办有既有基线结果。
- 操作步骤：1. 同快照预估与诊断；2. 给可发者新增抑制；3. 改当前配置为不同地区；4. 执行先前快照；5. 对比旧首发/材料催办测试结果。
- 预期结果：五种保护各有原因，可发者仅计1；执行沿原快照，新增抑制生效，本地捕获0封；绑定、准入、历史发送事实不变；旧首发/材料催办与基线相同。
- 覆盖：需求3；M-2/M-4/M-5；X2/X3。

### A-4：验证与模板开关
- 前置条件：测试环境有有效risky、无缓存、有效undeliverable三人和一名仅模板缺项者；拒绝调用供应商stub；其余条件齐全。
- 操作步骤：1. 按02 A-2切换实时/历史邮箱开关和allowedStates；2. 对模板缺项者开/关gate；3. 查看两处提示及验证表行数。
- 预期结果：实时验证开启、不允许risky时前者排除，无缓存者待验证；仅历史过滤开启时只排除undeliverable；两者关闭无邮箱历史排除；gate开启排除模板缺项1人，关闭显示“不因个性化缺项排除”；不出现“计入失败”；验证新增行0。
- 覆盖：需求2/3；M-3/M-4；X4/X5。

### A-5：异常、限量和页面契约
- 前置条件：按02 A-4准备1,201目标/30模板缺项及日志；浏览器可延迟响应、模拟500/partial/旧响应；测试页浅色/暗色各一屏。
- 操作步骤：1. 展开模板样本；2. 请求201个docId；3. 连续切换地区制造旧响应后到；4. 分别模拟500、partial、缺新字段；5. 比较页面计算样式与资源。
- 预期结果：样本最多20且标截断；201个ID明确参数错误；只显示最新选择结果；依次显示“预估不可用”“统计未完成”“详细解释不可用”，不伪造0；绑定提示和原选择保留；样式为S-M1实值，资源同key；无新增发送/验证请求。
- 覆盖：需求1/3；M-1/M-3～M-5；X5；S-M1。
