---
id: K-prompt-config-effective-default
domain: qa
created: 2026-07-02
last_used: 2026-07-02
hit_count: 3
source: create-p:ai-training-redesign
---
经验：`AiPromptConfig` 表只存自定义覆盖值，`freeFormSystemPrompt` 为空时实际生效的是 `AiReplyDraftService.buildFreeFormSystemPrompt()` 中硬编码的默认提示词。前端如需展示「当前生效」值，必须从后端获取有效值（而非前端硬编码默认值），否则前后端不一致。
正确做法：后端提供 `getEffectiveDto()` 方法统一返回有效值 + `isCustom` 标志；默认提示词提取为共享常量或 Service 方法，避免 `AiPromptConfigService` 与 `AiReplyDraftService` 循环依赖。
