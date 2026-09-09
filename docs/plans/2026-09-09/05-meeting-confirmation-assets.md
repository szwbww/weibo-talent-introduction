# 05 资源激活与整体浏览器验收

依赖01→02→03→04全部验证；1个静态资源子系统；9文件；新增存储字段0。

## 需求描述

R1：真实收发件箱加载已实现的会议组件/CSS，新入口可用。必须保留M1：既有7个资源注册顺序和其它视图加载；M2：全站样式及旧业务入口。范围外：本阶段业务代码/样式值改写、生产部署及真实专家邮件。

## 关键不变量

### Invariant I-1: 统一版本与顺序
- Rule：现有7个带?v资源保留，新增1CSS/1JS；9个统一20260909-meeting-confirmation；meeting JS在mailbox-chat之前，meeting CSS在mailbox-chat CSS之后；只有该阶段注册。
- Applies to：index与固定缓存测试
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：K-frontend-cache-key-triad

### Invariant I-2: 完整交付验证
- Rule：CSS和组件来自04，不注入local preview/mock数据；入口可点之前01..03 API已在同一验收环境可用。新组件404时不破坏旧会话，其他入口未改。
- Applies to：资源发布/浏览器检查
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：original

## 样式契约

### S-1 注册节点

复用index.html:11..13、2109..2112位置，不增布局节点/CSS/class/inline style。变更后的带版本注册全文：

```html
<link rel="stylesheet" href="styles.css?v=20260909-meeting-confirmation">
<link rel="stylesheet" href="expert-materials.css?v=20260909-meeting-confirmation">
<link rel="stylesheet" href="mailbox-chat.css?v=20260909-meeting-confirmation">
<link rel="stylesheet" href="meeting-confirmation.css?v=20260909-meeting-confirmation">
<script src="trust-reply-workbench.js?v=20260909-meeting-confirmation"></script>
<script src="expert-materials.js?v=20260909-meeting-confirmation"></script>
<script src="meeting-confirmation.js?v=20260909-meeting-confirmation"></script>
<script src="mailbox-chat.js?v=20260909-meeting-confirmation"></script>
<script src="app.js?v=20260909-meeting-confirmation"></script>
```

link仍在head、script仍在body末尾，保留task-modal-runtime.js原位置及其无版本现状，不移动到head。04 S-1..S-5为唯一UI样式合同，05不增样式。禁止项：新的class/inline style/替换其它html区域。此节仅资源节点变动映射到S-1。

## 现状审计

D5/IP-8及最新evidence/cache-key.txt、frontend-before.md为组成部分。当前版本已为20260909-mailbox-refinement，7个测试固定该值。最初20260907-material-chat是旧快照，不能当当前值；索引校验覆盖当前所有?v资源，并不只替换一个JS。模板/数据库所有业务写读由01..03负责，本阶段只读静态文件注册、浏览器消费，没有新的存储写路径。

## 实现方案

T1（I-1/I-2/S-1）：index只更换7处版本，加入上述2注册；用04现有全局导出挂载，网络面板9个资源200。若最新版本变化，先重查当前代码证据而非回滚其它任务资源。

T2（I-1/S-1）：白名单7个既有测试更换固定版本，凡断言数量7/具体资源数组的同步加入新两资源并改9；保留原相对顺序测试、原路径/功能断言，不能删除检查换成always true。

T3（I-1/I-2/S-1）：新增meetingConfirmationAssets.test.js检查9个带?v资产、CSS顺序、脚本顺序、无重复注册、组件存在且不含示例fetch。跑全JS套件与mvn test一次，执行04 A-1..A-8实际浏览器并记录截图/下载/MIME证据。模板关闭/网络错误用隔离环境；不发给真实专家。

## 变更文件清单

| # | 文件（仓库根目录相对路径） | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 新2资源/统一9版本 |
| 2 | `src/test/js/manualReplySubjectPrefill.test.js` | 同步固定缓存断言 |
| 3 | `src/test/js/ragKnowledgeBasePage.test.js` | 同步固定缓存断言 |
| 4 | `src/test/js/overlayAndDialogContrast.test.js` | 同步固定缓存断言 |
| 5 | `src/test/js/ragWorkbenchRender.test.js` | 同步固定缓存断言 |
| 6 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 同步固定缓存断言 |
| 7 | `src/test/js/checkRepliesRelocation.test.js` | 同步固定缓存断言 |
| 8 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 同步固定缓存断言 |
| 9 | `src/test/js/meetingConfirmationAssets.test.js` | 新增资源检查 |

## 验收标准

- I-1：9处值同为20260909-meeting-confirmation，7旧+2新，无重名资源；新组件在宿主之前、CSS在宿主之后；全JS tests通过。
- I-2：真实浏览器仅真实API，无本地preview注入；9资产HTTP200、控制台0新错误；01..04场景有真实证据。不把未执行Docker/日历客户端验证记通过。
- S-1：index diff只含规定注册，DOM/CSS逐字比对04合同；导航/资料/工作台基线不变。
- IP-8：无组件脚本时人工回复仍可用，加载后才有新按钮；同一页面不同时运行preview MeetingCore和生产组件。

## 人工验收清单

### A-1: 完整加载与整体回归
- 前置条件：隔离环境01..05全部构建；清浏览器缓存，登录测试账号。
- 操作步骤：1. 浏览器Network检查9个带版本资源。2. 执行04 A-1..A-8并保存1440×1000/390宽截图及实际ICS。3. 打开专家列表/邮件模板/工作台/材料入口。4. 临时阻止meeting-confirmation.js加载重开页面。
- 预期结果：9请求200同版本，正常时有新会议按钮；其它入口内容/样式不变；阻止组件时旧人工回复仍可操作；整体人工清单有逐条结果和真实下载文件。
- 覆盖：R1/M1/M2；I-1/I-2/S-1；IP-8
