# 03：生产控制台开关与逐邮箱日志

状态：待开发批准。前置：01、02已验证。基于线上“收发件箱→批量发送→批量邮件任务控制台”，保留“定时任务/手动执行”两页签和右侧执行日志抽屉。

## 需求描述

1. 定时任务编辑、手动执行均可选择“发送前验证邮箱（Emailable）”，可见是否开启及手动差异。
2. 执行日志可分页查看每个进入验证的专家/邮箱、验证状态/原因/时间、发送结果和标签处理结果。

必须不变：现有任务列表、两个页签、抽屉入口；手动来源与临时覆盖；已有运行进度、成功/失败/跳过/剩余指标及批次时间线；旧执行可读、开关缺省关闭；前端不掌握私钥。

不做：第三个日志页签、全站样式重构、验证结果导出、复杂筛选面板、批量清理异常标签、把未验证目标伪装成已通过。

## 关键不变量

### Invariant I-1: UI字段全链路
- Rule: 新建默认false，编辑从View回填；手动选源/还原来源/清空来源/深拷贝/规范化/差异比较/确认页/预估/提交均保留boolean。材料提醒时禁用并显式提交false；切换回介绍邮件不偷偷开启。手动差异不回写原配置。
- Applies to: app.js编辑、手动草稿、请求构造
- Violation consequence: 界面勾选但实际未验证，或预估/确认与执行不同。
- 来源: K-batch-console-source-identity；K-batch-console-regression-contract

### Invariant I-2: 明细来源、历史和隔离
- Rule: 验证明细按executionId读取新表，开关以该次requestSnapshot为准，不读当前配置推断。无字段/false显示“未启用邮箱验证”；true且无行显示“尚未进入邮箱验证”。API校验taskType=MANUAL_INITIAL_OUTREACH；传configId必须等于execution.batchConfigId。配置软删仍可全局按executionId读取。
- Applies to: 新GET接口、历史日志、前端展示
- Violation consequence: 历史被配置改写或跨任务泄漏/串日志。
- 来源: K-batch-task-config-snapshot-log-identity

### Invariant I-3: 状态表达不混淆
- Rule: PASS≠发送成功；SKIP显示验证未通过和provider原state/reason；ERROR显示服务异常且标签无需处理；tagStatusFAILED必须显示“标签写入失败”。SENDING在终态显示“结果未确认”，不显示“未发送”。原发送failure不混入验证拒绝；新验证汇总独立显示。
- Applies to: 新DTO/表格/汇总
- Violation consequence: 误以为所有通过邮箱都已发、所有服务故障都是坏邮箱。
- 来源: original

### Invariant I-4: 分页、轮询和安全
- Rule: afterId默认0、limit默认50最大100，后端严格范围校验；按id升序limit+1判断hasMore，下一游标取本页最后id。前端每次打开/切换执行/切页/关闭增加请求序号，旧响应丢弃；轮询刷新当前页，不重置游标与展开行。不另开timer，接入既有1500/3000ms循环；同一页请求未完成不重复发起。所有文本escapeHtml或textContent，接口失败显示“验证明细加载失败”，不能冒充0行。
- Applies to: 查询接口、app.js状态及异步读取
- Violation consequence: 串页、重复请求、XSS、加载失败显示假结果。
- 来源: original

### Invariant I-5: 保留旧链路且局部展示
- Rule: 原日志 DTO/折叠时间线/六个指标不改口径；新区域在batchLogMetrics后、integrityWarning前。现有静态资源版本查询串统一更新，不编辑其它已有未提交样式。前端/API只读验证明细，GET绝不触发验证或发送；无密钥字段。
- Applies to: HTML/JS/CSS与新GET
- Violation consequence: 影响现有发送控制台或查看日志产生付费请求。
- 来源: K-frontend-cache-key-triad；K-batch-console-log-timeline

## 样式契约

### S-1：两处配置开关与任务状态展示

- 复用 `styles.css:9331 .batch-config-field-label`、`:9354 .batch-task-status-toggle`、`:9378 .batch-task-status-switch`、`:9401 checked`、`:9409 focus-visible`、`:9708 .batch-gate-field`、`:9710 .batch-gate-row`、`:9717 .batch-gate-toggle`、`:9731 .batch-gate-hint`。禁用套现有 `.batch-gate-field.is-disabled`（透明度.6）且input.disabled=true；键盘焦点用现有3px蓝色环。既有规则不改，无新增CSS。
- 在两处“发送控制”grid开头插入（整行span），目标骨架：

```html
<div class="batch-config-field batch-gate-field" id="editorFieldEmailVerification">
  <span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>
  <div class="batch-gate-row">
    <label class="batch-task-status-toggle batch-gate-toggle">
      <input type="checkbox" id="batchConfigEditorEmailVerification" aria-describedby="batchConfigEditorEmailVerificationHint">
      <span class="batch-task-status-switch" aria-hidden="true"></span>
      <span class="batch-task-status-label" id="batchConfigEditorEmailVerificationLabel">已关闭</span>
    </label>
    <span class="batch-gate-hint" id="batchConfigEditorEmailVerificationHint">仅验证通过才发送；未通过跳过并标记邮箱异常。会消耗 Emailable 额度。</span>
  </div>
</div>
```

- 手动版同骨架替换：外层ID=`manualFieldEmailVerification`；checkbox/label/hint IDs分别为 `batchManualEmailVerification`、`batchManualEmailVerificationLabel`、`batchManualEmailVerificationHint`。外层末尾加现有差异 DOM：`<span class="batch-config-diff-badge" hidden>已修改</span><div class="batch-config-diff-original" hidden></div>`。手动hint末尾加“仅影响本次执行。”，差异清单复用原 formatter/确认弹窗，不增加新样式。
- 定时任务列表现有门禁pill旁追加 `<span class="batch-gate-pill">邮箱验证 · 开</span>`；关闭用 `batch-gate-pill is-off`，文字“邮箱验证 · 关”。复用 styles.css:9779/9792，不修改规则。
- 不为供应商URL/密钥加输入框；原DOM的inline保留，新DOM禁止inline。

### S-2：执行日志验证明细区

- 复用抽屉 `.batch-log-drawer` styles.css:9425，宽min(620px,72%)，不扩宽全局抽屉。汇总复用 `.batch-log-metrics/.batch-log-metric` :9843～9850（三列、gap8、padding10、圆角10px、bg #f8fafc）；徽章复用 `.badge` :1054、ok :1068、warn :1074、error :1080、info :1086；按钮复用 `.button.secondary` :852、`.button.small` :2482 和既有hover/active。现有class规则均不改，因此不引入全站使用点变更。
- 以下新CSS完整逐字追加到styles.css，禁止增删属性或改数值：

```css
/* 批量介绍邮件：Emailable 执行明细（仅此区域） */
.batch-email-verification { margin: 14px 0; }
.batch-email-verification h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-main); }
.batch-email-verification-note { margin: 6px 0; color: var(--text-sidebar); font-size: 12px; line-height: 1.6; }
.batch-email-verification-note.is-error { color: var(--error-strong); }
.batch-email-verification-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--panel-border); border-radius: var(--radius-md); }
.batch-email-verification-table { width: 100%; min-width: 560px; border-collapse: collapse; font-size: 12px; line-height: 1.5; }
.batch-email-verification-table th, .batch-email-verification-table td { padding: 8px 10px; text-align: left; vertical-align: top; border-bottom: 1px solid var(--panel-border); overflow-wrap: anywhere; }
.batch-email-verification-table th { color: var(--text-sidebar); background: var(--bg-subtle); font-weight: 600; }
.batch-email-verification-table tbody tr:last-child td { border-bottom: 0; }
.batch-email-verification-table details { margin-top: 4px; color: var(--text-secondary); }
.batch-email-verification-table summary { cursor: pointer; color: var(--primary); }
.batch-email-verification-table summary:hover { color: var(--primary-hover); }
.batch-email-verification-table summary:active { color: var(--primary-active); }
.batch-email-verification-table summary:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; border-radius: var(--radius-sm); }
.batch-email-verification-pager { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.batch-email-verification-pager .button:disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }

```

- 目标DOM（位于原batchLogMetrics之后）：

```html
<section id="batchLogEmailVerification" class="batch-email-verification" aria-labelledby="batchLogEmailVerificationTitle">
  <h4 id="batchLogEmailVerificationTitle">邮箱验证</h4>
  <p id="batchLogEmailVerificationNote" class="batch-email-verification-note" aria-live="polite"></p>
  <div id="batchLogEmailVerificationMetrics" class="batch-log-metrics"></div>
  <div class="batch-email-verification-table-wrap" id="batchLogEmailVerificationTableWrap" hidden>
    <table class="batch-email-verification-table">
      <thead><tr><th scope="col">专家 / 邮箱</th><th scope="col">验证结果</th><th scope="col">发送结果</th><th scope="col">标签处理</th></tr></thead>
      <tbody id="batchLogEmailVerificationRows"></tbody>
    </table>
  </div>
  <div id="batchLogEmailVerificationPager" class="batch-email-verification-pager" hidden>
    <button id="batchLogEmailVerificationPrev" class="button small secondary" type="button">上一页</button>
    <span id="batchLogEmailVerificationPage" class="batch-gate-hint"></span>
    <button id="batchLogEmailVerificationNext" class="button small secondary" type="button">下一页</button>
  </div>
</section>
```

- 动态metric固定3格，复用 `<div class="batch-log-metric"><div class="batch-log-metric-label">验证通过</div><div class="batch-log-metric-value">2</div></div>` 骨架；三项为通过、未通过、服务异常，分别附is-success/is-skipped/is-failure；运行中PENDING数量放note文字，不伪计入通过。
- tbody每行固定4个td：①专家名+`<br>`+邮箱；②`.badge ok|warn|error|info` 状态与 `<details><summary>验证详情</summary><div>原因：…</div><div>验证时间：…</div><div>请求次数：…</div></details>`；③发送结果文字+原因；④标签结果文字。所有动态值转义。复用行时间按站点现有格式化函数输出，无新时区算法。tag失败用`.badge error`，不靠颜色单独传递含义。
- note四类：关闭、尚未进入、正常分页说明、加载失败（加is-error）；失败可通过原刷新/重开重试，无新按钮。常驻正常说明：“仅列出已进入邮箱验证的明细；预筛选跳过见原跳过原因。服务异常会停止本次执行。”
- 新元素只能使用本S-1/S-2声明的class或已有上述class；禁止inline style，禁止改tokens/抽屉宽度/其它浮层层级。新增交互仅summary和既有按钮，各状态已明确。

## 现状审计

### 后端读写与交互

- 新表01唯一写方BatchEmailVerificationService/Repository，FK随task_execution清理；03只读。执行表/快照由Control→TaskExecutionService写；原BatchSendConfigController:109/119配置日志与135/146全局日志读取，:164折叠批次，:264 requestSnapshot。不得用progress.errorSamples假装全部邮箱。
- 配置存储02负责所有写路径；前端通过GET/POST/PUT configs读写View/Command，手动只POST snapshot，依旧是双入口。
- X1：配置View→编辑/手动草稿→请求→02存储/01执行；X2：01审计写→新接口→日志分页；X3：执行切换/来源软删→不同接口返回顺序；X4：主执行日志旧指标与新增独立验证汇总同时刷新。

### 前端样式盘点

- 改动前HTML/CSS完整逐字片段见 [证据F-9～F-15](emailable-evidence.md)，属于本节基线附件。已有两页签与日志抽屉，不新增第三页签。
- 基准：主色#1e40af、hover#1e3a8a、active#172554；正文#1e293b，次级#475569/#64748b，muted#94a3b8；success#059669、warning#d97706、error#e11d48；面板rgba(255,255,255,.55)，subtle#f8fafc；圆角7/10/18px。token值来自styles.css:1，不把半透明panel-bg当不透明背景。来源K-panel-bg-token-is-translucent。
- 弹窗 styles.css:9063宽min1180与视口限制，header:9079 padding20px 28px 16px，标题20px/700；tabs:9109 gap28、height48。保持不变。开关36×20、knob16、偏移2、选中平移16；label12px/600、hint11px/1.5。新表格采用S-2明确值。
- JS接缝（app.js行号）：state/reset 17382/17432；任务列表17531；编辑回填17692/17724；收集/预估/保存18513/18536/18657；来源18704，deepClone18735，默认18762，回填18787，读取18855，normalize18896，diff18918/18970，fieldMap19020，确认19065；日志19253～19573，绑定19647。字面命令见E-7。
- 原日志loadBatchLogDetail:19387有configId/executionId防串，但没有新分页请求token；新请求必须自带游标/序号比较。既有轮询1500/3000ms继续使用。

## 实现方案

### T1 只读验证明细接口（I-2/I-3/I-4/I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt`。新增非nullable仓储依赖，同步E-5两处测试构造器。接口：

`GET /api/mail/batch-send/executions/{executionId}/email-verifications?afterId=0&limit=50&configId=123`

- configId可省；非本任务类型/执行不存在/configId不匹配按404；afterId<0、limit不在1..100返回400。依赖既有认证，不开匿名路由。
- 返回：`executionId, enabled, summary:{total,pending,passed,rejected,errors,tagFailed}, items:[...], nextAfterId, hasMore`。items仅含01表的展示白名单：id、expertDocId、orcidId、expertName、email、decision、providerState/providerReason、errorCode、checkedAt、requestCount、sendStatus/sendReason、tagStatus/tagError。不得返回key/HTTP原文。
- summary为整次执行，不是本页；passed/rejected/errors互斥，total=三者+pending。tagFailed为附加维度不参与合计。nextAfterId只在hasMore时有值。enabled来自保存的requestSnapshot；旧payload缺字段为false；坏JSON明确返回历史快照读取错误，不能冒充关闭。
- 先校验执行身份再读表；仓储异常返回服务错误，不能返回空数组掩盖故障。运行期间summary与分页可有时间差，不声称多请求严格一致快照；单次响应使用一致读事务或一次仓储read方法，确保同响应汇总/行不自相矛盾。

### T2 开关及手动传播（I-1/I-5，S-1）

文件：`src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/test/js/batchEmailVerification.test.js`。S-1两处checkbox与状态文字；任务pill；所有E-7接缝逐项加bool字段；formatManualDiffValue返回开启/关闭，diff字段label“发送前验证邮箱”，fieldMap指manualFieldEmailVerification。选来源和还原带入原值，清空false；确认弹窗显示本次值。材料提醒时清false并disabled，hint显示“仅介绍邮件支持发送前验证”；介绍邮件恢复可操作但不自动开启。预估仍只是人数不发起验证。

### T3 日志读取与展示（I-2/I-3/I-4/I-5，S-2）

文件：`src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/main/resources/static/styles.css`、`src/test/js/batchEmailVerification.test.js`。新增section骨架和CSS；batchTaskState/reset加入verificationCursorStack、verificationRequestSeq、verificationLoading、当前页及展开rowId集合。打开/关闭/切换都reset失效旧token；prev出栈、next压入nextAfterId，loading期间禁用分页。当前主详情回包后加载本次页；只用原timer，不增加轮询间隔。终态仍补拉最后一次，运行中刷当前页及整次summary。单次失败保留既有已加载数据但醒目提示失败及可能未更新；首次失败不展示0计数。

展示映射：PASS→“通过”，SKIP→“未通过（risky/undeliverable/unknown）”，ERROR→“验证服务异常”，PENDING→“验证中”（终态则“验证未完成”）。发送SENT→已发送，FAILED→发送失败，SKIPPED→已跳过，NOT_SENT→未发送+原因，SENDING运行中→发送中/终态→结果未确认。标签APPLIED→已标记邮箱异常，FAILED→标签写入失败+受控原因，PENDING→待写入/终态→标签未完成，NOT_REQUIRED→无需处理。原因展示中文受控解释并保留原code；未识别reason直接安全展示，不猜中文含义。

### T4 验证与版本（I-1～I-5，S-1/S-2）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt`、`src/main/resources/static/index.html`、`src/test/js/batchEmailVerification.test.js`。新JS测试沿现有node:test/vm DOM stub方式覆盖传播/竞态/转义，避免仅断言字符串存在。index.html所有已有版本化styles/app/模块资源统一用本次release缓存串；当前是20260924-snippet-dialog-contrast，实施前复核，不覆盖其它工作区修改。本步骤不编辑docs/releases.json、不直接发布。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 新增验证明细只读接口 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` | 明细隔离/分页/历史/错误读取 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt` | 新增必需仓储依赖；旧接口回归 |
| 4 | `src/main/resources/static/index.html` | 两处开关、抽屉验证区、资产版本同步 |
| 5 | `src/main/resources/static/app.js` | 字段传播/差异/日志请求及渲染 |
| 6 | `src/main/resources/static/styles.css` | 仅追加 S-2 的局部 CSS |
| 7 | `src/test/js/batchEmailVerification.test.js` | 新增：快照/竞态/分页/转义测试 |

合计7文件；子系统为控制台前端与验证明细只读API。无新增DB/ES字段。

## 验收标准

- I-1：JS行为测试逐一模拟编辑/存储/选源/还原/清空/临时覆盖/确认/请求，assert相同boolean；材料提醒禁用且false；来源不被修改。
- I-2：控制器测试全局/指定config/软删/未知execution/其它taskType；历史缺字段false、坏JSON明确失败；直接查询新表结果不依赖当前config。
- I-3：每种decision/sendStatus/tagStatus渲染测试；通过但模板门禁仍显示未发送；终态SENDING显示结果未确认；服务故障不显示邮箱异常。
- I-4：数据库分页由01真实IT；controller限制参数；JS模拟慢旧请求、切换执行、快速翻页、关闭抽屉、轮询保留页/展开、拒绝回包与恶意reason字符串；没有额外timer或provider调用。
- I-5：BatchSendExecutionDetailTest原INIT/ROUND/FINAL与剩余逻辑不回归；现有JS全量测试；前端查不到密钥；只GET无发送副作用。
- S-1：新DOM与骨架一致；实际样式36×20开关、禁用.6、蓝色主色；没有新增inline或未声明class；原样式块零改动。
- S-2：新增CSS与合同逐字比较；表格窄屏可横滚、不撑坏原抽屉；badge有文字；原六指标和timeline保留；summary键盘可展开。

实施后执行：
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendConfigControllerTest,BatchSendExecutionDetailTest
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
```

## 人工验收清单

### A-1: 线上结构的两处开关
- 前置条件：隔离验收站已部署01～03，登录后从收发件箱打开批量邮件任务控制台，存在可编辑INTRODUCTION任务。
- 操作步骤：
  1. 进入定时任务→编辑，开启发送前验证并保存，重新打开。
  2. 进入手动执行选择该任务，将开关关掉，查看已修改标记与确认弹窗；还原来源再清空来源。
  3. 选择材料提醒模板。
- 预期结果：保存后开关仍开，任务行显示“邮箱验证 · 开”；手动覆盖确认页显示关闭，原任务仍开；还原变开、清空变关；材料提醒禁用并显示“仅介绍邮件支持发送前验证”。
- 覆盖：I-1/I-5；S-1；需求1；X1

### A-2: 逐邮箱真实持久化结果
- 前置条件：验收库执行01的A-1和A-2生成实际新表明细；使用SMTP捕获器。
- 操作步骤：
  1. 打开对应执行记录，查看验证通过/未通过/服务异常三项及邮箱列表，展开原因。
  2. 查看服务402那次执行，再查看一条PASS但被模板门禁拦截记录。
  3. 刷新浏览器，重新登录并打开同一执行。
- 预期结果：五人样例为通过2/未通过3/服务异常0；每行有邮箱、state/reason、时间、发送及标签结果。402显示服务异常且无需标签，未发送；模板门禁行通过但未发送。刷新后明细仍存在；原成功2/跳过3与批次时间线保留。
- 覆盖：I-2/I-3/I-5；S-2；需求2；X2/X4

### A-3: 历史/来源修改与分页竞态
- 前置条件：测试库准备101条同一execution验证明细（通过01仓储夹具产生），另一个execution有1条；准备一条旧执行payload无新字段。浏览器网络设慢速。
- 操作步骤：
  1. 第一页翻下一页，再下一页，再上一页。
  2. 快速切换到另一个execution，关闭重开抽屉。
  3. 删除原配置后从最近执行日志按execution访问；再打开旧记录；临时让明细API返回500。
- 预期结果：分页50/50/1条无重复漏行；切换后只显示新execution的1条；软删不丢历史；旧记录显示未启用；接口失败显示“验证明细加载失败”，不显示虚假0。
- 覆盖：I-2/I-4；S-2；X2/X3

### A-4: 外观与注入文本
- 前置条件：宽1440及窄768视口；测试明细reason含 `<img src=x onerror=alert(1)>`，有ERROR、tagFAILED、终态SENDING样例。
- 操作步骤：
  1. 依次查看两处开关、任务pill、抽屉表格；键盘Tab/空格操作开关，Enter展开详情。
  2. 在768视口横滚表格；运行中翻第2页并展开一条等一轮自动刷新。
  3. 查看恶意reason及三种异常状态。
- 预期结果：仍两页签/右侧抽屉；开关36×20、主色#1e40af、表格12px/1.5、8×10内边距、圆角10px；窄屏只表格横滚；轮询保留页和展开；恶意reason显示纯文字无弹窗；“验证服务异常”“标签写入失败”“结果未确认”清晰可见。
- 覆盖：I-3/I-4/I-5；S-1/S-2；需求2；X4
