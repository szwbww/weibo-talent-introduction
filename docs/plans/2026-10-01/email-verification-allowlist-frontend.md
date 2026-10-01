# Emailable 放行结果：收发件箱前端（第 3/3 计划）

依赖：[任务配置](email-verification-allowlist-config.md) 验证通过。用户认可的外观基准：[前端预览](../../mockups/email-verification-policy-preview/index.html)。预览是外观样本；当前业务页面源码是接线依据，不能把预览中的模拟按钮搬入业务。

## 需求描述

定时任务编辑和手动执行的「发送控制」区显示三个独立复选框：可投递 deliverable、有风险 risky、未知 unknown；固定说明 undeliverable 不可放行。新建/独立手动默认只勾 deliverable；旧任务按服务端返回的三项回显。可以全不选，此时本次明确验证结果全部跳过。

必须保持：现有验证总开关新建默认关闭；关闭时保留选择并禁用复选框；材料提醒继续禁用验证；手动修改仅影响本次，来源选择/重新选择/清空逻辑仍可用；默认验证范围预估不请求供应商、不宣称是验证后的可投递人数；轮次/成功配额/模板/账号筛选不变。

范围外：新设置页、新前端框架、下拉多选/全选按钮、全局样式调整、线上保存/发布、新增日志数据库字段。

## 关键不变量

### Invariant I-1：UI 与请求同一选择
- Rule：固定顺序 deliverable,risky,unknown；只发送勾选项，不因空数组回退默认；没有 undeliverable 控件。新建与独立手动为 [deliverable]；编辑/选来源按 API 数组；旧响应字段缺失/null 兼容三项，不能继承上次编辑值。
- Applies to：编辑回填/save/recipient snapshot、手动默认/clone/fill/read/build snapshot。
- Violation consequence：看到的选择与实际发信不符。
- 来源：用户确认的多选设计；app.js:18233-18237,19122-19145,19355-19445。

### Invariant I-2：开关与类型边界
- Rule：验证总开关保持原默认 false；关闭只 disabled 三个选择框，保留 checked；切到材料提醒沿用现有关闭验证行为，选择值仍留在草稿；切回介绍邮件不自动开启。界面必须写明列表仅在验证开启时生效。
- Applies to：refreshEmailVerificationState、updateEmailVerificationToggleLabel、两处开关 change 监听。
- Violation consequence：误以为默认已验证、关闭后丢选择或模板切换自动放开发信。
- 来源：app.js:19040-19069。

### Invariant I-3：手动覆盖不污染配置
- Rule：来源数组 slice 克隆；差异比较按集合比较、[] 显示「不放行任何结果」；重新选择同一来源恢复来源选择；clear 恢复独立默认；确认页显示有效选择，关闭时显示「未启用」。执行请求写 snapshot.emailVerificationAllowedStates，不 PUT 来源配置。
- Applies to：deepCloneConfig、fillManualFormDefaults/fromDraft、normalizeManualSnapshot、computeManualDiffs、computeAndRenderDiffs、showBatchManualConfirm、confirmManualExecution。
- Violation consequence：手动操作暗改定时发信或确认页漏报差异。
- 来源：app.js:19323-19448,19533-19795；K-batch-task-config-snapshot-log-identity。

### Invariant I-4：策略跳过不展示为地址无效
- Rule：sendReason=EMAIL_VERIFICATION_POLICY_SKIP 的 SKIP 行显示「按策略跳过」和「未勾选该验证结果，本次未发送」；provider state/reason 原样显示；tagStatus=NOT_REQUIRED 显示无需处理。历史 EMAIL_VERIFICATION_REJECTED 继续原有「未通过」文案。不从当前配置反推历史原因。
- Applies to：emailVerificationDecisionText、BATCH_EMAIL_VERIFICATION_SEND_REASON_LABELS、现有明细渲染。
- Violation consequence：把未选 risky/unknown/deliverable 误解释为坏邮箱。
- 来源：app.js:20254-20343；后端第 1 计划原因码。

### Invariant I-5：静态资源与预估回归
- Rule：index.html 的现有版本化资源统一改为 20261001-email-verification-allowlist；仅变更版本引用，不增加资源。预估入参可以携带列表，但不新发 Emailable 请求；原预估文案/成功额度语义保持。
- Applies to：index.html、buildConfigEditorRecipientSnapshot/buildManualExecutionSnapshot、既有 recipients/preview。
- Violation consequence：线上命中旧 JS/CSS，或预估消耗验证额度。
- 来源：K-frontend-cache-key-triad；app.js:19085-19145；BatchSendConfigController.kt:95-102。

## 样式契约

### S-1：复用容器与既有开关
- 复用：batch-config-field（styles.css:9578）、batch-gate-field（:9749）、batch-config-field-label（:9372）、batch-gate-row（:9751）、batch-task-status-toggle/switch（:9395-9456）、batch-gate-toggle/hint（:9758-9778）。保持现有 Emailable 总开关 DOM/id，仅替换两处说明文案。
- 总开关说明（HTML 与 JS 常量必须一致）：编辑「按下方勾选结果放行；不可投递始终跳过并标记邮箱异常。单邮箱验证未完成、超时或响应异常时暂缓；鉴权、额度、限流或服务故障停止执行。会消耗 Emailable 额度。」手动同文案末尾追加「仅影响本次执行。」
- 既有 class 规则不修改；禁止 inline style；不更改全局 label/input/p。完整现状 HTML/CSS 逐字见证据附件 E-4/E-5。

### S-2：三个复选框
在两处 Emailable 总开关字段之后、轮次字段之前插入以下结构。编辑版逐字：
~~~html
<div class="batch-config-field batch-gate-field batch-email-policy-field" id="editorFieldEmailVerificationAllowedStates">
    <span class="batch-config-field-label" id="batchConfigEditorEmailVerificationAllowedStatesLabel">允许发送的验证结果</span>
    <div class="batch-email-policy-options" role="group" aria-labelledby="batchConfigEditorEmailVerificationAllowedStatesLabel" aria-describedby="batchConfigEditorEmailVerificationPolicyHint">
        <label class="batch-email-policy-option"><input type="checkbox" id="batchConfigEditorAllowDeliverable" value="deliverable"><span>可投递 <small>deliverable</small></span></label>
        <label class="batch-email-policy-option"><input type="checkbox" id="batchConfigEditorAllowRisky" value="risky"><span>有风险 <small>risky</small></span></label>
        <label class="batch-email-policy-option"><input type="checkbox" id="batchConfigEditorAllowUnknown" value="unknown"><span>未知 <small>unknown</small></span></label>
    </div>
    <div class="batch-email-policy-fixed">不可投递（undeliverable）始终跳过，不可放行。</div>
    <div class="batch-email-policy-hint" id="batchConfigEditorEmailVerificationPolicyHint">仅在发送前验证开启时生效；未勾选结果跳过并记入日志，不占成功发信额度。全不选时不放行任何验证结果。</div>
</div>
~~~
手动版按同一结构逐字复制，ID 映射如下，不能复用编辑版 ID；最外层末尾额外加入既有差异 DOM：
| 编辑 ID | 手动 ID |
|---|---|
| editorFieldEmailVerificationAllowedStates | manualFieldEmailVerificationAllowedStates |
| batchConfigEditorEmailVerificationAllowedStatesLabel | batchManualEmailVerificationAllowedStatesLabel |
| batchConfigEditorEmailVerificationPolicyHint | batchManualEmailVerificationPolicyHint |
| batchConfigEditorAllowDeliverable | batchManualAllowDeliverable |
| batchConfigEditorAllowRisky | batchManualAllowRisky |
| batchConfigEditorAllowUnknown | batchManualAllowUnknown |

~~~html
<span class="batch-config-diff-badge" hidden>已修改</span>
<div class="batch-config-diff-original" hidden></div>
~~~

新增 CSS 必须原样复制；对比已认可预览仅使用正式 class/id、补全 input 的全局 34px 高度覆盖及键盘焦点：
~~~css
.batch-email-policy-options {
  display: flex;
  flex-wrap: wrap;
  gap: 9px 18px;
  align-items: center;
}
.batch-config-field .batch-email-policy-option {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  color: var(--text-main);
  font-size: 13px;
  cursor: pointer;
}
.batch-email-policy-option input[type="checkbox"] {
  width: 16px;
  height: 16px;
  min-height: 16px;
  margin: 0;
  padding: 0;
  accent-color: var(--primary);
}
.batch-email-policy-option input[type="checkbox"]:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.batch-email-policy-option small {
  color: var(--text-muted);
  font-size: 11px;
}
.batch-email-policy-fixed {
  margin-top: 9px;
  color: var(--text-muted);
  font-size: 11px;
}
.batch-email-policy-hint {
  margin-top: 9px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}
.batch-email-policy-field.is-disabled {
  opacity: .52;
}
.batch-email-policy-field.is-disabled .batch-email-policy-option,
.batch-email-policy-field.is-disabled input[type="checkbox"] {
  cursor: not-allowed;
}
@media (max-width: 600px) {
  .batch-email-policy-options { gap: 4px 10px; }
}
~~~
hover/active 使用原生 checkbox 与 styles.css:1143-1152 的全局输入规则，无新增色值/动画；checked 使用 accent-color；disabled 保留 checked 并原生禁用。所有新增 DOM 归本 S-2；禁止新增契约外 class。

### S-3：差异/确认/日志
- 复用既有 batch-config-diff-badge/original（styles.css:9609+）、batch-manual-confirm-summary/table/old/new（:9876-9882）、batch-task-scope-line、batch-gate-pill、batch-email-verification-table、badge warn（:11840-11855 等现有规则）。
- 任务列表原「邮箱验证 · 开」pill 后，在同一个既有 batch-task-scope-line 内追加 escaped 文本「放行：可投递、有风险、未知」（按实际选择）；全空显示「放行：无」。关时维持「邮箱验证 · 关」。
- 差异字段新增一行「允许发送的验证结果」，映射独立 manualFieldEmailVerificationAllowedStates，不能与旧开关共用 fieldMap key 的 DOM（会互相清掉红框）。
- 确认页现有 summary 的「发送前验证邮箱」行之后追加纯文本/现有 br：「允许发送的验证结果：…」；有差异时用既有三列表格的同一行结构。日志只替换 I-4 的字符串，不增 DOM/class/CSS。
- 禁止改变上述共用规则，无全站样式变更。

## 现状审计

### 前端状态与 API
- 写路径：saveBatchConfigEditor（app.js:19227-19305）独立构建 POST/PUT payload，不能只改预估快照；confirmManualExecution（:19766-19795）调用 buildManualExecutionSnapshot 并只 POST /manual-executions；编辑预估（:19089-19119）和手动预估（:19122-19145）各自组装完整快照。
- 读路径：编辑回填 :18214-18244；deepCloneConfig :19355-19379；独立 defaults :19382-19407；fillManualFormFromDraft :19409-19448；readManualFormValues :19494-19530；normalize/差异 :19533-19638；fieldMap/clear :19644-19708；确认 :19710-19758；列表 :18078-18087；日志 :20240-20401。
- 草稿写路径：applyBatchManualSource→deepCloneConfig；fillManualFormDefaults；detachBatchManualSourcePreservingDraft→Object.assign(readManualFormValues)；手动 input/change 监听 :20777-20790 写 manualDraft；重新选择来源再次调用 applyBatchManualSource 克隆回填。不存在本功能额外 localStorage 持久化需求。
- Interaction points：IP-1 保存→后端 View→重开编辑；IP-2 选来源→草稿/差异→手动 snapshot；IP-3 后端策略 skip→明细文案；IP-4 开关/模板变更→控件 disabled→请求保留值；IP-5 静态资源版本→浏览器加载。
- 后端 schema/写读路径由第 2 计划审计，本前端计划不增加存储或接口。来源：K-batch-console-regression-contract、K-js-test-invocation-surface。

### 前端样式盘点
- 可复用 class/完整规则块及被改两处 HTML：证据附件 E-4/E-5 逐字摘录。
- 设计 token：主色 #1e40af，hover #1e3a8a，active #172554；正文 #1e293b，次要 #475569，说明 #94a3b8；panel-bg rgba(255,255,255,.55)，border rgba(15,23,42,.11)；radius-md=10px、radius-lg=18px。暗色 text-main=#e2e8f0、text-muted=#7d8ca3；复用变量，不新建主题。styles.css:1-76,9990+。
- section padding=18px、border=1px solid rgba(15,23,42,.08)、shadow=0 1px 2px rgba(15,23,42,.03)；控件 grid 三列，gap=14px 16px；标题 14px/700，字段 label 12px/600；batch-gate-hint 11px/1.5。
- 既有 DOM 命名：编辑 batchConfigEditor*，手动 batchManual*；批量任务 DOM 静态位于 index.html，事件在 bindBatchSendTaskEvents 既有初始化段绑定。新 checkbox 必须在页面源码存在，不能仅靠 DOM stub 测试。
- 全局 label 是 flex-column、uppercase；全局 input min-height=34px。预览已用局部选择器覆盖方向，正式 CSS 同时覆盖 checkbox min-height。
- 无既有 class 规则被修改，不需要扩大其它使用点。本计划新 class 只允许两处 checkbox 组使用。
- 缓存键实查：rg -n '20260930-manual-template-reference' src/test src/main/resources/static/index.html 仅命中 index 的 11 条资源，src/test 0 命中；证据 E-6。执行前按当前值再查；若出现固定字面量测试，修订文件清单后执行，不能遗漏。（来源：K-frontend-cache-key-triad）

## 实现方案

### T-1：真实 DOM/CSS（I-1、I-2；S-1、S-2）
文件：src/main/resources/static/index.html、src/main/resources/static/styles.css。
在现有两处发送控制增加组；新建仅 deliverable，初始总开关仍 false；JS 初始化后禁用组；按契约准确复制 CSS。文案同步 JS。所有 ID 唯一。

### T-2：完整数据往返（I-1、I-2、I-3、I-5；S-1～S-3）
文件：src/main/resources/static/app.js、src/main/resources/static/index.html。
增加小型 read/fill/disable helper（按 editor/manual 定位三个 checkbox），不套现有大型下拉 picker 框架。补齐审计列出的编辑 payload/预估、手动 clone/default/fill/read/snapshot/normalize/diff/fieldMap/clear/confirm/事件。缺字段兼容三项与新建仅一项须显式分支，禁止用 length=0 当缺省。新增字段同时加入 clearAllDiffMarkers 的容器列表，避免清空来源后遗留红框。关闭时用 checked 读取保留值，不能只采集 enabled inputs。手动已有 change 链复用，不重复注册；updateEmailVerificationToggleLabel 刷新组禁用态，refreshEmailVerificationState 的提前 return 也覆盖材料提醒。
index 现有全部版本引用统一到新键，不增删其它资源。
接入消费者：配置 API View 和执行 API snapshot；新字段不改来源配置读写范围、不影响预估行为。

### T-3：日志说明与交互验证（I-3、I-4；S-3）
文件：src/main/resources/static/app.js、src/test/js/batchEmailVerification.test.js。
增加受控原因文案与优先级判断；保留未知原因原样显示。扩展现有 vm/DOM sandbox 使新增 helpers 真正执行；调整固定旧提示文案断言（:301,839）；新增真实 index ID/checkbox value 数量断言防 stub 掩盖缺 DOM。

### T-4：既有抽取函数测试的直接依赖（I-1、I-3、I-5；S-1～S-3）
文件：src/test/js/batchExpertTypeFilter.test.js、src/test/js/batchSenderFilter.test.js、src/test/js/expertTagBatchFix.test.js、src/test/js/batchManualExecutionLog.test.js、src/test/js/batchSendTaskConsoleInteraction.test.js。
这些测试直接抽取修改后的 app.js 函数或伪造其输入（实查见证据 E-7）。仅补新 helper 的加载/新字段 fixture；保留其原门禁、范围、日志和差异断言，不扩大测试设计或复制测试工具库。

## 变更文件清单

| # | 文件 |
|---|---|
| 1 | src/main/resources/static/index.html |
| 2 | src/main/resources/static/styles.css |
| 3 | src/main/resources/static/app.js |
| 4 | src/test/js/batchEmailVerification.test.js |
| 5 | src/test/js/batchExpertTypeFilter.test.js |
| 6 | src/test/js/batchSenderFilter.test.js |
| 7 | src/test/js/expertTagBatchFix.test.js |
| 8 | src/test/js/batchManualExecutionLog.test.js |
| 9 | src/test/js/batchSendTaskConsoleInteraction.test.js |

9 文件，一个前端子系统；后端路径仅用来核对契约，不改。

## 验收标准

- I-1：编辑/新建/独立手动/来源复制分别断言回填与 payload；[] 原样往返；无 undeliverable 选项；两个组各三 input、ID 唯一。
- I-2：开关 false→true→false，checked 不丢；材料提醒禁用→切回不自动开启；禁用组可读但不可点击。
- I-3：只改数组就出现独立差异字段；恢复后消失；来源数组不被 mutate；确认页与最终 snapshot 一致；手动只调用 POST。
- I-4：策略 SKIP 新文案；旧 REJECTED 原文案；providerState/reason 继续转义展示；NOT_REQUIRED 不呈现标签失败。
- I-5：资源版本一致；未选择的数量不从预估猜测；测试 mock 断言预估只调现有 preview API。
- S-1：现有总开关 DOM/class/id 保留，只改指定提示。
- S-2：新增 CSS 与契约逐字一致；两组 DOM 与骨架/ID 映射一致；无 inline style/未声明 class；1280px 和 390px 浏览器目测对照预览。
- S-3：无额外 CSS；差异独立容器且正文经 escapeHtml；日志只改指定文案。
- 命令：
~~~sh
node --check src/main/resources/static/app.js
node --test src/test/js/batchEmailVerification.test.js
node --test src/test/js/*.test.js
~~~
前端全量测试用于发现其他 extractFn sandbox 对新 helper 的依赖；出现本范围直接回归时先把确切测试文件补入本计划，不删断言逃避。浏览器必须打开真实 index+后端预发环境验证保存重开，不把 docs/mockups 当功能验收。

## 人工验收清单

### A-1：新建与旧配置
- 前置条件：预发完成前两计划，存在升级前旧配置 C；登录→收发件箱→批量任务。
- 操作步骤：1. 新增任务。2. 观察总开关/选项，开启验证。3. 勾选 risky 保存，关闭编辑并重开。4. 再打开旧配置 C。
- 预期结果：新增总开关「已关闭」，仅 deliverable 勾选且三框禁用；开启后三框可操作；保存后 deliverable+risky 勾选、unknown 未选；旧 C 三项均勾选。
- 覆盖：I-1、I-2；S-1、S-2；IP-1、IP-4。

### A-2：手动覆盖与恢复
- 前置条件：定时配置 C 开启验证且只选 deliverable。
- 操作步骤：1. 手动执行页选择 C。2. 加选 unknown。3. 打开执行确认后取消。4. 重新选择同一来源配置 C。5. 清空来源。
- 预期结果：第 2 步「允许发送的验证结果」显示「已修改」；确认页显示原值可投递、新值可投递/未知；重新选择后只选 deliverable 且差异消失；清空后独立默认只选 deliverable、总开关关闭；重新打开 C 仍只选 deliverable。
- 覆盖：I-3；S-3；IP-2。

### A-3：全部不选、关闭与材料提醒
- 前置条件：介绍邮件配置编辑开启验证。
- 操作步骤：1. 清空三个勾选并保存重开。2. 关闭再开启验证。3. 切到材料提醒模板再切回介绍邮件。
- 预期结果：空选择可保存重开仍全空；界面写「全不选时不放行任何验证结果」；关再开不自动补勾；材料提醒总开关关闭且禁用，三框禁用；切回仍不开启验证。
- 覆盖：I-1、I-2；S-1、S-2；IP-1、IP-4。

### A-4：真实执行与日志区分
- 前置条件：隔离预发中仅一条有有效 risky 原始验证记录的未联系候选人，SMTP 指向测试箱；关闭历史预筛选以保证进入验证；选 deliverable。
- 操作步骤：1. 启动手动介绍邮件。2. 打开本次执行邮箱验证明细。3. 查看专家标签及测试箱。
- 预期结果：明细「按策略跳过」「未勾选该验证结果，本次未发送」，原状态 risky 可见；标签「无需处理」；测试箱 0 封、专家无新增「邮箱异常」。
- 覆盖：I-4；S-3；IP-2、IP-3。

### A-5：样式、键盘与既有控件回归
- 前置条件：真实预发页面，新资源版本已加载；浏览器宽 1280px。
- 操作步骤：1. 查看编辑/手动两组，再缩至 390px。2. Tab 聚焦并空格勾选。3. 关闭验证，尝试点击。4. 修改每轮数量为 2、轮次为 1、每封间隔为 30 秒，查看确认页。
- 预期结果：三框横排或自然换行，无横向溢出；checkbox 16px，选项13px，说明11px；焦点2px蓝色描边；禁用组 opacity .52，点击不改变值；原数量/轮次/间隔仍按2/1/30显示，已有模板/发件账号选择仍在。
- 覆盖：I-2、I-5；S-1～S-3；IP-5；原控件回归。

### A-6：预估与总开关回归
- 前置条件：预发已登录，浏览器 Network 开启。
- 操作步骤：1. 改变三个勾选和总开关，等待预估刷新。2. 查看请求 URL 及返回的预估说明。3. 刷新页面检查资源 v 参数。
- 预期结果：预估仍只请求 /api/mail/batch-send/recipients/preview；没有从浏览器发往 Emailable 的请求；不新增「已验证可投递人数」承诺；所有原有版本化 CSS/JS 使用 20261001-email-verification-allowlist。
- 覆盖：I-5；IP-5；预估回归。

