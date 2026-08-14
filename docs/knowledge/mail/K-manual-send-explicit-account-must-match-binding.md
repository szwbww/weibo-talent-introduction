---
id: K-manual-send-explicit-account-must-match-binding
domain: mail
created: 2026-08-14
last_used: 2026-08-14
hit_count: 0
source: create-p:expert-detail-head
severity: P1
---

经验：人工发送接口的 `senderAccountCode` **不是"用哪个账号发"的入口**，而是一个一致性断言参数。
把前端下拉的当前值直接透传，对已绑定专家 **100% 抛异常**。

`ManualExpertMailService.resolveAccount()` `:159-177` 的三段逻辑（注释里标为 I-3 / I-1 / IP-1）：

```kotlin
val bound = contact.boundSenderAccountCode?.takeIf { it.isNotBlank() }
// I-3: 显式指定必须与绑定一致
if (requested != null && bound != null && requested != bound) throw IllegalArgumentException(...)
// I-1: 有绑定一律走绑定解析（含 enabled 门禁）—— requested 被忽略
if (bound != null) return senderAccountBindingService.resolveForSend(contact, manual = true)
// 无绑定兜底：显式 code 优先，否则选号；两者都补写绑定
```

因此 `requested` 只在**无绑定**时真正生效；有绑定时它要么等于 `bound`（多余），要么抛异常。
前端 `app.js` 的 `send-manual-mail` 分支一直传 `senderAccountCode: null`（`:8566`），这是**正确**的，不是偷懒。

**UI 层的连带约束**：任何把「发件账号选择器」和「发送按钮」放到同一视觉区域的布局，都必须补一道
「未保存闸门」——选择器值 ≠ 已保存绑定时禁用发送按钮。否则运营会自然地"选账号 → 点发送"，
而实际发出的是旧绑定账号，且**没有任何提示**，邮件发出去撤不回来。

替代方案「发送时自动 rebind」已被否决：`rebind()`（`SenderAccountBindingService.kt:50-70`）会写一条
`CHANGE_SENDER_ACCOUNT` 审计并把 `sender_account_changed` 置 true（`ExpertContactRepository.kt:79-88`），
污染"发件人被换过"这个给运营看的信号；且 `activeThreadHint()` `:133-139` 在会话进行中时有真实业务语义
（旧账号仍负责收信），不该做成发送按钮的隐式副作用。

关联：[[K-sender-account-enabled-scope]]、[[K-sender-account-selection-sites]]、[[K-operator-send-quota-paths]]
