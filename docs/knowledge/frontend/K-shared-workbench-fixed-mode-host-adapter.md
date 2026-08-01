---
id: K-shared-workbench-fixed-mode-host-adapter
domain: frontend
created: 2026-07-28
last_used: 2026-08-01
hit_count: 3
source: create-p:trusted-reply-shared-workbench
severity: P1
---

同一操作台需要出现在训练与生产入口时，共用边界应是“组件内部全部共用，宿主只传固定上下文和完成回调”：

1. 公共组件拥有 DOM、状态机、请求/SSE、异步身份、版本、锁定和整合。
2. 宿主只传 `mode + exact source + contextPath + onUnauthorized + onComplete`；组件内部保留唯一 JSON/SSE transport，宿主不复制 API、内部表单或状态。
3. 训练与生产不提供用户可切换模式；训练固定模拟且永不发送，生产固定采用到人工编辑器且不自动发送。
4. 每次 mount 返回 `unmount()`；实例拥有独立 abort/listener/requestSeq，防止页面或来源切换后的 late response 污染新状态。
5. 公共外链脚本应使用幂等 IIFE/namespace，禁止顶层 `$` 和 `document.write`，避免重复加载触发全局 lexical declaration 冲突。

这种边界既能保证两入口视觉和行为同构，又把真正不同的副作用限制在页面 adapter。
