---
id: K-aggregate-generation-reuse-item-path
domain: frontend
created: 2026-08-04
last_used: 2026-08-05
hit_count: 1
source: create-p:trust-reply-durable-locks-and-assembly-generation
severity: P1
---

“生成并整合”不能假定一次 full-draft 会为每个安全条目返回完整版本；复杂多问题生成可能整体 fallback，而相同条目的单项生成仍可成功。

当产品语义是替用户执行多次既有单项动作时，聚合按钮应按 canonical order 编排同一个 item-generation seam：每项成功即校验、回填并持久化，失败/取消后停止且不 assemble，重试只处理剩余项。不得用 synthetic click，也不得在 full-draft 缺项后要求用户重新逐项点击。
