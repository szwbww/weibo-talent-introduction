---
id: K-introduction-compose-hardcode
domain: mail
created: 2026-07-05
last_used: 2026-07-08
hit_count: 5
source: create-p:batch-send-template-selector
---

`IntroductionMailComposer.compose()` 通过 `renderByCode("INTRODUCTION")` 硬编码引用 templateCode。所有涉及"介绍邮件使用哪个模板"的需求必须经过此入口。

**写路径**:
- `InitialOutreachService.sendInitialBatch()` — 旧同步路径
- `ManualInitialOutreachService.runScheduledBatch()` — 主批量发送路径

**Why:** 模板选择从内置改为可配置后，此处是唯一需要改动的 compose 入口。遗漏任何调用点会导致部分邮件仍用旧模板。

**How to apply:** 任何涉及介绍邮件内容变更的 plan，先 grep `IntroductionMailComposer` 所有调用点确认覆盖。
