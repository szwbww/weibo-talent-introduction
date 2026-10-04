# 02 会议确认国家选择与 UTC 分组

状态：待评审；未实施。前置：[01 后端](meeting-country-timezone-01-backend.md) 完成并通过接口契约验证。所属：[主计划](meeting-country-timezone-master.md)。

## 需求描述

会议弹窗先选国家/地区，再按本场会议起止时间展示 `巴西（UTC-3）`。同国相同起止偏移只出现一个选项；只有一个有效选项时自动采用并隐藏额外时区选择；多个选项才选择。邮件与附件名来自01，不在浏览器重拼。

必须保留：
1. 提交真实 zoneId、起止当地时间及 generatedAt；编辑已有会议不静默换时区或清空日期/链接。
2. 旧会议正文的填入/更新/手动冲突处理、发送 stale/hash 检查、不同专家/账号隔离。
3. 预览下载与已发下载的原件/部署前缀，普通通用附件和材料统计。
4. 世界时钟的城市/IANA搜索、旧目录/`filterZones` 导出契约。
5. 现有布局、键盘操作、焦点/ESC、窄屏、disabled/hidden样式。

不做新页面/额外模态框/地图/国家定位服务/新CSS/数据库字段/自动修改联系人位置；不增加前端时区计算库，不在浏览器用 Intl 或固定UTC反推真实zone规则。

## 关键不变量

### F-1：国家＋本场会议偏移对分组
- Rule：仅会议组件创建 groups，key=`countryCode + startOffsetSeconds + endOffsetSeconds`，不跨国家/地区合并。成员必须有01国家元信息、`localTimeIssue=null`、非空endOffsetSeconds，且属于当前起止请求返回。常规显示 `巴西（UTC-3）`；两端偏移不同显示 `美国（UTC-5 → UTC-4）`，不能遮掉DST变化。
- Applies to：分组纯函数、renderZoneOptions、country summary。
- Violation consequence：不同国家混组，或跨DST的会议看似同一偏移。
- 来源：01 I-1/I-3；K-meeting-timezone-offset-is-noon-metadata。

### F-2：分组不覆盖真实身份
- Rule：group 保存 raw member IDs；当前selectedZone.id仍属于该group时保留原ID（含Brazil/East旧别名）。新选择优先使用该group中 `id==canonicalZoneId` 的可用项，按id字典序选首项；没有可用canonical项才用该group的可用raw ID字典序首项。决不能提交一个不在响应中的新ID。
- Rule：改期/改时后，原group的canonical成员若落入多个新的偏移对或出现合法性分歧，清空有效选择并提示重新选择。若成员仍落于单一有效group则保留原raw ID并更新label；当前raw ID若有issue不得自动换成其他成员。
- Applies to：selectZone/group selection、populateFormFromOptions、loadZones、buildMeetingInput。
- Violation consequence：冬天同偏移的Phoenix/Denver到夏天分开后静默选错。
- 来源：原 `buildMeetingInput` 仅提交zoneId；JDK探针证明两个地区冬同夏异。group成员列表是组件内存，不新增草稿/数据库持久字段。

### F-3：一个自动，多个显式选择
- Rule：国家列表保留可用国家/地区边界，选中一个国家后计算有效groups：1个自动采用，>1个显示选择器，0个提示检查时间。新切国家清空旧国家选择；已知旧saved/defaultZone仅在合法且属于对应group时恢复。已有明确选择可继续使用，多选并不要求每次重开重新点。
- Rule：起止未填完整时仍可选国家，显示“填写完整会议日期和时间后显示时区。”，不使用UTC中午结果代替会议选项，不产生可应用预览。
- Applies to：country change、populate、render、localIssues/canPreview/applyEnabled。
- Violation consequence：为多时区国家静默选了任意地区，或空表单显示误导偏移。
- 来源：已确认用户需求；当前选定默认仍由options.defaultZoneId=Asia/Shanghai提供，本轮不推断专家所在地。

### F-4：异步结果必须属于当前表单
- Rule：起止任一项变化，立即invalidate preview/blob/apply，再获取该起止的目录。请求用独立zonesSeq（不复用initial configSeq），结果同时核对open/disposed、seq及完整起止key；旧响应不得恢复选择/预览。关闭、切目标、dispose都使该seq失效。
- Rule：目录失败或缺01元信息时阻断预览/应用并提供重试；不能沿用旧noon目录或旧成功preview。只更换Zoom URL不重取目录。
- Applies to：loadConfig/loadZones、bindFormEvents、schedulePreview/runPreview、close/dispose/retry。
- Violation consequence：日期变了，旧UTC选项或旧附件仍能发出。
- 来源：现有configSeq与formRevision/previewSeq机制；原loadZones catch静默保留目录必须调整。

### F-5：所有对用户的会议时区标签一致
- Rule：country select只显示国家/地区名；额外选择器、选中值、summary/hint均用国家＋UTC，不输出城市/原始IANA/`East Time`。搜索可保留隐含别名便于旧习惯，但候选不展示它们。邮件/ICS/文件名直接消费01结果，不在JS自行编造。
- Applies to：zoneLabel、renderZoneOptions、selectZone、populate、country summary；renderPreviewPane保持服务端来源。
- Violation consequence：选项改了，输入框/hint仍显示城市或技术ID。
- 来源：`meeting-confirmation.js:403–497,948–955` 多个label/hint写点。

### F-6：兼容宿主与未改功能
- Rule：`MailboxMeeting.filterZones` 和host挂载/应用载荷不改；不向会议input新增countryCode/groupKey。saved raw zone认不出国家时保留其值供恢复，显示“该旧时区没有国家归属，请重新选择国家和时区”，不回退Shanghai、不允许应用。
- Rule：旧文件名SENT卡片继续显示原样；新预览/下载使用server filename。部署contextPath、普通附件/MIME/材料归属不变。
- Applies to：export API、onApply、savedMeeting、renderPreviewPane、宿主消费链。
- Violation consequence：世界时钟/草稿/其他附件遭到连带破坏。
- 来源：K-download-context-path-host-injection、K-calendar-not-expert-material-owner。

### F-7：样式/资源契约明确
- Rule：S-1～S-4；不新增CSS class、inline style；country select走既有样式。静态版本键统一更新，按当时实际键反查固定值测试，不照历史数量改。
- Applies to：meeting DOM/JS、index.html、对应样式/资源测试。
- Violation consequence：样式跑偏、隐藏无效，或客户端仍运行旧JS。
- 来源：K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs。

## 样式契约

所有CSS引用为本轮 `src/main/resources/static/meeting-confirmation.css`；完整原规则见 [code-baseline styles 段](meeting-country-timezone-evidence/code-baseline.md)。该文件不在变更清单，不应被修改。既有Style测试比较其与2026-09-09 target.css逐字相同，必须保留。

### S-1：国家控件和单选摘要
- 复用：`.meeting-form label`（20行）、`.meeting-form select`（21行）、`.meeting-form small`（24行）、hover/invalid/disabled（25–28行）、focus-visible（92行）。
- 实值：label 12px/18px、#52647e、gap6px、margin-bottom12px；select高36px、padding7px 10px、边框1px #d7e0ed、圆角7px、白底#fff、文字#334155；small 11px/16px #76859b；hover边框#93b4ec；disabled opacity .55/background#f1f5f9；focus outline2px #82a8e8/offset2px。
- 新增样式：无。目标DOM（插入在 `.meeting-form` 的loadStatus后、起止字段前）：

```html
<label for="meetingCountry">国家/地区
  <select id="meetingCountry" aria-describedby="meetingCountrySummary"></select>
  <small id="meetingCountrySummary" aria-live="polite">填写完整会议日期和时间后显示时区。</small>
</label>
```

- 动态option：初始提示“请选择国家/地区”，后为countryLabelZh；UTC特殊项显示“协调世界时”；按中文label排序，同名按code排序，选项value是code。
- 摘要：一个group时`土耳其（UTC+3）`；多个未选时“该国家/地区有多个时区，请选择。”；多个已选时为完整国家＋UTC；无有效group时“该时间没有可用时区，请调整日期或时间。”。已选raw项的gap/overlap错误优先原错误文案。
- 禁止：新class、inline style、替换全局select样式、自动改联系人国家。

### S-2：多时区选择器
- 复用：`.meeting-zone-field/control/options/empty`（76–91行），原button hover/active/selected、原role/ESC/focus逻辑；`[hidden]`（9行）为`display:none!important`。
- 实值：dropdown白底、border1px #cbd9ed、圆角8px、shadow0 10px 25px #223c6226、z-index10、max-height252px；候选padding9px 10px、12px/1.6；hover#eff5ff、active#dbeafe、selected#eaf1ff/#1e40af；不使用半透明panel变量。
- 位置：从原起止字段前移到两个`.meeting-fields`之后、meetingClock之前。目标DOM：

```html
<div id="meetingZoneField" class="meeting-zone-field" hidden>
  <label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>
  <div class="meeting-zone-control">
    <input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="选择 UTC 偏移">
    <button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>
  </div>
  <div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>
  <small id="meetingZoneHint">日期和时间均按所选时区填写。</small>
</div>
```

候选骨架（动态值必须escapeHtml；现有index作为DOM id后缀）：

```html
<button type="button" id="meeting-zone-option-0" role="option" aria-selected="false" data-zone="实际代表ID" tabindex="-1">
  <span><span data-role="zone-label">巴西</span></span>
  <span data-role="zone-offset">UTC-3</span>
</button>
```

- 保留原选中 ✓；删掉候选内展示IANA ID的small；不用`title`把城市重新暴露。
- 输入完整值使用`巴西（UTC-3）`；hint为`巴西（UTC-3） · 日期和时间均按此时区填写`，不输出raw ID。
- 一个group时整块hidden、关闭已开列表并清理aria-activedescendant；焦点若在被隐藏区域，移到country select，避免焦点失踪。
- 新增样式：无；不得为这个移动修改`.meeting-zone-options`公共规则。

### S-3：加载/错误/附件
- 复用：meetingLoadStatus/meetingError/meetingFilename/meetingFileMeta/meetingClock/下载link原DOM，不新增提示条；`.meeting-status`背景#f0f5ff、边框#dce7fa，`.meeting-error`背景#fff1f2、边框#fecdd3、文字#be123c（55–56行）。
- 新起止目录请求显示“正在更新会议时区…”；失败“会议时区加载失败，请重试”，现有retry按钮处理新的`zones`动作；不擦除用户日期/链接。
- 元信息缺失文案“会议时区配置版本不匹配，请刷新后重试”，阻断应用，不用旧城市选项冒充新功能。
- 文件名strong的样式/层级不变，只由server attachment.filename更新；错误/等待状态继续“日历附件待生成”。
- 禁止inline style、额外弹窗/动画/新下载协议。

### S-4：布局和发布
- `.meeting-grid`45%/55%；dialog宽min(1080px,100vw-40px)、圆角14px、#fff。800px媒体断点单列，420px控件缩距，沿原CSS101–103行。
- 保留原页面link/script顺序；只替换index中的版本字符串为`20261004-meeting-country-timezone`，版本化资源必须同键（当前11项），不增加script/link。执行时按实际旧键重新核对，避免覆盖别的任务新注册资源。
- 无新增CSS/DOM除S-1/S-2所列，其他元素仅text/hidden/disabled/ARIA动态变化。

## 现状审计

### meeting-confirmation.js 内存/草稿

- Schema：state `258–286` 有configSeq/previewSeq/options/zones/zoneDate/selectedZone/preview/formRevision/savedMeeting；不是DB或localStorage。buildMeetingInput `581–594` 发zoneId、当地起止、链接、generatedAt。
- 写路径：loadConfig `859–903` 写options/zones并populate；loadZones `833–856` 改zones并保留selectedZone；populate `907–955` 从saved或options恢复；selectZone `483–496` 改选中项；open `1286起` reset；close/dispose负责失效与blob释放。
- 读路径：renderZoneOptions/zoneLabel/hint（403–497）；zoneSelectedValue/localIssues（524–546）；buildMeetingInput/canPreview（581–600）；renderPreviewPane（747–782）与onSubmit（1148起）。
- 当前缺陷：改日期才loadZones，改开始/结束时间只formChanged；loadZones失败保留旧目录不阻断；applyEnabled `340–345` 只检查phase与成功preview，没有目录key门禁。
- 交互 Q1：国家/起止输入 → 异步目录 → 分组/selected raw → preview request → API结果 → apply；每段都要用当前表单key。
- 交互 Q2：宿主savedMeeting.input → populate → 保留原raw ID → 更新正文/附件；原宿主在mailbox-chat.js中处理手动正文冲突，不改其合同。
- 交互 Q3：preview attachment.filename → renderPreviewPane/create Blob/download；已发送filename由服务端timeline快照直接给宿主，不在本组件重新命名。

### 共用函数/接口

- 世界时钟复用`MailboxMeeting.filterZones`且请求原date-only API；不可修改导出函数以实现会议专用去重。完整调用点见 [grep回执](meeting-country-timezone-evidence/grep-receipts.md) 的time-zones/filterZones段。
- 新group helper可在同文件新增纯函数并导出供本组件测试；不得改变原filterZones的参数/返回/行为，不新增独立组件文件。

### 前端样式盘点及改前基线

原时区区域（`meeting-confirmation.js:1393–1401`）：

```html
<div class="meeting-zone-field">
<label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>
<div class="meeting-zone-control">
<input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="搜索国家、城市、时区或 UTC 偏移">
<button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>
</div>
<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>
<small id="meetingZoneHint">下方日期和时间均按所选时区填写。</small>
</div>
```

原规则摘录（其余完整规则见code-baseline styles）：

```css
.meeting-form label{display:flex;flex-direction:column;gap:6px;font-size:12px;line-height:18px;color:#52647e;margin-bottom:12px;font-weight:500;letter-spacing:0;text-transform:none;min-width:0}
.meeting-form input,.meeting-form select,.meeting-form textarea{min-width:0;width:100%;height:36px;min-height:36px;margin:0;padding:7px 10px;font:inherit;font-weight:400;color:#334155;border:1px solid #d7e0ed;border-radius:7px;background:#fff;box-shadow:none}
.meeting-zone-field{position:relative;margin-bottom:14px}
.meeting-zone-field>label{margin-bottom:6px}
.meeting-zone-options{position:absolute;top:64px;left:0;right:0;max-height:252px;overflow:auto;overscroll-behavior:contain;z-index:10;border:1px solid #cbd9ed;border-radius:8px;background:#fff;box-shadow:0 10px 25px #223c6226;padding:5px}
```

既有classes使用位置已通过 evidence `rg 'meeting-zone-field|meeting-zone-control|meeting-zone-options|meeting-form' src/main/resources/static ...` 留存；因规则不修改，不衍生近似style。新DOM在组件模板中生成，不在index里；存在性测试应查真实组件模板而非仅查stub。

### 资源/测试合同

当前index版本键`20261004-mailbox-suspension-followup`；grep精确反查src/test无匹配(exit=1)。当前11项资源逐行位置为index11–15、2352–2357，详见回执；因此本计划不根据旧知识扩大固定键测试修改清单。

meetingConfirmationStyle已有class白名单/关键id检查，并逐字比对CSS；meetingConfirmation.test、Integration、mailboxOutboundAttachments都mock时区接口且真实挂载会议DOM，必须一并更新country字段和选项操作。不能只改unit fixture而放任宿主测试留旧城市合同。

## 实现方案

### T-1 纯分组与国家选择（F-1/F-2/F-3/F-5；S-1/S-2）

文件：meeting-confirmation.js、meetingConfirmation.test.js。

1. 保持raw DTO列表；从国家元信息生成country列表；不得从labelZh拆“·”推国家。`countryCode=UTC`为明确特殊项；null国家的SystemV不放进新国家列表。
2. 新增会议专用分组helper，按F-1分组；每组保留raw成员及canonical成员集合/代表项。按起点offset降序、终点offset降序、代表ID字典序稳定排序；Brazil常规顺序-2/-3/-4/-5。同组有alias与标准ID只渲染一行。
3. state新增仅必要状态：selectedCountryCode、zoneGroups、selectedGroupMemberIds（canonical集合）、zonesSeq、zonesKey、zonesPending/zonesError；复用原selectedZone作为实际提交值，不引入额外“选定时区”副本。
4. 修改country/select/date渲染按F-3；保留旧raw ID用于恢复及错误说明，不将不可用旧值冒充valid selection。新切国家必须清掉原国selectedZone、group成员和成功preview。
5. canonical representative是本次有效组中的真实ID；不从国家自动定位专家城市。默认恢复仍由原options.defaultZoneId驱动，savedMeeting优先。

选择恢复顺序必须先判断原raw项无效（保留raw值、显示其issue且禁止应用），再判断原group分拆（selectedZone置null、清成员集合、要求重选），最后才应用“一个group自动选择”。原group分拆后本次结果不自动选回；用户明确选择后再存新成员集合，避免隐藏地覆盖提示。已有有效group的成员集合在请求pending期间保留作对照，不拿它当最新可发送配置。

### T-2 完整起止请求与失效（F-2/F-3/F-4/F-6；S-3）

文件：meeting-confirmation.js、meetingConfirmation.test.js、meetingConfirmationIntegration.test.js。

请求流程冻结：

| 状态/动作 | 行为 |
|---|---|
| 初次打开且无完整日期/时间 | loadConfig按原方式加载options和date-only目录（现在含国家字段）；可以选国家；不做会议分组/预览 |
| 重开有完整saved input | 恢复真实字段；再用实际start/end请求会议模式目录；结果到达之前不可应用 |
| date/start/endDate/end任一变化 | 立即撤销preview与blob、关闭候选；300ms防抖后请求date+完整startLocal+endLocal；不完整或本地end<=start则不请求，保留输入提示 |
| 只改Zoom URL | 重做原preview校验，不重新获取时区目录 |
| 当前目录返回 | 验seq/open/disposed/起止key，更新groups；执行F-2分拆检测；单选或已有合法显式选择后再schedulePreview |
| 目录失败 | zonesError=true，预览/应用/下载禁用，显示S-3重试；保留国家和日期，不使用旧offset |
| 点击重试 | 新增retryAction=`zones`，只重发当前起止目录，不重置表单/重新加载全部options |
| 关闭/切专家/重开/dispose | 使zonesSeq失效，清定时器；旧成功/失败都不得修改新窗口 |

起止key仅由两个完整当地字符串组成（目录返回所有国家）；country切换不必重复联网，但必须刷新groups/selection并invalidate preview。旧configSeq继续保护初始options，不拿它作为时刻请求seq。

`canPreview/localIssues/applyEnabled`增加条件：元信息已加载、国家已选、有效raw在当前group、zonesKey等于当前起止、非pending/error、选项无localTimeIssue；保留既有URL/时间/正文冲突检查。

要同步审计 `runPreview` 的成功和失败回调：除了seq，还核对捕获的formRevision/当前zonesKey，防旧失败覆盖新目录状态。不改preview request的字段形态。

### T-3 DOM与文本（F-3/F-5/F-7；S-1～S-3）

文件：meeting-confirmation.js、meetingConfirmationStyle.test.js。

逐字实现S-1/S-2骨架，按声明移动原时区块；更新zoneLabel、候选small删除、populate/select/load返回后的hint。country options通过escape/textContent写入。验证真实模板存在meetingCountry/meetingCountrySummary/meetingZoneField，并保留旧ARIA/link/附件节点。

一个group时隐藏时区块，不只隐藏dropdown；多group才显示。国家summary里始终可核对最终UTC；dates不完整时只显示填写提示。拒绝“表面只显示一项但后台仍取旧selectedZone”的实现。

### T-4 宿主与发布（F-4/F-6/F-7；S-3/S-4）

文件：meetingConfirmationIntegration.test.js、mailboxOutboundAttachments.test.js、index.html；涉及纯函数的unit fixture同时更新。

- mock日期模式要产生对应实际起止offset/endOffset/issue，不用固定noon值满足测试；新增Brazil别名分组/单时区/US拆分fixture。
- 新filename fixture按真实算法形状，明确说明是mock；真实摘要/文件名因果由01测试覆盖，不在JSmock里声称真实加密证明。
- 更新旧`Istanbul ·...`与rawID hint期望为国家＋UTC。保留world-clock复用filterZones的旧fixture/期望。
- 重跑填入/编辑/手工冲突/移除日历/普通附件共存/切专家/失败重试，验证host onApply payload不新增字段。
- 统一index版本键，运行Assets与共享挂载测试；执行前若新旧键或注册列表已变化，重查并调整计划证据，禁止抹掉其他任务修改。

## 变更文件清单

| # | 文件 | 修改 |
|---:|---|---|
| 1 | `src/main/resources/static/meeting-confirmation.js` | 会议专用国家分组、状态/请求门禁、DOM/文案 |
| 2 | `src/main/resources/static/index.html` | 已有版本化资源统一cache key |
| 3 | `src/test/js/meetingConfirmation.test.js` | 纯分组与组件状态/异步测试 |
| 4 | `src/test/js/meetingConfirmationIntegration.test.js` | 真实组件/宿主流程与字段fixture |
| 5 | `src/test/js/meetingConfirmationStyle.test.js` | 新DOM与国家/UTC显示契约 |
| 6 | `src/test/js/mailboxOutboundAttachments.test.js` | 协同会议挂载fixture/交互，附件共存回归 |

CSS/app.js/mailbox-chat.js/world-clock.js以及其状态结构不在修改清单。若按当前键反查新增了写死键的测试，基于实际命中先补计划清单，不能偷偷修改；仍遵守最多10文件。

## 验收标准

- **F-1**：Brazil 2026-10-07 09:00–09:30只出现-2/-3/-4/-5；Sao_Paulo和Brazil/East在同组；跨国同偏移不合并；两端offset不同时用pair区别；小数偏移保留分钟。
- **F-2**：旧Brazil/East重新打开/重预览的payload仍是Brazil/East；新的分组选择用有效canonical代表；Denver/Phoenix冬同组、夏拆分时清选择要求重选；统一偏移变化不重置日期/链接；rawgap不得换别的成员掩盖。
- **F-3**：土耳其一个group时自动有效且整块hidden；BR多group切入后必须选择；未填时间时不拿noon值当结果；0有效group阻断；已有合法saved/default仍可恢复。
- **F-4**：目录响应逆序、旧失败后于新成功、preview旧失败/成功、输入清空、关闭再开/切专家、目录失败重试；逐项断言apply/download不可因旧结果变enabled。未完成/不合法时间没有多余网络请求；改Zoom不发目录请求。
- **F-5**：可见选择值/候选/hint/summary无城市/IANA/East Time；请求仍含真实zoneId。server会议正文不被JS改写。
- **F-6**：filterZones旧测试/worldClock回归；宿主payload字段原样；新旧附件文件名各自保留，普通附件共存且材料统计不新增；/talent部署路径下载回归。
- **F-7/S-1～S-4**：模板源文本存在新ID、role关联正确、无新增class/inline style、CSS bytes与原target相同；index已有资源同一新键/顺序不变；800px/420px目测。

运行：

```bash
node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/meetingConfirmationStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/worldClock.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/mailboxCalendarIntegration.test.js
```

这些测试通过后做一次仓库正常构建（使用JDK11，`mvn package`，不跳过既有检查），发现无关既有失败必须区分基线，不把全仓修复塞入本计划。不以DOM stub通过代替真实模板/浏览器验收。

## 人工验收清单

### A-1：一个可用时区
- 前置条件：测试联系人/模板可用，两个子计划已部署到测试环境。
- 操作步骤：1. 打开会议确认；2. 选土耳其；3. 填2026-10-07 09:00–09:30与测试Zoom链接。
- 预期结果：国家select为土耳其，摘要`土耳其（UTC+3）`；额外时区选择整块隐藏；邮件含Turkey (UTC+3)，北京14:00–14:30；可填入回复。
- 覆盖：F-1/F-3/F-5；S-1/S-2；Q1。

### A-2：巴西多时区及重复项
- 前置条件：沿A-1保留日期/链接。
- 操作步骤：1. 国家切巴西；2. 打开时区列表；3. 选UTC-3。
- 预期结果：展示4个选项`巴西（UTC-2/-3/-4/-5）`，UTC-3只一项；选择前不能应用；选后北京20:00–20:30、邮件Brazil (UTC-3)；无圣保罗/IANA/East Time。
- 覆盖：F-1/F-3/F-5；S-2；Q1。

### A-3：空日期、分数偏移与跨日
- 前置条件：新开未填写日期的弹窗。
- 操作步骤：1. 选印度但不填时间；2. 填2026-10-07 23:45到10-08 00:15；3. 查看预览；4. 清空结束时间。
- 预期结果：第1步提示填写完整时间且不可应用；第3步India (UTC+5:30)、北京10-08 02:15–02:45，邮件包含两天日期；第4步立即撤销附件下载/应用。
- 覆盖：F-1/F-3/F-4；S-3；时间/跨日不变项。

### A-4：冬同夏异不静默选地区
- 前置条件：用测试草稿/API fixture创建America/Denver会议2026-01-15 09:00–09:30，重开后归入美国UTC-7组（含Phoenix）。
- 操作步骤：1. 将起止日期改为2026-07-15；2. 等目录刷新；3. 再选UTC-6或UTC-7。
- 预期结果：第2步提示“会议日期或时间变化后，原时区选项已分开，请重新选择。”并禁用应用；第3步明确重新选择后才恢复预览；日期/链接保留。
- 覆盖：F-2/F-4；Q1/Q2。

### A-5：DST无效与跨偏移
- 前置条件：测试草稿显式zoneId=America/New_York。
- 操作步骤：1. 改2026-03-08 02:30–03:30；2. 起点改01:30；3. 再试11-01 01:30–02:30。
- 预期结果：第1步提示当地时间不存在，不换成美国其他时区；第2步可看到美国UTC-5→UTC-4（若原组分裂则先重选此项）；第3步提示时间出现两次，不自动取一个偏移。
- 覆盖：F-1/F-2/F-4；原DST拒绝；Q1。

### A-6：慢网络与失败
- 前置条件：浏览器开发工具在测试环境限速/拦截time-zones请求，不拦截线上真实用户。
- 操作步骤：1. 连续改两个日期；2. 延迟旧响应使其最后到；3. 让最新请求失败；4. 点击重试；5. 请求未结束时关闭重开另一联系人。
- 预期结果：只显示最后日期结果；失败时旧preview/下载不可用且有重试；重试保留表单；另一联系人不出现上一人的状态或附件。
- 覆盖：F-4；S-3；Q1/Q2。

### A-7：已有别名草稿与正文编辑
- 前置条件：保存的会议input.zoneId=Brazil/East，会议正文已填入；另备手动改过正文的草稿。
- 操作步骤：1. 重开第一份，核对日期/链接并更新；2. 浏览器网络查看preview payload；3. 对第二份编辑会议。
- 预期结果：第一份显示巴西UTC-3且payload仍Brazil/East；第二份仍按原流程提示替换/冲突，不擅自覆盖手改正文。SystemV测试草稿则提示重选国家，不能默认变成中国。
- 覆盖：F-2/F-6；Q2；原草稿/正文边界。

### A-8：附件名与原件贯通
- 前置条件：01 A-4/A-5的受控邮箱与新旧存档。
- 操作步骤：1. 下载当前预览；2. 发送受控测试邮件；3. 从已发卡片下载；4. 下载旧姓名格式附件；5. 加普通附件再预览。
- 预期结果：新名是`meeting-2026-10-07-0900-[8位hex].ics`，收到/卡片/下载一致；旧文件原名原件保留；普通附件没有丢失，材料计数未因ICS改动增加。/talent部署前缀下载成功。
- 覆盖：F-5/F-6；S-3；Q3。

### A-9：样式与键盘
- 前置条件：桌面宽度1080以上与390px窄屏各一次。
- 操作步骤：1. Tab切国家/日期/时间；2. 多时区下拉用上下键/Enter选择、ESC关闭；3. 切单时区；4. 检查窄屏与disabled状态。
- 预期结果：select/input高36px、白底、7px圆角；dropdown白底而非透底；800px以下单列；country摘要11px；隐藏时区块不残留焦点；无水平溢出；ESC先关列表、再按原流程关闭弹窗。
- 覆盖：F-7；S-1～S-4；原交互/样式不变项。

### A-10：共用功能回归
- 前置条件：刷新新版本，测试环境有原独立排期及普通回复。
- 操作步骤：1. 打开世界时钟搜纽约/Europe/Istanbul；2. 查看专家位置目录；3. 查看原独立排期；4. 发送受控无会议普通回复。
- 预期结果：世界时钟仍显示城市/IANA候选，专家位置目录未去重，原排期时刻不变；普通回复不自动带日历附件。网络加载的既有CSS/JS版本键一致。
- 覆盖：F-6/F-7；S-4；不变项2/3/4。
