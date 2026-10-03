# 手机核心流程适配执行记录

日期：2026-10-03。状态：实现完成、独立机器验证通过；真实设备与后台写操作待验收，未部署。

授权：用户“好 你开始开发吧”；补充要求“会议日历要显示zoomh会议链接”。原计划文件保持不变；计划身份见 [执行清单](mobile-core-evidence/manifest.json)。

工作树：`/Users/lukai/IdeaProjects/weibo-talent-introduction`；分支 `main`；HEAD `b6be5f4604867f78c26fe9b4b1a32847068ad21e`。未暂存、提交或推送；原有无关改动保留。

## 实现

| 阶段 | 结果 | 执行 / 独立复核 |
|---|---|---|
| 01 导航与专家 | 手机下拉导航；专家列表、详情互斥；返回恢复位置和焦点；异步旧响应不抢回详情；桌面分栏偏好保留 | mobile01 / verify01 |
| 02 邮箱与回复 | 手机列表、会话互斥；复用编辑器；返回保草稿、归属及阅读位置；防迟到响应和发送后旧草稿复活 | mobile02 / mobile01 |
| 03 日历与任务 | 手机默认排期列表；用户视图选择保留；真实会议链接可点击；任务历史七列局部横滑，长日志完整保留 | mobile03 / mobile02 |

产品改动限于 `app.js`、`mailbox-chat.js`、`styles.css`、`index.html`。未改服务端、接口、数据库、任务业务 JS 或原 `mailbox-chat.css`。11 个版本化资源统一为 `20261003-mobile-core-03`，顺序、数量不变。

会议列表与详情使用原 `event.meetingLink`；仅允许现有安全检查接受的 HTTP/HTTPS 链接；Zoom 域名显示“Zoom 会议链接”。列表链接与详情按钮为兄弟节点，无链接时不编造地址。

## 验证

| 阶段 | 指定测试 | 全量 JS | 语法 / diff | 独立结论 |
|---|---:|---:|---|---|
| 01（含竞态修复） | 77/77 | 1361/1361 | PASS | PASS |
| 02 | 217/217 | 1377/1377 | PASS | PASS |
| 03 / 最终集成代码 | 116/116 | 1385/1385 | PASS | PASS |

基线全量 1319/1319。最终全量零失败、零跳过。各阶段执行对应 `node --check`、计划指定 `node --test` 集及 `node --test src/test/js/*.test.js`；最终独立日志在 `/private/tmp/mobile-core-execution/03-independent-focused.log`、`03-independent-full.log`。各阶段完整报告、基线、差异和日志保存在 `/private/tmp/mobile-core-execution/`。

真实生产 HTML/CSS/JS 配合本地演示 API 验证，浏览器为 Codex 内置浏览器，非实体 iPhone：

- 360×800、390×844、430×932、760×852、761×852、768×1024、1024×768、1025×768、1440×900、852×393、393×852；核心页均无页面级横向溢出。
- 专家联系人及 ES 专家入口、返回焦点验证通过。邮箱 A/B 草稿隔离、返回重开、旋转、隐藏刷新后草稿和阅读位置保留；回复框字体 16px、最小高度 160px。
- 日历自动模式随断点切换；整组尺寸与旋转测试仅初次一次会议查询。手选月视图后，旋转、切月、刷新仍保留。新桌面实例默认月视图。
- 日历详情 393px 下宽 361px、输入字体 16px；Zoom 链接保留实际 href、`_blank`、`noopener`。
- 任务历史七列保留；393px 下滚动区宽 367px、内容宽 720px，键盘右移成功。30 条长日志完整保留，局部高度 426px、内容高度 5856px，长词不撑宽页面。

尺寸记录、请求计数及截图见 [证据目录清单](mobile-core-evidence/manifest.json)。[日历手机截图](mobile-core-evidence/03-calendar-393.jpg)、[详情截图](mobile-core-evidence/03-calendar-dialog-393.jpg)、[桌面截图](mobile-core-evidence/03-calendar-desktop-1440.jpg)。

## 执行差异

三个批准的 CSS 块逐字保留，原历史 CSS/日历骨架合同未改。以下为完成授权功能所需的小范围修正，已单独复核：

- 01：后加载的世界时钟 CSS 强制导航不换行，实测393px页面撑至538px；追加手机导航换行覆盖。
- 01：六个既有测试文件适配新增 helper / 导航调用及资源键合同：`authFlow`、`expertMaterialsShared`、`expertProfileAbsence`、`checkRepliesRelocation`、`contactTimingStyle`、`contactHeadLayout`。旧业务回归断言保留。
- 02：通用表单选择器将编辑器160px覆盖为44px；追加更具体的编辑器选择器恢复计划高度。
- 02：`mailboxTemplateReferenceStyle`、`meetingConfirmationStyle` 的历史全局 CSS 排除守卫仅剔除精确批准的02块；块外断言不变。
- 03：按用户会议链接补充要求，追加链接节点与窄范围样式。

测试桩调整是实现依赖修正，不属于用户另行批准的需求扩展；原计划未改写。

## 待人工验收

实体 iPhone Safari、Android Chrome 的软键盘、附件上传、真实邮件发送、会议保存及真实登录完整闭环尚未验收。本地 API 的写请求均禁用，演示数据不能证明实际后台写入。

本地真实前端预览：<http://127.0.0.1:18776/>；固定393×852视口，演示数据。仅本机可用，非线上部署。
