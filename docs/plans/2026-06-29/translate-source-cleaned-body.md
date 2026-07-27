# 翻译源改为清洗后正文（展示全文，仅翻译回复）

> 用 create-p 生成。复验对象：本计划。

## 需求描述

- 可观察结果：专家详情面板「查看完整正文」点击「🌐 翻译为中文」时，只翻译专家本次回复（`cleanedBody`），不再把整封含引用的原文送去翻译。正文展示仍为全文不变。
- 背景根因：当前时间线 `renderMailItem`（app.js）翻译源用 `mail.body`（全文，含被引用的原始外联正文），长邮件接近 `TRANSLATION_MAX_CHARS=5000`，后端约需 9-10s，超过 `TRANSLATION_TIMEOUT_MS=5000` → 返回 `TRANSLATION_FAILED`，前端显示「翻译失败，重试」。
- 必须不变（NOT change）：
  - 正文展示内容（全文）不变，`.pre` 纯文本渲染契约不变（escapeHtml + white-space:pre-wrap）。
  - `onTranslateClick` 的展开/收起/缓存交互不变。
  - 后端 `/api/translate`、`MailTranslationService`、`TranslateRequest/Response` 不变。
  - 其它 `translatableBody` 调用点行为不变（收发件箱详情、未匹配来信、自动回复预览、邮件记录页）。
- 不在本次范围（Out of scope）：
  - 不调整 `TRANSLATION_TIMEOUT_MS` / `TRANSLATION_MAX_CHARS`（仅在「兜底建议」中记录，作为后续可选运维项，不在本计划文件清单内）。
  - 不抽取 K-mail-body-display-sites 建议的「共享正文渲染器」重构（独立技术债，单独立项）。
  - 不改 `MailBodyCleaner` 清洗算法。
  - 不为其它正文展示点改翻译源。

## 关键不变量

### Invariant I-1: 翻译源与展示文本可分离，默认相等
- Rule：`translatableBody(text, opts)` 的展示文本恒为 `text`；翻译源为 `opts.translateSrc`，**当且仅当** `opts.translateSrc != null` 时生效，否则回退为 `text`。即不传 `translateSrc` 的调用点，展示与翻译源都仍是 `text`，行为零变化。
- Applies to：`translatableBody`（app.js:~1039）唯一定义点；`data-translate-src` 编码值来源。
- Violation consequence：若展示文本被错误改成翻译源，则面板只显示清洗后正文，违反「展示全文」需求；若 `translateSrc` 默认不回退，其它 4 个调用点翻译会变空。
- 来源：original

### Invariant I-2: 翻译源选取——有清洗正文用清洗正文，否则回退全文
- Rule：时间线邮件的翻译源 = `mail.cleanedBody` 去空白后非空时取 `cleanedBody`，否则取 `mail.body || ""`。`cleanedBody` 仅 INBOUND 邮件有值，OUTBOUND / 历史旧数据为 null/空 → 必须回退全文。
- Applies to：`renderMailItem`（app.js:~3887）对 `translatableBody` 的调用；新增辅助 `pickTranslateSrc(mail)`。
- Violation consequence：不回退则 OUTBOUND / 旧数据翻译源为空，点击翻译得到空结果或报错。
- 来源：original（写路径已核实，见现状审计 K-来源标注）

### Invariant I-3: `data-translate-src` 编码契约不变
- Rule：翻译源仍经 `encodeTranslateSrc`（btoa(unescape(encodeURIComponent(...)))）编码写入 `data-translate-src`，`onTranslateClick` 仍用 `decodeTranslateSrc` 解码后 POST。编码/解码函数与调用方式不改。
- Applies to：`translatableBody`（写）、`onTranslateClick`（读，app.js:~1086）。
- Violation consequence：编码方式不一致会导致解码乱码 / 翻译内容错误。
- 来源：original

## 现状审计

### 前端翻译链（app.js）
- `translatableBody(text, opts={})`（:~1039）：唯一渲染翻译块的函数。当前 `display`/`encoded` 均来自同一个 `raw=text`。展示与翻译源未分离。
- `encodeTranslateSrc`/`decodeTranslateSrc`（:~1023 / :~1031）：base64 编解码，成对使用。
- `onTranslateClick(btn)`（:~1065）：读 `data-translate-src` → decode → POST `/api/translate` → 写 `.translation-text`。含展开/收起/已翻译缓存三态。**本计划不改**。
- `translatableBody` 全部调用点（write paths of 翻译块）：
  1. `renderMailItem` :~3887 `translatableBody(body)`，`body = mail.body || ""`（:~3855）——**本计划唯一修改点**（专家详情面板时间线，源 `detail.mails`）。
  2. :~4900 `translatableBody(body, { emptyLabel: "无正文" })`（收发件箱详情）——不改。
  3. :~5250 `translatableBody(preview.replyBody)`（自动回复预览，OUTBOUND 草稿）——不改。
  4. :~5477 `translatableBody(record.body)`（邮件记录页 原始正文）——不改。
  5. :~5483 `translatableBody(record.cleanedBody)`（邮件记录页 清洗后正文）——不改。
- 数据来源：`renderMailItem` 的 `mail` 来自 `detail.mails`（:~4108），即联系详情接口。

### 后端 DTO / 数据（已核实，无需改）
- `ExpertContactManagementController.MailRecordResponse`（:~368）含 `body` 与 `cleanedBody` 两字段；`MailRecord.toResponse()`（:~478）映射 `cleanedBody = cleanedBody`。→ 前端 `mail.cleanedBody` 已可用。(来源: 本次 grep 核实)
- `MailRecord.cleanedBody`（domain :21）可空。
- INBOUND 落库写 `cleanedBody = mailBodyCleaner.clean(received.body)`：`AutoMailReplyService` :77/:84、:113、:172/:185、:241、:274、:306、:330 等。→ 专家回复有清洗后正文。(来源: 本次 grep 核实)
- OUTBOUND 记录不写 `cleanedBody` → null。→ I-2 回退分支必需。

### Interaction points
- 写路径①（`renderMailItem` 渲染 `data-translate-src`）× 读路径（`onTranslateClick` POST 翻译）：本计划只改写入的「源文本」内容，编码/读取契约（I-3）不变 → 单一交互点，已被 I-1/I-3 覆盖。
- 后端写 `cleanedBody`（AutoMailReplyService）× 前端读 `mail.cleanedBody`（新增 `pickTranslateSrc`）：跨模块交互点，由 I-2 的回退规则兜底（INBOUND 有值 / 其余回退）。

### 知识库关联
- K-mail-body-display-sites（hit_count 已+1）：正文展示点全集与「勿给所有 `.pre` 盲挂」告警；本计划仅改时间线单点，符合。**注意：该条记录的行号（renderMailItem ~3796 等）已与现状漂移，本审计行号以本次 grep 为准；执行前再 grep `translatableBody(` 复核。**
- K-plaintext-reply-client-reflow：`.pre` 纯文本渲染契约，本计划不动展示，保持。

## 实现方案

纯前端，单文件 `src/main/resources/static/app.js`，3 处改动。

### 任务 T-1：`translatableBody` 支持可选翻译源（遵守 I-1、I-3）
在 `translatableBody`（:~1039）内，展示沿用 `text`，新增 `opts.translateSrc` 决定编码源：

```js
function translatableBody(text, opts = {}) {
    const raw = String(text ?? "");
    const display = !raw.trim() && opts.emptyLabel ? opts.emptyLabel : raw;
    const srcRaw = opts.translateSrc != null ? String(opts.translateSrc) : raw;
    const encoded = encodeTranslateSrc(srcRaw);
    return `
        <div class="translatable-body-block">
            <div class="pre translatable-body" data-translate-src="${encoded}">${escapeHtml(display)}</div>
            <button class="btn-translate" type="button">🌐 翻译为中文</button>
            <div class="translation-text pre" hidden></div>
        </div>
    `;
}
```
关键：`display` 不变（I-1）；`encodeTranslateSrc` 仍是唯一编码入口（I-3）；不传 `translateSrc` 时 `srcRaw===raw`，其它 4 个调用点零变化（I-1）。

### 任务 T-2：新增翻译源选取辅助（遵守 I-2）
在 `renderMailItem` 上方新增：

```js
function pickTranslateSrc(mail) {
    const c = mail.cleanedBody;
    return (c && c.trim()) ? c : (mail.body || "");
}
```

### 任务 T-3：时间线调用传入翻译源（遵守 I-1、I-2）
`renderMailItem` :~3887：

```js
${translatableBody(body, { translateSrc: pickTranslateSrc(mail) })}
```
`body`（展示，全文）不变；翻译源走 `pickTranslateSrc`。

## 变更文件清单

| 文件 | 改动 | 任务 |
| --- | --- | --- |
| src/main/resources/static/app.js | `translatableBody` 加 `opts.translateSrc`；新增 `pickTranslateSrc`；`renderMailItem` 调用传 `translateSrc` | T-1, T-2, T-3 |

文件数：1（≤10）。子系统数：1（前端静态资源）。新增共享存储字段：0。

## 验收标准

- I-1：
  - 不传 `translateSrc` 的 4 个调用点（4900/5250/5477/5483）渲染出的 `data-translate-src` 与改动前一致（可对同一输入对比 base64）；点击翻译行为不变。
  - 时间线 `<div class="translatable-body">` 内可见文本仍为全文（含被引用段落）。
- I-2：
  - INBOUND 专家回复：`data-translate-src` 解码后等于 `cleanedBody`（仅上半段回复，不含 `On ... wrote:` 引用块）；字符数显著下降（样例邮件由近 5000 降至约 130）。
  - OUTBOUND 邮件 / `cleanedBody` 为空的历史记录：`data-translate-src` 解码后等于全文 `body`，翻译不为空。
- I-3：
  - 点击翻译，POST `/api/translate` body 中的 `text` 等于解码后的翻译源；展开/收起/二次点击缓存三态正常。
- 集成场景（交互点）：
  - 用样例邮件（专家回复 + 引用原文）实测：翻译成功返回，不再 `TRANSLATION_FAILED`，译文仅含专家回复中文。
  - 浏览器无 JS 报错；其它正文展示点目视无回归。

## 兜底建议（不在本计划文件清单内，单独运维项）

仍建议将 `TRANSLATION_TIMEOUT_MS` 调到 `15000`，覆盖 OUTBOUND 长文或无 `cleanedBody` 的历史数据全文翻译场景。此为配置/运维变更，不属于本计划代码改动，避免扩大范围。
