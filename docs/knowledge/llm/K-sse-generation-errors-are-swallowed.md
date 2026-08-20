---
id: K-sse-generation-errors-are-swallowed
domain: llm
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:P0-sse-error-code-and-state-reset
severity: P1
---

# SSE 生成链路把异常同时从响应和日志里抹掉；普通 HTTP 链路不会

`AiReplyGenerationCoordinator.start()` 的 worker 体：

```kotlin
} catch (_: Exception) {
    control.sendTerminal("error", mapOf("generationId" to generationId, "message" to "AI generation failed"))
}
```

- `catch (_: Exception)` 用下划线，**异常对象没有被绑定**，语法上无法记录。
- `grep -n "logger\|Logger\|log\." AiReplyGenerationCoordinator.kt` → **零命中**，整个文件没有 logger。
- 异常被 catch 后不再传播，Spring 全局 handler 也拿不到。

**结果：响应体只有 `"AI generation failed"`，服务端日志一片空白，根因不可恢复。**
同形状的代码在 `UnmatchedInboundMailController` 里还有一份拷贝，两处必须同时改。

不属于这一类的：`GenerationControl.sendLocked()` 内的 `catch (_: Exception)` 捕的是
`emitter.send()` 失败（客户端已断开），是正常路径，不要顺手改。

## 对照：普通 HTTP 端点可以查

`TrustReplyWorkbenchController` 有自己的 `@ExceptionHandler(TrustReplyWorkbenchException::class)`，
返回 `{"code": ex.code}` + `ex.status`。所以 **bootstrap / assemble / state 这些端点，
F12 → Network → Response 里直接能看到确切错误码**。
注意 `GlobalExceptionHandler` **没有**该异常的全局 handler——靠的是这个局部的；
别的 controller 抛同一个异常会落到 `Exception` 分支变成 500。

排查此链路的第一步永远是：**先看这是 SSE 还是普通 HTTP**。

## 前端已经准备好接收 code，服务端从没发过

`trust-reply-workbench.js` 的 `errorFromStream` 已经读 `data.code || data.errorCode` 并写进
`error.code`，`isStaleError()` / `isFrameStaleError()` 也在消费它。补 code 是纯增量。
但注意 `new Error(message || code || fallback)` 里 **message 优先**——服务端补了 code
界面文字也不会变，中文文案要在渲染层按 code 查表出。

关联：[[K-manual-send-safety-gate-first-hit-only]]、[[K-custom-exception-http-status-mapping]]
