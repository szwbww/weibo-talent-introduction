# 开发计划：按收件服务商节流（account × provider 限速）

> 用 create-p skill 编写。独立计划。建议在「多部分」计划之后做，但无硬依赖。

## 需求描述

- 可观察结果：批量外发对某一收件服务商（如 Gmail）连续触发 421/452 限流时，只对「该发件账号 × 该服务商」组合提高发送间隔并退避，不影响同一账号发往其它服务商的节奏；该组合连续成功后自动恢复。
- 必须不变：现有「按账号」退避语义（`AccountRateLimiter` 既有方法签名与行为）保持可用；外发主循环的成功/失败/暂停判定不变；纯文本/HTML 内容不变。
- 不做：真实 MX → provider 的远程实时解析放进发送热路径（用带缓存的归一化，且失败回退到账号级，不阻塞发送）；服务商级日配额（仅做间隔退避，不做计数封顶）。

## 关键不变量

### Invariant I-1：provider 归一化确定且离线
- 规则：`ProviderResolver.resolve(email): String` 返回稳定的小写 provider 标识（`gmail`/`outlook`/`yahoo`/`tencent`/`netease`/`edu`/`other`）。优先用域名后缀映射表；可选用 `DnsMxLookupClient` 的 MX 结果细化（命中缓存才用，未命中或异常一律回退到域名/`other`，**绝不**在发送热路径同步阻塞 DNS）。
- 适用于：节流键计算。
- 违反后果：发送被 DNS 拖慢，或键不稳定导致退避状态错乱。

### Invariant I-2：节流键 = accountCode + provider，向后兼容
- 规则：限速状态键从 `accountCode` 扩展为 `"$accountCode|$provider"`。新增的 provider 维度方法不得改变现有「纯 accountCode」方法的行为；二者可共存（账号级仍作为兜底/回退）。
- 适用于：`AccountRateLimiter`。
- 违反后果：现有按账号退避逻辑回归。

### Invariant I-3：退避只增不误伤其它组合
- 规则：`recordThrottled(account, provider, base)` 只提高该 (account,provider) 组合的间隔；`getIntervalMs(account, provider, base)` 取该组合与账号级中的较大值；`recordSuccess` 仅回收该组合。
- 适用于：外发主循环对 421/452 的处理。
- 违反后果：一个服务商的限流拖慢全部发送，或退避不生效。

## 现状审计

### `AccountRateLimiter`（写/读路径）
- 现状方法：`getIntervalMs(accountCode, base)`、`recordSuccess(accountCode, base)`、`recordThrottled(accountCode, base)`，状态 `ConcurrentHashMap<String, RateState>`，指数退避（`1 shl level`，上限 `MAX_INTERVAL_MS=60s`），连续成功 `RECOVERY_THRESHOLD=10` 后降级。
- 集成点：`ManualInitialOutreachService` 主循环：
  - `:288` 成功 → `recordSuccess(accountCode, config.perMailIntervalMs)`
  - `:324` 收到 421/452 → `recordThrottled(accountCode, config.perMailIntervalMs)`
  - `:396` 取间隔 → `getIntervalMs(accountCode, config.perMailIntervalMs)` 后 `Thread.sleep`
- 收件邮箱：循环内 `expert.email`。

### MX/域名能力（已存在）
- `expert/service/DnsMxLookupClient.kt` + `MxLookupResult`（FOUND/NOT_FOUND/DNS_ERROR），`EmailValidationService.hasMxRecord` 已带缓存。可复用其缓存能力；本计划新增的 `ProviderResolver` 以**域名后缀表为主**，MX 仅作可选增强。

## 实现方案

### 任务 1：ProviderResolver（I-1）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ProviderResolver.kt`（新增）
```kotlin
@Service
class ProviderResolver {
    fun resolve(email: String?): String {
        val domain = email?.substringAfterLast('@', "")?.lowercase()?.trim().orEmpty()
        if (domain.isBlank()) return "other"
        return when {
            domain.endsWith(".edu") || domain.contains(".edu.") || domain.endsWith(".ac.uk") -> "edu"
            domain in GMAIL -> "gmail"
            domain in OUTLOOK -> "outlook"
            domain in YAHOO -> "yahoo"
            domain in TENCENT -> "tencent"
            domain in NETEASE -> "netease"
            else -> "other"
        }
    }
    companion object {
        private val GMAIL = setOf("gmail.com", "googlemail.com")
        private val OUTLOOK = setOf("outlook.com","hotmail.com","live.com","msn.com")
        private val YAHOO = setOf("yahoo.com","ymail.com")
        private val TENCENT = setOf("qq.com","foxmail.com")
        private val NETEASE = setOf("163.com","126.com","yeah.net")
    }
}
```
（注：自定义域名走 Google Workspace/M365 的，后缀表识别不到 → `other`；可选后续用 MX 增强，本期不在热路径同步查。）

### 任务 2：扩展 `AccountRateLimiter` 增加 provider 维度（I-2, I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AccountRateLimiter.kt`
- 新增重载，键为 `"$accountCode|$provider"`，复用现有 `RateState` 与退避算法：
```kotlin
fun getIntervalMs(accountCode: String, provider: String, base: Long): Long =
    maxOf(getIntervalMs(key(accountCode, provider), base), getIntervalMs(accountCode, base)) // 组合与账号级取大(I-3)
fun recordThrottled(accountCode: String, provider: String, base: Long) = recordThrottled(key(accountCode, provider), base)
fun recordSuccess(accountCode: String, provider: String, base: Long) = recordSuccess(key(accountCode, provider), base)
private fun key(a: String, p: String) = "$a|$p"
```
- 现有以 `accountCode` 为键的三个方法**保持不变**（I-2）。

### 任务 3：外发主循环接线（I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- 注入 `ProviderResolver`。循环内计算一次 `val provider = providerResolver.resolve(expert.email)`。
- `:288` → `recordSuccess(accountCode, provider, config.perMailIntervalMs)`
- `:324`（421/452）→ `recordThrottled(accountCode, provider, config.perMailIntervalMs)`
- `:396` → `getIntervalMs(accountCode, provider, config.perMailIntervalMs)`
- 自动外发 `InitialOutreachService` 当前无 per-mail sleep；本期不强制接入（保持范围最小，标注为可选后续）。

### 任务 4：测试
文件：`src/test/kotlin/.../ProviderResolverTest.kt` — 各服务商域名、edu、未知→other、空邮箱→other（I-1）。
文件：`src/test/kotlin/.../AccountRateLimiterTest.kt`（补充）—
- 对 (A,gmail) `recordThrottled` 后，(A,gmail) 间隔上升，(A,outlook) 不变（I-3）。
- 账号级 `getIntervalMs(A, base)` 行为与旧用例一致（I-2）。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `mail/service/ProviderResolver.kt` | 新增 |
| 2 | `mail/service/AccountRateLimiter.kt` | 修改 |
| 3 | `campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 4 | `test/.../ProviderResolverTest.kt` | 新增 |
| 5 | `test/.../AccountRateLimiterTest.kt` | 新增/修改 |

文件数 = 5 ≤ 10。子系统：限速器（含 resolver）+ 外发循环接线 = 2。

## 验收标准
- I-1：resolver 对样例邮箱返回稳定 provider；空/未知回退；不触发同步 DNS。
- I-2：旧的账号级方法签名与行为单测不变。
- I-3：单一 (account,provider) 退避隔离，其它组合不受影响；组合与账号级取较大值。
- 集成：模拟 Gmail 连续 421，断言后续发往 Gmail 的间隔变大、发往其它服务商间隔不变。
