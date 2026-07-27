# 重新验证候选人：与发现专家（快速）筛选条件对齐 & 去除最大晋升数限制

日期: 2026-06-16

## 背景

目前两个任务在筛选逻辑上有差异：

| 维度 | 发现专家（快速）`promoteEligibleRawExperts` | 重新验证 `revalidateCandidates` |
|---|---|---|
| 资格评估 | `CandidateEligibilityService.evaluateEligibility` (含学位、年龄、国籍、H-index、引用数、活跃度) | 同上 ✅ |
| 邮箱格式+一次性 | `evaluateEligibility` 内检查 | `evaluateEligibility` 内检查 ✅ |
| 邮箱深度验证（MX/SMTP） | `emailValidationService.validate()` — 仅当 `requireValidEmail=true` | `emailValidationService.validate()` — 仅当 `requireValidEmail=true` ✅ |
| 已存在候选人检查 | `documentExistsInIndex(CANDIDATE, orcidId)` — 跳过已晋升 | 不适用（本身扫描 CANDIDATE 层） |
| maxPromotions 限制 | 有，默认 1000，API 参数 1–10000 | 无 |
| 前端启动弹窗筛选面板 | `showFilters: true`, `showMaxPromotions: true` | `showFilters: true`, `showMaxPromotions: false` |

**两者的筛选条件实际上已经一致**——都调用 `evaluateEligibility` + `emailValidationService.validate`。差异仅在于 `promoteEligibleRawExperts` 有 `maxPromotions` 上限。

## 目标

1. **去除 `promoteEligibleRawExperts` 的 `maxPromotions` 限制**——扫描完整个 RAW 索引为止（仍可被用户取消）。
2. **前端去除最大晋升数输入框**。
3. **对齐确认**：确认两边筛选条件已一致，无额外工作。

## 设计约束

- 不引入新类、新状态、新接口。
- 不修改已有数据库迁移。
- 保留前端 `showFilters: true` 展示筛选面板（两个任务都已有）。
- 保留取消功能（`progressStore.isCancelled`）。

## 变更清单

### Task 1: Service 层 — 去除 maxPromotions 参数

**文件**: `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt`

变更：
- `promoteEligibleRawExperts(maxPromotions: Int = 1000)` → `promoteEligibleRawExperts()`
- 删除参数 `maxPromotions`
- 删除第 132 行 `if (maxPromotions <= 0) return ...`
- 删除第 146 行 `var limitReached = false`
- 删除第 148–151 行 `if (stats.promoted >= maxPromotions) { limitReached = true; break }`
- 第 208 行回调返回值从 `!limitReached && stats.promoted < maxPromotions` 改为 `true`（继续扫描直到结束或被取消）

### Task 2: Controller 层 — 去除 maxPromotions 请求参数

**文件**: `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`

变更：
- 第 141 行 `@RequestParam(defaultValue = "1000") maxPromotions: Int` → 删除该参数
- 第 143 行 `require(maxPromotions in 1..10000)` → 删除
- 第 155 行 `mapOf("maxPromotions" to maxPromotions)` → 改为字符串 `"promote-eligible-raw"`（与重新验证一致的风格）
- 第 161 行 `revalidationService.promoteEligibleRawExperts(maxPromotions)` → `revalidationService.promoteEligibleRawExperts()`

### Task 3: 前端 — 去除最大晋升数输入框

**文件**: `src/main/resources/static/app.js`

变更：
- `taskLaunchConfigs.RAW_PROMOTION_SCAN` 中 `showMaxPromotions: true` → `showMaxPromotions: false`
- `executePromoteRaw()` 中删除 `const maxPromotions = parseInt(...)` 行
- API 调用从 `` `/api/experts/promote-eligible-raw?maxPromotions=${maxPromotions}` `` → `"/api/experts/promote-eligible-raw"`，改为 `{ method: "POST" }`

### Task 4: 测试修复

**文件**: `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt`

变更：
- 所有 `controller.promoteEligibleRaw(maxPromotions = ...)` 调用去掉 `maxPromotions` 参数
- 删除 `maxPromotions` 边界校验测试（`rejects maxPromotions 0`, `rejects negative`, `rejects 10001`）——已无此参数
- 保留正常执行、并发冲突等测试

**文件**: `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt`

变更：
- `.param("maxPromotions", "100")` → 删除此 param

**文件**: `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt`

变更：
- `PromotionScanStats with maxPromotions zero returns empty result` 测试 → 删除（已无此逻辑）

**文件**: `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt`

变更：
- `stops at maxPromotions limit` 测试 → 删除
- 其他调用 `promoteEligibleRawExperts(maxPromotions = 2)` 的地方去掉参数

### Task 5: 验证

- `mvn test` 全部通过
- 手动确认前端启动弹窗：RAW_PROMOTION_SCAN 不再显示"最大晋升数"输入框，仍显示筛选条件面板
- 确认扫描可正常取消

## 不涉及的内容

- 重新验证的筛选逻辑不需要改动——已与发现专家一致（都走 `evaluateEligibility` + `emailValidationService.validate`）
- 不新增数据库迁移
- 不改变前端筛选面板的 UI（两边都已有 `showFilters: true`）
