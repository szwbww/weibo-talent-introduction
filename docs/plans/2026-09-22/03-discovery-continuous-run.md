# 子计划03：统一入口、持续运行与可见状态

日期：2026-09-22。状态：详细计划已完成，待用户确认，未实施。
依赖：[01账号预算](01-openalex-account-budget.md)、[02论文队列](02-discovery-paper-queue.md)验证通过。使用02既定launch/pause/resume/tick/status契约；不在本阶段偷偷补数据库状态机。

## 需求描述

- R-1：手动与定时使用同一流水线和已保存查询；4小时窗口结束后自动续跑，官方额度恢复后自动继续可用工作，人工暂停持续有效。
- R-2：点击启动立即得到明确“已受理/失败”结果；展示真实预算、队列和等待原因，不长期挂“初始化中”；采集数与专家数分别显示。
- R-3：深度发现后台持续运行时，用户仍可点击检查回复。
- 保持：RND_TARGET和禁用来源、专家资格/邮箱/去重/学术补全规则；旧模式关闭开关后可回退；任务历史/executionId隔离；除“深度发现↔检查回复”组合外的现有互斥；既有UI布局和视觉样式。
- 范围外：邮件收发实现、检查回复定时策略、全局任务中心重构、新配色、新资产、创建新任务类型、修改预算和队列存储模型。

## 关键不变量

### I-1：所有入口只使用一份已保存配置
- Rule：pipeline-enabled=true时，/run、/run/by-keyword统一规范化并调用02.launch；scope强制RND_TARGET、source按同一规则解析。定时tick只推进已启用的同一pipeline，不另造默认查询覆盖人工条件，不检查“今天已执行一次”来阻止续跑。首次部署desired_state=PAUSED，用户明确启动后才持续运行；人工暂停后cron、日切、重启均不得launch。
- Applies to：两个手动入口、原cron、新恢复tick、状态查询。
- Violation consequence：手动/定时查询分叉、暂停失效、每日只跑一次。
- 来源：本次用户明确要求及02接口契约。

### I-2：HTTP接受与实际完成严格分开
- Rule：新模式POST在配置持久化成功后返回202：mode=CONTINUOUS、pipelineId、phase、executionId可空。202不是完成，不显示“专家发现完成”。不同查询冲突409；同查询重复启动幂等；保存失败503且保持配置界面可重试。executionId空时不得绑定历史最新任务；仅status提供的新currentExecutionId可绑定。executor暂不可用仍有持久化QUEUED任务由后续tick恢复。
- Applies to：controller响应、前端launch、轮询、任务记录绑定。
- Violation consequence：假成功、重复任务、错绑旧日志、初始化挂住。
- 来源：当前config.run未await及先切页面的源码审计。

### I-3：暂停控制持久化且可恢复
- Rule：新模式EXPERT_DISCOVERY取消接口先调用02.pause，成功后请求当前窗口取消；窗口间隙也必须200且状态PAUSED，不能因没有RUNNING记录返回409。暂停幂等，resume是显式用户操作；只暂停发现生产/消费，已独立入队的学术补全仍可继续。关闭弹窗不等于暂停。暂停响应后可能有在途结果保存，不能宣称立即撤销已发请求。
- Applies to：TaskProgressController.cancel、pipeline/resume、取消按钮及页面重开。
- Violation consequence：看似取消实际次日重启、误停独立补全。
- 来源：02-I-7。

### I-4：后台窗口不占调度线程
- Rule：新模式cron和30秒恢复tick只调用02.tick派发，不同步调用discover、不睡眠等待配额/PDF；全局owner和同查询互斥由02数据库保证。特性开关关闭时旧cron每天一次语义保留。feature开启但不存在持久化启用记录时保持暂停，不自动导入正在运行的旧进程。
- Applies to：ExpertDiscoveryScheduler、部署切换、重启。
- Violation consequence：长任务堵住其他定时器、两个模式并跑。
- 来源：现有同步cron审计。

### I-5：预算、进度、等待和故障诚实呈现
- Rule：新模式无固定每日总论文数，不显示虚假的百分比100%。进度显示CONTINUOUS状态与采集论文/ORCID记录、活跃队列/已处理、新增/重复专家；预算显示免费上限、官方已用、本地预占、保护后可用、下次reset。数据未同步用“待同步”，不填0。WAITING细分DAILY_BUDGET/QUEUE_FULL/RATE_LIMIT/ENRICHMENT_RESERVE/BUDGET_SYNC/OWNER_RECOVERY/SOURCE_ERROR；多个原因同时展示，可继续的来源不被渲染为全部停止。窗口终态不等于pipeline结束。
- Applies to：status DTO读取、弹窗轮询、任务摘要/通知。
- Violation consequence：误判完成、额度误解、错误放大故障影响。
- 来源：K-circuit-breaker-terminal-status及02计数口径。

### I-6：只放开经过确认的互斥组合
- Rule：前端运行锁针对请求任务判断，仅允许EXPERT_DISCOVERY与CHECK_REPLIES并存；同类型仍禁止重复，其他组合保持现有互斥。后端既有检查回复实现/任务锁不改。请求状态失败不得被当作“所有任务空闲”，应显示检查失败并允许重试。
- Applies to：executeDiscover、executeCheckReplies及其启动前检查；统一弹窗回调。
- Violation consequence：全天深度发现永久挡住检查回复或过度放宽其他操作。
- 来源：当前progressStoreHasRunningTask全局判断审计。

### I-7：界面生命周期与旧记录隔离
- Rule：获取来源/启动/轮询都有有限超时与可见错误；来源加载失败时禁止按空列表意外启动所有默认来源。启动配置在切视图之前读取，includeRawScan显式true/false。每次异步回调校验modal generation、taskType和pipeline/执行标识，关窗或切任务后旧响应不得覆盖新弹窗。旧模式/其他任务继续现有显示与通知语义。
- Applies to：来源加载、config.run、executeDiscover、status轮询、取消/恢复、弹窗重开。
- Violation consequence：配置丢失、一直初始化、旧响应污染新任务。
- 来源：当前runBtn先切视图，executeDiscover内部可能提前return。

## 样式契约

### S-1：只更新现有状态节点
- 复用：`.modal-content.task-modal` styles.css:4417；`.task-modal-progress` :4443；`.task-modal-status`及四种状态 :4486–4518；`.task-progress-detail` :3432；`.text-muted` :2489。不改规则，不新增class/CSS文件；仅更新textContent、hidden和既有状态class。
- DOM结构：保持下面既有层级，原有inline样式逐字保留，本次禁止新增/改写inline样式。方括号表示待填纯文本，不是新增DOM。

```html
<div id="taskModalProgressSection" class="task-modal-progress-section" hidden>
    <div class="task-modal-progress">
        <div class="task-progress-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <span id="taskModalStatus" class="task-modal-status">[状态]</span>
            <span id="taskModalPercent" class="task-progress-percent" style="font-weight: 600; font-size: 13px;">[持续运行]</span>
        </div>
        <!-- 既有task-progress-track及taskModalFill原样保留；持续模式隐藏track -->
        <div id="taskModalMessage" class="task-progress-detail" style="font-size: 12px; color: var(--text-muted); margin-top: 6px;">[指标与等待原因]</div>
    </div>
    <!-- taskModalBySource原样保留，沿用现有来源明细renderer -->
    <div class="task-modal-actions" style="display: flex; justify-content: flex-end;">
        <button class="button warn" id="taskModalCancelBtn" onclick="handleCancelTask()">[暂停发现/恢复发现]</button>
    </div>
</div>
```

- 颜色映射：QUEUED/RUNNING/WAITING复用running（#0ea5e9）；PAUSED复用cancelled（#d97706）；FAULTED复用failed（#e11d48）；DRAINED无失败复用completed（#059669），有失败复用failed并写“已排空，存在失败”。状态名/原因文字必须同时显示，不只用颜色。
- 禁止项：新增徽章卡片、改变弹窗宽度、innerHTML拼接未经转义的错误/URL/查询词、新配色或进度百分比。

### S-2：配置和暂停/恢复操作
- 复用：现有#taskLaunchDesc/#taskLaunchRunBtn/#taskModalCancelBtn；`.button` styles.css:802、hover :825、active :832、primary :838/847；无新增按钮/DOM。已有#taskLaunchRunBtn的inline渐变保持。
- 暂停时同一#taskModalCancelBtn文本改“恢复发现”；运行/等待时“暂停发现”，逻辑由handleCancelTask分派，pending请求时disabled=true，finally恢复。disabled使用现有原生禁用属性，无新增CSS。
- #taskLaunchDesc显示“持续运行；人工暂停后不会自动恢复。已入队的学术补全可继续。”或来源加载错误。加载错误按钮disabled，重新打开弹窗重试加载；不另造重试组件。
- 所有class规则不修改，因此无跨使用点样式变更；不允许借本计划整理原有inline样式。

## 现状审计

### 后端入口与共享状态
- ExpertDiscoveryController两个POST当前同步runAndRecord→discover；scope已有RND_TARGET修复。GET sources按相同scope禁用医学源；enrich有独立执行器，本计划保持。
- ExpertDiscoveryScheduler旧cron先countScheduledSince(today)，随后同步discover。新模式应绕过“当天已跑”限制，同时只tick已保存配置；旧模式保留此限制。
- TaskProgressController.cancel目前只requestCancel进程内TaskProgressStore，窗口间隙409，不能持久化停用。X-1：取消接口→02控制表→重启/tick；X-2：手动/定时→同一pipeline→相同stream。
- 02新表写者仍只有DiscoveryPaperQueueRepository，读者由pipeline service统一封装；本计划controller只调用service，不直接SQL。01预算由policy.snapshot提供，不在controller重新计算美元/credits。
- task_execution/task_progress_log写者仍TaskExecutionService/TaskProgressStore；读者包括TaskProgressController历史/日志、任务中心、前端弹窗。X-3：202的可空executionId→状态接口→正确窗口日志；既有历史记录不迁移。

### 前端交互与缓存
- app.js openTaskLaunchModal的runBtn回调先隐藏配置、显示进度，再config.run()且不await；executeDiscover里全局锁可能直接return。此路径存在“显示初始化但没有发起任务”的风险；这是源码确认的路径，不据此断言当前线上每一次初始化均由此导致。
- loadDiscoverySources捕获异常只console.error；当前无法可靠区分未选来源与加载失败。executeDiscover在openTaskModal之后才读取sources/includeRawScan，存在视图重置风险。
- progressStoreHasRunningTask扫描所有类型；executeCheckReplies和executeDiscover均受它阻挡。X-4：深度发现运行状态→前端全局锁→检查回复入口。
- index.html当前11个版本化资源均`20260922-task-activity-center`；精确反查仅src/test/js/taskActivityCenter.test.js写死此键。X-5：app.js发布→index资源键→固定键测试。（来源：K-frontend-cache-key-triad；这是工作区快照，实施前必须重查。）

### 前端样式盘点与改动前基线
- token：primary=#1e40af、primary-hover=#1e3a8a、primary-active=#172554、text-muted=#94a3b8、radius-sm=7px、panel-border=rgba(15,23,42,0.08)。弹窗max-width700px；modal-body padding20px；现有内联gap20px；进度区gap8px。
- `.button`高32px、左右padding12px、字号12px、字重500、圆角7px；hover translateY(-1px)及阴影0 2px 6px rgba(15,23,42,0.08)，active scale(.97)/opacity .85。`.task-modal-status`字号12px、padding2px 8px、字重600。原生button disabled，无独立新增视觉规则。
- 当前节点逐字基线（index.html:1085、1139、1145）：

```html
<p id="taskLaunchDesc" class="text-muted" style="margin: 0; font-size: 13px; line-height: 1.5; color: var(--text-muted);"></p>
<div id="taskModalMessage" class="task-progress-detail" style="font-size: 12px; color: var(--text-muted); margin-top: 6px;">初始化中...</div>
<button class="button warn" id="taskModalCancelBtn" onclick="handleCancelTask()">取消任务</button>
```

对应CSS逐字摘录（styles.css:3432、4443、4486）：

```css
.task-progress-detail {
    font-size: 11px;
    color: var(--text-muted);
    font-family: var(--font-mono);
}
.task-modal-progress {
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.task-modal-status {
    font-size: 12px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    background-color: var(--surface);
    color: var(--text-muted);
    font-family: var(--font-body);
}
```

本计划只复用这些规则。#taskModalMessage实际字号由原有inline的12px覆盖11px，验收以12px为准。

## 实现方案

### T-1：统一控制器协议（I-1/I-2/I-3/I-5/I-7）
修改清单1、3、4、6。

- 开关true时两个POST复用02.launch返回202；false走原同步逻辑。保留既有构造测试调用兼容，例如新增末尾可选ObjectProvider参数；生产开关true而依赖缺失必须明确失败，不能静默走旧逻辑。
- 新增GET `/api/expert-discovery/pipeline`返回02.status及mode=CONTINUOUS/LEGACY；无新查询返回PAUSED、configured=false，不能返回旧task最新记录冒充当前pipeline。新增POST `/api/expert-discovery/pipeline/resume`显式恢复已保存条件，沿用现有鉴权。
- 既有POST `/api/task-progress/EXPERT_DISCOVERY/cancel`按I-3持久化暂停；其他taskType保持原行为。新接口不返回API Key、不发起耗时官方网络校准；仅读取共享快照。
- 返回状态文本映射：QUEUED“已受理，等待执行”；RUNNING“正在采集和处理”；DAILY_BUDGET“OpenAlex额度已用尽，等待北京时间08:00重置”（以真实reset格式化日期时间，不硬写下一天）；QUEUE_FULL“队列已满，正在处理已采集论文”；PAUSED“已暂停，需手动恢复”；FAULTED“运行失败：具体原因”。多个来源局部错误单列来源，不全部报失败。
- 02负责窗口task result；这里不新增共享TaskProgress枚举、不修改统计提取器。MockMvc断言202、409、503、权限、空executionId及取消间隙；旧控制器测试保持编译并全量运行。

### T-2：接通持续tick（I-1/I-3/I-4）
修改清单2、5。

- 原cron入口按开关分支：新模式仅tick，旧模式完整保留。新增fixedDelay读取02的pipeline-tick默认30000ms，同样开关判断后tick。已有enabled条件仍生效。
- 不在scheduler中启动新查询或恢复PAUSED；第一次由用户点击开始写RUNNING，之后cron/恢复tick共用保存的条件。使用数据库owner防多个Tomcat上下文/实例同时跑；scheduled调用耗时仅状态检查/派发，外部API时间不计入scheduler线程。
- 测试同日多个tick能续窗口、cron与tick竞争仅一owner、预算reset后恢复、手动暂停跨日不恢复、旧模式仍每天一次。

### T-3：受理、状态和操作反馈（I-2/I-3/I-5/I-6/I-7，S-1/S-2）
修改清单7、8、9、10。

- executeDiscover先读取并冻结keywords/sources/includeRawScan，再做针对任务的互斥检查。来源配置成功加载之前不能开始；来源加载10秒超时，启动POST15秒超时，状态查询10秒超时。旧模式耗时POST沿用原长请求机制，不能被新模式15秒截断；先通过pipeline GET确定模式。
- runBtn回调await config.run；仅深度发现新模式在202受理后切进度，其余任务仍由原启动函数接管；异常显示并恢复配置/按钮。GET模式/来源失败留在配置区域，显示“加载失败：原因；请重新打开重试”，禁止停留“正在加载”无出口。
- 新模式202不走原COMPLETED通知分支；先显示“已受理，等待执行”。轮询pipeline接口3秒一次、单次最多一请求；generation校验。执行ID非空才调用既有bindTaskModalExecution。窗口完成继续轮询pipeline，不能因某个历史COMPLETED停止追踪。
- 复用#taskModalMessage的纯文本格式：`论文：采集 100 / 已处理 40；ORCID：采集 20 / 已处理 10；队列 70；专家：新增 8 / 重复 12；OpenAlex：已用 227 / 上限 10000 credits，本地预占 2，可用 9771；等待：…`。所有数字来自快照，未同步显示“待同步”；无日总数时#taskModalPercent写“持续运行”并隐藏既有track，退出新模式恢复原显示。
- #taskModalCancelBtn复用暂停/恢复，显示规则按S-2；关闭弹窗停止该modal轮询，后台继续。重新打开深度发现先查pipeline，有已有配置即展示状态；PAUSED显示恢复按钮，配置变更不默默覆盖旧积压。
- progressStoreHasRunningTask新增请求类型参数，仅深度发现/检查回复双向放开；其他调用默认保持旧逻辑。状态读取失败可见而非return false。后端检查回复逻辑不动。
- index.html全部11项缓存键同步改`20260922-discovery-continuous`；taskActivityCenter.test.js将CACHE_KEY改为从styles.css派生，保留资源一致性断言，避免再次固化。实施前若资源/固定键测试变动超出清单，先更新计划，不能遗漏后直接上线。

### T-4：上线与回退步骤（I-1至I-7）
执行前两阶段部署验证已通过；本阶段关闭pipeline-enabled先发布并鉴权检查旧功能。确认没有旧深度发现在跑，再开启特性开关、重启Tomcat，首次仍PAUSED；用户点击启动才进入持续模式。确认部署目录只有预期应用上下文，备份不得放Tomcat webapps目录内被再次加载。部署前确认数据库可用磁盘支持01/02容量与保留策略。

只在既有上线授权仍明确适用时执行发布；本计划交付本身不是上线记录。回退先pause并等待在途结果保存/窗口释放，关闭特性开关后重启；新表、队列和旧游标均保留。旧流程可能保守重放已有内容，由既有专家去重处理，不能用采集cursor覆盖旧已处理cursor。恢复新模式从未完成job继续。

## 变更文件清单

恰好10个文件，2个子系统：发现入口/调度控制、任务弹窗。无共享存储新增字段、无CSS改动。

| # | 文件 | 改动 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt | 模式分支、202、status/resume |
| 2 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt | 专用服务派发及恢复tick |
| 3 | src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt | 发现暂停持久化 |
| 4 | src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt | 新旧协议与异常 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt | cron/tick/暂停回归 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt | 窗口间隙暂停及其他任务回归 |
| 7 | src/main/resources/static/app.js | 启动、轮询、暂停恢复、精确互斥 |
| 8 | src/main/resources/static/index.html | 11项资源缓存键 |
| 9 | src/test/js/discoveryContinuousRun.test.js | 异步UI/状态/互斥场景 |
| 10 | src/test/js/taskActivityCenter.test.js | 缓存键派生保持资源契约 |

## 验收标准

- I-1：两个POST规范化输出相同；cron/tick读取已保存条件，scope/source一致；当日多窗口可运行；初始PAUSED及人工暂停不被cron解除。
- I-2：慢外部API下POST仍快速202（隔离环境≤2秒）；无executionId时不绑定旧记录；202不触发完成通知；重复同查询幂等，异查询409，落库失败503。
- I-3：运行中/窗口间隙/额度等待均能暂停；重启跨reset不恢复；显式resume续原队列；独立学术补全不被取消。
- I-4：调度测试阻塞PDF100秒时scheduler派发调用1秒内返回，其他测试定时tick可执行；旧模式每天一次回归。
- I-5：全部等待原因、未知预算、混合来源状态均有文案；不显示日总百分比；窗口终止与整体状态不混淆；故障不显示成功。
- I-6：深度发现↔检查回复两方向允许；同类型拒绝；发现↔RAW_PROMOTION_SCAN及其他组合保持拒绝；状态查询失败不擅自启动。
- I-7：10秒来源超时/15秒新模式POST超时有出口；启动409恢复配置；先读取选择；关闭/切换弹窗后旧响应不更新；旧模式测试通过。
- S-1/S-2：styles.css无本计划差异；无新增class/inline样式；DOM既有层级保持；颜色、字号、状态class、暂停/恢复文本按契约；11资源同键且固定键测试无遗漏。
- Java11定向：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test`。
- 前端：`node --check src/main/resources/static/app.js`、`node --test src/test/js/*.test.js`。新增测试应实际调用函数、控制Promise和时间，不只grep源码；用浏览器鉴权实测启动/暂停/刷新/回复检查。最后package一次；HTTP401只证明权限存在，不算功能验收。

## 人工验收清单

### A-1：统一入口与日内续跑（X-2）
- 前置条件：隔离环境完成01/02迁移、feature=true、窗口1分钟/tick30秒、初始PAUSED，准备三源固定数据；登录页面，关闭外发。
- 操作步骤：1. 点击开始；2. 再点击同查询启动；3. 等待首窗口结束并手动触发测试cron；4. 查看pipeline配置与新窗口；5. 提交不同关键词。
- 预期结果：第一次202、2秒内“已受理”；重复不新增并行窗口；下一tick续原队列和同一RND_TARGET配置；不同查询409；人工/定时来源一致。
- 覆盖：R-1/R-2、I-1/I-2/I-4，范围与门禁保持项。

### A-2：额度等待与人工暂停（X-1）
- 前置条件：Mock OpenAlex预算耗尽、reset两分钟后，Crossref/公开全文仍有任务，新专家独立补全已有入队。
- 操作步骤：1. 观察等待文案和其他来源；2. 在窗口间隙点“暂停发现”；3. 重启并跨reset；4. 重开弹窗点“恢复发现”。
- 预期结果：OpenAlex显示额度等待及真实reset，其他工作继续；暂停返回200、显示PAUSED；跨reset/重启不自动启动；恢复续原任务；已独立入队补全按其预算/调度可继续；关闭弹窗本身不暂停。
- 覆盖：R-1、I-3/I-5，学术补全保持项。

### A-3：初始化失败与错绑回归（X-3）
- 前置条件：浏览器DevTools可阻断测试来源/状态请求；已有一条旧COMPLETED发现记录。
- 操作步骤：1. 阻断来源后打开启动弹窗并等10秒；2. 恢复网络重新打开并启动，令worker暂缓启动；3. 返回202空executionId；4. 关闭再开检查回复弹窗，释放旧状态响应；5. 恢复worker。
- 预期结果：来源失败显示“加载失败”，按钮禁用而非自动选择全部源；202显示“已受理，等待执行”；不显示旧任务完成日志；旧响应不污染检查回复弹窗；worker启动后只绑定新executionId。
- 覆盖：R-2、I-2/I-7，任务历史隔离保持项。

### A-4：检查回复并行与其他锁（X-4）
- 前置条件：测试邮箱有一封可识别回复、无发信任务，深度发现持续运行。
- 操作步骤：1. 点击检查回复；2. 再次点击检查回复；3. 尝试RAW快速晋升；4. 停止两个测试任务后，先启动检查回复再启动发现。
- 预期结果：首个检查回复受理并读取该回复；重复检查被阻止；RAW晋升仍受原互斥阻止；反向顺序发现可受理；不触发邮件发送。
- 覆盖：R-3、I-6，其他互斥及邮件实现保持项。

### A-5：指标、样式及发布缓存（X-5）
- 前置条件：快照注入论文采集100/处理40、ORCID20/10、队列70、新增8/重复12、官方已用227/上限10000、本地预占2/可用9771；保存改动前弹窗截图。
- 操作步骤：1. 打开新模式弹窗；2. 切换WAITING/PAUSED/FAULTED；3. 查看样式和Network资源；4. 切回旧模式任务。
- 预期结果：逐值显示上述数字，“持续运行”无假百分比；暂停橙色#d97706、失败#e11d48；宽700px、消息12px、圆角7px保持；11资源同版本；暂停/恢复按钮可操作；旧任务恢复原进度条。
- 覆盖：R-2、I-5/I-7、S-1/S-2，UI保持项。

### A-6：关闭开关与专家规则回归
- 前置条件：隔离环境包含合法、无效邮箱、身份歧义、已存在APPLICATION样本，feature=true且有已保存未消费结果；无外发。
- 操作步骤：1. 完成一轮新发现/补全；2. pause并等窗口释放；3. feature=false重启运行旧模式；4. 再打开feature并显式恢复。
- 预期结果：仅合格专家按原规则晋升；运营字段不变、三层学术局部更新保持；旧入口和原每天一次cron仍可用；新表保留，恢复不重下已存结果；任务历史按各自executionId读取；邮件发送0。
- 覆盖：I-1/I-3/I-4/I-7及全部业务/回退保持项。
