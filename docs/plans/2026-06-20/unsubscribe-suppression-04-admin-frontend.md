# 子计划 04：退订抑制名单管理（API + 前端）

> 用 create-p skill 编写。退订系列第 4 篇。
> **依赖子计划 01**（`EmailSuppression` 表 / 服务 / 仓储）。

## 需求描述

- 可观察结果：管理员在后台页面可查看抑制名单（邮箱、来源、时间，分页/搜索），可手动新增一条（来源 `MANUAL`），可移除一条（误加恢复联系）。所有操作经 `/api/**` 鉴权。
- 必须不变：抑制名单的归一化与幂等语义（G-1/G-2）不变；外发过滤、入站捕获、一键端点行为不变；新增 `MANUAL` 来源不改变既有来源值。
- 不做：批量导入/导出 CSV；操作审计日志专表（沿用现有 `OperatorActionLog` 若适用，否则本期仅普通日志）；前端复杂筛选（仅邮箱关键词 + 分页）。

## 关键不变量（引用 + 专属）

- 引用 G-1（归一化）、G-2（幂等）。
- Invariant L4-1：管理写操作走同一服务入口。新增走 `EmailSuppressionService.suppress(email, MANUAL, reason)`（复用归一化/幂等）；移除走 `EmailSuppressionService.remove(email)`（按归一化邮箱删除，幂等：不存在也不报错）。控制器不直接操作仓储绕过归一化。
- Invariant L4-2：列表只读、分页确定。列表按 `createdAt` 倒序，服务端分页（page/size），可选邮箱关键词包含匹配（对归一化邮箱）。
- Invariant L4-3：管理端点受鉴权保护。路径在 `/api/suppressions` 之下，经 `AuthInterceptor`（与一键退订 `/u/**` 的免鉴权端点隔离，二者不复用路径）。

## 现状审计

### 来自子计划 01
- `EmailSuppression(id, email, source, reason, createdAt)`、`EmailSuppressionRepository`、`EmailSuppressionService(normalize/isSuppressed/suppress/looksLikeUnsubscribe)`、`SuppressionSource{INBOUND_REPLY, ONE_CLICK, MAILTO, MANUAL}`。本篇新增 `remove` 与分页查询。

### 前端（静态后台）
- `common/controller/FrontendController` 提供静态页；`static/index.html` + `static/app.js` + `static/styles.css`，通过 `/api/*` 调后端。现有列表页（如专家联系页）已有分页器与表格渲染范式（`app.js` 内 `renderContactPager` 等），新页可仿照。

### 鉴权
- `AuthInterceptor` 拦截 `/api/**`（排除 login/me）。新增 `/api/suppressions` 自动受保护（L4-3）。

## 实现方案

### 任务 1：仓储扩展（L4-2, L4-1）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/EmailSuppressionRepository.kt`
```kotlin
fun deleteByEmail(email: String): Int
fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<EmailSuppression>
fun findByEmailContainingOrderByCreatedAtDesc(keyword: String, pageable: Pageable): Page<EmailSuppression>
```
（Spring Data JDBC 分页：如不便用 `Page`，改为带 LIMIT/OFFSET 的 `@Query` + 单独 count 查询，保持服务端分页。）

### 任务 2：服务扩展（L4-1, G-1）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`
```kotlin
fun remove(email: String): Boolean {           // 幂等
    val n = normalize(email)
    return repository.deleteByEmail(n) > 0
}
fun list(keyword: String?, page: Int, size: Int): SuppressionPage { /* L4-2 服务端分页/搜索，关键词先 normalize */ }
```

### 任务 3：控制器（L4-3, L4-1）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionController.kt`（新增）
```kotlin
@RestController
@RequestMapping("/api/suppressions")
class EmailSuppressionController(private val service: EmailSuppressionService) {
    @GetMapping fun list(@RequestParam(required=false) keyword: String?,
                         @RequestParam(defaultValue="0") page: Int,
                         @RequestParam(defaultValue="50") size: Int) = service.list(keyword, page, size)
    @PostMapping fun add(@RequestBody req: AddSuppressionRequest): ResponseEntity<Any> {
        require(req.email.isNotBlank()) { "email required" }
        val added = service.suppress(req.email, SuppressionSource.MANUAL, req.reason ?: "manual add")
        return ResponseEntity.ok(mapOf("added" to added))
    }
    @DeleteMapping fun remove(@RequestParam email: String) =
        mapOf("removed" to service.remove(email))
}
data class AddSuppressionRequest(val email: String, val reason: String?)
```

### 任务 4：前端页面
文件：`src/main/resources/static/index.html`
- 新增「退订名单」入口/区块：搜索框、表格（邮箱/来源/时间/操作）、分页器、新增表单。
文件：`src/main/resources/static/app.js`
- `loadSuppressions(page, keyword)` → `GET /api/suppressions`；渲染表格 + 分页（仿现有列表范式）；新增调用 `POST /api/suppressions`；移除调用 `DELETE /api/suppressions?email=`；操作后刷新。

### 任务 5：测试
文件：`src/test/kotlin/.../EmailSuppressionControllerTest.kt`（MockMvc）
- POST 新增 → 服务以 `MANUAL` 调用、归一化生效（L4-1, G-1）。
- DELETE 移除幂等：删不存在返回 removed=false，不 500（L4-1）。
- GET 分页/关键词返回有序结果（L4-2）。
- 未带鉴权访问 `/api/suppressions` 被 `AuthInterceptor` 拦截（L4-3，若测试环境启用鉴权）。
文件：`src/test/kotlin/.../EmailSuppressionServiceTest.kt`（补充 01 的测试）— `remove` 幂等、`list` 分页与关键词归一化。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `mail/repository/EmailSuppressionRepository.kt` | 修改 |
| 2 | `mail/service/EmailSuppressionService.kt` | 修改 |
| 3 | `mail/controller/EmailSuppressionController.kt` | 新增 |
| 4 | `src/main/resources/static/index.html` | 修改 |
| 5 | `src/main/resources/static/app.js` | 修改 |
| 6 | `test/.../EmailSuppressionControllerTest.kt` | 新增 |
| 7 | `test/.../EmailSuppressionServiceTest.kt` | 修改 |

文件数 = 7 ≤ 10。子系统：后端 API（仓储/服务/控制器）+ 前端页面 = 2。

## 验收标准
- L4-1：新增/移除均经服务入口、归一化生效、幂等。
- L4-2：列表服务端分页、倒序、关键词匹配。
- L4-3：`/api/suppressions` 受鉴权；与 `/u/**` 免鉴权端点互不影响。
- 集成：前端新增一条 → 该邮箱被 `InitialOutreachService` 跳过（依赖 01 的 G-3）；移除后恢复可发。
