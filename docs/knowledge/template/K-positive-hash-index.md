---
id: K-positive-hash-index
domain: template
created: 2026-07-06
last_used: 2026-07-06
hit_count: 1
source: fix-v:mail-personalization-anti-spam:fix-1
severity: P1
---
经验：用 `abs(hashCode()) % size` 做数组下标时，`Int.MIN_VALUE` 会保持负数，导致极少数真实 key 触发负下标。
正确做法：把 hash 映射到非负下标时使用 `Math.floorMod(hash, size)`，或在同一 helper 内统一处理最小值边界。
反例：`IntroductionMailComposer.kt:29` 和 `MailComposeTemplateService.kt:335,341` 对 `hashCode()`/seed 使用 `kotlin.math.abs`，`"polygenelubricants".hashCode()` 为 `-2147483648`，`abs` 后仍为负数。
