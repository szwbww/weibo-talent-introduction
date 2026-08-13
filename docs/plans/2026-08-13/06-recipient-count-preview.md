# P-F：批量任务收件人预估

优先级 **P1（功能）** ｜ 前置：**P-E** ｜ 子系统：2 ｜ 文件数：6

## 需求描述

**Observable outcome**

1. 在「定时任务配置」编辑器与「手动执行」面板修改任一过滤条件后，
   面板实时显示"当前条件命中 N 位专家（其中未联系 X、可重试 Y）"。
2. 该数字与实际执行时的目标数**一致**。

**What must NOT change**

- 既有 `GET /types/{sendType}/pending-count` 端点的行为（其他页面在用）。
- `batchConfigEditorVolumeHint`（`app.js:13602-13609`）的"单次调度最多发送
  轮次×每轮数量"提示——它算的是发送上限，与命中人数是两回事，两者应并存。

**Out of scope**：把预估结果持久化；执行前的强制确认。

## 前置依赖说明

**依赖 P-E**：若在 P-E 之前做，预估不会计入状态过滤，等于给运营一个**必然错误**的数字，
P-E 落地后还要重新验收一遍。

## 关键不变量

### I-1：预估与执行同源
- **Rule**：预估接口必须复用执行路径的同一套目标计算代码
  （`RecipientScope.fromSnapshot` + `countEsTargets` + `buildRetryableTargets`），
  不得另写一份查询逻辑。
- **Violation consequence**：两套实现必然漂移，预估数与实际发送数对不上，
  这正是 `K-batch-send-filter-retry-parity`（P1，hit_count=8）记录的原生形态：
  "pending 统计与实际发送必须复用已过滤的重试目标集合"。
- **来源**：K-batch-send-filter-retry-parity

### I-2：入参即执行快照
- **Rule**：预估接口的入参必须是 `BatchExecutionSnapshot` 本身，
  而非另定义一个"预估请求" DTO。
- **Violation consequence**：两个 DTO 会各自演化；新增过滤维度时漏改其一即静默失准。
- **来源**：original

### I-3：预估不得产生副作用
- **Rule**：预估路径不得创建 `task_execution`、不得写 `expert_contact`、
  不得创建 campaign（注意 `getOrCreateManualCampaign()` 会**建行**）。
- **证据**：现有 `countPending()`（`:100-117`）读 campaign 用的是
  `campaignRepository.findByCampaignCode("MANUAL_OUTREACH")` 并判空，
  **没有**调用会建行的 `getOrCreateManualCampaign()`（`:1000` 附近）。新代码须沿用只读写法。
- **Violation consequence**：运营在编辑器里每敲一个字符就建一条记录。
- **来源**：original

### I-4：POST 而非 GET
- **Rule**：预估端点用 POST。
- **依据**：照抄既有 `/cron/preview`（`BatchSendConfigController:98`）的既有决策，
  其注释逐字写明"POST (not GET) so cron expressions with '?' '*' do not need
  query-string escaping"。本接口入参含 tags / regions 数组，同样不适合 query string。
- **来源**：original（沿用仓库既有范式）

## 现状审计

### 现有计数能力

`GET /api/mail/batch-send/types/{sendType}/pending-count`（`BatchSendConfigController:188`）
→ `manualInitialOutreachService.countPending(sendType)`（`:406-414`）
→ INTRODUCTION 走 `countPending()`（`:100-117`）。

该方法只读 `batchSendSettingService.getConfig()` 的全局 `emailDomain` / `discipline`
（`:110-114`），**完全不认识**配置里的 funnelLevel / tags / regions
（P-E 后还要加 operatorStatus）。

### 可直接复用的构件（都已存在）

| 构件 | 位置 | 作用 |
|---|---|---|
| `RecipientScope.fromSnapshot` | `BatchExecutionModels.kt:78-93` | snapshot → 统一过滤语义 |
| `countEsTargets(scope)` | `ManualInitialOutreachService:1177-1184` | 按 funnelLevels 逐层 ES count |
| `buildRetryableTargets(campaignId, scope)` | `:926-960` | 已应用 scope 过滤的重试目标集合 |
| `PendingOutreachSummary` | `:1352-1356` | 现成的返回结构（pending / retryable / totalSendable） |

`runIntroductionFromSnapshot`（`:445-460`）的目标估算就是
`buildRetryableTargets(...).size + countEsTargets(scope)`——**预估直接复用这两行即可与执行同源**。

### 前端展示位

- 配置编辑器：`batchConfigEditorVolumeHint`（`index.html:1247`）旁边，
  复用 `.batch-config-editor-hint`（`styles.css:8606-8614`，逐字：
  `margin-top:10px; padding:8px 12px; border-radius:8px; background:rgba(37,99,235,.06);
  color:#475569; font-size:12px; line-height:1.6;`）与
  `.batch-config-editor-hint strong`（`:8616-8619`，`color:#2563eb; font-weight:600;`）。
- 手动执行面板：确认弹窗 `batchManualConfirmBody`（`index.html:1460`）内已有文案区，
  参考 `app.js:11787` 既有措辞
  `将向 ${totalSendable} 位专家发送介绍邮件（${pending} 位未联系）…`。

### Interaction points

| # | 写 | 读 | 验收 |
|---|---|---|---|
| IP-1 | 前端过滤条件变更 | 预估接口 | A-1 |
| IP-2 | 预估接口 | 与执行路径同一套目标计算 | A-2 |

## 实现方案

### T-1 预估方法【I-1, I-2, I-3】
文件：`campaign/service/ManualInitialOutreachService.kt`

新增 `countBySnapshot(snapshot: BatchExecutionSnapshot): PendingOutreachSummary`：
`RecipientScope.fromSnapshot(snapshot)` → `buildRetryableTargets` + `countEsTargets`，
返回既有 `PendingOutreachSummary`。campaign 取用只读写法（I-3）。

MATERIAL_REMINDER 分支复用 `buildMaterialReminderSnapshot(scope, config).targets.size`
（与 `countPending(MATERIAL_REMINDER)`（`:408-412`）一致）。

### T-2 端点【I-4】
文件：`mail/controller/BatchSendConfigController.kt`

`POST /api/mail/batch-send/recipients/preview`，入参 `BatchExecutionSnapshot`（I-2），
返回 `PendingOutreachSummary`。放在 `/cron/preview`（`:97-99`）旁边保持归类。

### T-3 前端
文件：`index.html`、`app.js`

两个面板各加一个提示行（复用 `.batch-config-editor-hint`，`styles.css` 零改动）；
过滤条件变更时**防抖 500ms** 调预估接口；加载中显示"计算中…"，失败显示
"预估不可用"而非报错弹窗（避免编辑过程被打断）。

**并发保护**：用请求序号丢弃过期响应，防止慢请求覆盖新结果——
参考 `K-ai-preflight-stale-response-draft-identity` 记录过的同类问题。

### T-4 测试
`ManualInitialOutreachServiceTest` 补：`countBySnapshot` 与
`runIntroductionFromSnapshot` 的 `totalEstimate` 在同一 snapshot 下**数值相等**（I-1 的核心断言）。

## 变更文件清单（6 个）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `campaign/service/ManualInitialOutreachService.kt` | 改 |
| 2 | `mail/controller/BatchSendConfigController.kt` | 改 |
| 3 | `src/main/resources/static/index.html` | 改 |
| 4 | `src/main/resources/static/app.js` | 改 |
| 5 | `test/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 改 |
| 6 | `docs/knowledge/campaign/K-recipient-count-preview-parity.md` | 新增 |

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

## 验收标准

- **I-1**：单测断言 `countBySnapshot(s).totalSendable` == 同 snapshot 下执行路径的 `totalEstimate`。
- **I-2**：端点签名入参类型为 `BatchExecutionSnapshot`。
- **I-3**：单测 `verifyNoInteractions(taskExecutionService)`；
  断言 campaign 不存在时返回 retryable=0 而非建行。
- **I-4**：端点为 `@PostMapping`。
- **样式**：`git diff src/main/resources/static/styles.css` 为**空**。
- **回归**：执行『验证命令』节全部通过。

## 人工验收清单

### A-1：改条件即时看到数字【outcome 1 / IP-1】
- 步骤：① 打开配置编辑器；② 依次改漏斗层级、标签、地区、邮箱服务商、学科、专家状态。
- 预期：每次改动后 500ms 内提示行更新为"当前条件命中 N 位专家（未联系 X、可重试 Y）"；
  数字随条件收紧而下降。

### A-2：预估与实际一致【outcome 2 / IP-2 / I-1】
- 步骤：① 记下某条件下的预估 N；② 用同一条件手动执行；③ 看 execution 日志的 target。
- 预期：**target == N**。

### A-3：编辑过程不建垃圾数据【I-3】
- 前置：`SELECT COUNT(*) FROM task_execution` 与 `SELECT COUNT(*) FROM campaign` 各记一个数。
- 步骤：在编辑器里反复改条件 20 次。
- 预期：两个计数**均无变化**。

### A-4：失败不打断编辑【outcome 1】
- 步骤：断开 ES（或制造预估接口 500），继续编辑配置。
- 预期：提示行显示"预估不可用"，**无报错弹窗**，配置仍可正常保存。

### A-5：既有提示并存【must-NOT-change】
- 步骤：查看配置编辑器。
- 预期："单次调度最多发送 N 封（执行轮次 × 每轮数量）"这句仍在，
  与新的命中人数提示**并存且语义不混淆**。

### A-6：既有端点无回归【must-NOT-change】
- 步骤：调 `GET /api/mail/batch-send/types/INTRODUCTION/pending-count`。
- 预期：返回结构与数值和升级前一致。
