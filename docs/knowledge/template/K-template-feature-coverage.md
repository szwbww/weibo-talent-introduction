---
id: K-template-feature-coverage
domain: template
created: 2026-07-06
last_used: 2026-07-06
hit_count: 1
source: fix-v:template-expert-variables-and-fallback:fix-1
severity: P1
---

经验：模板功能计划即使全量测试通过，也可能完全没有落到关键实现点；本次 `renderText()`、`IntroductionMailComposer.compose()`、模板编辑器提示栏和对应测试均仍是旧行为。

正确做法：验证模板计划必须逐项打开渲染引擎、INTRODUCTION 变量注入点、前端变量提示栏和测试文件，逐行对照计划不变量，不得只用 `mvn test` 通过作为完成依据。

反例：`MailComposeTemplateService.kt:341` 仍只做普通 `${key}` 替换；`IntroductionMailComposer.kt:14` 仍只传 5 个 sender 变量；`index.html:1400` 仍只展示 6 个变量。
