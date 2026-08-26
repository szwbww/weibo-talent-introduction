---
id: K-master-plan-shared-file-sequential-gates
domain: audit
created: 2026-08-24
last_used: 2026-08-24
hit_count: 0
source: create-p:00-trust-reply-manual-authority-master
severity: P1
---

# 多个子计划共享文件时，master 必须规定顺序门禁和原子发布单元

子计划即使各自满足 ≤10 文件/≤2 子系统，只要修改同一生产文件，就不能从同一旧基线并行实现后再机械合并。Master 必须列出共享文件、规定后计划基于前计划产物执行、为每个子计划设置独立机器门禁，并在跨层语义只有同时上线才闭环时声明原子发布/回滚单元。

典型断层：前端/工作台先放开一种选择语义，发送端仍按旧语义重筛。此时两个子计划各自测试可能都通过，但分批发布会造成预览与外发不一致。正确做法是允许前半计划进入 `IMPLEMENTED_NOT_RELEASABLE`，直到后半计划及联合测试通过。

Master 本身只做治理，不应把所有子计划代码文件合并成一个超限执行计划；每个子计划的文件清单仍是唯一授权边界。任何新增文件或跨计划不变量变化都必须先修订子计划和 master，再恢复执行。
