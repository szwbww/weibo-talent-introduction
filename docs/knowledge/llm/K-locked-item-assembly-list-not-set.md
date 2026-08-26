---
id: K-locked-item-assembly-list-not-set
domain: llm
created: 2026-07-28
last_used: 2026-08-21
hit_count: 7
source: create-p:trusted-reply-shared-workbench
severity: P1
---

逐项审核并显式锁定的回复不能复用通用“自然化聚合”逻辑。锁定 assembly 必须使用按 canonical request 顺序排列的 list，逐项插入已验证正文：

- 禁止 `Set/linkedSetOf/distinct`：两个不同请求即使答案文本相同，也必须各保留一次。
- 禁止 `take(n)` 或隐式长度裁剪：请求超过四项时仍须全量保留。
- 禁止整合阶段再次调用 LLM、去重、重排或润色：否则锁定失去语义。
- 版本创建时可完成一次规范化；锁定后 composer 不得再次 trim 或格式化。服务端只能在条目前后增加固定 frame 与空行；每个非省略 `answerText` 必须按原顺序逐字出现且恰好一次。
- 前端不得自行 join；必须显示并采用同一次服务端 assembly 返回的 raw/rendered/draftHash。

通用 composer 若已有自然化去重/截断行为，应新增窄的 locked composer，而不是改变旧行为影响其他调用方。
