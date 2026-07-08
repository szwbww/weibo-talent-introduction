---
id: K-manual-compose-template-option-type-gate
domain: mail
created: 2026-07-04
last_used: 2026-07-08
hit_count: 2
source: fix-v:mail-compose-template:fix-1
severity: P1
---
经验：把某类手动发送选项从前端下拉和 `listSendOptions()` 移除后，后端 `sendManualMail()` 的 `optionType` 分发也必须同步收口；否则调用方可直接 POST 旧 `optionType` 绕过 UI，继续发送已下线的裸选项。
正确做法：手动发送入口的允许类型以服务端 `compose()`/enum/validation 为最终闸门；前端选项列表只是展示层，不能作为安全或业务约束来源。
反例：`ManualExpertMailService.kt:159-164` 仍接受 `ManualMailOptionType.QA -> composeQa(...)`，即使 `listSendOptions()` 已不返回 QA。
