---
id: K-reply-subject-two-algorithms
domain: mail
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:ui-tweaks-03-manual-reply-subject-prefill
severity: P2
---

经验：仓库里「回复邮件主题怎么算」有**两套语义不同**的实现，选错会改变线程主题或叠加前缀：

| 位置 | 规则 | 空值 | 长度 |
|---|---|---|---|
| `GroundedAutoReplyDecisionService.buildReplySubject`（`:93-103`） | 已以 `Re:` 开头（**忽略大小写**）则原样返回，否则 `"Re: " + trimmed` | `"Re:"` | 不截断 |
| `ManualExpertMailService.buildReplySubject`（`:275-279`） | 先 `stripReplyPrefixes` **反复剥掉**全部 `Re:`/`Fwd:` 前缀，再 `"Re: " + stripped` | 用调用方 `fallback` | `.take(255)` |

- 要「保持专家那条线程的主题不变」→ 用**第一套**。第二套会把 `Fwd:` 一并剥掉，等于改了线程主题。
- 另有 `AutoReplyPreviewService:105` / `AutoMailReplyService:996` 的
  `rendered.subject.ifBlank { "Re: ${…}".trim() }`，那是「模板算出主题优先、算不出才兜底」，
  不是第三套算法。

前端镜像这套规则（例如给人工回复框做主题预填）时，两个服务端硬约束必须一起镜像，
否则运营点发送才吃 400、且看不出错在预填：

1. `PendingMailOperationService.sendManualRichReply:159` —
   `require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }`
   → 前端预填必须 `.slice(0, 255)`。转发链长的来信是高发场景。
2. `MailVariableService.requireValidPlaceholders` →
   `MailPlaceholderService.PLACEHOLDER_REGEX = Regex("""\$\{([^}]*)\}""")`（`:99`）
   → 来信主题若含 `${…}` 会被拒。**不要**在前端静默改写来规避：改写会让运营看到的主题
   与实际外发的主题不一致，比报错严重。保持既有报错语义，让运营自己改。

服务端**从不**给人工回复主题补 `Re:` —— `sendManualRichReply` 只做 trim / 长度 / 占位符校验后
`renderForContact`，主题的唯一来源就是请求体。
