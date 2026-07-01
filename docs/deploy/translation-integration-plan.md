# 专家回信中文翻译 — 接入方案（含前端预览）

> 目标：把已部署的 LibreTranslate（`http://127.0.0.1:5000`，语言码 `en` / `zh-Hans`）接入 weibo-talent-introduction，将专家英文回信正文翻译为中文，供运营在后台查看。
> 原则：**不阻断现有邮件主流程**；翻译失败降级为空，不报错。本文件仅为方案，暂不改代码。

---

## 1. 总体设计

采用「**入库时预翻译 + 前端直接展示** 」为主，「**按需补翻译**」为辅：

- **预翻译**：邮件入站管线算出 `cleanedBody` 后，调一次 LibreTranslate，把中文结果写入新列 `mail_record.cleaned_body_zh`。前端读取即显示，无额外延迟、无重复翻译。
- **按需补翻译**：历史邮件 / 译文为空的记录，前端提供「翻译」按钮，调一个新接口实时翻译并回填。覆盖存量数据和翻译失败重试。

翻译对象只取**入站 INBOUND 邮件**的 `cleanedBody`（已清洗去签名/引用），出站邮件不翻译。

```
IMAP收信 → MailBodyCleaner.clean() → [新增] MailTranslationService.translate(en→zh-Hans)
                                          ↓ 失败则 null
                                   存 mail_record.cleaned_body_zh
                                          ↓
                              详情接口透出 translatedBody
                                          ↓
                              前端 renderMailItem 显示「中文译文」
```

---

## 2. 后端改造清单

### 2.1 配置（`config/` + `application.yml`）
新增 `@ConfigurationProperties` 类 `TranslationProperties`：

```yaml
talent-introduction:
  translation:
    enabled: true
    base-url: http://127.0.0.1:5000
    source: en          # 源语言；不确定可用 auto
    target: zh-Hans     # 注意：本版本是 zh-Hans，不是 zh
    timeout-ms: 5000
    max-chars: 5000     # 超长正文截断，保护翻译服务
    api-key:            # LibreTranslate 启用鉴权时填
```

默认可整体关闭（`enabled: false`），保持与现有 mail-queue / scheduling 一致的可选化风格。

### 2.2 新增 `MailTranslationService`（`mail/service`）
- 方法 `translate(text: String, source: String?=null, target: String?=null): String?`
- 用 `RestTemplate`（项目已有 ES 调用范式）POST `${base-url}/translate`，body：`{"q":..,"source":..,"target":..,"format":"text"}`，解析 `translatedText`。
- **健壮性**：`enabled=false` 或入参空白 → 返回 null；超长按 `max-chars` 截断；try-catch 所有异常，失败记 `log.warn` 返回 null，**绝不抛出**。
- 单测 mock RestTemplate：成功、超时、5xx、空文本四种路径。

### 2.3 数据库迁移 `V11__add_cleaned_body_zh.sql`
```sql
ALTER TABLE mail_record ADD COLUMN cleaned_body_zh TEXT NULL COMMENT '入站正文中文译文';
-- 可选：未匹配来信也展示译文
ALTER TABLE inbound_mail_processing ADD COLUMN cleaned_body_zh TEXT NULL COMMENT '清洗正文中文译文';
```
**新建迁移，勿改已应用的 V1..V10。**

### 2.4 领域类 `MailRecord`
加字段 `val cleanedBodyZh: String? = null`（immutable data class，跟现有风格一致）。

### 2.5 入站管线 `AutoMailReplyService`
在每处算出 `cleanedBody` 之后、保存 INBOUND `MailRecord` 之前，调用翻译并带入 `cleanedBodyZh`。共 3 处入站保存点：
- 行 ~114（auto-reply disabled 分支）
- 行 ~173（主分支）
- `saveMailRecord()`（行 ~628）
建议抽一个私有方法 `translateInbound(cleanedBody): String?` 复用。

### 2.6 接口 DTO
- `MailRecordResponse` 加 `val translatedBody: String? = null`，`MailRecord.toResponse()` 映射 `translatedBody = cleanedBodyZh`。
- 影响详情接口 `/api/expert-contacts/{id}`（`ExpertContactManagementController`），前端 `detail.mails` 即带译文。
- 同理可给收发件箱 `MailboxService` / 未匹配来信 `UnmatchedInboundMailController` 的 DTO 加该字段（按需）。

### 2.7 按需翻译接口（可选，覆盖存量）
`POST /api/mail/{mailRecordId}/translate` → 调 `MailTranslationService` 翻译该记录 `cleanedBody`，回写 `cleaned_body_zh` 并返回译文。前端「翻译」按钮调用它。

---

## 3. 前端预览设计（重点）

涉及文件：`src/main/resources/static/app.js`（`renderMailItem`，约 3756 行）、`styles.css`。

### 3.1 展示位置与交互
在专家详情面板的每条邮件卡片里，**仅对入站邮件**（`direction === "INBOUND"`）且存在英文正文时，在原「正文预览 / 查看完整正文」下方增加一块「中文译文」。

交互方案（默认折叠，点击展开）：
- 有译文（`translatedBody` 非空）→ 显示可折叠的 `<details>`「🌐 中文译文」，展开见译文全文。
- 无译文（历史数据 / 翻译失败）→ 显示一个「翻译为中文」按钮，点击调 2.7 接口，成功后原地渲染译文。

### 3.2 卡片布局（ASCII 预览）
```
┌─────────────────────────────────────────────┐
│ ✉ 收件 · 回复                       06-29 10:12 │
│ Re: Introduction                              │
│ I am very interested in this opportunity ...  │  ← 英文预览(原 mail-preview)
│ ▸ 查看完整正文                                  │  ← 原折叠(原文)
│ ───────────────────────────────────────────  │
│ 🌐 ▸ 中文译文                                   │  ← 新增：默认折叠
│     我对这个机会非常感兴趣……                      │  ← 展开后译文
└─────────────────────────────────────────────┘
```
无译文时最后一行替换为按钮：`[ 🌐 翻译为中文 ]`

### 3.3 `renderMailItem` 增量（示意，非最终代码）
在现有 `mail-body-detail` 之后插入：

```js
// 仅入站邮件显示译文区
const isInbound = mail.direction === "INBOUND";
const hasZh = mail.translatedBody && mail.translatedBody.trim().length > 0;
const translationBlock = !isInbound ? "" : (hasZh
    ? `<details class="mail-translation">
         <summary>🌐 中文译文</summary>
         <div class="pre translation-text">${escapeHtml(mail.translatedBody)}</div>
       </details>`
    : `<button class="btn-translate" data-mail-id="${mail.id}" onclick="translateMail(${mail.id}, this)">
         🌐 翻译为中文
       </button>`);
// ...在 return 的模板里，<details class="mail-body-detail">…</details> 之后加入 ${translationBlock}
```

按需翻译处理函数（新增）：

```js
async function translateMail(mailId, btn) {
    btn.disabled = true;
    btn.textContent = "翻译中…";
    try {
        const res = await api(`/api/mail/${mailId}/translate`, { method: "POST" });
        const text = res.translatedBody || "";
        const block = document.createElement("details");
        block.className = "mail-translation";
        block.open = true;
        block.innerHTML = `<summary>🌐 中文译文</summary>
            <div class="pre translation-text">${escapeHtml(text || "（翻译为空）")}</div>`;
        btn.replaceWith(block);
    } catch (e) {
        btn.disabled = false;
        btn.textContent = "翻译失败，重试";
    }
}
```

### 3.4 样式（`styles.css` 示意）
```css
.mail-translation { margin-top: 8px; border-top: 1px dashed var(--border); padding-top: 6px; }
.mail-translation > summary { cursor: pointer; color: var(--accent); font-size: 0.9em; }
.translation-text { color: var(--text); background: var(--surface); border-radius: 6px; padding: 8px; }
.btn-translate { margin-top: 8px; font-size: 0.85em; padding: 4px 10px; cursor: pointer;
  border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--accent); }
.btn-translate:disabled { opacity: .6; cursor: default; }
```

### 3.5 其他可加预览的页面（按需）
- **收发件箱**（mailbox 视图）：来信列表项同样可挂「中文译文」折叠。
- **未匹配来信** / **转人工详情**：运营最常看，建议优先在此展示译文，必要时把译文也并入 `handoff.note`。

---

## 4. 性能与稳定性

- 翻译是网络 IO（本机回环，通常几十~几百 ms）。预翻译放在入站管线内，单封一次；若 IMAP 批量拉取量大，可考虑异步/队列（项目已有 RabbitMQ 可选通道），但首版同步即可。
- LibreTranslate 单实例并发有限，`timeout-ms` 兜底，超时降级 null，不拖垮收信。
- 超长正文按 `max-chars` 截断翻译，避免极端耗时。

---

## 5. 测试与验收

- `MailTranslationServiceTest`：成功 / 超时 / 5xx / 空文本 / disabled 五条路径。
- `AutoMailReplyServiceTest`：入站记录落库带 `cleanedBodyZh`；**翻译抛异常时主流程（QA/人工分支）仍正常**。
- 接口测试：详情接口返回 `translatedBody`；`POST /api/mail/{id}/translate` 回写并返回。
- 前端自测：入站卡片显示译文折叠；无译文显示按钮，点击后原地渲染；出站卡片不显示译文区。

验收标准：一封英文回信入站后，运营在专家详情页能一键看到中文译文；翻译服务挂掉时邮件照常入库、页面不报错（按钮可手动重试）。

---

## 6. 落地顺序建议

1. 迁移 V11 + `MailRecord` 字段 + `TranslationProperties` + `MailTranslationService`（含单测）。
2. 入站管线接入预翻译。
3. DTO 透出 `translatedBody` + 按需翻译接口。
4. 前端 `renderMailItem` 译文区 + 样式。
5. 联调验收。

> 确认方案后，可走 create-p 出正式开发计划再实现。
