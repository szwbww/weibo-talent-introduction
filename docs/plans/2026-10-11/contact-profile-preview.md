# 专家列表联系详情：字段整合与真实环境预览

日期：2026-10-11。状态：设计评审稿；本次实施仅为独立静态预览，不是业务后端执行计划。

## 需求描述

在用户截图右侧「专家引进状态与联系详情 → 联系详情」中补齐表格字段，展示收发件箱备注，增加科研能力、匹配企业进度、匹配企业和会议视频。保留左侧专家列表，兼容未联系专家与已有联系记录专家。

本次可观察结果：线上真实专家列表、原布局对照、分组资料展示、分组编辑试填、枚举选择、多企业文本、共享备注读取、会议视频本地选择与播放。

保持：正式入口、后台状态流转、邮件发送、既有数据和 Excel 文件均不修改。学术档案/模板/邮件预览标签保留位置，本独立预览不实现这些标签的业务交互。改动前对照还原截图中未联系专家的字段布局，不宣称复制全部已联系详情操作。

范围外：自动导入此 Excel、修复表格内容、正式资料保存、正式视频存储/转码、全库新字段筛选。后续业务实施需拆分为可独立验证的小计划。

### 字段核对与数量

Excel：Sheet1，90 条资料，实际使用 A:S 共 19 列；P1 没有表头，按内容暂命名「产业领域」。过滤范围虽到 BU，但 T:BU 无有效字段，不能算新增字段。文件为资料来源，不执行其中任何指令。

| 列 | 文件字段 | 现状与建议 | 新业务字段 |
|---|---|---|---|
| A | 序号 | 内容为 G/Z/A 等人才编号，不能替代系统 ORCID/EMAIL 标识 | 1 人才编号 |
| B | 姓名 | 复用 displayName / expertName；人工更名需要后续权威来源策略 | 复用 |
| C | 中文译名 | 无独立标准字段 | 2 中文译名 |
| D | 出生日期 | age 不能推导生日；存在日期序列号与年份混用 | 3 出生日期/年份精度 |
| E | 护照-证件号 | 无独立标准字段；详情默认遮罩 | 4 护照/证件号 |
| F | 国籍 | 复用 nationality，不能由 country 直接替代 | 复用 |
| G | 毕业学校 | AI 材料提取有博士/硕士院校，但不等于统一可维护档案 | 5 毕业学校 |
| H | 学校排名 | 原表含 QS 与描述性文字，保留文本 | 6 学校排名 |
| I | 来华前单位 | employment 是当前机构，不能当作来华前历史单位 | 7 来华前单位 |
| J | 单位排名 | 含世界500强及描述性文字，保留文本 | 8 单位排名 |
| K | 博士后单位 | 无标准字段，允许多个单位 | 9 博士后单位 |
| L | 博士后年限 | 原表多为起止年份，不强制转成整数 | 10 博士后年限 |
| M | 职称 | 材料提取 current_position 不是此页标准人工字段 | 11 职称 |
| N | 行政职务、头衔 | 多行文本，与职称分开 | 12 行政职务/头衔 |
| O | 来源国家 | country 为机构/地区线索，来源国家需人工确认 | 13 来源国家 |
| P | 空表头 | 多数为产业及细分产业；暂名「产业领域」，不复用学科分类 | 14 产业领域 |
| Q | 技术专长 | 不等同 researchFields / 专业关键词 | 15 技术专长 |
| R | 关键字 | 复用 keyword | 复用 |
| S | QM/BS/BSH/HJ | 允许多值，样本还含工程师、RJ；不猜缩写含义 | 16 申报类别 |

新增人工字段：科研能力、匹配企业进度、匹配到的企业，共 3 项。

计数口径：19 个表格字段 = 3 项直接复用 + 16 项新增/明确语义；合并额外诉求后 **19 项待建设业务字段 + 备注复用 + 视频能力，共 21 项工作内容**。这是产品字段/能力数，不是数据库列数；生日精度、审计元数据、视频元数据可能需要多列。

### 页面设计

保留左右分栏及四个详情标签。联系详情内依次为：收发件箱备注、科研与企业匹配、身份基本信息、教育任职、产业技术、会议视频、原材料及联系历史。每组独立编辑，取消不写入；短字段三列、长文本整行；空值显示未填写。顶部锚点用于快速跳转，不另增顶层导航。

科研能力下拉：很强 / 强 / 中上 / 中等 / 一般 / 低；空值显示未评估。匹配进度：未匹配 / 匹配中 / 匹配上；匹配上必须填写企业。企业可多家，预览每行一家；正式版建议一对多关联，禁止用最后一次输入覆盖前一家。

## 关键不变量

### I-1：预览数据边界
- 规则：仅调用白名单同源 GET；没有 POST/PUT/PATCH/DELETE、发送、状态推进、材料获取任务。加载失败展示错误，不伪造线上数据。发布包不含专家、身份证件、备注或登录信息快照。
- 适用：read/load/select；影响：误写线上或泄露资料。

### I-2：身份与并发
- 规则：新资料试填按稳定 orcidId（包括 EMAIL-* 内部标识）隔离；备注读取按真实 contactId。切换专家后旧响应不得写回当前专家；列表也有独立请求序号。
- 适用：state.edits、state.videos、select、load；来源 K-expert-detail-two-panel-render-sites、K-bound-expert-cross-campaign-read。
- 正式版需同时接入 showExpertDetail 与 loadContactDetail。无 contactId 不能假造联系记录；同专家不同 campaign 的备注不能擅自合并。

### I-3：新增字段语义
以下每项均适用本页编辑/取消/保存试填及后续正式保存，违反会造成资料误填；试填值只进入内存，不修改 ES/MySQL。

| 编号 | 字段 | 规则 |
|---|---|---|
| I-3.1 | 人才编号 | 字符串，保留前缀和前导零；与系统标识分离 |
| I-3.2 | 中文译名 | 人工填写，不自动替换原文名 |
| I-3.3 | 出生信息 | 年份不伪造为 1 月 1 日；正式版校验真实日期及精度 |
| I-3.4 | 护照/证件号 | 字符串，详情遮罩、编辑可见；不上传表格实例数据 |
| I-3.5 | 毕业学校 | 允许多院校文本；不推断为博士院校 |
| I-3.6 | 学校排名 | 接受 QS 和描述性文字，不强转数字 |
| I-3.7 | 来华前单位 | 与当前 employment 独立，不默认复制 |
| I-3.8 | 单位排名 | 文本，不强转数字 |
| I-3.9 | 博士后单位 | 多单位信息不截断 |
| I-3.10 | 博士后年限 | 接受起止年份或时长，不把二者混同 |
| I-3.11 | 职称 | 与头衔、行政职务分离 |
| I-3.12 | 行政职务/头衔 | 多行文本，保留换行 |
| I-3.13 | 来源国家 | country 仅作参考，不默认当作国籍/来源国家 |
| I-3.14 | 产业领域 | P 列临时命名，不等同学术学科 |
| I-3.15 | 技术专长 | 独立于关键词，不覆盖研究方向 |
| I-3.16 | 申报类别 | 允许多值及工程师/RJ，不猜缩写、不限制为单选 |
| I-3.17 | 科研能力 | 仅用户给定六档；空值为未评估，不从 h-index 自动评定 |
| I-3.18 | 匹配企业进度 | 三档；未填写显示未匹配，不改邮件阶段状态 |
| I-3.19 | 匹配企业 | 可多家；匹配上至少一家；切到其它进度不隐式删除已填企业 |

### I-4：备注单一来源
- 规则：从 /api/mail/contact-notes/{contactId} 读取 expert_contact_note，正式版编辑复用同一 PUT，不另建副本。读取失败不能当作空备注覆盖。
- 适用：备注块、后续两个详情宿主；本预览试填明确标识未同步。
- 无联系记录：展示尚无收发件箱备注；此时本页可试填，正式版若要持久化需单独解决专家级备注与联系人备注归属。

### I-5：会议视频
- 规则：预览仅创建本地 Blob URL；支持多选、控件播放和移除当前试选；关闭页面释放 URL。浏览器不支持的编码不代表文件损坏。
- 正式版：独立会议视频分类，不计入简历/申报材料完成度；需鉴权播放、Range、容量限制、取消/重试/分片续传、孤儿文件清理；关联稳定专家身份，不能要求先发邮件才能存视频。
- 来源 K-expert-document-ownership-chain：已有 contact 附件必须通过 expert_document 和唯一 owner 完整校验。

## 样式契约

### S-1：原页面布局与既有组件
- 复用线上 styles.css；主色 #1e40af、正文 #1e293b、边框 rgba(15,23,42,.11)、小圆角 7px；元数据规则见 src/main/resources/static/styles.css:1652，头像/姓名规则见 :1605。
- 复用 .panel/.panel-head/.list-item/.expert-list-item/.expert-profile-header/.expert-avatar/.metadata-grid/.metadata-card/.button/.badge；不修改任何正式样式。
- DOM：`.cp-layout > .contacts-list-panel + .contact-detail-panel > #detail > .expert-profile-header + .cp-tabs + sections`；新字段 `.cp-section > .cp-section-head + dl.cp-fields > .cp-field > dt + dd`；表单 `dialog#editor > form > header + .cp-edit-grid + footer`。

### S-2：新增样式
- 本预览所有新 class 均为 cp-*；完整逐字样式附于本文末尾，唯一来源为 preview.css。所有新增 DOM 使用其声明 class 或 S-1 既有 class；禁止额外 inline style。
- 交互：焦点 2px #3b82f6；disabled opacity .5；对话框白色不透明，避免 --panel-bg 半透明透底。来源：现有样式实值及 K-expert-detail-two-panel-render-sites。

## 现状审计

### 专家档案与详情
- ExpertProfile.kt / ExpertIndexResponse 目前提供姓名、nationality、country、keyword、employment、age、degree、学术指标和分类；没有本次 16 个标准人工档案字段。
- /api/experts GET 从 ES 搜索，再以规范化 orcidId 匹配联系人；同一专家有多个联系人时选 updatedAt 最新者，不意味着全局唯一联系人。
- app.js:11243 的 showExpertDetail 和 :11794 的 loadContactDetail 分别渲染 contact panel；现状 screenshot 为前者，不能只修改后者。（来源 K-expert-detail-two-panel-render-sites）
- 本次对 ES/联系人表只有既有 GET，无新写路径。正式实施须重查该表全部整行 save 与列级更新，不能只数构造点；推荐将人工资料从自动更新范围分离。（来源 K-expert-contact-two-write-sites）

### 收发件箱备注
- schema：V150__create_expert_contact_note.sql，主键 expert_contact_id；note varchar(2000)，updated_by、updated_at；外键级联删除。
- 应用写路径：ExpertContactNoteController PUT → ExpertContactNoteService.save → UPSERT 或空文本 DELETE；父 contact 删除触发级联删除。该 service 不改 expert_contact。
- 读路径：同 controller GET → service.get；mailbox-chat.js:3599 使用其返回的 note/updatedBy/updatedAt。联系详情两宿主目前未调用此接口。
- 新预览仅复用 GET；在本页 Map 模拟编辑，刷新清空，不与正式收发件箱互写。

### 专家材料与会议视频
- 已有 ManualExpertMaterialUploadService：manual_expert_material_upload → mail_attachment → expert_document，手动上传上限 100 MiB。application.yml multipart 100MB/101MB。
- ExpertMaterialController GET /{contactId}/materials 是只读列表；预览复用该接口，不调用 transfers。
- ExpertDocumentBrowseService.isPreviewable 仅 PDF/image/text；现有手动上传并非视频专用播放/大文件链路。
- 本次不改以上存储，不审计为业务迁移计划。正式视频计划必须先审计附件三种 owner、删除/转移/下载/播放、无联系人专家归属及反向代理限额，不能只放宽 multipart 上限。

### 前端样式盘点与改动前基线
- 元数据原 DOM 为 `.metadata-grid > .metadata-card > .metadata-card-header + .metadata-card-value`；整行用 `.span-all`。
- 当前 contact panel 逐字片段：`<div class="detail-tab-panel" data-panel="contact" hidden>`；标题 `<span>国家 / 国籍</span>`、`<span>年龄 / 学历</span>`、`<span>当前单位 / 机构</span>`、`<span>专业关键词</span>`。
- 原 CSS 逐字：`.metadata-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; margin-bottom: 4px; }`（原文件以多行排版）；完整事实引用 styles.css:1652–1712。
- 线上发布前哈希：styles.css 92015667c82825c6084e3472a0e2b8ce6f5588d2d603b5249de8a6c62f730d52；app.js 27a93d26ec1f0f212ac26c494d7881b59eccf4f7fb3ae701a873c60edc06c60b；index.html ac489a9df99f85dea384bba4deb4f72037ddf8e02ab9ee5a07cca3239df26c76。

### 表格数据质量
- D 列含 Excel 日期序列号与四位年份，需保留精度。
- 第 80、83、84、90 行存在证件类型/号码/国籍等错位迹象；P 列部分行也混入有/无。无自动导入授权，本次只核对字段，不搬运或修复数据。
- 学校/单位排名含斜杠和文字，斜杠不能解释为数值零。

## 实现方案

1. 独立 HTML/CSS 建立截图位置对应的预览布局，复用线上样式，新增 cp-* 样式原样按 S-1/S-2；不加载 app.js。
2. preview.js 通过 I-1 白名单 GET 读取真实列表、当前联系人备注、前 10 份材料；分页 50 人，分页上限遵守现有 10000 条限制。遵守 I-2 隔离。
3. 按 I-3/I-4 提供分组内存编辑；按 I-5 选择视频本地播放。保存/取消、空值、错误可见。
4. 仅部署上述三份静态文件到 /talent/previews/contact-profile-20261011/，复核线上三份正式资源哈希不变；浏览器检查两个专家隔离、已有联系人备注、编辑校验、重载清空。

正式实施建议顺序（仅范围建议，不授权执行）：①人工资料身份与持久化小切片；②各字段顺序增量；③两种详情宿主与备注复用；④企业匹配；⑤会议视频存储与播放。每个后续执行计划仍遵守最多 10 文件、2 子系统、每共享存储最多 1 新字段，禁止把本清单直接当作一轮大改任务。

## 变更文件清单

| 文件 | 内容 |
|---|---|
| docs/mockups/contact-profile-preview/index.html | 独立预览结构 |
| docs/mockups/contact-profile-preview/preview.css | 仅预览的新样式 |
| docs/mockups/contact-profile-preview/preview.js | 白名单读取、内存交互 |
| docs/mockups/contact-profile-preview/README.md | 访问方式与验证事实 |
| docs/plans/2026-10-11/contact-profile-preview.md | 本评审稿 |

验证截图为衍生证据；知识条目仅更新本次阅读计数，不包含产品改动。正式业务源文件与迁移不在变更范围。

## 验收标准

- I-1：脚本语法检查；网络构造只有 read() 的固定 GET；没有保存/发送 API；部署正式文件 hash 不变。
- I-2：Jiayi Jin 试填，切到 Pierpaolo Mastrolia 为空，再切回恢复；异步读响应验证 seq；未联系专家完整展示新增组。
- I-3.1–I-3.16：基本信息/教育任职/产业技术表单字段逐项对应表格；日期精度与原始文本不擅自推断。取消不更新详情。
- I-3.17–I-3.19：下拉值逐字与需求一致；匹配上且企业为空拒绝保存；多行企业保留。
- I-4：有联系记录读线上备注；无记录显示尚无；错误不冒充无备注。
- I-5：本地文件仅 Blob URL；没有上传请求；支持移除、释放及切专家隔离；服务器上传/Range 未实施。
- S-1/S-2：复用 class 与 cp-* 样式对照；对话框不透底，详情独立滚动，短字段多列；新文件不引入 inline style。

## 人工验收清单

### A-1：区域准确与真实数据
- 前置：已登录正式系统。
- 操作：打开预览，查看默认筛选专家，点击 Jiayi Jin。
- 预期：左侧真实列表、右侧「专家引进状态与联系详情」；联系详情被选中；无联系人仍显示所有新增字段。
- 覆盖：I-1/I-2/S-1/S-2。

### A-2：字段完整与取消
- 操作：分别打开基本信息、教育任职、产业技术编辑；比对本文 19 列表；在中文译名试填后取消。
- 预期：19 个表格项都有位置；中文译名仍未填写；国家/年龄/当前单位作为参考，不填入生日/来源国家/来华前单位。
- 覆盖：I-3.1–I-3.16。

### A-3：科研与企业匹配
- 操作：编辑科研匹配；选择很强、匹配上，企业留空保存；再输入两行演示企业保存；切换另一专家再回来。
- 预期：第一次提示“选择‘匹配上’时，请填写匹配到的企业”；第二次显示很强、匹配上和两家企业；另一专家为未评估，回来保留试填。
- 覆盖：I-2/I-3.17–I-3.19。

### A-4：备注复用与正式数据回归
- 前置：有效专家中选择有 contactId 的专家。
- 操作：查看备注；在预览试填带“演示”的备注，刷新页面；对照正式收发件箱。
- 预期：读取同一 contactId 的正式备注；试填刷新消失，正式备注不变；没有邮件发送或联系状态推进。
- 覆盖：I-1/I-4。

### A-5：会议视频
- 操作：在会议视频处选择本地 MP4，播放，切换专家，切回后移除。
- 预期：本地预览可播放浏览器支持的编码，另一专家无此视频；移除仅移除当前页面文件项；服务器无上传。
- 覆盖：I-2/I-5。

### A-6：对照与刷新
- 操作：点击改动前布局、改版预览；刷新整个预览页，再打开正式系统。
- 预期：改动前显示截图原字段；改版显示新分组；试填清空；正式页面无新增内容。白色编辑弹层不透底，滚动不遮挡底部按钮。
- 覆盖：I-1/S-1/S-2。

## S-2 逐字 CSS

```css
.cp-page{overflow:hidden}.cp-top{height:72px;display:flex;align-items:center;gap:32px;padding:0 28px;background:rgba(255,255,255,.58);border-bottom:1px solid var(--border)}.cp-top strong{font-size:20px;white-space:nowrap}.cp-top small{display:block;font-size:12px;font-weight:400;color:var(--text-muted)}.cp-top nav{flex:1;color:#64748b}.cp-top nav b{color:var(--primary);background:white;border-radius:24px;padding:12px 18px}.cp-main{padding:22px 28px;height:calc(100vh - 72px);display:flex;flex-direction:column;gap:12px}.cp-title{display:flex;align-items:center;justify-content:space-between;gap:16px}.cp-title h1{font-size:23px}.cp-title p{color:var(--text-muted);margin-top:4px}.cp-preview-label{background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;color:#1e40af;padding:8px 12px;font-size:12px}.cp-toolbar{display:flex;align-items:center;gap:12px;padding:12px 16px;border:1px solid var(--border);border-radius:12px;background:var(--panel-bg)}.cp-toolbar label{display:flex;align-items:center;gap:8px;white-space:nowrap}.cp-toolbar select{width:135px}.cp-toolbar input{width:240px}.cp-switch{display:flex;gap:6px;margin-left:auto}.cp-layout{display:grid;grid-template-columns:minmax(280px,37%) minmax(0,1fr);gap:24px;min-height:0;flex:1}.cp-layout>.panel{min-height:0;display:flex;flex-direction:column;overflow:hidden}.cp-layout .panel-head{min-height:58px}.cp-layout .list{overflow:auto;flex:1;min-height:0}.cp-layout .list-item{width:100%;text-align:left;cursor:pointer}.cp-layout .expert-list-item{padding:14px 12px}.cp-layout .expert-title{font-size:14px}.cp-muted{font-size:11px;color:#64748b}.cp-pager{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:12px;border-top:1px solid var(--border)}.cp-detail{overflow:auto;min-height:0;padding:20px 22px 32px;scroll-behavior:smooth}.cp-detail .expert-profile-header{margin-bottom:16px}.cp-tabs{display:flex;gap:12px;border-bottom:1px solid var(--border);margin-bottom:16px}.cp-tabs span{padding:12px 15px;color:var(--text-muted);font-size:14px}.cp-tabs .active{color:var(--primary);font-weight:600;border-bottom:2px solid var(--primary)}.cp-header-tail{margin-left:auto;display:flex;gap:6px;align-items:center}.cp-section{border:1px solid var(--border);border-radius:10px;background:rgba(255,255,255,.43);padding:15px 16px;margin:0 0 12px}.cp-section-head{display:flex;align-items:center;gap:10px;margin-bottom:12px}.cp-section-head h3{font-size:14px;flex:1}.cp-section-head small{font-size:11px;font-weight:400;color:#64748b;margin-left:8px}.cp-note{background:#f3f6fd;border-color:#d5dff3}.cp-note p{white-space:pre-wrap;line-height:1.8;color:#334155;font-size:13px}.cp-note .cp-muted{display:block;margin-top:6px}.cp-fields{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px 20px}.cp-field{min-width:0}.cp-field dt{font-size:11px;color:#64748b;margin-bottom:5px}.cp-field dd{font-size:13px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.cp-field.cp-wide{grid-column:1/-1}.cp-empty{color:#94a3b8;font-weight:400}.cp-overview{display:grid;grid-template-columns:1fr 1fr 1.6fr;gap:10px}.cp-overview .cp-field{background:#f4f7fd;padding:10px 12px;border-radius:7px}.cp-overview dd{color:#1e40af;font-weight:600}.cp-fold{margin-top:12px;border-top:1px solid var(--border);padding-top:10px}.cp-fold summary{cursor:pointer;color:#64748b;font-size:12px}.cp-fold .metadata-grid{margin-top:12px}.cp-anchor{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px}.cp-anchor a{font-size:12px;color:#64748b;padding:5px 10px;border-radius:6px;text-decoration:none;border:1px solid var(--border)}.cp-anchor a:hover{color:#1e40af;background:#eff6ff}.cp-upload{border:1px dashed #b9c8e4;border-radius:8px;padding:18px;text-align:center;background:#f7f9fd}.cp-upload p{font-size:12px;color:#64748b;margin-top:7px}.cp-video{padding:12px 0;border-top:1px solid var(--border);display:flex;gap:10px;align-items:center;flex-wrap:wrap}.cp-video strong{flex:1;overflow-wrap:anywhere}.cp-video video{width:100%;max-height:300px;background:#0f172a;border-radius:8px}.cp-error{padding:10px 14px;background:#fff1f2;border:1px solid #fecdd3;color:#9f1239;border-radius:8px;font-size:12px}.cp-dialog{position:fixed;inset:0;margin:auto;width:min(760px,calc(100vw - 32px));max-height:88vh;border:1px solid #cbd5e1;border-radius:14px;background:#fff;color:#1e293b;padding:0;box-shadow:var(--shadow-xl)}.cp-dialog::backdrop{background:rgba(15,23,42,.35)}.cp-dialog header,.cp-dialog footer{display:flex;align-items:center;gap:10px;padding:16px 22px;background:#f8fafc}.cp-dialog header{border-bottom:1px solid var(--border)}.cp-dialog header h2{flex:1;font-size:17px}.cp-dialog footer{border-top:1px solid var(--border)}.cp-dialog footer span{flex:1;color:#64748b;font-size:12px}.cp-edit-grid{padding:22px;display:grid;grid-template-columns:1fr 1fr;gap:16px;max-height:62vh;overflow:auto}.cp-edit-grid label{display:flex;flex-direction:column;gap:6px;color:#475569;font-size:12px}.cp-edit-grid textarea{min-height:90px;resize:vertical}.cp-edit-grid .cp-wide{grid-column:1/-1}.cp-toast{position:fixed;bottom:26px;left:50%;transform:translateX(-50%);background:#1e293b;color:white;padding:12px 20px;border-radius:10px;z-index:9999}.cp-count{color:#64748b;font-size:11px}.cp-material{padding:10px 0;border-bottom:1px solid var(--border);font-size:12px}.cp-mode-note{color:#64748b;font-size:12px;margin-bottom:12px}.cp-page button:focus-visible,.cp-page a:focus-visible{outline:2px solid #3b82f6;outline-offset:3px}.cp-page button:disabled{opacity:.5;cursor:not-allowed}
.cp-top{height:58px}.cp-main{height:calc(100vh - 58px);padding-top:14px}.cp-toolbar label{flex-direction:row;margin:0}.cp-layout .panel-head{min-height:48px;padding:12px 16px}.cp-detail{padding:16px 22px 32px}.cp-detail .expert-profile-header{margin-bottom:8px}.cp-tabs{margin-bottom:10px}.cp-tabs span{padding:8px 15px}.cp-anchor{margin-bottom:10px}.cp-note{padding:10px 14px}.cp-note .cp-section-head{margin-bottom:5px}.cp-note .cp-muted{font-size:10px}.cp-section-head small{letter-spacing:0}.cp-detail .cp-note p{margin:0}.cp-overview .cp-field{padding:8px 10px}
@media(min-width:1800px){.cp-main{padding:24px 36px}.cp-detail{padding:24px 28px}.cp-fields{gap:18px 26px}.cp-section{padding:18px 20px}.cp-layout{gap:36px}}
@media(max-width:1100px){.cp-top nav{display:none}.cp-top #user{margin-left:auto}.cp-toolbar{flex-wrap:wrap}.cp-switch{margin-left:0}.cp-fields{grid-template-columns:repeat(2,minmax(0,1fr))}.cp-overview{grid-template-columns:1fr 1fr}.cp-overview .cp-field:last-child{grid-column:1/-1}.cp-layout{gap:12px;grid-template-columns:290px minmax(0,1fr)}.cp-main{padding:16px}.cp-title{align-items:flex-start}.cp-tabs{gap:0}.cp-tabs span{padding:10px}}
@media(max-width:720px){.cp-page{overflow:auto}.cp-main{height:auto}.cp-layout{display:flex;flex-direction:column}.cp-layout .contacts-list-panel{max-height:300px}.cp-detail{overflow:visible;padding:16px}.cp-preview-label{max-width:180px}.cp-fields,.cp-edit-grid{grid-template-columns:1fr}.cp-title h1{font-size:20px}.cp-toolbar input{width:100%}.cp-top{padding:0 16px}.cp-top strong{font-size:17px}}
```

