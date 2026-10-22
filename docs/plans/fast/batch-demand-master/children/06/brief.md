# Child 06 approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master
- Branch: fast/batch-demand-master
- Exact approved plan: docs/plans/2026-10-22/batch-demand-06-ui.md
- Plan identity: commit:f684da0a316b8cfb560fe7ebb0a8817d8a2594f2
- Approval: explicit user `/fast-p docs/plans/2026-10-22/batch-demand-master.md`, 2026-10-22. The invocation authorizes these exact bytes despite their pre-approval status wording; do not alter plan text.
- Depends on: 05
- Product base: prior child terminal Code head, supplied at dispatch

## Complete child contract

# 06 统计缓存页面：只读加载、显式刷新与缓存时间

状态：待批准。依赖：05。范围：6文件；批量任务前端一个子系统。不改CSS文件，不改启动/保存API。

## 需求描述

配置编辑与手动发信两处统一显示缓存目标估计和缓存时间；进入/修改条件仅查询已有缓存，显式按钮才全量统计。保持模板门禁真实开关、当前表单快照、验证选项、诊断样本/partial提示、启动安全校验与历史日志；取消为比较门禁而同时请求on/off两套全量统计。

## 关键不变量

### Invariant I-1: 普通交互只查缓存
- Rule: 两面板打开及有效条件变化仅调用statistics?cacheOnly=true，300ms debounce且旧seq失效；按钮调用statistics?refresh=true一次，payload使用当前真实gateFilterEnabled。不得再调用preview off/on双请求，不因MISS/stale/ERROR自动重算。点击同面板刷新时禁用按钮，完成/失败恢复。
- Applies to: editor/manual打开、变化、按钮与scheduleRecipientPreview。
- Violation consequence: 页面反复全扫、条件比较翻倍成本。
- 来源: original


### Invariant I-2: 区分缺失与零
- Rule: MISS显示尚未统计，不显示0；READY/0才显示0。缓存目标估计与本次执行目标分开；cachedAt为服务端缓存完成时间，summary.asOf单独标诊断采样时间。stale或ERROR/REFRESHING带旧值时必须带状态，不能改写cachedAt为当前浏览器时间。partial必须保留。
- Applies to: 两处hint、状态标签及错误处理。
- Violation consequence: 误导为最新精确可发送人数。
- 来源: original


### Invariant I-3: 竞态与安全内容
- Rule: 每面板seq独立；任何返回（成功/失败/轮询）都验证seq与当前snapshot身份。切换配置/关闭面板使旧响应失效；换条件先清旧人数。error.data可用旧summary只允许匹配当前cacheKey；所有动态模板/原因/sample/error文本escapeHtml或textContent。发送按钮不得因cache MISS/0/陈旧被禁用。
- Applies to: fetch完成回调、reset、close及render。
- Violation consequence: 旧条件覆盖新界面、XSS或缓存阻断发信。
- 来源: original


### Invariant I-4: 原操作合同保留
- Rule: 不改buildManualExecutionSnapshot/buildConfigEditorRecipientSnapshot选人字段语义、保存/启动模板令牌、邮箱验证选项。取消的只是门禁off/on差量比较；当前门禁状态仍展示。既有baseline/原因样本/重叠计数/partial不能隐藏或当全量理由。
- Applies to: UI预估局部与既有启动/验证入口。
- Violation consequence: 优化统计时误改真实发送规则。
- 来源: original



## 样式契约

### S-1: 两处控制区
- 复用：styles.css:9313-9347的.batch-recipient-preview-controls（gap16px、padding12px 14px、border rgba(37,99,235,.1)、bg rgba(37,99,235,.035)）；.button/.primary:802-850（高32px、字12px、padding0 12px）；既有disabled规则opacity .7/cursor wait。<=640px列布局原样保留。
- DOM结构：以下editor骨架为最终结构；manual逐字同构，只将id前缀batchConfigEditor替换batchManual。仅改现有strong/span/button文本，不新增控件/新class。
```html
<div class="batch-recipient-preview-controls">
  <div><strong id="batchConfigEditorRecipientStatus">尚未统计</strong><span>页面仅加载已有缓存；点击按钮后重新统计当前条件。</span></div>
  <button class="button primary" type="button" id="batchConfigEditorRecipientPreviewBtn">统计并缓存</button>
</div>
<div class="batch-config-editor-hint" id="batchConfigEditorRecipientHint"></div>
```
- 状态文本：MISS“尚未统计”；READY“已加载缓存”；READY+stale“缓存已超过24小时”；REFRESHING“正在刷新统计…”；ERROR有旧值“刷新失败，保留上次缓存”，无旧值“统计不可用”。按钮无值“统计并缓存”，有值“刷新统计”，请求期间“统计中…”并disabled=true。
- 禁止项：inline style、新class、新CSS、改全局button规则。

### S-2: 缓存参考和诊断
- 复用：styles.css:9298 .batch-config-editor-hint（padding8px 12px、margin-top10px、radius10px、bg rgba(37,99,235,.06)、12px/1.6）；:9851 .batch-gate-excluded（#be123c），:9856 .batch-gate-warnline（block、margin-top4px、#b45309）。
- 有缓存的hint内固定骨架如下；值动态转义，诊断缺失用“详细解释不可用”，不补0。
```html
<div>缓存时间：<span>2026-10-22 16:00:00</span></div>
<div>缓存目标估计：<strong>100</strong> 人（未做实时验证）</div>
<span class="batch-gate-warnline">仅供参考；发送时按当次额度取人并重新检查。</span>
<div>模板门禁：<span>已开启</span></div>
<div>诊断采样时间：<span>2026-10-22 15:59:58</span></div>
<div>诊断范围内 <strong>100</strong> 人；排除 <span class="batch-gate-excluded">10</span> 人；符合任务条件 <strong>90</strong> 人；待执行验证 <strong>90</strong> 人</div>
<span class="batch-gate-warnline">原因人数可重叠；发送前还会复核退订、发送历史和账号运行状态。</span>
<details><summary>查看排除原因与样本</summary><div>原因：0 人；仅显示前 0 条</div></details>
```
- 上述100/10/90仅为DOM示例值，不是同一业务fixture断言。有partial附加现有warning span，文本“诊断仅覆盖部分候选；不是全量排除分析。”；stale附加“缓存已超过24小时，请按需刷新。”。保留现有每原因样本div、截断标记，禁止原因数相加冒充总排除。
- MISS只textContent“尚未统计，点击‘统计并缓存’获取参考人数。”；ERROR无缓存只textContent“统计不可用，可重试。”；REFRESHING无值只textContent“正在统计当前条件…”。
- 禁止项：新class/CSS、内联样式、用旧“本次目标”指代缓存人数、隐藏partial。

## 现状审计

### 前端状态/HTTP
- app.js:22649两面板seq；:22651状态/按钮；:22668 reset；:24023 schedule当前只标旧态；:24053 refresh；:24136门禁可用时双POST；:25693/25697按钮绑定。
- 现有api:4701读取error.data，因此503 envelope可安全提取，不需修改全局api或新写fetch。
- 打开editor:23061和manual:24406先reset；模板异步解析:23731-23789再schedule。lookup必须在最终snapshot可用后发出，不要用中间模板状态缓存键。
- 静态存储只有DOM/内存seq，不引入localStorage；权威cache在05后端。write路径：open/reset、change、refresh回调；read路径：状态render、提示render、按钮；启动逻辑仍读表单，不读人数。
- X1：后端缓存→两处提示；X2：表单变化/慢响应→当前提示；X3：模板门禁/验证选项→请求snapshot→启动请求。

### 前端样式盘点
- 证据账本“前端逐字基线”包含index:1568-1572、1792-1796以及CSS完整原文。上述S-1/2引用精确实值；不修改现有class定义，因此其他使用点不受影响。
- app.js:24080原统计时间来自asOf，不是缓存时间；:24112旧fallback与gateHtml混合文案会误称本次目标，本步改为S-2。
- 03资源token为20261022-batch-demand-progress；本步16处统一改20261022-batch-demand-cache。实施前重新rg旧token的测试引用，若新增文件依赖，修订manifest后再改。（来源: K-frontend-cache-key-triad）
- 测试通过抽取函数执行；新增helper需加入现有脚本提取名单，不能让DOM stub吞掉依赖。（来源: K-js-test-invocation-surface）

## 实现方案

1. 清单1为refreshRecipientPreview增加显式mode参数或局部lookup helper；按钮只refresh，schedule只300ms cacheOnly。打开/最终模板解析完成后lookup；close/reset递增seq并清timer。请求状态用独立loading与server state映射，不把cacheOnly查找当全量“统计中”长期阻塞（I-1/3，S-1）。
2. 清单1移除off/on双请求与差值gateHtml，保留当前门禁开关文案；使用05 envelope summary渲染S-2，基于cachedAt显示时间，asOf只诊断；MISS绝不numberOf(undefined)渲染0（I-2/4，S-2）。
3. 清单1错误处理从error.data拿同key旧值；网络错误仅可保留当前同snapshot已展示缓存。REFRESHING可点击之后再cacheOnly，不引入自动重扫；本页发起refresh的promise完成后渲染最终状态，同key其他页无需实时广播。配置改变清理旧值即使网络失败也不得展示原key人数（I-2/3，S-1/2）。
4. 清单2按S-1修改两处固定文本，统一资源token；不修改发送按钮、样式文件或其他页面（I-4，S-1）。
5. 清单3-6更新明确双请求/禁止自动统计的旧测试：现在允许cacheOnly，仍严格禁止自动计算。新增缓存时间不变、0/MISS、503旧值、条件竞态、partial、门禁单请求及启动不依赖统计的行为测试（I-1至I-4，S-1/2）。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/resources/static/app.js` |
| 2 | `src/main/resources/static/index.html` |
| 3 | `src/test/js/recipientPreviewDiagnostics.test.js` |
| 4 | `src/test/js/batchEmailVerification.test.js` |
| 5 | `src/test/js/batchSendTaskConsoleInteraction.test.js` |
| 6 | `src/test/js/batchRecipientStatisticsCache.test.js` |

## 验收标准

- I-1：打开、改筛选各至多一个cacheOnly（debounce合并）；点击按钮只一个refresh POST；gate true/false均不出现比较双请求；MISS/ERROR无自动重算。
- I-2：同cachedAt多次打开文本相同；READY0显示0，MISS不显示0；partial警告/原因样本/截断标记保留；cachedAt与asOf不同 fixture分别显示。
- I-3：A→B切换，A晚返回成功或错误都不覆盖B；editor/manual互不影响；close后回调不污染重开；XSS fixture只显示字面；缓存无值仍允许启动。
- I-4：同表单snapshot在lookup/refresh/启动的gate、类型、地区、验证状态一致；只删除差量对比，不删除实际门禁选项；旧历史日志03回归。
- S-1/2：无styles.css改动，无新增class/inline style，DOM与骨架同构；按钮disabled/恢复、warning颜色/字号/间距目测；资源引用同值。
- node --test src/test/js/recipientPreviewDiagnostics.test.js src/test/js/batchEmailVerification.test.js src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchRecipientStatisticsCache.test.js。
- 完成后node --test src/test/js/*.test.js；若发现其它测试写死旧合同，不得直接放宽断言，先修订清单。

## 人工验收清单

### A-1: 页面不全扫、按钮才刷新
- 前置条件: 测试固定100名合格候选、缓存空；浏览器网络面板和服务端查询记录可见。
- 操作步骤: 1. 打开配置编辑；2. 点击统计并缓存；3. 关闭再打开；4. 在手动发信中用相同条件打开；5. 点击刷新统计。
- 预期结果: 首次仅cacheOnly且显示尚未统计；按钮才全扫，显示缓存目标100和缓存时间；两处相同条件复用同时间，重开不全扫；再次刷新才更新时间。
- 覆盖: I-1/2、X1、缓存需求。

### A-2: 快速切换与异常
- 前置条件: A条件缓存100、B条件无缓存；网络代理可延迟A或使refresh503。
- 操作步骤: 1. A请求未返回前改B；2. 释放A；3. 切回A让refresh失败；4. 打开无候选但已成功缓存0的C条件。
- 预期结果: B显示尚未统计而不是100；A失败仍显示原100/原缓存时间及“刷新失败，保留上次缓存”；C明确显示0；无自动全扫。
- 覆盖: I-2/3、X2、缺失/零/失败区别。

### A-3: 门禁、诊断与发送
- 前置条件: 测试候选30001，diagnostic partial=true；SMTP捕获箱；手动任务每轮30。
- 操作步骤: 1. 开启门禁刷新；2. 查看请求和样本；3. 关闭门禁不刷新，直接执行；4. 查看捕获箱与日志。
- 预期结果: 第1步仅当前gate=true一次请求；保留partial和“原因人数可重叠”；第3步使用gate=false且不因缓存缺失阻止；成功最多30，执行卡片为03语义。
- 覆盖: I-1/4、X3、验证/启动合同与诊断保留。

### A-4: 样式与安全文本
- 前置条件: 两表单宽1200px/600px；测试原因文本含尖括号，缓存时间与诊断采样时间不同。
- 操作步骤: 1. 查看控制区、hint、原因展开；2. 点击刷新看disabled；3. 比较两个时间和特殊字符。
- 预期结果: 控制区padding12px 14px、gap16px，hint12px/1.6、圆角10px；按钮高32px、disabled opacity .7；窄屏纵排；危险文本不执行；缓存时间与诊断时间标签不同。
- 覆盖: S-1/2、I-2/3。



## Global constraints

Read the master invariants I-1 through I-4 at docs/plans/2026-10-22/batch-demand-master.md. Authorized Files are exclusively the exact table in the child plan above. Preserve date-only compatibility, qualification rules, realtime send safety, old execution interpretation, and sequential interfaces. No production sends, release, migrations, schema changes, or unauthorized changes. Real SMTP prohibited; isolated tests only. Existing worktree application.yml remains committed baseline; unrelated main-worktree mail attachment timeout changes are preserved there and not imported.

Use execute-p against the exact approved child plan. Local implementation commit is explicitly authorized with subject `feat(fast-p): implement 06`. Do not run verify-p/review-p/fix-v; fast-p independent lightweight verifier follows. Exclude fast-p evidence from product commit. Any plan amendment or extra file requires PLAN_CONFLICT. Do not modify knowledge/docs outside fast-p execution report, as authorized-file boundary supersedes general documentation cleanup. Do not run builds/tests/formatters mid-flight; collect required commands after implementation, once final state is established. Smoke must exercise changed path; real converter replay for 01, real cursor containers for 02, browser surface for 03/06. Record exact commands, exits, skips and environment limits, never infer PASS.

## Baseline evidence

Java 11.0.15 Zulu and Docker server 29.8.2 observed available. Initial child 01 Maven baseline is controller-owned and its result will be recorded before dispatch. No whole-system baseline is claimed. Prior child interfaces are only those explicitly approved in their plans, not later product changes.
