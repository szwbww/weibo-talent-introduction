---
id: K-custom-exception-http-status-mapping
domain: common
created: 2026-08-10
last_used: 2026-08-16
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
