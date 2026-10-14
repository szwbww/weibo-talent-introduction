# 03：页面显示过滤解释并修正模板提示

主约束：[MAIN 主计划](batch-filter-main.md)。实施顺序、共享响应、共同fixture和联合验收必须同时遵守MAIN；本文件的S-1/S-2仍为本组唯一页面样式契约。

状态：方案完成，未实施。依赖02接口；5文件、1个静态管理页面子系统、零持久字段。范围仅批量任务编辑/手动执行预估提示。

## 需求描述

1. 在两个批量任务表单显示母集、排除、符合任务条件、待执行验证及原因样本；明确样本截断、快照时间和运行时可能变化。
2. 模板关闭时明确不因个性化缺项排除；开启时被排除数量不称为投递失败。
3. 保留当前选择、500ms防抖、丢弃过期响应、错误不弹窗、绑定账号提示、全部现有保护与页面样式；不改变发送开关、收件人或任务配置。
4. 范围外：新筛选条件、改CSS/布局、供应商验证按钮、线上数据回填、详情页重做。后端02完成后再接入，不用前端猜测缺失原因。

## 关键不变量

### Invariant I-1: 展示事实和推测分开
- Rule: 直接显示后端baseline、excludedUnique、eligibleBeforeLiveVerification、awaitingVerification及asOf；reasonHits允许重叠，不能求和伪造排除人数；无缓存显示“待执行验证”，不能显示可投递或供应商unknown。样本明确“仅显示前N条”；partial明确“统计未完成”。
- Applies to: 两个表单预估响应渲染，无业务写入。
- Violation consequence: 页面继续制造可发人数与过滤原因的黑盒。
- 来源: K-recipient-count-preview-parity；本轮F2/F3。

### Invariant I-2: 模板提示与开关一致
- Rule: gate关闭显示“不因个性化缺项排除”；开启显示“因模板缺项排除N人”并列missingKeys；不出现“计入失败”。普通配置错误仍显示错误，不假装开关能修复模板不存在。
- Applies to: baseHintHtml、refreshRecipientPreview、两处静态gate提示。
- Violation consequence: 操作者被错误文字诱导为关闭模板也仍拦截。
- 来源: 本轮F4。

### Invariant I-3: 交互安全和兼容
- Rule: 500ms防抖、seq检查保留；关闭表单后不得写旧DOM，错误显示“预估不可用”；姓名/邮箱/原因/模板key均使用既有HTML转义。只读展开样本不发发送/验证请求，不修改原任务。所有版本化静态资源同一新cache key。
- Applies to: app.js和index.html，版本查询参数为唯一静态文件引用写入。
- Violation consequence: 老响应覆盖新筛选、注入、误执行或浏览器继续读旧JS。
- 来源: K-recipient-count-preview-parity；K-frontend-cache-key-triad。

## 样式契约

### S-1: 两处收件人提示
- 复用：`batch-config-editor-hint`，styles.css:9298；其strong规则:9308；`batch-gate-excluded`:9809；`batch-gate-warnline`:9814。禁止修改规则或自造相似class。无新增CSS。
- 新增：无class、无CSS。
- DOM结构：保留两个原容器ID；同一渲染函数只填入下列骨架，动态内容全部转义：

```html
<div class="batch-config-editor-hint" id="batchConfigEditorRecipientHint">
  <div>统计时间：<span>时间</span></div>
  <div>范围内 <strong>0</strong> 人；排除 <span class="batch-gate-excluded">0</span> 人；符合任务条件 <strong>0</strong> 人；待执行验证 <strong>0</strong> 人</div>
  <span class="batch-gate-warnline">原因人数可重叠；发送前还会复核退订、发送历史和账号运行状态。</span>
  <details><summary>查看排除原因与样本</summary><div>原因：0人；仅显示前0条</div><div>样本的专家标识与具体原因</div></details>
</div>
```

- 手动执行使用完全相同内部骨架，容器ID替换为batchManualRecipientHint；原因行/样本行仅重复上述div，不增加表格、按钮、输入控件。partial/不可用/gate开关说明放既有batch-gate-warnline。无结果不伪造数字。
- 禁止项：inline style、新class、改styles.css、把某一class全局改色。仅就地修改两个容器内部内容，其他使用点不受影响。

### S-2: 模板开关说明与静态缓存
- 复用：`batch-gate-hint`，styles.css:9772；is-warn规则:9778。保留input/toggle/keys DOM、ID和层级。
- DOM结构：仅替换两个既有span的textContent：

```html
<span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">开启后按模板必填字段筛选，缺项会在发送前排除；关闭时不因个性化缺项排除。</span>
<span class="batch-gate-hint" id="batchManualGateFilterHint">仅影响本次执行；开启后按模板必填字段筛选，关闭时不因个性化缺项排除。</span>
```

- 新增CSS：无。禁止改变toggle、keys以及绑定账号提示；资源链接只更新cache key，不改顺序/路径。

## 现状审计

### 页面状态与接口
- 静态文件由开发构建写，index.html引用app.js；用户表单写内存snapshot并请求既有预估接口，02扩展响应只读消费。无localStorage/DB/ES新写入。
- 写路径：表单字段事件更新编辑/手动执行state；refreshRecipientPreview异步更新提示DOM；保存配置/执行按钮维持原handler，不与诊断展开混用。
- 读路径：baseHintHtml/refreshRecipientPreview渲染当前snapshot及API结果；后端旧reasonHits/excludedRecipients未显示。gate关闭时现有文案仍称发送时拦截/计失败，与后端不符。
- X1：02完整计数/原因及快照时间 → 两个提示；X2：表单开关/模板变化 → 防抖请求 → 当前响应；X3：index版本引用 → 浏览器加载修改后的app.js。
- 线上完整app.js/index与本地文件哈希不同，但本次两个函数文本已逐字核对一致；不要推断整站版本一致。

### 前端样式盘点
- 容器：index.html:1559 `batchConfigEditorRecipientHint`；:1779 `batchManualRecipientHint`，均为 `<div class="batch-config-editor-hint" id="..."></div>`；绑定账号提示在:1625，保留。
- 原模板说明：index.html:1490 的文字含“缺字段的会在发送时被门禁拦下并计入失败”；:1759为手动说明。完整原DOM与CSS已现场读取，目标替换严格按S-1/S-2。
- 改动前规则逐字基线：

```css
.batch-config-editor-hint {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: rgba(37, 99, 235, .06);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.batch-config-editor-hint strong {
  color: var(--primary);
  font-weight: 600;
}
.batch-gate-hint {
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}
.batch-gate-hint.is-warn { color: var(--warning-strong); }
.batch-config-editor-hint .batch-gate-excluded {
  color: var(--error-strong);
  font-weight: 600;
}
.batch-config-editor-hint .batch-gate-warnline {
  display: block;
  margin-top: 4px;
  color: var(--warning-strong);
}
```

- tokens：primary=#1e40af；text-muted初始#94a3b8、任务区覆盖#64748b；warning-strong=#b45309；error-strong=#be123c；暗色使用既有变量覆盖，不新增hover/active/disabled规则。原控件状态不修改。
- 本次不修改任何CSS规则，故其他class使用点保持原样。原16个带版本资源共用20261013-materials-zip；检索测试无硬编码该key，仅index命中。实施时16个统一改为20261014-batch-filter，不加独立版本。

## 实现方案

1. `app.js` 两处预估使用同一个诊断渲染辅助函数，消费02响应，按S-1骨架展示数量/样本、重叠提醒、截断/partial与asOf。缺少新字段的旧响应只显示现有数量并注明详细解释不可用，不能伪造0。遵守 I-1、I-3、S-1。
2. `app.js` 修正baseHintHtml/refreshRecipientPreview模板文案，gate关闭时不输出模板阻拦/失败；`index.html`按S-2替换两静态提示，统一16个版本化引用的key。遵守 I-2、I-3、S-1、S-2。
3. 新 `recipientPreviewDiagnostics.test.js` 验证数量重叠、限量样本、转义、partial、API错误/缺新字段、旧响应丢弃；断言真实index存在两个容器ID，不能只依赖永远返回元素的DOM stub。修改 `batchEmailVerification.test.js` 与 `gateTemplateFilter.test.js` 中受影响的文案/开关预期，保留配置字段和切换行为测试。遵守 I-1～I-3、S-1/S-2。

## 变更文件清单

|序号|精确路径|变更|
|---|---|---|
|1|src/main/resources/static/app.js|诊断与正确文案|
|2|src/main/resources/static/index.html|静态文案/统一cache key|
|3|src/test/js/recipientPreviewDiagnostics.test.js|新增诊断契约测试|
|4|src/test/js/batchEmailVerification.test.js|邮箱提示回归|
|5|src/test/js/gateTemplateFilter.test.js|模板开关回归|

## 验收标准

- I-1：同MAIN G-2及02的10人fixture显示范围10/排除4/符合6/待验证3；原因6次不变成排除6人；无缓存显示待执行验证；truncated/partial可见。
- I-2：gate关不出现拦下/计入失败；开显示模板排除数和变量名；配置错误独立呈现。
- I-3：快速连续修改只显示最后响应，500ms防抖保留；API失败为“预估不可用”；恶意样本字符串转义；诊断无发送/验证POST；16个资源key一致。
- S-1/S-2：DOM结构与契约比对，CSS diff为零，无inline style/未声明class；真实index ID断言、浅色/暗色目测。
- 使用仓库现有Node测试命令执行三个测试及关联批量页面契约测试；不为本计划扩展测试框架或更新无关快照。

## 人工验收清单

### A-1: 两处解释一致
- 前置条件: 测试环境02接口已有MAIN G-2的10人fixture，唯一排除4、符合6，原因命中6（地区2+状态2+模板1+邮箱1），3人待验证。
- 操作步骤: 1. 打开任务编辑；2. 打开同快照的手动执行窗口；3. 展开排除原因。
- 预期结果: 两处均显示10/4/6/3；原因可重叠提示存在；样本截断时明确前N条；显示统计时间和运行时复核提示；打开不产生邮件/验证记录。
- 覆盖: I-1、I-3、S-1、X1、需求1/3。

### A-2: 模板与现有保护提示
- 前置条件: 测试环境1人缺expertFamilyName；存在已绑定账号专家，保留原提示。
- 操作步骤: 1. 开模板开关；2. 关闭；3. 分别在编辑与手动窗口查看文案。
- 预期结果: 开时显示“因模板缺项排除1人”及expertFamilyName；关时显示“不因个性化缺项排除”；两处不出现“计入失败”；绑定账号说明仍在；表单选择未被重置。
- 覆盖: I-2、I-3、S-2、X2、需求2/3。

### A-3: 慢请求与不可用
- 前置条件: 浏览器测试网络设置首请求延迟2秒、第二请求100ms；另可模拟接口500和partial。
- 操作步骤: 1. 连续改变地区两次；2. 等待2秒；3. 模拟接口500；4. 模拟partial；5. 关闭后重开窗口。
- 预期结果: 仅保留第二次地区结果；500显示“预估不可用”且不弹窗；partial显示“统计未完成”；旧响应不写入已关闭或新建表单；无自动执行。
- 覆盖: I-1、I-3、S-1、X2、需求3。

### A-4: 样式和更新资源
- 前置条件: 清空浏览器缓存并加载测试页面；准备浅色/暗色各一屏。
- 操作步骤: 1. 检查两个提示区域；2. 展开details；3. 检查网络资源版本与计算样式。
- 预期结果: 容器margin-top=10px、padding=8px 12px、font-size=12px、line-height=19.2px；警告行margin-top=4px；gate说明11px/16.5px；没有新增悬浮按钮或布局变化；16个资源均为20261014-batch-filter。
- 覆盖: S-1、S-2、I-3、X3、需求3。
