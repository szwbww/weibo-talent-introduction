# 04 — 邮件监控中的打开跟踪管理页

状态：待实施。依赖01 API及03发送关联。只用现有静态前端，不新增框架、路由系统或构建工具。

## 需求描述
- O-1：邮件监控的“首发邮件”旁增加“打开跟踪”，可查看/切换全局开关。
- O-2：按时间、发件账号查看跟踪发出数、收到信号数和信号率；按状态/关键字查询分页列表及详情。
- 不改变：N-1 旧监控子标签、总览、服务商/地区/信誉/账号区和自动刷新；N-2 专家详情及回复编辑器、原首发邮件表列与统计口径。
- 范围外：新一级侧栏、可视化图表、额外自动轮询、IP/设备/位置展示、打开次数、逐次事件时间线、修改专家页或收发件箱。

## 关键不变量
### Invariant I-1: 开关以后端为准
- Rule: 首次进入读取settings，保存期间禁用开关；PUT成功后用响应替换状态，失败恢复原值并显示错误；未配置禁止开启但始终允许关闭。加载失败不可显示假关闭成功。
- Applies to: load/save settings、离开返回页面
- Violation consequence: 页面状态与真实发送行为相反。
- 来源: original
### Invariant I-2: 状态只表达信号
- Rule: OPENED=已收到打开信号；NO_SIGNAL=暂无打开信号；NOT_TRACKED=未跟踪。未跟踪不推断原因；历史无收件快照明确标记；不开图片预览、不请求token。
- Applies to: 表格、详情、指标说明
- Violation consequence: 误称已读/未读或浏览管理页制造信号。
- 来源: original
### Invariant I-3: 指标和分页口径明确
- Rule: 共享监控日期/账号为summary范围；status/keyword只筛列表；分页20条，筛选改变归零；分母0显示—。一次records响应更新指标/列表/总数。
- Applies to: 查询参数、render、pagination
- Violation consequence: 混用不同范围分母或跨页漏数据。
- 来源: original
### Invariant I-4: 异步结果不串页面
- Rule: 为tracking请求保存递增seq及查询快照；晚到结果不得覆盖新筛选/别的子标签；detail有独立seq；新加载禁用分页，失败显示错误不伪装空列表。
- Applies to: 切页/切tab/筛选/详情load
- Violation consequence: 旧请求覆盖新记录或操作对象。
- 来源: original
### Invariant I-5: 只安全呈现业务字段
- Rule: 表格/详情所有动态姓名/主题/账号/邮箱使用escapeHtml或textContent；只用数值ID绑定操作；不innerHTML渲染服务端正文或加载邮件图片。
- Applies to: 所有动态DOM
- Violation consequence: XSS或假打开事件。
- 来源: K-mail-body-display-sites

## 样式契约
所有新增规则仅作用 `.mot-*`，不得改全局tokens/classes；新增DOM禁止inline style。下面骨架中的动态文本允许替换，class/层级/ID不自行扩展。既有DOM原先的inline style保留，不借本功能清理旧页面。

### S-1: 子标签与内容区
- 复用 `.tab.button.secondary/.active`（styles.css:802、838–880、3025–3055）、`.toolbar`（355）、表格（987–1050）、`.pagination`（3057）、`.muted`（3084）。
- 在introductions后增加下列按钮。原table-wrap与monitoringPagination整体包进新普通div `monitoringLegacyActivity`，只用hidden切换；不改它们原有内容/inline style。tracking区插在该wrapper前。tracking区默认hidden；离开时隐藏并显示原wrapper。
```html
<button class="tab button secondary" data-subtab="tracking">打开跟踪</button>
<div id="monitoringOpenTracking" class="mot-root" hidden>
  <div class="toolbar">
    <label class="mot-toggle"><input id="motEnabled" type="checkbox" disabled>开启非回复邮件打开跟踪</label>
    <span id="motSettingStatus" class="muted" role="status">正在读取设置…</span>
    <button id="motSettingsRetry" class="button secondary" type="button" hidden>重试设置</button>
  </div>
  <p class="muted">适用于所有非回复外发邮件；自动回复、人工回信均排除。关闭后停止新邮件跟踪及新信号记录，历史保留。</p>
  <p id="motConfigStatus" class="muted"></p>
  <div id="motMetrics" class="card-grid" aria-live="polite"></div>
  <p class="muted">打开信号表示图片被加载，不等于本人已读。指标按发送日期和发件账号统计；下方状态与搜索只影响列表。</p>
  <div class="toolbar">
    <label>状态 <select id="motStatus"><option value="ALL">全部</option><option value="OPENED">已收到打开信号</option><option value="NO_SIGNAL">暂无打开信号</option><option value="NOT_TRACKED">未跟踪</option></select></label>
    <label>搜索 <input id="motKeyword" type="search" maxlength="200" placeholder="专家、收件邮箱或主题"></label>
    <button id="motQuery" class="button secondary" type="button">查询</button>
    <span id="motRefreshed" class="muted"></span>
  </div>
  <p id="motError" class="mot-error" role="alert" hidden></p>
  <div class="table-wrap"><table id="motTable"><thead><tr><th>发送时间</th><th>专家</th><th>收件邮箱</th><th>发件账号</th><th>主题</th><th>跟踪状态</th><th>首次信号</th><th>最近信号</th><th>操作</th></tr></thead><tbody></tbody></table></div>
  <div id="motPagination" class="pagination mot-pagination"></div>
  <section id="motDetail" class="mot-detail" hidden></section>
</div>
<div id="monitoringLegacyActivity">
  <!-- 原 monitoringActivityTable 的 table-wrap 与原 monitoringPagination，逐字移入 -->
</div>
```
新增CSS逐字复制：
```css
.mot-root {
    padding: 0 24px 24px;
}
.mot-root[hidden], .mot-detail[hidden], #motSettingsRetry[hidden] {
    display: none;
}
.mot-toggle {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}
.mot-toggle input {
    width: 16px;
    height: 16px;
    min-height: 0;
    accent-color: var(--primary);
}
.mot-error {
    color: #b91c1c;
}
.mot-pagination {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 16px;
}
```
- 开关使用原生checkbox（非定制switch），focus/checked/disabled沿浏览器原生交互；按钮所有hover/active/disabled复用原规则。无新增动画、阴影或一级路由。

### S-2: 指标、动态行与分页
- 指标复用 `.metric-card/.metric-label/.metric-value`（styles.css:2976–3022），状态复用 `.badge.ok/.warn`及基础badge（1054–1096）；不改其规则。
- motMetrics严格三张卡，下例重复3次，标签依次为“跟踪发出”“收到打开信号”“打开信号率”；数值初次未加载为—。
```html
<div class="metric-card"><div class="metric-label">跟踪发出</div><div class="metric-value">0</div></div>
```
- 行骨架：
```html
<tr><td>发送时间</td><td>专家名称</td><td>收件邮箱</td><td>账号</td><td>主题</td><td><span class="badge ok">已收到打开信号</span></td><td>首次时间</td><td>最近时间</td><td><button class="button secondary" type="button" data-mot-detail="123">详情</button></td></tr>
```
NO_SIGNAL用badge warn，NOT_TRACKED用badge基础；null时间=—、recipient=null=未保存收件快照、姓名缺失=—。错误/加载/空状态使用一行 `<tr><td colspan="9" class="muted">…</td></tr>`，文案分别“加载失败，请重试”“加载中…”“暂无记录”。不造未读状态。
- 分页：
```html
<span class="muted">共 0 条 · 第 1 页</span><button class="button secondary" type="button" data-mot-page="prev" disabled>上一页</button><button class="button secondary" type="button" data-mot-page="next" disabled>下一页</button>
```
按钮disabled由页码/total/loading决定；数据删除造成当前页超限则回到最后有效页重查一次，不无限递归。

### S-3: 行内详情
- 详情放列表下方，不加弹窗；复用panel-head和button secondary（962、802）。
```html
<div class="panel-head"><h3>跟踪详情</h3><button class="button secondary" type="button" id="motDetailClose">关闭详情</button></div>
<dl class="mot-detail-grid"><dt>邮件记录</dt><dd>123</dd><dt>主题</dt><dd>主题</dd><dt>状态</dt><dd>已收到打开信号</dd><dt>收件邮箱</dt><dd>邮箱</dd><dt>发送时间</dt><dd>时间</dd><dt>首次信号</dt><dd>时间</dd><dt>最近信号</dt><dd>时间</dd><dt>Message-ID</dt><dd>标识</dd></dl>
```
详情加载/失败/404时使用同panel-head及 `<p class="muted">加载中…</p>`、`<p class="mot-error" role="alert">加载失败，请重新点击详情</p>` 或“该邮件记录不存在”。关详情清空目标ID并失效旧请求。
新增CSS逐字复制：
```css
.mot-detail {
    margin-top: 16px;
    padding: 16px;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: #f8fafc;
}
.mot-detail-grid {
    display: grid;
    grid-template-columns: 100px minmax(0, 1fr);
    gap: 8px 16px;
    margin: 0;
}
.mot-detail-grid dt {
    color: var(--text-secondary);
}
.mot-detail-grid dd {
    margin: 0;
    overflow-wrap: anywhere;
}
```

## 现状审计
### 存储与交互
- 前端不直写DB。settings API写batch_send_setting；records/detail读取mail_record与mail_open_tracking；完整写读清单在[审计](mail-open-tracking-audit.md) §2/3。
- 新状态只在state.monitoring.openTracking（settings/loading/saving/summary/rows/count/page/status/keyword/requestSeq/detailSeq/refreshedAt）；不持久化localStorage，不借旧rows容器存跟踪行。
- IP-1：管理员保存设置→数据库→刷新页面读回→03发送；IP-2：03成功记录/公网GET→01查询snapshot→前端指标/行；IP-3：共享时间/账号→新查询，同时保留旧tab查询；IP-4：切换/响应乱序→当前DOM。
### 前端样式盘点
- 可复用class、实值、原DOM和CSS完整原文见 audit §6 与 `mail-open-tracking-evidence/source-excerpts.txt`；这份基线是审计组成部分，不用截图或预览mock替代源码。
- primary #1e40af/hover #1e3a8a；text #1e293b/secondary #475569/muted #94a3b8；body13px/1.5；border rgba(15,23,42,.11)；布局沿现有24px内容边距、8/16px间隔。panel背景半透明rgba(255,255,255,.55)，本详情使用声明的不透明#f8fafc。(来源: K-panel-bg-token-is-translucent)
- index.html:254–274是唯一DOM插入区域；app.js:14176加载分支/14205渲染/14325绑定。只新增子标签，**无需侧栏viewMeta注册**。
- 当前11个已有版本化资源使用同一缓存键，精确测试字面量匹配0；回执cache-key.txt。开工需重查，不能靠此数量推断以后仍0。(来源: K-frontend-cache-key-triad)

## 实现方案
### T1 — DOM/CSS落地（I-2/5，S-1～3）
文件：index.html、styles.css。按契约新增按钮、root、旧表wrapper和动态模板；CSS全部mot作用域，禁止改全局类，无现有class规则变动故无需扩大其它页面使用点。
### T2 — 数据与交互（I-1～5，S-1～3）
文件：app.js。
- 初始化新state；loadMonitoringSubTab遇tracking后走新loader并return，不调用旧api(undefined)。新增tab切换显隐wrapper，并使旧请求结果只作用它启动时的tab；修改旧loader的结果提交保护，不重写其它tab功能。
- loadSettings、saveSettings、loadRecords、loadDetail职责分开；使用现有api、escapeHtml、formatPercent、monitoringWindowParams。账号过滤tracking也透传；参数使用URLSearchParams。
- 日期/范围/账号改变归零；tracking自己的status与keyword保留在state，Enter/查询触发；手动查询刷新设置与records，60秒旧timer不冒称tracking数据已刷新，另用motRefreshed标实际查询时间。
- 进入tracking每次重读settings；未配置显示“未配置公网跟踪地址，暂不能开启”；配置存在显示“公网地址已配置，连通性需实际验证”。PUT失败显示具体安全错误，checkbox恢复服务端旧值，重试按钮只重读设置。
- 01的状态标签、收件快照null与指标范围依契约；详情仅读元数据。日期/账号变化后的旧response、切tab、重复详情打开均用seq+上下文校验丢弃；无需引入全局请求管理框架。
### T3 — 缓存和验证（I-1～5，S-1～3）
文件：index.html、src/test/js/mailOpenTracking.test.js。
- 重新读取当前资源缓存键并反查src/test；全部已有版本化资源统一更新为本次唯一键。若执行前新出现硬编码测试，先修订文件清单再动，不能擅自超范围。(来源: K-frontend-cache-key-triad)
- 沿monitoringDateDefault.test.js:1–72的Node test DOM stub/函数抽取方式执行真实函数，mock HTTP延迟/失败；不靠检查静态字符串自证状态流。覆盖settings保存失败恢复、快速筛选乱序、切tab后响应、0分母、分页20、keyword编码、动态HTML转义。
- 样式可用diff/人工视检，不为每个CSS值制造脆弱测试。

## 变更文件清单
共4文件，一个子系统：邮件监控前端。

| # | 文件 |
|---|---|
| 1 | `src/main/resources/static/index.html` |
| 2 | `src/main/resources/static/app.js` |
| 3 | `src/main/resources/static/styles.css` |
| 4 | `src/test/js/mailOpenTracking.test.js` |

## 验收标准
- I-1：GET失败不能操作假开关；PUT失败值复原；缺配置不能开启，已开启但配置丢失可关闭；刷新后与后端一致。
- I-2：三个状态中文准确；无“未读”推断；network中无像素请求，详情无正文HTML；无收件快照不显示当前联系邮箱。
- I-3：改变日期/账号同步summary；状态/keyword不变summary；筛选分页归零；0分母为—，20条分页正确。
- I-4：延迟响应测试最新筛选生效、离开页无旧覆盖；details先A后B只显示B；失败有可见错误。
- I-5：主题/姓名含 `<img onerror=...>` 显示文本、不执行/请求图片；ID参数严格数值。
- S-1/S-2/S-3：新增CSS与各块逐字一致；DOM映射一致，无新增inline style/未声明class；原全局规则diff零变化。
- `node --test src/test/js/mailOpenTracking.test.js`；`node --test src/test/js/*.test.js`；`node --check src/main/resources/static/app.js`。随后JDK11运行集成的mvn test，不把未执行/失败算通过。(来源: K-js-tests-run-via-exec-plugin)

## 人工验收清单
### A-1: 开关读写
- 前置条件: 测试实例配置HTTPS回调，01～04均部署；登录。
- 操作步骤: 1. 邮件监控点打开跟踪。2. 打开开关。3. 刷新页面。4. 关闭并刷新。
- 预期结果: 首次无key为关闭；保存/刷新保持开启，再关闭/刷新保持关闭；保存期间控件禁用。
- 覆盖: O-1、I-1、IP-1、S-1
### A-2: 三状态与指标
- 前置条件: 测试数据含2封跟踪成功（1封有信号）和1封未跟踪成功，发送日/账号相同。
- 操作步骤: 1. 查对应日期账号。2. 筛已收到信号。3. 搜索不存在关键词。4. 换空日期。
- 预期结果: 指标2、1、50.0%；全部3条，筛后1条且指标不变；不存在关键词0条且指标仍2/1/50.0%；空日期0/0/—。
- 覆盖: O-2、I-2/I-3、IP-2/IP-3、S-2
### A-3: 详情与实际打开
- 前置条件: 给自己的测试邮箱发送一封非回复；该行初始暂无信号。
- 操作步骤: 1. 打开详情。2. 不打开邮件，刷新详情。3. 打开邮件并加载图片。4. 手动查询再开详情。
- 预期结果: 步骤1/2不产生信号；步骤4在图片确实请求后显示首次/最近时间；无正文、设备或打开次数。
- 覆盖: O-2、I-2/I-5、IP-2、S-3
### A-4: 乱序与错误
- 前置条件: 浏览器网络限速，可临时阻断settings或records测试API。
- 操作步骤: 1. 快速切日期与账号。2. 请求未结束切收信分类。3. 回到tracking阻断API。
- 预期结果: 显示最后选择对应结果；收信分类不被跟踪行覆盖；阻断后显示加载失败，可重试，不把失败显示成零信号。
- 覆盖: I-1/I-4、IP-4、S-1/S-2
### A-5: UI和旧页面回归
- 前置条件: 桌面浏览器宽1440与窄窗口375各一次；保存旧页面截图。
- 操作步骤: 1. 切全部旧监控tab。2. 查看打开跟踪及详情。3. 打开专家详情和原回复编辑器。
- 预期结果: 旧tab/总览/信誉仍可用；新按钮主色#1e40af，详情底色#f8fafc、圆角10px、8/16px间距；窄屏表格在table-wrap横向滚动；原专家与回复功能未改。
- 覆盖: N-1/N-2、I-5、IP-3、S-1/S-2/S-3
