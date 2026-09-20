# SharePoint 文件卡显示修复

## 需求描述

将 Outlook/OneDrive 在纯文本邮件中生成的 Microsoft 文件卡显示为可点击的 SharePoint 文件链接，并移除文件卡切断单词时残留的零宽字符。只影响入站邮件正文的显示，不改入站原文、清洗正文、邮件接收、材料登记或下载行为。

不得改变普通邮件文本、非邮件的翻译正文、翻译请求原文或现有邮件详情布局。超出范围：下载 SharePoint 文件、把 SharePoint 链接登记为附件、后端数据迁移。

## 关键不变量

### Invariant I-1: 仅识别受控文件卡
- Rule: 只将 Microsoft Fluent UI 文件图标卡中、HTTPS 且主机为 `*.sharepoint.com` 的 `<URL>` 渲染为链接；不匹配的文本按现有 `escapeHtml` 原样显示。
- Applies to: `app.js` 的文件卡识别、正文渲染。
- Violation consequence: 任意正文可能被错误 linkify 或引入 XSS。
- 来源: original

### Invariant I-2: 正文数据不变
- Rule: `body`、`cleanedBody` 和翻译 API 的输入保持原始字符串；只生成展示 HTML。匹配到的文件卡从正文展示文本中移除，链接以独立“附件链接”行显示；去除其余零宽空格使被切断单词重新连接。
- Applies to: `translatableBody` 与全部启用该选项的入站邮件展示点。
- Violation consequence: 影响审计、翻译或后端邮件处理语义。
- 来源: original

### Invariant I-3: 邮件全显示点一致
- Rule: 专家时间线、收发件箱详情、未匹配来信详情、AI 训练邮件详情及来信线程均启用相同的文件卡展示；非邮件调用不启用。
- Applies to: `app.js` 所有 mail-body `translatableBody` 调用点。
- Violation consequence: 同一邮件在不同页面显示不一致。
- 来源: K-mail-body-display-sites

## 样式契约

### S-1: 文件链接
- 复用：`.pre`（`src/main/resources/static/styles.css:1877`）提供正文盒、换行与滚动；`.btn-translate`（`:1896`）提供同页正文操作的主色交互基准。
- 新增：
```css
.mail-external-file-link {
    color: var(--primary);
    font-weight: 600;
    text-decoration: underline;
    text-underline-offset: 2px;
    overflow-wrap: anywhere;
}

.mail-external-file-link:hover {
    color: var(--primary-hover);
}
```
- DOM 结构：`<div class="pre translatable-body">…<div class="mail-external-file-links"><span>附件链接：</span><a class="mail-external-file-link" target="_blank" rel="noopener noreferrer">文件名</a></div></div>`。
- 禁止项：inline style；未声明的新 class；修改 `.pre` 或 `.btn-translate`。

## 现状审计

### 邮件正文展示（浏览器内存数据，无新增持久化）
- Schema/mapping: 无。服务端返回 `body`/`cleanedBody`；前端仅以 `escapeHtml` 插入 `.pre`。
- Write paths: 无；本计划不写数据库。
- Read paths:
  1. `app.js:renderMailItem` — 专家详情邮件时间线。
  2. `app.js:renderAiTrainingMailDetail` — AI 训练邮件详情。
  3. `app.js:loadMailboxDetail` — 收发件箱邮件详情。
  4. `app.js:renderUnmatchedInboundDetail` — 未匹配来信的原始/清洗正文。
  5. `app.js:renderInboundThread` — 来信线程。
- Interaction points: 所有读路径共享 `translatableBody`；该函数也用于非邮件内容，故功能必须 opt-in。

### 邮件接收
- Schema/mapping: `ImapMailReceiveService.walk` 对 `text/plain` 直接保存文本，对 `text/html` 以 `stripHtml` 得到 `ReceivedMail.body`；`MailBodyCleaner` 再生成 `cleanedBody`。
- Write paths: 本计划不改 `mail_record` 或 `inbound_mail_processing` 的既有写入。
- Read paths: `translatableBody` 的 `data-translate-src` 将保持当前的原始正文输入。
- Interaction points: Microsoft 文件卡已在入库正文中存在；前端展示层不得反写、不得改变翻译输入。

## 实现方案

### A. 安全展示转换（I-1, I-2, I-3, S-1）
- 修改 `src/main/resources/static/app.js`。
- 新增受控文件卡识别/展示辅助函数：仅识别 Fluent UI 图标卡 + `https://*.sharepoint.com/...`，逃逸文件名，拒绝其他 URL。
- `translatableBody` 增加默认关闭的 `externalFileLinks` 选项；启用时渲染独立链接行，原始 `data-translate-src` 不变。
- 为全部五个入站邮件显示点启用该选项；时间线短邮件含文件卡时仍可展开查看链接。非邮件调用保持默认值。

### B. 样式与测试（I-1, I-2, I-3, S-1）
- 修改 `src/main/resources/static/styles.css`，逐字加入 S-1 的规则。
- 新增 `src/test/js/sharepointFileCardDisplay.test.js`：验证有效文件卡、零宽字符拼接、链接安全属性、文件名转义、无效 URL 保持纯文本、邮件/非邮件 opt-in 边界与五个调用点。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/app.js` | 受控文件卡展示与邮件读路径启用 |
| `src/main/resources/static/styles.css` | S-1 文件链接样式 |
| `src/test/js/sharepointFileCardDisplay.test.js` | 前端显示与安全回归测试 |

## 验收标准

- I-1: 测试仅将合法 SharePoint 文件卡变为链接；`http`、伪造主机、用户名 URL 和普通文本保持转义文本。
- I-2: 测试断言传给翻译数据属性的字符串仍为原正文，展示文字为 `remaining documents`，且链接在独立行。
- I-3: 静态测试断言五个邮件调用点均传入 `externalFileLinks: true`，非邮件调用不传。
- S-1: 测试/grep 断言链接有声明的 class、`target="_blank"` 与 `rel="noopener noreferrer"`；CSS 规则与本计划逐字一致。
- 命令：`node --test src/test/js/sharepointFileCardDisplay.test.js`、`node --check src/main/resources/static/app.js`、`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DskipNodeTests=false`。

## 人工验收清单

### A-1: SharePoint 文件卡展示
- 前置条件: 收发件箱中存在含 `Publications 3.rar` SharePoint 文件卡的来信。
- 操作步骤: 打开专家详情的邮件时间线，再打开收发件箱中的同一来信。
- 预期结果: 正文显示完整 `remaining documents`；下方显示“附件链接：Publications 3.rar”；点击在新标签页打开 SharePoint。
- 覆盖: I-1, I-2, I-3, S-1

### A-2: 普通正文回归
- 前置条件: 存在不含 Microsoft 文件卡的来信及任意 AI 草稿。
- 操作步骤: 分别打开其正文与 AI 草稿。
- 预期结果: 普通来信文字与改动前一致；AI 草稿不出现“附件链接”行。
- 覆盖: I-1, I-3
