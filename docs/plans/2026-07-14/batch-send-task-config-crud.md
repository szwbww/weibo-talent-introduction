# 批量邮件任务配置 CRUD

> 顺序：1/3。后续依次执行 `batch-send-task-execution-and-logs.md`、`batch-send-task-console-frontend.md`。

## 需求描述

- 将原“发送类型 + 单份配置”改为可命名的任务配置列表，支持查询、新增、查看、编辑、启停、软删除。
- 每份配置独立保存：任务名称、内部邮件类型、收件范围、模板、定时规则、发送限额。
- 收件范围不再硬编码漏斗层级；增加漏斗层级和标签多选。
- 本计划只建立配置模型和 API；调度、手动执行、配置级日志在计划 2 接入；页面在计划 3 接入。
- 不改自动回复、模板管理、普通收发件流程；不删除旧 `batch_send_setting`，旧接口暂时保留。

## 关键不变量

### I-1：配置是独立实体

- 每份配置有稳定 `id`；名称在未删除配置中唯一。
- 配置编辑覆盖同一记录；删除使用 `deleted_at` 软删除，保证后续执行日志可追溯。
- 列表和手动筛选默认排除已删除配置；按名称模糊查询，按 `updated_at DESC, id DESC` 排序。

### I-2：页面不再暴露“发送类型”，运行语义仍明确

- 配置内部保留 `mailType`，值仅允许 `INTRODUCTION`、`MATERIAL_REMINDER`。
- 选择模板时，由后端读取已启用模板的 `mailType` 并写入配置，前端不能自行提交或覆盖 `mailType`。
- 未选择模板时仅允许 `INTRODUCTION`，发送时沿用系统默认 `INTRODUCTION` 模板。
- 创建、更新以及计划 2 的实际启动均重新校验模板存在、启用且类型一致；禁止静默降级到其他模板。

### I-3：收件范围组合语义固定

- `funnelLevel` 可空；空值表示全部可发送层级：`CANDIDATE`、`APPLICATION`，明确排除 `RAW`。
- `tags` 为去重后的字符串集合；空集合表示不限制标签；多个标签按 OR 匹配。
- 漏斗层级、标签、邮箱服务商、学科分类四类条件之间按 AND 匹配。
- `emailDomain`、`discipline` 为空/`ALL` 时表示不限制。

### I-4：定时配置完整且可校验

- `autoEnabled=true` 时 `cron` 必须是合法 Spring cron；关闭时仍保存 cron，便于再次启用。
- `dailyCap > 0`、`roundSize > 0`、`perMailIntervalMs >= 0`、`perRoundIntervalMs >= 0`、`selfCheckTtlMinutes >= 1`。
- 所有更新先完整校验，再单次 `save`，禁止逐字段部分写入。

### I-5：旧 KV 仅用于迁移和兼容

- V72 从 `batch_send_setting` 生成“默认介绍邮件任务”和“材料提醒任务”，迁移可重复判断、不可重复插入。
- 旧 typed API 保留一个版本，继续读写旧 KV；新页面只调用 `/api/mail/batch-send/configs`。
- 不把新实体继续拆回 KV；配置列表、名称、标签、软删除必须写新表。

## 现状审计

### 存储

- `batch_send_setting` 是 `setting_key` 唯一的 KV 表，只能表达每种邮件类型一份配置。
- 唯一生产写入口：`BatchSendSettingService.upsert()`；读取入口：`loadAll()`、`getConfig(sendType)`。
- 该模型无法稳定表达列表、软删除、配置级日志外键，故拒绝继续扩展 KV，新增规范化表。
- 当前最高迁移为 V71；本计划使用 V72。

### API 与校验

- `BatchSendConfigController` 当前按 `/types/{sendType}` 读写配置。
- 模板合法性由 `BatchSendSettingService`/控制器校验；新服务必须复用相同类型门禁。
- 现有模板选项与预览接口继续作为权威数据源，不复制模板内容到配置表。

### 调度/执行交互点

- `BatchSendScheduler` 当前按 `BatchSendType` 维护两个 future。
- `BatchSendControlService`、`ManualInitialOutreachService` 当前运行时重新读取 KV。
- 本计划不改这些路径；计划 2 必须改为传入一次性配置快照，避免运行中配置变化造成同批参数漂移。

## 实现方案

### Phase 1：表结构与领域模型

#### Task 1.1：新增 V72 表

文件：`src/main/resources/db/migration/V72__create_batch_send_task_config.sql`

创建 `batch_send_task_config`：

| 字段 | 类型/约束 | 说明 |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | 稳定配置 ID |
| `config_name` | VARCHAR(120) NOT NULL | 展示名称 |
| `mail_type` | VARCHAR(32) NOT NULL | 后端派生类型 |
| `auto_enabled` | BOOLEAN NOT NULL DEFAULT FALSE | 是否参与调度 |
| `cron` | VARCHAR(64) NOT NULL | Spring cron |
| `daily_cap` | INT NOT NULL | 自然日上限 |
| `round_size` | INT NOT NULL | 每轮数量 |
| `per_mail_interval_ms` | BIGINT NOT NULL | 单封间隔 |
| `per_round_interval_ms` | BIGINT NOT NULL | 轮次间隔 |
| `self_check_ttl_minutes` | INT NOT NULL | 自查 TTL |
| `funnel_level` | VARCHAR(32) NULL | 空=全部可发送层级 |
| `tags_json` | TEXT NOT NULL | JSON 字符串数组 |
| `email_domain` | VARCHAR(120) NULL | 空=不限 |
| `discipline` | VARCHAR(120) NULL | 空=不限 |
| `template_id` | BIGINT NULL | 模板引用 |
| `legacy_code` | VARCHAR(64) NULL UNIQUE | 迁移兼容定位 |
| `deleted_at` | DATETIME NULL | 软删除 |
| `created_at/updated_at` | DATETIME NOT NULL | 审计时间 |

- 建索引：`(deleted_at, updated_at)`、`(auto_enabled, deleted_at)`、`template_id`。
- 对模板使用外键但不级联删除；模板被引用时按现有模板删除策略拒绝或由业务校验阻止运行。
- 从旧 KV 插入两个带 `legacy_code` 的默认配置；`NOT EXISTS` 防止重复。

#### Task 1.2：新增领域对象

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`

- 定义表映射实体、`BatchSendTaskConfigView`、创建/更新命令。
- 实体中的 `tagsJson` 只负责存储；API view 暴露 `tags: List<String>`。
- 创建命令不接收 `mailType`；更新命令不接收 `id`、`mailType`、审计字段。

### Phase 2：仓储和服务

#### Task 2.1：新增 Repository

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt`

- `findByIdAndDeletedAtIsNull(id)`。
- 未删除列表、名称模糊查询、名称冲突检查。
- `findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc()` 预留给计划 2。
- 禁止在控制器直接使用 `CrudRepository.save/delete`。

#### Task 2.2：新增配置服务

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

- 提供 `list(query)`、`get(id)`、`create(cmd)`、`update(id, cmd)`、`setEnabled(id, enabled)`、`softDelete(id)`。
- 名称 trim 后校验非空、长度、未删除唯一性。
- 标签 trim、去空、去重并稳定排序后 JSON 序列化。
- 校验漏斗层级白名单，规范化 `ALL` 为 null。
- 通过 `MailComposeTemplateService` 派生 `mailType` 并验证模板门禁（I-2）。
- `setEnabled(true)` 复用完整配置校验；删除时先置 `autoEnabled=false` 再写 `deletedAt`。
- create/update/setEnabled/softDelete 成功后发布现有 `BatchSendCronChangedEvent` 作为“全量重载”信号；计划 2 的 scheduler 不依赖事件中的 cron 值。
- 所有返回均转换为 view，不暴露 `tagsJson`、`deletedAt`。

### Phase 3：CRUD API

#### Task 3.1：扩展控制器

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`

新增：

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/mail/batch-send/configs?q=` | 配置列表/名称筛选 |
| POST | `/api/mail/batch-send/configs` | 新增，返回 201 |
| GET | `/api/mail/batch-send/configs/{id}` | 查看详情 |
| PUT | `/api/mail/batch-send/configs/{id}` | 全量更新 |
| PATCH | `/api/mail/batch-send/configs/{id}/enabled` | `{enabled}` 启停 |
| DELETE | `/api/mail/batch-send/configs/{id}` | 软删除，返回 204 |

- 非法参数 400、重名 409、不存在/已删除 404、模板不合法 422。
- 旧 `/types/{sendType}` 路由保留并标记兼容，不被新 UI 调用。

### Phase 4：自动化测试

#### Task 4.1：配置服务/控制器测试

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`

- 覆盖 CRUD、模糊查询、重名、软删除、启停合法性。
- 覆盖无模板默认 `INTRODUCTION`、模板派生类型、禁用/错类型模板拒绝。
- 覆盖空漏斗、标签规范化、非法范围/数值/cron。
- 覆盖删除后列表不可见但记录仍存在。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V72__create_batch_send_task_config.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 新增 |

共 6 文件，1 个子系统（配置后端），满足 create-p 限制。

## 验收标准

- V72 在空库和现有库均成功；旧配置迁移一次且不重复。
- 六个新 API 返回码、字段、排序符合契约。
- 配置名称可查询；重名、非法 cron、非法模板、非法范围均被拒绝且不产生部分更新。
- 漏斗为空、标签为空的 API 表达稳定为 `null`、`[]`。
- 删除后列表/详情 404，数据库记录和未来日志关联能力保留。
- 旧 typed API 仍可用，现有相关测试不回归。

## 人工验收清单

- [ ] 新建“每日材料提醒”，选材料模板，保存后详情的 `mailType=MATERIAL_REMINDER`。
- [ ] 新建不选模板的介绍任务，保存成功且类型为 `INTRODUCTION`。
- [ ] 漏斗不选、标签选两个，刷新后值完整恢复。
- [ ] 编辑名称/范围/限额后只更新当前配置。
- [ ] 关闭、再次启用配置，定时字段仍保留。
- [ ] 删除配置后列表不可见；数据库行仍在且 `deleted_at` 非空。
- [ ] 同名、禁用模板、非法 cron 均显示可理解错误。
