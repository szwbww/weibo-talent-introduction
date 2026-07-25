---
id: K-ai-reply-modal-helper-scope
domain: frontend
created: 2026-07-16
last_used: 2026-07-25
hit_count: 22
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
---
经验：跨详情重置、全局事件绑定和局部 action handler 共用的 modal helper 若定义在 handler 局部作用域，源码检查与语法检查都会通过，但页面初始化或详情切换会因未定义符号中断。
正确做法：共享 modal 状态机 helper 必须定义在所有调用者可见的模块作用域，并用可执行 DOM stub 覆盖 bind/reset/confirm 三条入口。
反例：`src/main/resources/static/app.js:8838,8914,10570-10579` 调用/引用 `handleUnmatchedAction()` 内部的 `cancelReviewSession` 等函数。
