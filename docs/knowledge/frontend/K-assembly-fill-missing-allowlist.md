---
id: K-assembly-fill-missing-allowlist
domain: frontend
created: 2026-08-01
last_used: 2026-08-04
hit_count: 1
source: create-p:trust-reply-manual-generation-and-stable-input
severity: P1
---

用户点击“生成并整合”时，如果批量生成只允许补齐部分条目，合并必须以点击前冻结的 missing-key allowlist 为边界：

1. 保留所有已有 resolved/active/manual versions，不用批量结果替换整个 versions 集合。
2. 只消费 allowlist 中每个 key 的唯一、同 source/evidence identity、handling 合法版本；其余合法返回也丢弃。
3. 任一 allowlist key 缺失、重复或身份错误都 fail-closed，禁止继续 assemble。
4. 合并后重新从 canonical request list 和 resolved versions 构造整合输入，禁止直接使用批量 raw draft。

该约束适用于“安全项可自动补齐、风险项必须人工确认”的混合工作台，可避免批量响应覆盖人工决定。
