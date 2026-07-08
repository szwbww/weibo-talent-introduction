---
id: K-subject-variants-json-string-array
domain: template
created: 2026-07-08
last_used: 2026-07-08
hit_count: 2
source: fix-v:variant-pool-1-engine:fix-1
severity: P1
---
经验：用 Jackson 直接把 JSON 读成 `Array<String>` 不等于“严格字符串数组校验”；默认标量强转会让 `[1,true]` 变成 `["1","true"]`，非法配置可落库。
正确做法：保存配置前先用 tree/model 校验根节点是 array 且每个元素 `isTextual`，再做 trim、重复、占位符等业务校验；读路径容错可继续独立保留。
反例：`MailComposeTemplateService.kt:367` 使用 `objectMapper.readValue(subjectVariantsJson, Array<String>::class.java)`，实测 `ObjectMapper().readValue("[1,true]", String[].class)` 返回 `["1", "true"]`。
