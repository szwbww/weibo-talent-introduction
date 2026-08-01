---
id: K-training-evaluation-bounded-action-log
domain: audit
created: 2026-07-28
last_used: 2026-08-01
hit_count: 3
source: create-p:trusted-reply-shared-workbench
severity: P1
---

AI 训练评估可复用 append-only 操作日志，但必须作为独立 action type，并把 payload 限制为可验证的有界元数据：

1. 保存前由服务端重新 assemble，不能信任客户端自报 draftHash 或自由文本。
2. 日志只记 source/evidence version、服务端 draft hash、评分、计数，以及限量 item snapshot 的 requestKey/handling/versionId/answerHash/model/kind。
3. 禁止来信正文、回复正文、逐项指令、claims 文本、QA answerBody 和可替代正文的 preview；snapshot 数量和每个字符串均设上限并标记 truncation。
4. 每次评估新增一条记录，不 update/upsert 旧评估；唯一业务写路径为统一 action-log service。
5. 新训练 action 不得加入线上 ready/partial/blocked/send/review 的精确 action filter，避免训练点击污染生产指标。
6. 训练评估 service 不得依赖发送 service 或保存 mail/inbound 状态。
