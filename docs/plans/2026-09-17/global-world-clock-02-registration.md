# 全局北京时间与时区对照：02 注册与缓存发布

日期：2026-09-17。状态：待用户批准执行。前置：[01 独立组件](global-world-clock-01-component.md) 已实现、机器验证通过。
主计划：[global-world-clock-master.md](global-world-clock-master.md)。

## 需求描述

把01的独立组件注册到全局页面：时钟位于「退出登录」左侧，页面共用，不换行，宽度不足退化成图标；删除顶栏「轮询日志」按钮。同步静态资源缓存键与既有测试，让旧浏览器缓存能够更新。

必须保持：九个data-view导航、用户名与退出登录DOM/业务处理、会议组件脚本相对依赖顺序、任务轮询与日志API、现有CSS/JS内容不变（新组件文件在01完成）。

不做：修改 app.js、styles.css、后端、数据库、日志函数/日志面板；不引入缓存版本生成工具、不放宽测试为“任意版本均通过”、不部署生产。新增显示行为全部由01实现，本计划只是注册。

## 关键不变量

### I-1：全局宿主位置
- Rule：index只保留原用户名和退出按钮；01脚本在runtime把时钟插入二者之间，panel直接挂在topnav，不能在邮箱/专家 `.view` 内再放一个入口。不新增静态时钟DOM，以免与01重复。
- Applies to：index注册、启动集成检查。
- Violation consequence：重复按钮或只有某业务页可见。
- 来源：01 I-1；E-3、`index.html:143-166`。

### I-7：仅移除轮询日志入口
- Rule：删除`#showPollLogBtn`整个button，保留`#logoutBtn`、`#pollLogPanel/#pollLogBody/#closePollLogPanelBtn`及其原handler/API。旧日志文本仍可存在于任务面板，验收不能错误要求全仓“轮询日志”零命中。
- Applies to：index编辑、源文本测试、回归。
- Violation consequence：删超范围、破坏任务日志或退出登录。
- 来源：K-ui-removal-retires-obsolete-contract-tests；E-4。

### I-8：11资源同键、依赖顺序固定
- Rule：11个带?v=本地CSS/JS引用统一为`20260917-global-world-clock`；5CSS+6JS；task-modal-runtime.js保持未版本化。CSS顺序：styles、expert-materials、mailbox-chat、meeting-confirmation、world-clock；JS顺序：trust-reply-workbench、expert-materials、meeting-confirmation、mailbox-chat、app、world-clock。既有9份固定值测试同步新键，涉及9项计数的断言改成11，并补新资源的名字、顺序和去重断言。
- Applies to：index引用与本文件列出的9份JS测试。
- Violation consequence：新增脚本未执行、api/filterZones尚未加载、缓存旧资源、全量测试阻塞发布。
- 来源：K-frontend-cache-key-triad；E-5/E-6。

### I-9：CSS/DOM契约继承
- Rule：组件逐字采用01 S-1～S-4，不重新设计。index变更只允许本节S-5/S-6，既有inline style不在本次清理范围，不新增inline style/onclick。
- Applies to：index及现有contract tests。
- Violation consequence：第二步擅改样式使批准预览失真，或两个CSS来源漂移。
- 来源：01 I-9。

其余时间/搜索/生命周期规则沿用01 I-2～I-6，不重复定义另一套状态或默认值。

## 样式契约

### S-5：index顶栏最终静态DOM

- 复用：`styles.css:160-172,226-258,5076-5083` 的topnav-side/user-info/nav-tab/logout-btn。作用域及完整新增样式在01 S-4，没有本步骤新CSS。
- 删除目标原button为`index.html:147-156`，原文连同SVG完整保存在审计附件E-4及源片段；只删该节点。
- `index.html`中topnav-side最终内容必须如下。01 boot在logout前动态插入其S-4 trigger，不能把这里改成另一个人工复制的时钟模板。

```html
        <div class="topnav-side">
            <div class="user-info">
                当前登录: <span id="currentUserDisplay">admin</span>
            </div>
            <button class="nav-tab logout-btn" id="logoutBtn">
                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                    <polyline points="16 17 21 12 16 7"/>
                    <line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
                <span>退出登录</span>
            </button>
        </div>
```

- 禁止：给时钟套nav-tab、重命名logout/currentUserDisplay、把组件放进某个view、修改原导航SVG或data-view顺序。

### S-6：资源注册完整目标

head 中原4个版本化link替换为以下5行，其余meta/字体link不动：

```html
    <link rel="stylesheet" href="styles.css?v=20260917-global-world-clock">
    <link rel="stylesheet" href="expert-materials.css?v=20260917-global-world-clock">
    <link rel="stylesheet" href="mailbox-chat.css?v=20260917-global-world-clock">
    <link rel="stylesheet" href="meeting-confirmation.css?v=20260917-global-world-clock">
    <link rel="stylesheet" href="world-clock.css?v=20260917-global-world-clock">
```

body 底部原脚本注册替换为以下7行；world-clock必须位于app.js后：

```html
<script src="task-modal-runtime.js"></script>
<script src="trust-reply-workbench.js?v=20260917-global-world-clock"></script>
<script src="expert-materials.js?v=20260917-global-world-clock"></script>
<script src="meeting-confirmation.js?v=20260917-global-world-clock"></script>
<script src="mailbox-chat.js?v=20260917-global-world-clock"></script>
<script src="app.js?v=20260917-global-world-clock"></script>
<script src="world-clock.js?v=20260917-global-world-clock"></script>
```

不存在新增全局class的自由度；新增CSS以01完整内容为准。原static目录里CSS数量不限，本契约计数是index中带版本的引用数。

## 现状审计

### 注册页文件：读取和写入

- 当前schema为普通HTML：`index.html:11-14`引用4CSS，末尾引用5个版本化JS与1个未版本化runtime。index的部署由项目构建打包静态资源；本计划不改变构建机制。
- 当前读取路径：浏览器解析link/script；下面9份测试读取index并约束键或资源列表。写入路径：本步骤人工编辑index；生产运行时01只增自己的trigger/panel和class，不改资源注册行。
- script依赖依据：普通`app.js:1540`定义api，bootstrap在17997执行；`meeting-confirmation.js:1452-1467`导出MailboxMeeting.filterZones。由后置script消费二者，不加另一条API封装。

### 缓存键反查回执（2026-09-17）

```text
$ rg -l '20260914-followup-email' src/test
src/test/js/ragWorkbenchRender.test.js
src/test/js/checkRepliesRelocation.test.js
src/test/js/trustReplyWorkbenchSharedMount.test.js
src/test/js/batchSendTaskConsoleVisualFix.test.js
src/test/js/meetingConfirmationAssets.test.js
src/test/js/mailboxChatStyle.test.js
src/test/js/overlayAndDialogContrast.test.js
src/test/js/ragKnowledgeBasePage.test.js
src/test/js/manualReplySubjectPrefill.test.js
```

数量9是这次代码快照，不作为以后固定事实。执行前再查，若新增命中则先修订范围，不隐式改第11个文件。

| 测试 | 证据位置 | 必改内容 |
|---|---|---|
| batchSendTaskConsoleVisualFix | 49-59 | 固定字面量、assets/ordered加入world-clock.css/.js |
| checkRepliesRelocation | 11,66-76 | KEY、keys.length 9→11、ordered |
| mailboxChatStyle | 95 | mailbox-chat.css固定版本正则，只替换本版本值 |
| manualReplySubjectPrefill | 13,90-99 | KEY、总数9→11、ordered |
| meetingConfirmationAssets | 21-58,76-90 | KEY、CSS_ORDER/JS_ORDER、3处数量、正则、标题注释、新增注册/移除入口断言 |
| overlayAndDialogContrast | 22,59-68 | KEY、总数9→11、ordered |
| ragKnowledgeBasePage | 333-350 | 文案、字面量、总数9→11、ordered |
| ragWorkbenchRender | 20,392-402 | KEY、总数9→11、ordered |
| trustReplyWorkbenchSharedMount | 22,175-190 | KEY、总数9→11、ordered、注释 |

`meetingConfirmationAssets`的数量位置30/43/49检查keys、refs、唯一name；其JS_ORDER原末项为app，目标增加world-clock到最后。不能只更新两个“熟悉的”测试；E-6记录其他文件也固定了9。

### 删除入口回执

```text
$ rg -n 'showPollLogBtn|showPollLog|pollLogPanel|closePollLogPanelBtn' src/main/resources/static/app.js src/main/resources/static/index.html src/test/js
src/main/resources/static/index.html:147:            <button class="nav-tab logout-btn" id="showPollLogBtn" onclick="showPollLog()">
src/main/resources/static/index.html:632:            <div id="pollLogPanel" class="panel" style="margin-top: 12px; margin-bottom: 12px; padding: 14px;" hidden>
src/main/resources/static/index.html:635:                    <button class="button small secondary" id="closePollLogPanelBtn">关闭</button>
src/main/resources/static/app.js:7332:async function showPollLog() {
src/main/resources/static/app.js:7380:    const panel = $("#pollLogPanel");
src/main/resources/static/app.js:14114:    $("#closePollLogPanelBtn")?.addEventListener("click", () => {
src/main/resources/static/app.js:14115:        $("#pollLogPanel").hidden = true;
```

本查询覆盖test/js未返回旧入口测试，故新增负断言即可；不要顺手删日志实现。`showPollLog`调用GET recent-polls，见`app.js:7332-7389`；该链不被时钟请求消费。

### 前端基线与交互点

原顶栏HTML和原CSS逐字基线位于01审计R-2及`global-world-clock-audit.txt`，S-5标明本步骤唯一DOM删除。运行结果中的新clock/panel由01模板控制。

| 编号 | 写入 → 读取 | 验证 |
|---|---|---|
| IP-5 | index脚本顺序 → app.api / MailboxMeeting.filterZones → WorldClock boot | 页面直接加载时钟可点，依赖未定义零新增错误 |
| IP-7 | index新资源键 → 浏览器缓存与9个测试 | 同键11引用，文件真实存在，旧缓存可更新 |
| IP-8 | 删除showPollLogBtn → 旧导航/日志/退出处理 | 退出节点保持，任务记录和日志实现未改 |

## 实现方案

### T1：注册并替换入口（I-1/I-7/I-8/I-9；S-5/S-6）

文件：`src/main/resources/static/index.html`。

1. 前置确认01两个资源存在，且CSS逐字验证通过。
2. 应用S-5/S-6，删除日志入口，不写静态时钟DOM，不改既有脚本内容。
3. 相对资源路径保持不加`/`，兼容现有部署前缀。禁止把会话visualization路径写进href/src。

### T2：同步发布测试（I-8/I-9；S-6）

文件：变更清单中第2～10项（以下逐一列明，不包含隐式文件）。

- `batchSendTaskConsoleVisualFix.test.js`、`checkRepliesRelocation.test.js`、`mailboxChatStyle.test.js`、`manualReplySubjectPrefill.test.js`、`meetingConfirmationAssets.test.js`、`overlayAndDialogContrast.test.js`、`ragKnowledgeBasePage.test.js`、`ragWorkbenchRender.test.js`、`trustReplyWorkbenchSharedMount.test.js`：仅更新本次资源键、数量、顺序/集合和直接相关断言，不能改其原业务语义测试。
- `meetingConfirmationAssets.test.js`增加：新CSS/JS各存在且引用一次；CSS位于最后一个旧CSS后；JS位于app后；index没有showPollLogBtn，仍有logout/currentUserDisplay/pollLogPanel/closePollLogPanelBtn；确认原九个data-view集合和顺序没有变化。
- 新组件自身DOM/行为验证仍由01的`worldClock.test.js`承担，本步骤不修改它。

### T3：整体检查（I-1/I-7/I-8/I-9；S-1～S-6）

只运行下列检查和人工验收，不再扩大修改范围。若失败来自非本范围，记录现状与证据；若属于本功能，先按所属子计划修订/修复，不在注册步骤塞入新的产品行为。

## 变更文件清单

| # | 文件 | 范围 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | S-5/S-6 |
| 2 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存契约 |
| 3 | `src/test/js/checkRepliesRelocation.test.js` | 缓存契约 |
| 4 | `src/test/js/mailboxChatStyle.test.js` | 固定键正则 |
| 5 | `src/test/js/manualReplySubjectPrefill.test.js` | 缓存契约 |
| 6 | `src/test/js/meetingConfirmationAssets.test.js` | 资源注册与入口回归 |
| 7 | `src/test/js/overlayAndDialogContrast.test.js` | 缓存契约 |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | 缓存契约 |
| 9 | `src/test/js/ragWorkbenchRender.test.js` | 缓存契约 |
| 10 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 缓存契约 |

范围10文件、一个前端注册/验证子系统、无新持久化字段；不修改docs/releases.json或版本历史，不抵消当前工作区的其他改动。

## 验收标准

- I-1：新资源加载后触发按钮在当前用户与退出之间；初始关闭；所有业务view可用；不存在第二个静态时钟。
- I-7：`rg -n showPollLogBtn src/main/resources/static/index.html`无匹配，`logoutBtn/currentUserDisplay/pollLogPanel/closePollLogPanelBtn`仍存在；app.js与后端无本步骤diff。
- I-8：index中?v=恰11个且全部新键，逐个资源唯一；未版本runtime原位；`rg -l '20260917-global-world-clock' src/test`应命中清单中9份原测试（如其他测试故意另有新引用，需记录而非伪造“恰9”断言）。旧键在本9份测试和index中无剩余。不在历史计划中替换旧键。
- I-9/S-5/S-6：目标顶栏静态HTML、注册行逐字匹配；01 CSS/模板契约继续通过，新增组件不污染meeting CSS的旧字节测试。
- 01 I-2～I-6：所有计算与行为测试继续通过；执行01 A-1～A-8完整人工清单。

```bash
node --check src/main/resources/static/world-clock.js
node --test src/test/js/worldClock.test.js
node --test src/test/js/*.test.js
git diff --check
```

通过判据：退出码0，Node汇总fail0。不要把样式stub绿测等同于真实布局通过。真实浏览器需要按01 A-2/A-7截图保存证据，特别是1100px旧断点、窄屏横向导航、暗色浮层、时钟图标点击。开发验收仅使用测试环境，不发送真实邮件。

Maven全量不是本纯静态变更的必做门禁；若后续发布流程要求构建，使用项目JDK11和原构建命令，不为本功能修改pom。

## 人工验收清单

正式验收时导出同名前缀`-acceptance.md`；01 A-1～A-8是行为验收的权威清单，本节补注册/删除专项，不复制另一套可漂移的行为标准。

### A-9：退出旁入口与日志删除
- 前置条件：01+02已完成，登录后打开任一业务页。
- 操作步骤：1.查看顶栏右侧。2.点击时钟与关闭。3.进入任务记录。4.退出登录再登录。
- 预期结果：顺序为当前用户（窄屏可隐藏）→时钟→退出登录；没有轮询日志按钮；时钟展开完整当前北京时间；任务记录正常显示原结果，退出回登录页，重新登录恢复顶栏。
- 覆盖：I-1/I-7、S-5、IP-8；保留导航/认证/任务日志要求。

### A-10：旧缓存升级与资源顺序
- 前置条件：测试浏览器曾加载旧版本页面；上线本次测试包，DevTools Network启用Preserve log。
- 操作步骤：1.普通刷新。2.查看本页CSS/JS请求及Response源码。3.打开时钟并搜索London。4.在根路径及既有context-path环境重复。
- 预期结果：11个版本化URL使用20260917-global-world-clock（允许304但响应内容必须是对应新资源）；world-clock.js位于app之后；搜索伦敦返回结果；目录GET前缀一次；旧会议确认仍能打开，无新增未定义api/filterZones错误。
- 覆盖：I-8/I-9、S-6、IP-5/IP-7。

### A-11：样式接入未破坏旧组件
- 前置条件：可打开会议确认，浅色/暗色各一次。
- 操作步骤：1.先打开时钟，再点击原会议确认入口。2.分别观察旧会议表单、预览、关闭与日历下载控件。3.退出会议后再开时钟。
- 预期结果：时钟不覆盖模态会议窗口；旧会议布局/内容/控件保持原功能，时钟重新打开仍按01契约显示；没有将world-clock规则混入旧meeting CSS文件。
- 覆盖：I-9、S-1～S-6、IP-5；会议行为必须保持。
