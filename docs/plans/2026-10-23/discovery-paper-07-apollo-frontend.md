# 07 — Apollo 保存配置页面与分渠道日志

状态：待执行。依赖06a/06b，且在05之后顺序修改index/app/styles。完成后才进行整体验收。

## 需求描述

把启动弹窗里的一次性Apollo勾选改为后端持久配置：免费搜索、付费详情分开显示和保存；显示未保存状态与生效范围。每次执行记录显示自己的配置快照及搜索/详情尝试数。

必须保持：所有原有准入计数、原规则提示、论文原文页、专家审核、其他任务配置；不展示Key、不在保存时调用Apollo、不根据未保存的输入启动任务。范围外：配置新准入规则、额度购买、预计费用计算、付费余额仪表盘。

## 关键不变量

### Invariant I-1: 保存是唯一生效动作
- Rule: checkbox仅修改draft；PUT成功后更新saved+revision。未保存时明确提示并阻止当前页面开始执行；“恢复已保存值”撤销draft。启动请求不携带draft或强制apolloAssist=false，省略该参数由06读取已保存设置。
- Applies to: 默认启动、关键词启动、连续模式、配置加载与错误重试。
- Violation consequence: 用户以为已保存，实际每次启动覆盖全局设置。
- 来源: K-task-launch-config-registration。

### Invariant I-2: 免费与付费独立
- Rule: 付费开关不会被免费开关自动勾选/清除；搜索关闭但付费true时标“已保存，搜索关闭期间不调用”。缺Key/部署关闭显示不可执行原因，不能显示已具备可用能力；Key只显示已配置/未配置。
- Applies to: 编辑、GET/PUT回读、重启。
- Violation consequence: 隐式付费或状态误导。
- 来源: original。

### Invariant I-3: 任务快照与当前配置分开
- Rule: 表单显示最新保存设置；执行记录只显示summary.apolloAccess，不能拿表单值回填历史。渠道数字为已登记尝试数，不等于credits；未知显示未记录。
- Applies to: apvSummaryHtml、历史/运行中切换、网络失败。
- Violation consequence: 以为关闭付费会改变已运行任务或虚报扣费。
- 来源: K-admission-event-and-projection-counts。

### Invariant I-4: 宿主生命周期不变
- Rule: 设置响应受当前taskType/generation与配置requestSeq约束；关闭/切任务丢弃旧响应；不能让定时刷新覆盖用户draft。保存成功不重启/重跑任务，不改变准入记录。
- Applies to: apvInit/dispose/loadCapabilities、保存、恢复、日志刷新。
- Violation consequence: 弹窗串状态、重复保存或业务变更。
- 来源: K-dom-stub-tests-hide-dangling-refs。

## 样式契约

### S-1: 保存配置区

复用 button/primary/secondary/small（styles.css:802–870/2482），apv-rules（:13001附近，精确块在代码证据）。不更改全局或现有apv-option规则；仅将`#taskLaunchApolloRow`的class从apv-option换为apc-settings，其他apv组件不动。

以下是完整替换DOM；保留taskLaunchApolloAssist、taskLaunchApolloHint ID以减少宿主风险，但checkbox语义改为草稿中的searchEnabled：

```html
<section class="apc-settings" id="taskLaunchApolloRow" aria-labelledby="apcTitle" hidden>
  <div class="apc-head"><h4 id="apcTitle">Apollo 辅助核验设置</h4><span class="apc-save-state" id="apcSaveState" role="status">正在读取已保存配置…</span></div>
  <div class="apc-grid">
    <div class="apc-option"><label for="taskLaunchApolloAssist"><input type="checkbox" id="taskLaunchApolloAssist" aria-describedby="apcSearchHelp" disabled>免费搜索</label><p id="apcSearchHelp">使用 People API Search 查询姓名和机构；该搜索接口为 0 credits。</p></div>
    <div class="apc-option"><label for="apcPaidDetails"><input type="checkbox" id="apcPaidDetails" aria-describedby="apcPaidHelp" disabled>允许付费详情</label><p id="apcPaidHelp">需要完整人物资料时允许调用详情接口，可能消耗 Apollo credits；实际以账户账单为准。</p></div>
  </div>
  <p class="apc-note" id="apcEffective">正在读取可用性…</p>
  <p class="apc-note" id="taskLaunchApolloHint">保存后对新任务生效；正在运行的任务沿用启动时配置。需要付费详情而未启用时保留待核验。</p>
  <div class="apc-error" id="apcError" role="alert" hidden></div>
  <div class="apc-actions"><span class="apc-note" id="apcSavedAt"></span><button class="button secondary small" id="apcReload" type="button" disabled>恢复已保存值</button><button class="button primary small" id="apcSave" type="button" disabled>保存 Apollo 设置</button></div>
  <details class="apv-rules"><summary>查看准入口径</summary><p>当前本人教授类职称可明确符合；其他情况核对已取得博士及年龄、职称条件。Apollo 仅返回候选不能直接准入。缺少可靠来源时保留待核验。</p></details>
</section>
```

disabled仅表示加载中/保存中或没有有效GET；不因为search关闭而禁用付费checkbox（两个设置可独立保存）。已保存但付费实际不调用，用文案说明。

### S-2: 运行日志追加行

在现有apv-flow之后追加，不替换原有“自动准入/其中Apollo/已明确/未明确”列：

```html
<div class="apc-run-config"><span data-apc="runConfig"></span><span data-apc="runAttempts"></span><span class="apc-run-note">以上为已登记请求尝试，不等于实际扣费次数或 credits。</span></div>
```

固定输出例：“本次配置：免费搜索 开 · 付费详情 关 · 保存版本 3”；“免费搜索尝试 8 · 付费详情尝试 0 · 其他 0 · 登记后尚未确认完成 0”。旧run：“本次配置：旧任务沿用原规则”，随后展示后端提供的有效开关；缺channel统计写“分渠道尝试：未记录”，不能填0。

### S-3: 全量新增CSS（逐字）

```css
/* discovery-apollo-settings-contract:start */
#taskProgressModal[data-discovery-review="true"] .apc-settings{padding:14px 16px;border:1px solid rgba(15,23,42,.08);border-radius:7px;background:#f8fafc;color:#1e293b;font-size:12px;line-height:1.7}
#taskProgressModal[data-discovery-review="true"] .apc-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}
#taskProgressModal[data-discovery-review="true"] .apc-head h4{margin:0;color:#1e293b;font-size:13px;font-weight:600}
#taskProgressModal[data-discovery-review="true"] .apc-save-state{color:#64748b;font-size:11px;line-height:1.6}
#taskProgressModal[data-discovery-review="true"] .apc-save-state[data-state="dirty"]{color:#b45309}
#taskProgressModal[data-discovery-review="true"] .apc-save-state[data-state="saved"]{color:#047857}
#taskProgressModal[data-discovery-review="true"] .apc-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
#taskProgressModal[data-discovery-review="true"] .apc-option{padding:12px;border:1px solid #dfe4eb;border-radius:6px;background:#fff;min-width:0}
#taskProgressModal[data-discovery-review="true"] .apc-option label{display:flex;flex-direction:row;align-items:center;justify-content:flex-start;gap:7px;margin:0;color:#1e293b;font-size:12px;font-weight:600;letter-spacing:normal;text-transform:none}
#taskProgressModal[data-discovery-review="true"] .apc-option input[type="checkbox"]{appearance:auto;width:14px;height:14px;min-width:14px;min-height:14px;max-width:14px;max-height:14px;flex:0 0 14px;margin:0;padding:0;accent-color:#1e40af;cursor:pointer;box-shadow:none;box-sizing:border-box}
#taskProgressModal[data-discovery-review="true"] .apc-option p{margin:6px 0 0 21px;color:#475569;font-size:11px;line-height:1.7}
#taskProgressModal[data-discovery-review="true"] .apc-note{margin:8px 0 0;color:#64748b;font-size:11px;line-height:1.7}
#taskProgressModal[data-discovery-review="true"] .apc-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin-top:12px}
#taskProgressModal[data-discovery-review="true"] .apc-actions>.apc-note{margin:0 auto 0 0}
#taskProgressModal[data-discovery-review="true"] .apc-error{margin-top:10px;padding:10px 12px;border:1px solid rgba(225,29,72,.16);border-radius:6px;background:rgba(225,29,72,.07);color:#be123c;font-size:12px;line-height:1.7;overflow-wrap:anywhere}
#taskProgressModal[data-discovery-review="true"] .apc-settings :is(button,input,summary):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
#taskProgressModal[data-discovery-review="true"] .apc-settings :is(button,input):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
#taskProgressModal[data-discovery-review="true"] .apc-settings .button:disabled:hover{transform:none;box-shadow:none}
#taskProgressModal[data-discovery-review="true"] .apc-run-config{display:flex;flex-direction:column;gap:4px;margin-top:8px;padding-top:10px;border-top:1px solid #e2e8f0;color:#475569;font-size:12px;line-height:1.7}
#taskProgressModal[data-discovery-review="true"] .apc-run-note{color:#94a3b8;font-size:11px}
@media(max-width:640px){#taskProgressModal[data-discovery-review="true"] .apc-grid{grid-template-columns:1fr}#taskProgressModal[data-discovery-review="true"] .apc-head{align-items:flex-start;flex-direction:column;gap:5px}#taskProgressModal[data-discovery-review="true"] .apc-actions{flex-wrap:wrap}#taskProgressModal[data-discovery-review="true"] .apc-actions>.apc-note{width:100%}#taskProgressModal[data-discovery-review="true"] .apc-settings{padding:12px}}
/* discovery-apollo-settings-contract:end */
```

button完整hover/active沿用styles.css:825–870，没有新造“近似蓝按钮”。禁止新增inline style、未列class、改变全局input/label及旧apv-option规则。

## 现状审计

### 前端基线

`index.html:1192–1197`逐字旧块已在[代码快照](discovery-paper-code-receipts.md)，其中：

```html
<div class="apv-option" id="taskLaunchApolloRow" hidden>
    <label for="taskLaunchApolloAssist"><input type="checkbox" id="taskLaunchApolloAssist" aria-describedby="taskLaunchApolloHint" disabled>启用 Apollo 辅助核验</label>
    <p id="taskLaunchApolloHint">正在读取资格核验能力…</p>
    <details class="apv-rules"><summary>查看准入口径</summary><p>当前本人教授类职称可明确符合；其他情况核对已取得博士及年龄、职称条件。Apollo 仅返回候选不能直接准入。缺少可靠来源时保留待核验。</p></details>
</div>
```

旧CSS完整块`styles.css:12966–13012`，apv-option 7px圆角、10px 12px padding、#f8fafc背景、label横向7px gap、checkbox14px、p11px/1.7；本次另起apc-*，不更改旧class。全局复用类的所有引用文件清单见代码证据，禁止波及邮件/其他任务。（来源: K-batch-checkbox-global-input-height；K-global-p-is-muted-in-dialogs）

### 当前行为与全部接缝
- `app.js:10110` launch直接读checkbox；`:10172` postDiscoveryLaunch默认false，keyword query无条件append apolloAssist，普通body无条件带字段。这三个位置必须一起改，不能只换文案。
- `apvInit:27080`禁用旧checkbox；`apvLoadCapabilities:27121`按apolloDefault覆盖checked；apvCanLaunch:27136。新draft状态不可被这些旧赋值覆盖。
- `apvSummaryHtml:27256`生成摘要和总请求数；此处读取06新增snapshot/channel DTO。
- `discoveryVerification.test.js:379–388`明确测试旧一次性勾选；需改成持久配置语义，不靠旧stub跑绿。
- 06设置DB唯一写者为SettingsRepository，本UI只是PUT调用者；执行记录读run snapshot/attempts，不读当前表单。无localStorage。

## 实现方案

### T-1 表单状态与启动（I-1/I-2/I-4；S-1/S-3）

在apvState增加仅页面状态`apolloSettings:{saved,draft,revision,status,requestSeq,dirty}`；不复用现有业务store字段。首次同时读取capabilities与apollo-settings，两个独立结果都验证generation；删除旧apvLoadCapabilities中直接按capability.apolloDefault写checkbox.checked的赋值，checkbox仅由settings.saved/draft渲染；GET失败显示重试/恢复按钮，不以部署默认填成已保存。

监听两checkbox更新draft/dirty；dirty文案“有未保存修改”，save可点；保存中所有配置输入和save禁用，重复点击只发1个PUT。保存成功用服务端回读值更新saved/draft，显示“已保存 · 对新任务生效”。错误保留draft；409提示“配置已被其他会话修改，请恢复已保存值后重新设置”，503“保存失败，原配置仍生效”。

apvCanLaunch改为无draft参数，先判设置读成功且无dirty，再用saved/effective readiness；免费关闭可继续纯本地流程，不能因Apollo关闭阻断整个发现。所有postDiscoveryLaunch调用省略apolloAssist；函数默认null，只有外部显式非null参数才写query/body，保持旧API opt-out兼容，但页面不会传草稿。

“API Key：已配置/未配置”与服务/worker可用性仅显示布尔状态；搜索false/detailtrue显示“付费详情：已保存开启，搜索关闭期间不调用”；没有余额或金额推算。

### T-2 执行日志与保留旧行为（I-3/I-4；S-2/S-3）

在apvSummaryHtml新增S-2；数据只来自summary。LEGACY使用明确标签；尚未部署06返回字段缺失时显示未记录，同时配置API404视为后端未就绪、保存disabled，不给假可用配置。

任何定时刷新只刷新summary，不覆盖draft；关闭apvDispose中止设置请求并解绑新按钮/清状态；重新打开读取保存值。应用HTML字符串必须escapeHtml；禁用键盘提交期间的并行保存。

### T-3 静态资产与测试（I-4；S-1～S-3）

index所有本地CSS/JS版本统一升级`20261023-discovery-paper-apollo-settings`，包含05新增模块；再rg旧key及字面值测试。不能只给styles加key；不编辑旧发布记录冒充上线。（来源: K-frontend-cache-key-triad）

## 变更文件清单

8 文件；子系统1（核验前端）；既有共享store新增字段0。原5文件加已批准的3份直接契约测试同步，仍在主计划10文件上限内，不授权其他产品行为或范围。

| 文件 | 动作 |
|---|---|
| src/main/resources/static/index.html | 配置DOM/统一cache key |
| src/main/resources/static/styles.css | 仅追加S-3 |
| src/main/resources/static/app.js | 保存配置状态/两种启动/摘要 |
| src/test/js/discoveryVerification.test.js | 改旧启动语义回归 |
| src/test/js/discoveryApolloSettings.test.js | 实际DOM/CAS/dirty/快照回归 |
| src/test/js/discoveryContinuousRun.test.js | 启动断言改为省略apolloAssist，保留冻结关键词/来源/includeRawScan、超时及未完成断言 |
| src/test/js/discoveryScheduleSetting.test.js | 启动断言改为省略apolloAssist，保留人工启动/来源/状态及零schedule GET/PUT断言 |
| src/test/js/discoveryPapers.test.js | CSS逐字校验限定既有start/end标记（含end）；保留整个论文CSS契约逐字相等 |

## 验收标准

- I-1：修改未保存不发PUT；开始执行被dirty阻止；PUT成功后重开读取saved；两种启动POST都不带apolloAssist，后端冻结保存值。
- I-2：四组合独立，免费on从不自动开paid；无key/部署关闭/worker关闭可区分，不返回key。
- I-3：当前设置free-only不改历史run的paid标签；数字来自channel DTO；缺字段未记录；无实际扣费推算。
- I-4：保存期间点击2次仅1请求；503保留draft；A弹窗慢响应不能写B；无更改已有准入结果/国家补全/保护。
- S-1/S-2：DOM逐节点存在且label/ARIA正确；旧三页签及05纸库仍可用；未保存状态文案与开关实际一致。
- S-3：CSS标记块逐字diff、无inline、class集合完全声明；1440×1080两列，640px以下一列；checkbox14px，disabled opacity .45；键盘focus #3b82f6。
- node --check app.js；node --test src/test/js/discoveryApolloSettings.test.js src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryPapers.test.js。

## 人工验收清单

### A-1: 保存与生效范围
- 前置条件：隔离实例06已部署，已保存搜索true/详情false；一正在执行任务A快照true/true。
- 操作步骤：1. 打开深度发现，勾选付费但不保存。2. 点开始执行。3. 恢复已保存值。4. 保存搜索false/详情true。5. 刷新页面，查看A日志。
- 预期结果：步骤2提示“有未保存修改”，没有新任务；步骤3回到true/false；刷新后false/true保持且明确搜索关闭时不调用；A仍显示本次付费开启，不被表单当前值覆盖。
- 覆盖：I-1/I-2/I-3，S-1/S-2。

### A-2: 失败、重复保存与请求路径
- 前置条件：两浏览器会话读同revision；测试代理可延迟PUT/返回503；观察Network。
- 操作步骤：1. A保存后B保存。2. B恢复后再次修改，模拟503并双击保存。3. 恢复服务保存成功，分别用默认来源/关键词启动。4. 关闭弹窗再重开。
- 预期结果：B先409，503时草稿保留；双击1个PUT；两种启动请求都不携带apolloAssist；保存值重开保持；无Apollo测试调用/Key泄露。
- 覆盖：I-1/I-2/I-4，S-1/S-3。

### A-3: 分渠道日志与整体样式回归
- 前置条件：06的3搜索/2详情/1其他/pending1 fixture；1440×1080及643×900窗口。
- 操作步骤：1. 展开执行记录。2. 看当前配置和任务快照。3. 检查两种窗口宽度，再切论文原文、专家审核、审核记录，打开另一个普通任务。
- 预期结果：显示3/2/1和登记尚未确认1，保留原准入数并显示非账单说明；新卡片在≤640px一列，否则两列，checkbox14px、主色#1e40af、焦点#3b82f6；原文/审核功能及其他任务样式不变。
- 覆盖：I-2/I-3/I-4，S-1～S-3；must-NOT-change回归。

## 修正记录

- 用户“批准 继续”批准07暂停C-1的三份测试补充：5→8文件，加入ContinuousRun/ScheduleSetting两条旧启动参数断言同步和Papers CSS边界校验修正。旧key/接缝批量检查凭据见 `../fast/discovery-paper-master/children/07/execution.md:30–74`。必须保留其余行为断言与论文CSS字节不变，仅将两启动断言改为无apolloAssist、CSS比较限定已有完整标记块；S-3仍按原文追加，不改为插入旧块前。无其他文件或产品行为扩展，批准与身份见ledger Amendments。完整JS/浏览器/WAR与独立轻量复验后才能READY_FOR_HUMAN_REVIEW。
