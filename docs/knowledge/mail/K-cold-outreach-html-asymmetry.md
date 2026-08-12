---
id: K-cold-outreach-html-asymmetry
domain: mail
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:unsubscribe-06-html-anchor-body
severity: P1
---

经验：两个冷外联模板的**外发形态不对称**，任何"给冷外联正文加能力"的需求都必须分别处理，不能按一条路径推另一条。

| 模板 | 组装点 | `ComposedMail.html` | `ComposedMail.text` |
|---|---|---|---|
| `INTRODUCTION` | `IntroductionMailComposer.compose()` `:36-41` | **false**（取默认值 `:73`） | **null** |
| `MATERIAL_REMINDER` | `ManualExpertMailService.composeComposeTemplate()` `:243-252` | **true** | `rendered.body` |

即 MATERIAL_REMINDER 早已走 `multipart/alternative`（`:241` 调 `plainTextToHtml`），INTRODUCTION 仍是单部件纯文本走 `SmtpMailDeliveryService.kt:60-62` 的 `setText`。

**把 INTRODUCTION 改成 `html = true` 的连带成本**：`mail.body` 会变成 HTML，而它被**原样写进 `mail_record.body`** 的落库点有 7 个，必须一次改全（改传 `mail.text ?: mail.body`）：

- `InitialOutreachService.kt:95`（recordSuccess）、`:105`（recordFailure）
- `ManualInitialOutreachService.kt:695`（recordSuccess）、`:709` / `:723` / `:747` / `:763`（四个 errorCategory 分支各一个 recordFailure）

漏改的失败形态：收发件箱与专家详情把 `<p>`/`<br>` 当文本转义后显示为源码（`.pre` + `escapeHtml`），审计 `bodyPreviewText` 同样被污染。

**另一个易漏点**：`SmtpMailDeliveryService.kt:50-51` 在 `mail.text` 为空时会回退 `htmlToPlainText(mail.body)`，而 `htmlToPlainText`（`MailContentService.kt:21`）的 `.replace(Regex("<[^>]+>"), "")` **只删标签不保留 href** —— 链接 URL 会在纯文本部件里彻底消失。所以 `html = true` 时必须显式给 `text`，不能依赖回退。

关联：[[K-plaintext-reply-client-reflow]]、[[K-compose-template-html-after-render]]、[[K-dual-outreach-paths]]（INTRODUCTION 的两条并行发送路径共用同一个 composer）、[[K-material-reminder-single-compose-seam]]。
