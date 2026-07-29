---
id: K-trust-reply-resolved-version-single-source
domain: frontend
created: 2026-07-29
last_used: 2026-07-29
hit_count: 0
source: create-p:trust-reply-unsupported-answer-v1
severity: P1
---

逐项版本工作台必须把“下一次编辑意图”和“已经采用的版本”分开建模：

1. `draftHandling`、textarea 和 `activeVersionId` 只控制下一次生成或当前预览。
2. `resolvedVersionId` 才表示已确认决定；用户改变 handling、说明或版本时必须使该决定失效。
3. assemble payload 的 handling、正文、claims、model、generation kind、source/evidence version、指令及 hash、versionId 必须全部从同一个 resolved version 复制，禁止从下拉状态和版本对象混装。
4. 服务端仍应重新 materialize 并计算 versionId；前端状态一致不是信任边界。

如果用可变下拉 handling 搭配已生成版本正文，常见症状是界面看似“已锁定”，服务端却返回 locked item invalid；更危险的实现会猜测并接受错误语义。

