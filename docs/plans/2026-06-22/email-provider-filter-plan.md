# 邮箱服务商筛选功能

## 需求描述

- **可观察结果**：专家联系页面新增「邮箱服务商」下拉框，列出当前索引层级下所有邮箱域名及数量，按数量降序排列；选择后筛选结果仅显示该域名的专家。批量发送介绍邮件的配置面板新增同类下拉框，保存后自动 / 手动发送仅发送该域名的候选人。
- **不得变更**：现有 `notContactedWithEmailFilters()` 无参调用行为不变（默认参数 `emailDomain=null`）；批量发送核心流程（轮次、限额、账号池、进度上报）不变；ES 索引 mapping 不变。
- **不在范围**：不引入新 ES 字段（如 emailDomain）；不修改 ES mapping；不修改数据库 migration；不修改 ProviderResolver 的分组逻辑——本功能按原始域名（如 `gmail.com`、`harvard.edu`）筛选，而非按 ProviderResolver 的分组（gmail / outlook / edu）。

## 关键不变量

### Invariant I-1: emailDomain 筛选使用 ES wildcard 查询
- Rule: 当 `emailDomain` 非空时，在 ES bool filter 中追加 `{"wildcard": {"email": {"value": "*@<domain>"}}}` 进行筛选。不使用 script query，不新增字段。
- Applies to: `ExpertSearchService.searchExperts()`、`ExpertSearchService.notContactedWithEmailFilters()`
- Violation consequence: 筛选失效或查询报错。

### Invariant I-2: 聚合 API 使用 painless script 提取域名
- Rule: `GET /api/experts/email-providers` 端点对 ES `email`（keyword 类型）字段执行 terms aggregation，script 为 `doc['email'].value.substring(doc['email'].value.indexOf('@')+1)`，返回 `[{domain, count}]` 按 count 降序。
- Applies to: `ExpertSearchService.aggregateEmailDomains()`
- Violation consequence: 下拉框数据不正确或聚合查询失败。

### Invariant I-3: notContactedWithEmailFilters 兼容无参调用
- Rule: `notContactedWithEmailFilters(emailDomain: String? = null)` 增加可选参数，默认值 null 时行为与当前完全一致。仅当 emailDomain 非空时额外追加 wildcard filter。
- Applies to: `ExpertSearchService.notContactedWithEmailFilters()`、所有调用方
- Violation consequence: 现有发送 / 计数逻辑意外被过滤。

### Invariant I-4: 批量发送 emailDomain 通过 batch_send_setting 持久化
- Rule: `emailDomain` 作为 `batchSend.emailDomain` key 存储在 `batch_send_setting` 表中，空字符串表示"不过滤"。`BatchSendConfig` 和 `BatchSendConfigUpdateRequest` 新增 `emailDomain: String` 字段（默认空字符串）。
- Applies to: `BatchSendSettingService.getConfig()`、`BatchSendSettingService.updateConfig()`
- Violation consequence: 定时执行时 emailDomain 筛选丢失或无法保存。

## 现状审计

### CANDIDATE ES index（email 字段）
- Schema/mapping: `"email": { "type": "keyword" }` — 三个索引一致（`orcid_info_candidate.json` L12）。
- Write paths:
  1. `ExpertIndexWriterService` — 写入 / 更新 email 字段。
  2. `ExpertIndexPromotionService` — L3→L2 晋升时复制 email。
- Read paths:
  1. `ExpertSearchService.searchExperts()` — `_source` 包含 email，返回给前端。
  2. `ExpertSearchService.notContactedWithEmailFilters()` — `exists` filter 检查 email 存在。
  3. `ExpertSearchService.searchExpertsFiltered()` — 被 `ManualInitialOutreachService` 调用获取候选人。
  4. `ExpertSearchService.countExperts()` — 被 `ManualInitialOutreachService.countPending()` 调用。
- Interaction points: 本计划仅新增读路径（聚合 + wildcard filter），不修改任何写路径。

### batch_send_setting 表（MySQL）
- Schema: `id BIGINT PK, setting_key VARCHAR UNIQUE, setting_value VARCHAR, updated_at DATETIME`。
- Write paths:
  1. `BatchSendSettingService.upsert()` — 所有配置保存通过此方法。
  2. `BatchSendSettingService.updateConfig()` — 调 upsert 保存各 key。
  3. `BatchSendSettingService.setRuntimeStatus()` — 运行态 key。
- Read paths:
  1. `BatchSendSettingService.getConfig()` — 读取所有配置。
  2. `BatchSendSettingService.getRuntimeStatus()` — 读取运行态。
  3. `BatchSendConfigController.getConfig()` — 透传给前端。
- Interaction points: 新增 `batchSend.emailDomain` key，`updateConfig` 写 → `getConfig` 读 → `ManualInitialOutreachService.runScheduledBatch()` 消费。

## 实现方案

### 阶段一：后端——聚合端点 + 搜索过滤

#### Task 1: ExpertSearchService 新增 aggregateEmailDomains 和 emailDomain 过滤 [I-1, I-2, I-3]

文件：`src/main/kotlin/.../expert/service/ExpertSearchService.kt`

1. 修改 `notContactedWithEmailFilters()` 签名为 `notContactedWithEmailFilters(emailDomain: String? = null)`。当 emailDomain 非空时，在返回的 filter list 末尾追加 `mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$emailDomain")))`。
2. 修改 `searchExperts()` 签名新增 `emailDomain: String? = null` 参数。在 filters 构建逻辑中，当 emailDomain 非空时追加同样的 wildcard filter（不依赖 operatorStatus 分支）。
3. 新增 `aggregateEmailDomains(level: ExpertIndexLevel): List<EmailDomainCount>` 方法，执行 size=0 的 ES 搜索 + terms aggregation（painless script 提取 `@` 后的域名），返回 `[EmailDomainCount(domain, count)]`。
4. 新增 `data class EmailDomainCount(val domain: String, val count: Long)`。

#### Task 2: ExpertIndexController 新增端点和参数 [I-1, I-2]

文件：`src/main/kotlin/.../expert/controller/ExpertIndexController.kt`

1. `listExperts()` 新增 `@RequestParam(required = false) emailDomain: String?` 参数，传递给 `searchExperts()`。
2. 新增 `@GetMapping("/email-providers") fun getEmailProviders(@RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel): List<EmailDomainCount>` 端点。

### 阶段二：后端——批量发送 emailDomain 持久化 + 消费

#### Task 3: BatchSendSettingService 新增 emailDomain 配置 [I-4]

文件：`src/main/kotlin/.../campaign/service/BatchSendSettingService.kt`

1. 新增 companion 常量 `KEY_EMAIL_DOMAIN = "batchSend.emailDomain"`、`DEFAULT_EMAIL_DOMAIN = ""`。
2. `getConfig()` 读取 emailDomain（strValue，默认空字符串）并填入 `BatchSendConfig`。
3. `updateConfig()` upsert emailDomain。
4. `BatchSendConfig` data class 新增 `val emailDomain: String = ""`。
5. `BatchSendConfigUpdateRequest` data class 新增 `val emailDomain: String = ""`。
6. `validate()` 无需对 emailDomain 做特殊校验（空字符串 = 不过滤）。

#### Task 4: ManualInitialOutreachService 消费 emailDomain [I-3, I-4]

文件：`src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt`

1. `countPending()` 中，读取 `batchSendSettingService.getConfig().emailDomain`，当非空时传递给 `notContactedWithEmailFilters(emailDomain)`。
2. `runScheduledBatch()` 中，`val esFilters = ExpertSearchService.notContactedWithEmailFilters(config.emailDomain.ifBlank { null })` 替换当前无参调用。

### 阶段三：前端

#### Task 5: index.html 新增下拉框 UI

文件：`src/main/resources/static/index.html`

1. 在 `contactsFilterGroup` 的标签筛选之后，新增：
   ```html
   <label class="toolbar-label">
       邮箱服务商:
       <select id="expertEmailDomainFilter">
           <option value="">全部服务商</option>
       </select>
   </label>
   ```
2. 在 `batchSendConfigPanel` 的「流量控制」fieldset 内，新增邮箱服务商选择下拉框：
   ```html
   <div class="bsc-field">
       <span class="bsc-field-label">邮箱服务商</span>
       <select id="batchSendEmailDomain" class="bsc-input bsc-select">
           <option value="">全部</option>
       </select>
   </div>
   ```

#### Task 6: app.js 数据加载 + 交互逻辑

文件：`src/main/resources/static/app.js`

1. 新增 `async function loadEmailProviders(level)` — 调用 `GET /api/experts/email-providers?level=<level>`，更新 `#expertEmailDomainFilter` 的 options（`全部服务商` + 各域名 `domain (count)`）。同时更新 `#batchSendEmailDomain` 选项。
2. `loadContacts()` 中读取 `$("#expertEmailDomainFilter")?.value || ""`，作为 `emailDomain` 参数追加到 `/api/experts` 请求。
3. `loadContacts()` 首次调用时触发 `loadEmailProviders(level)`；切换漏斗层级时重新加载。
4. `#expertEmailDomainFilter` 的 `change` 事件触发 `loadContacts()`。
5. `fillBatchSendConfigForm(config)` 中设置 `#batchSendEmailDomain` 的 value 为 `config.emailDomain`。
6. `readBatchSendConfigForm()` 中读取 `#batchSendEmailDomain` 的 value，追加到 payload 的 `emailDomain` 字段。

## 变更文件清单

| # | 文件路径 | 变更类型 | 说明 |
|---|---------|---------|------|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改 | 新增 aggregateEmailDomains()、emailDomain 过滤 |
| 2 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | 修改 | 新增 email-providers 端点、emailDomain 参数 |
| 3 | `src/main/kotlin/.../campaign/service/BatchSendSettingService.kt` | 修改 | 新增 emailDomain 配置项 |
| 4 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 | 传递 emailDomain 到 ES 过滤器 |
| 5 | `src/main/resources/static/index.html` | 修改 | 新增两个下拉框 |
| 6 | `src/main/resources/static/app.js` | 修改 | 加载服务商列表、传递筛选参数、批量发送配置读写 |

共 6 个文件。

## 验收标准

- **I-1**: 在专家联系页面选择某个域名（如 `gmail.com`），列表仅显示该域名的专家；清空选择后恢复全部。API 请求中 `emailDomain=gmail.com` 参数可见。
- **I-2**: `GET /api/experts/email-providers?level=CANDIDATE` 返回 `[{domain: "gmail.com", count: 1234}, ...]`，按 count 降序，domain 不含 `@` 前缀。
- **I-3**: 不传 emailDomain 参数时，`/api/experts` 返回结果与修改前完全一致。`ManualInitialOutreachService.countPending()` 在 emailDomain 为空时返回值与修改前一致。
- **I-4**: 在批量发送配置面板选择 `gmail.com` 并保存 → `GET /api/mail/batch-send/config` 返回 `emailDomain: "gmail.com"` → 执行批量发送时仅向 gmail.com 专家发送（可通过 pending-count 端点验证筛选后数量减少）。清空选择并保存后恢复发送全部。
- **集成场景**: 切换漏斗层级（RAW → CANDIDATE → APPLICATION）时，邮箱服务商下拉框内容随之刷新，count 数据与该层级一致。
