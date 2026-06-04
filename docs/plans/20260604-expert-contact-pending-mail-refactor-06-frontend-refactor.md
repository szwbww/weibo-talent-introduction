# Phase 6：专家联系页和待处理邮件页前端重构

> 目标：把 Phase 1-5 的后端能力接入页面，完成专家联系页和待处理邮件页交互重构。

## 1. 前置依赖

必须先完成：

- Phase 1：操作日志 API。
- Phase 2：专家状态/层级 API。
- Phase 4：资料文件 API。
- Phase 5：待处理邮件操作 API。

执行前检查：

```bash
sed -n '1,260p' src/main/resources/static/index.html
sed -n '1,260p' src/main/resources/static/app.js
sed -n '520,1220p' src/main/resources/static/app.js
sed -n '1340,1620p' src/main/resources/static/app.js
sed -n '1,260p' src/main/resources/static/styles.css
```

## 2. 全局常量

文件：

```text
src/main/resources/static/app.js
```

新增/调整：

```js
const operatorStatusLabels = {
    NOT_CONTACTED: "未联系",
    CONTACTED: "已联系",
    REPLIED: "已回复",
    MATERIALS_RECEIVED: "已回复材料",
    INVITED: "已邀约",
    COMPLETED: "已完成"
};

const operatorStatusOptions = [
    ["NOT_CONTACTED", "未联系"],
    ["CONTACTED", "已联系"],
    ["REPLIED", "已回复"],
    ["MATERIALS_RECEIVED", "已回复材料"],
    ["INVITED", "已邀约"],
    ["COMPLETED", "已完成"]
];

const indexLevelOptions = [
    ["RAW", "原始"],
    ["CANDIDATE", "筛选"],
    ["APPLICATION", "有效"]
];
```

保留已有 `conversationStatusLabels`，但专家联系页主状态展示优先用 `operatorStatusLabels`。

## 3. 专家联系页列表

### 3.1 保留三层

`#expertIndexLevel` 保持：

- `RAW` 原始
- `CANDIDATE` 筛选
- `APPLICATION` 有效

不要删除。

### 3.2 状态筛选改为 6 个运营状态

`#contactStatusFilter` 改为：

```html
<select id="contactStatusFilter">
  <option value="">全部状态</option>
  <option value="NOT_CONTACTED">未联系</option>
  <option value="CONTACTED">已联系</option>
  <option value="REPLIED">已回复</option>
  <option value="MATERIALS_RECEIVED">已回复材料</option>
  <option value="INVITED">已邀约</option>
  <option value="COMPLETED">已完成</option>
</select>
```

JS 请求参数从 `status` 改为 `operatorStatus`：

```js
const operatorStatus = $("#contactStatusFilter")?.value || "";
if (operatorStatus) params.set("operatorStatus", operatorStatus);
```

保留旧 `needsAttention` 筛选。

### 3.3 列表状态显示

列表中显示：

```js
const status = contact.operatorStatus
    ? operatorStatusLabels[contact.operatorStatus]
    : contact.contactId ? labelStatus(contact.contactStatus) : "未联系";
```

未建 contact 的 ES 专家仍显示「未联系」。

## 4. 专家详情头部操作条

当前 `loadContactDetail` 里 `#contactHeadActions` 有：

- 自动/人工切换按钮。
- 层级按钮组。
- 邮件发送下拉。

改为：

1. 自动/人工切换按钮保留。
2. 新增「专家状态」下拉。
3. 层级按钮组改成「专家层级」下拉。
4. 邮件发送保留。

建议 HTML：

```html
<select id="operatorStatusSelect" data-contact-id="...">
  ...
</select>
<select id="indexLevelSelect" data-contact-id="...">
  ...
</select>
<button class="button" data-action="toggle-reply-mode" ...>切换为人工回复</button>
```

状态变更：

```http
POST /api/expert-contacts/{id}/operator-status
```

层级变更：

```http
POST /api/expert-contacts/{id}/index-level
```

操作人第一版可以弹窗输入，或使用统一默认：

```js
const operatorName = window.localStorage.getItem("operatorName") || "console";
```

如果弹窗输入，保存到 localStorage，避免每次重复输入。

## 5. 专家详情资料文件区

在 `loadContactDetail(contactId)` 中并行加载：

```js
const [detail, options, documents, logs] = await Promise.all([
    api(`/api/expert-contacts/${contactId}`),
    loadMailSendOptions(),
    api(`/api/expert-contacts/${contactId}/documents`),
    api(`/api/operator-action-logs?expertContactId=${contactId}&pageSize=50&pageOffset=0`)
]);
```

新增渲染函数：

```js
function renderExpertDocuments(documents) { ... }
function renderOperatorLogs(logs) { ... }
```

文件区展示：

- 文件名。
- 文档类型。
- 状态。
- 大小。
- 上传时间。
- 下载按钮。
- 如果 `previewable=true`，显示在线浏览按钮。

在线浏览：

- 点击打开内嵌 modal 或新窗口。
- 第一版可以 `window.open(previewUrl, "_blank")`。
- 下载用普通链接。

## 6. 专家详情操作日志区

展示最近 50 条：

- 时间。
- 操作人。
- 操作类型中文摘要。
- 备注。
- before/after 可折叠显示。

不要把日志做成另一个复杂页面；先在详情底部展示即可。

## 7. 待处理邮件列表

保留现有列表，操作列改为：

- 「查看/处理」
- 如果已关联专家，保留「查看专家」
- 「标记已处理」

点击「查看/处理」必须展示邮件内容，而不是只展示绑定候选。

列表新增显示：

- 关联专家运营状态。
- 关联专家层级。

如果后端列表响应还没有这些字段，可以在详情中显示，列表先不加；但最终验收希望列表能扫到状态和层级。

## 8. 待处理邮件详情面板

改造 `showUnmatchedDetail(id)`。

必须包含以下区域：

### 8.1 邮件内容

- 发件邮箱。
- 主题。
- Message-ID。
- In-Reply-To。
- 收信时间。
- 原始正文。
- 清洗正文。

现有已有，保留并整理。

### 8.2 关联专家

未关联：

- 推荐候选联系人。
- 搜索并绑定。

已关联：

- 专家姓名、邮箱、ORCID。
- 当前运营状态。
- 当前层级。
- 跳转专家详情按钮。

### 8.3 手动变更专家状态

下拉使用 6 个状态。

调用：

```http
POST /api/mail/unmatched-inbound/{id}/operator-status
```

### 8.4 手动变更专家层级

下拉使用三层。

调用：

```http
POST /api/mail/unmatched-inbound/{id}/index-level
```

### 8.5 QA 邮件回复

加载：

```http
GET /api/expert-contacts/mail-send-options
```

只显示：

```js
options.filter(o => o.optionType === "QA")
```

发送：

```http
POST /api/mail/unmatched-inbound/{id}/qa-reply
```

发送按钮文案：`发送 QA 邮件`。

### 8.6 人工富文本回复

第一版轻量实现：

```html
<input id="manualReplySubject">
<div class="rich-toolbar">
  <button data-command="bold">B</button>
  <button data-command="italic">I</button>
  <button data-command="insertUnorderedList">列表</button>
  <button data-command="createLink">链接</button>
</div>
<div id="manualRichReplyEditor" contenteditable="true"></div>
<button data-action="send-manual-rich-reply">发送人工回复</button>
```

发送：

```http
POST /api/mail/unmatched-inbound/{id}/manual-rich-reply
```

请求：

```js
{
  senderAccountCode,
  subject,
  htmlBody: editor.innerHTML,
  textBody: editor.innerText,
  operatorName
}
```

### 8.7 标记已处理

保留按钮。

调用：

```http
POST /api/mail/unmatched-inbound/{id}/mark-resolved
```

处理成功后：

- 关闭或刷新详情。
- 调 `loadUnmatched()`。
- 该记录不再显示。

### 8.8 操作日志

显示当前待处理邮件的操作日志：

```http
GET /api/operator-action-logs?inboundProcessingId={id}&pageSize=50&pageOffset=0
```

## 9. 样式要求

文件：

```text
src/main/resources/static/styles.css
```

新增样式时遵循现有风格：

- 表单控件高度和现有 toolbar/select 保持一致。
- 详情面板不要出现文字溢出。
- 富文本编辑框最小高度 160px。
- 邮件正文 `.pre` 保持 `white-space: pre-wrap; word-break: break-word;`。
- 文件列表和日志列表在窄屏下纵向堆叠。

## 10. 事件绑定

现有事件委托：

- `#contactHeadActions`
- `#contactDetail`
- `#unmatchedDetailPanel`

新增 action 时复用 `handleContactAction` / `handleUnmatchedAction`，不要散落多个 `addEventListener`。

新增 action 建议：

- `change-operator-status`
- `change-index-level`
- `send-pending-qa-reply`
- `send-manual-rich-reply`
- `preview-document`
- `rich-command`

## 11. 测试和验证

前端语法：

```bash
node --check src/main/resources/static/app.js
```

后端回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

手动页面验收：

1. 专家联系页：
   - 原始/筛选/有效三层仍在。
   - 状态筛选只有 6 个状态。
   - 自动/人工切换按钮仍在。
   - 状态下拉能改状态。
   - 层级下拉能改层级。
   - 文件区能展示资料。
   - 日志区能展示操作。
2. 待处理邮件页：
   - 点击记录能看正文。
   - 已关联专家可以改状态和层级。
   - 可发送 QA 邮件。
   - 可发送富文本人工回复。
   - 标记已处理后列表移除。
   - 操作日志更新。

## 12. 禁止事项

- 不要把页面做成新的 landing page。
- 不要删除现有邮件时间线。
- 不要用 `prompt`/`confirm` 大量散落；如果已有 `openActionDialog`，优先复用。
- 不要把 Office 文件 iframe 预览，第一版只下载。
