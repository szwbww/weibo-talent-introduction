# 企业微信群通知 02：收发件箱控制开关

日期：2026-10-06；状态：待实施。前置：[后端子方案](wecom-inbound-notification-01-backend.md)验证通过。4 个文件，一个前端子系统。

## 需求描述

在「收发件箱 → 已激活账号收发邮件记录」右侧操作区增加「群消息」开关，紧跟「自动回复」。点击保存全局状态，显示已关闭/已开启、加载中、保存中和失败反馈；手机宽度可操作。

必须保持：现有按钮顺序与行为、聊天组件移入的刷新按钮、搜索/过滤/分页/专家会话、全局自动回复开关。群消息状态不随专家切换、过滤条件或用户浏览器变化。

不包含：Webhook 输入框、通知日志页面、群选择、多方案 UI、测试消息按钮、群消息内容编辑、浏览器推送。

## 关键不变量

### I-1：状态来源
- Rule：进入 mailbox、手动刷新、页面从后台重新可见时 GET `/api/expert-inbound-notifications/settings`；刷新时加载态并禁用。只有成功响应确认 enabled；不读写 localStorage 作为开关事实源，不在初次加载 PUT。GET 失败显示「群消息 加载失败」，点击只重试 GET。未配置且 disabled 时显示「群消息 未配置」并禁用；enabled=true/configured=false 仍允许关闭。
- Applies to：app.js 初始化、setView、刷新/visibilitychange、渲染。
- Violation consequence：假开关、界面与服务端不一致。
- 来源：original。

### I-2：明确保存与异常
- Rule：每次发 PUT `{enabled: !confirmedEnabled}`，不发“toggle”命令；保存中禁用，只处理最新请求代次。成功以响应为准；失败恢复此前确认值并 showStatus 错误，随后 GET 对账；若网络超时且对账失败显示「状态未知」并只允许重查，不能将旧值当最新真实状态。跨标签页不承诺实时广播，切回页面重查。
- Applies to：app.js 点击/加载状态。
- Violation consequence：重复请求、旧响应覆盖新状态、失败伪装成功。
- 来源：original。

### I-3：独立与最小变更
- Rule：开关不调用 auto-reply/bulk、不重载联系人、不改变搜索状态、不新增定时高频轮询；所有接口继续走 api() session 处理。只新增本功能事件，避免每次 setView 重复注册监听；必要时用请求序号隔离退出页面/登出前响应。
- Applies to：app.js/HTML。
- Violation consequence：误开自动回复、操作列表被重置、多次外部切换。
- 来源：original。

### I-4：资源与无障碍
- Rule：role=switch、aria-checked 始终反映已确认值、aria-label「企业微信群消息推送」；键盘 Enter/Space 可操作、保存中 disabled、结果使用现有 showStatus。全体版本化资源同值 bump，按实际旧键反查固定断言；新 DOM id 必须在 HTML 源文件断言存在。
- Applies to：HTML/JS/CSS/JS tests。
- Violation consequence：旧资源混用、键盘不可达、测试绿但页面无节点。
- 来源：K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs。

## 样式契约

### S-1：按钮与状态

复用 `.button` 的 `styles.css:802–836`（32px 高、12px 字、500 字重、7px 圆角、0 12px 内边距）。不修改共享 `.button`。新规则只作用本按钮；新增样式逐字如下：

```css
#mailboxGroupPushBtn { gap: 8px; min-width: 164px; white-space: nowrap; }
#mailboxGroupPushBtn[aria-checked="true"] { color: var(--primary); border-color: var(--primary); background-color: var(--primary-light); }
#mailboxGroupPushBtn .mailbox-push-track { position: relative; display: inline-block; width: 28px; height: 16px; border-radius: 8px; background: var(--border-strong); flex-shrink: 0; }
#mailboxGroupPushBtn .mailbox-push-track::after { content: ""; position: absolute; left: 2px; top: 2px; width: 12px; height: 12px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
#mailboxGroupPushBtn[aria-checked="true"] .mailbox-push-track { background: var(--primary); }
#mailboxGroupPushBtn[aria-checked="true"] .mailbox-push-track::after { transform: translateX(12px); }
#mailboxGroupPushBtn:disabled, #mailboxGroupPushBtn:disabled:hover, #mailboxGroupPushBtn:disabled:active { opacity: .55; cursor: not-allowed; transform: none; box-shadow: none; }
#mailboxGroupPushBtn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { #mailboxGroupPushBtn .mailbox-push-track::after { transition: none; } }
```

DOM：在 bulkAutoReplyBtn 后原样增加。开关文字「群消息」固定，status span 分别显示「加载中…」「已关闭」「已开启」「保存中…」「未配置」「加载失败」「状态未知」。

```html
<button class="button" id="mailboxGroupPushBtn" type="button"
        role="switch" aria-checked="false" aria-label="企业微信群消息推送" disabled>
    <span>群消息</span><span id="mailboxGroupPushState">加载中…</span>
    <span class="mailbox-push-track" aria-hidden="true"></span>
</button>
```

禁止 inline style、额外图标/设置弹窗、未声明 class、为此改全局颜色。hover/active 复用 .button，仅 disabled 覆盖防止失效按钮跳动。

### S-2：工具栏自适应

现有 `.panel-head`/`.panel-head-actions` 在 styles.css:962–985；不修改共享 class，仅增加定位到本面板的规则：

```css
#mailboxConversationPanel > .panel-head { flex-wrap: wrap; }
#mailboxConversationPanel > .panel-head > .panel-head-actions { flex-wrap: wrap; min-width: 0; max-width: 100%; }
@media (max-width: 760px) { #mailboxGroupPushBtn { min-height: 44px; height: auto; } }
```

受影响元素只限 `index.html` 中该面板标题/动作区（各一处），保留既有 DOM 层级与按钮。既有 mobile-core-02（styles.css:12543–12546）继续生效。1366/1920px 力求单行；宽度不足时按 DOM 顺序换行，禁止 horizontal scroll 或缩小文字凑单行。320/393/768/1024px 无溢出；44px 手机触摸目标。

## 现状审计

### 数据与交互

- 本步无数据库写路径。新增 PUT 由后端 01 Controller→Service→Repository 持久化；新增 GET 由同一配置记录消费。API 只返回状态，无 Webhook。
- app.js:5031 setView，mailbox 分支 :5094 loadMailbox + refreshAutoReplySummary；initBulkAutoReply:18012 只初始化既有开关，:19730 调用；新增 initMailboxGroupPush 独立初始化，不混入旧逻辑。
- 刷新按钮 `mailboxRefreshBtn` 原在 toolbar，mailbox-chat.js:870–887 将同一节点移入 panel-head-actions 首位，退出时还原。给此节点增加通知 GET 监听不应改移动逻辑或重复替换节点；无须改 mailbox-chat.js。
- Interaction X1：01 PUT/DB → 02 GET/界面状态；X2：组件移动刷新节点 → 02 状态 GET + 既有列表刷新；X3：切视图/后台恢复 → 旧请求隔离/重新读取。

### 前端样式盘点

- tokens 实值：亮色 primary #1e40af、primary-bright #3b82f6、primary-light rgba(30,64,175,.07)、text-main #1e293b、border rgba(15,23,42,.11)、border-strong #cbd5e1、radius-sm 7px；字体 Inter/system，按钮 12px/500；按钮 hover translateY(-1px)，active scale(.97)。暗色 primary #3b82f6、text-main #e2e8f0、border-strong #475569；新样式用变量继承。
- 可复用：.button :802，.button:hover :825，.button:active :832；.panel-head :962，.panel-head-actions :979。
- 改动前 DOM（index.html:794–804）：

```html
<section class="panel" id="mailboxConversationPanel">
    <div class="panel-head">
        <h2>已激活账号收发邮件记录</h2>
        <div class="panel-head-actions">
            <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
            <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
            <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
        </div>
    </div>
```

逐字基线 CSS：

```css
.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--line);
}
.panel-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
```

### 资源审计证据

`rg -n '\?v=' src/main/resources/static/index.html`：styles.css/expert-materials.css/mailbox-chat.css/meeting-confirmation.css/world-clock.css；trust-reply-workbench.js/expert-materials.js/meeting-confirmation.js/mailbox-chat.js/app.js/world-clock.js 均为 `20261006-discovery-review-merge`。`rg -n -F '20261006-discovery-review-merge' src/test` 无匹配（exit 1）。本次无需改其他测试的固定键；实施前再次反查，不能固化此无匹配结论。（来源：K-frontend-cache-key-triad）

## 实现方案

1. index.html 插入 S-1 DOM；styles.css 追加 S-1/S-2 全文规则；保持现有共享类不变。（I-3/I-4）
2. app.js 新增独立状态、初始化、GET、PUT、渲染函数；初始化一次，setView mailbox 并行 GET；给现有刷新节点增加一次监听；visibilitychange 在 mailbox 可见时刷新；退出/登出使在途请求序号失效。保存中禁用，失败对账，复用 showStatus。（I-1/I-2/I-3）
3. index.html 全部版本化链接统一 bump 为 `20261006-wecom-inbound-notification`；不新增脚本标签。新增 mailboxGroupPush.test.js 验证真实 DOM、接口、状态、失败、请求代次与缓存键一致。（I-4）

## 变更文件清单

| 路径 | 操作 |
|---|---|
| src/main/resources/static/index.html | 新开关、资源键 |
| src/main/resources/static/app.js | 控制逻辑 |
| src/main/resources/static/styles.css | 局部样式 |
| src/test/js/mailboxGroupPush.test.js | 新增验证 |

## 验收标准

- I-1：加载只 GET；显示真实值；未配置/失败可区分；无需 localStorage；页面再可见对账。
- I-2：开关请求明确 bool；双击一次 PUT；200 显示服务端值；失败恢复并重查；超时后 GET 失败显示未知；旧 GET 不覆盖新 PUT。
- I-3：测试 spy 确认只调用新 endpoint，不调用 auto-reply/bulk/loadContacts；刷新/会话组件挂载卸载不重复监听；退出清理旧响应。
- I-4：真实 index.html 包含两个 id；aria 属性、键盘行为、disabled、版本化资源全键一致；无凭据进入 bundle。
- S-1/S-2：CSS 与契约逐字匹配；DOM 顺序正确；无 inline style/未声明 class；320、393、768、1024、1366、1920px 浏览器检查，浅深色开关辨识清楚、文字无裁剪。
- 命令：`node --test src/test/js/mailboxGroupPush.test.js`、`node --test src/test/js/*.test.js`、`git diff --check`。浏览器实际点击开/关/键盘及失败分支不能由 DOM stub 代替。

## 人工验收清单

### A-1：位置、视觉、移动端
- 前置条件：后端 01 已部署、配置有效，浏览器打开收发件箱。
- 操作步骤：在 1366px 查看工具栏；改为 393px/320px；键盘 Tab 到开关、Space 切换。
- 预期结果：群消息在自动回复右侧，刷新仍最前；宽度不足有序换行，无横向溢出；手机高度≥44px；有焦点轮廓；已关闭灰轨、已开启蓝轨，文字同步。
- 覆盖：S-1/S-2、I-4。

### A-2：保存与跨页面
- 前置条件：两个标签页登录同一测试环境。
- 操作步骤：A 页开启；B 页切回前台；刷新 A；重启服务后刷新。
- 预期结果：均显示「群消息 已开启」；后台页回来 GET 最新值；保存期间禁用；自动回复开关值不变。
- 覆盖：I-1/I-2/I-3，X1/X3。

### A-3：网络失败与未配置
- 前置条件：测试环境可断网并可切换 Webhook 配置。
- 操作步骤：加载时断网；点击失败按钮重试；保存途中断网再恢复；移除配置后重新访问。
- 预期结果：分别「加载失败」并可重查、保存失败提示/状态未知并对账、未配置且关闭时禁用；不会把失败当开启成功；已开启但配置缺失时能关。
- 覆盖：I-1/I-2。

### A-4：既有操作回归
- 前置条件：已有多位专家及多页会话，聊天模式启用。
- 操作步骤：搜索、筛选、分页、选择专家；点击刷新；切到其他页面再回来；操作自动回复和测试批量发送流程；切群消息。
- 预期结果：列表操作原行为不变；刷新按钮只有一个且仍在首位；点击一次群消息只有一次 PUT；不会触发自动回复设置变化或丢失专家选择。
- 覆盖：I-3，X2/X3；全部 must-NOT-change。
