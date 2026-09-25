---
id: K-email-invalid-no-existing-seam
domain: campaign
created: 2026-09-24
last_used: 2026-09-24
hit_count: 1
source: create-p:bounce-dsn-classification-and-email-invalid-writeback
---

## 2026-09-24 校正：已有专用入口

原题名“no-existing-seam”描述的是历史缺口。当前 `ExpertOperatorStatusService.kt:78 markEmailInvalid(contact, reason)` 已实现专用入口，`:88` 保存EMAIL_INVALID，`:89`同步ES；应复用，不再重复新增。

`updateAutomatically` 面向正常枚举并对EMAIL_INVALID短路；`changeStatus` 属人工操作并产生人工状态变更审计。两者均不能替代自动无效邮箱入口。markEmailInvalid对已回复及更后状态有保护，具体以当前实现为准。

**专家标签“邮箱异常”与 operatorStatus.EMAIL_INVALID 是不同数据合同。** 用户只要求打标签时，不应顺带调用状态迁移；追加tags也不能被描述为修改了联系状态。现有批量SMTP永久失败另有直接copy分支，属于遗留行为，不借标签功能扩改。

证据命令：`rg -n 'EMAIL_INVALID|fun markEmailInvalid' src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt`，命中78/85/88/89/95；`ManualInitialOutreachService.kt:777`为遗留永久SMTP失败分支。
