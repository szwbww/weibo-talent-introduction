---
id: K-state-input-no-per-keystroke-innerhtml
domain: frontend
created: 2026-08-01
last_used: 2026-08-01
hit_count: 0
source: create-p:trust-reply-manual-generation-and-stable-input
severity: P1
---

状态型表单的 `input` 事件应逐键更新内存，但不能无条件重写邻近 DOM：

1. 先判断该次输入是否真的使 active/resolved/assembled decision 从有效变为无效。
2. 只在第一次真实失效时更新版本、回答区、操作区和摘要；失效完成后的后续字符只写内存。
3. 不替换正在输入的 textarea 节点，保留 focus、selection 和相邻组件状态。
4. 清 assembly 的宿主回调只在 assembly 原本存在时触发，禁止把幂等清空当作每键副作用。

症状通常不是输入监听器过多，而是监听器内反复 `innerHTML`；测试应统计 DOM setter 次数，而不只断言最终文本和焦点。
