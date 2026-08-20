---
id: K-custom-exception-http-status-mapping
domain: common
created: 2026-08-10
last_used: 2026-08-20
hit_count: 2
source: create-p:sender-binding-02-send-path-consistency
severity: P1
---

经验：`GlobalExceptionHandler`（`common/controller/GlobalExceptionHandler.kt`）只映射三类异常到 400/404：

- `IllegalArgumentException` → 400 `BAD_REQUEST`（`:14-16`）
- `IllegalStateException` → 400 `BAD_REQUEST`（`:18-20`）
- `NoSuchElementException` → 404 `NOT_FOUND`（`:22-24`）
- `AnalysisFailedException` → 500 `ANALYSIS_FAILED`（`:26-28`）
- **其余一切 `Exception` → 500 `INTERNAL_ERROR`（`:38-40`）**

因此新定义的业务异常若直接继承 `RuntimeException`，运营在 UI 上只会看到 500，
异常 message 虽被透传但 `code` 为 `INTERNAL_ERROR`，前端错误分支与监控都会当成系统故障。

正确做法：面向运营的可恢复业务错误（参数非法、状态不允许、资源不可用）继承
`IllegalArgumentException` 或 `IllegalStateException`；服务内的校验用 `require(...)` /
`check(...)` 而非自定义 `RuntimeException`。

副作用（必须一并处理）：Kotlin 的 `error(...)` 抛的就是 `IllegalStateException`，
所以自定义的 `IllegalStateException` 子类在 `catch` 链里**必须排在通用
`catch (e: Exception)` 之前**，否则会被批量流程的通用兜底吞掉并升级为整批失败。

关联：[[K-manual-send-error-response-opaque]]

## 补充（2026-08-20，create-p:manual-send-safety-confirm 实测）

同一条规则对 `ResponseStatusException` 同样成立，且后果更隐蔽：Spring 的
`ExceptionHandlerExceptionResolver` 先于 `ResponseStatusExceptionResolver` 生效，
本类 `@ExceptionHandler(Exception::class)`（`:38-40`）会吃掉 `ResponseStatusException`，
于是 `throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "…")`
**实际以 HTTP 500 + `code=INTERNAL_ERROR` 抵达前端**，`message` 被拼成
`422 UNPROCESSABLE_ENTITY "…"` 这种带状态前缀的串。

可观察后果：`app.js:10375` 的人工发送错误分支不得不写成
`(e?.status === 422 || e?.status === 500)`，并用正则从 message 里抠业务码。
**任何需要结构化错误体的接口，不要用 `ResponseStatusException`** ——
`GlobalExceptionHandler` 只透传 `ApiErrorResponse(code, message, detail)` 三个字段，
其余全丢；`app.js:1455-1470` 的 `api()` 也只保留 `message` / `status`。

正确做法：定义专用异常 + 在本类注册专用 `@ExceptionHandler`，返回自带字段的响应 DTO。
仓库先例：`AnalysisFailedException`（`document/service/AnalysisFailedException.kt:3`，
`: RuntimeException`）+ `GlobalExceptionHandler.kt:26-28`。
注意：**仓库内无任何测试断言过「专用 handler 确实被选中」**
（`grep -rln GlobalExceptionHandler src/test/kotlin` 只有两个 `@WebMvcTest` 类，
断言的是业务码不是 handler 选择），所以新增此类映射时要用一次真实 HTTP 实测兜底。
