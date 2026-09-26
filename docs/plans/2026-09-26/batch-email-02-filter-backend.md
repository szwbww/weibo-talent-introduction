# 子计划 02：过滤配置和候选筛选后端

状态：DRAFT，仅创建开发计划，未实施、未运行测试、未部署。
目标工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，审计分支 `main`，HEAD `d6f54c25b228ee2e9e0317d053957ae3f56984b5`。当前已有其他未提交改动，见证据 E-00；执行前重查，禁止覆盖。
证据：[源码快照](batch-email-reliability-evidence.md)。E-n 均包含读取命令/原始输出或带原文件行号的摘录；下文“拟改”是设计决策，不是现状事实。

依赖：子计划 01 已独立验证。该子计划完成后 REST 配置、预估、执行同时生效；不发布“能保存但不生效”的中间后端。

## 需求描述

新增 `excludeVerifiedUnavailableEmails`，开启时在候选生成阶段排除当前收件邮箱最新有效历史结论为 undeliverable 的目标；介绍邮件与材料提醒均支持。预估返回过滤后人数与排除人数。

必须保持：旧配置/旧快照默认关闭；原有门禁、退订、已发去重、账号绑定与轮次额度；发送前实时验证仍由独立 emailVerificationEnabled 控制；手动快照不回写来源定时配置。

范围外：新增全局设置、批量重验、SMTP/账号分配改造、ES 字段/映射迁移、跨层去重统计口径重写、导出明细与自动补发。

## 关键不变量

### Invariant I-1：兼容与持久化
- Rule：DB/entity/view/snapshot/scope 缺省 false；create command 缺省 true（新建）；update command `Boolean?=null` 表示保留，显式 false 才关闭。迁移一个 NOT NULL BOOLEAN DEFAULT FALSE 列，存量不启用。开启后编辑无关字段、启停、旧 typed API 不得清零。
- Applies to：create/update/setEnabled/softDelete/updateLegacyConfig、toFields/toView/toExecutionSnapshot、手动 JSON 反序列化。
- Violation consequence：历史定时任务自动改人群，或更新丢开关。
- 来源：K-batch-config-legacy-adapter-field-preservation；E-03/E-23。

### Invariant I-2：过滤与实时验证独立
- Rule：过滤只调用 01 的只读 helper；无密钥也可运行。过滤 off 时不做新历史查询；验证 off + 过滤 on 只读历史；两者 off 保持原路径。仅已验证 undeliverable 排除；ERROR/过期/unknown/risky/无记录放入原队列，不承诺最终发送。
- Applies to：预估、介绍邮件、材料提醒；新字段不沿用“只有 INTRODUCTION 允许验证”的校验。
- Violation consequence：开关互相覆盖、预估产生付费或错误排除。
- 来源：original；E-05/E-14。

### Invariant I-3：目标地址和候选路径一致
- Rule：介绍邮件依据 expert.email；材料提醒依据 contact.expertEmail（真正发送地址，E-07:1529/E-06 对照）；预估和执行使用同一批量 helper，保留原候选顺序与去重优先级。同一次预估/执行固定 now 作有效期边界，但不承诺两个不同请求之间数据库不变。
- Applies to：buildRetryableTargets、countBySnapshot、runIntroductionFromSnapshot、buildMaterialReminderSnapshotFromScope。
- Violation consequence：列表数已排除但重试仍发，或按错误邮箱排除材料提醒。
- 来源：K-batch-send-filter-retry-parity、K-batch-snapshot-two-write-entrances；E-06/E-07。

### Invariant I-4：原始页控制翻页
- Rule：OutreachTargetIterator 依据原始 page.size 判断结束/推进 offset，然后执行去重与新的 pageFilter。全页被排除不是末页；过滤结果不得反过来缩短 fetchEsPage 的原始页。保留现有返回有用页后 offset=0 的收缩集合行为。
- Applies to：OutreachTargetIterator、fetchEsPage 调用；retryable targets 的 seenOrcids 生命周期保持原先优先级。
- Violation consequence：前页坏邮箱多时，后续正常邮箱永远无法发送。
- 来源：original；E-07:1568/E-08；现有 shrinks/filtered 测试 E-20。

### Invariant I-5：统计与副作用
- Rule：PendingOutreachSummary 新增派生字段 `excludedVerifiedUnavailable: Int = 0`；pending/retryable/totalSendable 为过滤后沿用原统计口径的数。被预排除目标不进入验证明细、不标标签、不建/绑定 contact、不记本次发送跳过/失败，不占成功发送额度。预估仅统计不持有全量 ExpertProfile。
- Applies to：预估 helper、两类发送候选构造、页面结果。
- Violation consequence：仍反复刷“验证未通过”，虚增发送处理量，或预估内存膨胀。
- 来源：original；E-06/E-07；derived count 非新数据库字段。

## 现状审计

### batch_send_task_config
- Schema：V72 原表含 template FK、活动名称 generated column 唯一键；后续 V91/V92/V93/V95/V97/V98/V99/V103/V108/V110/V128/V135/V139 的累积变更，原文检索 E-22；当前 entity 见 E-23。未硬编码 SQL insert 列表于 repository，Spring Data JDBC 根据实体字段映射。
- 写路径：service.create→saveConfig，update→existing.copy/saveConfig，setEnabled→copy/save，softDelete→copy/save，updateLegacyConfig→全量 update；历史 Flyway 建表/种子/ALTER/UPDATE 全集 E-01/E-22。仅新迁移可改 schema。
- 读路径：配置 list/get/toView；setEnabled.toFields 校验；旧配置适配 getLegacyConfig/toLegacyConfig；BatchSendControlService 启动时读实体生成快照；BatchSendScheduler 读 autoEnabled/cron；template 删除引用检查。E-02/E-03/E-11。
- IP-1：新增实体列→View/编辑回填；IP-2：旧适配器 update→不得清新字段；IP-3：配置→快照→预估/执行→request_payload 历史回显。

### 验证历史、ES、联系人
- 验证历史写入/读取/删除在 01 的现状审计中完整列出；本子计划仅调用 01 helper，无新写方。
- ES 三层 mapping dynamic=false，email keyword 未声明 normalizer（E-15）。因此不能直接把 lowercased 历史地址塞到 ES terms 里宣称匹配等价；本方案在 JVM 用已存在的 normalizeVerificationEmail 对照。无 ES 写路径变更，既有 addTag 只在实际验证 undeliverable 时调用，不由过滤触发。
- E-06/E-07：ES 目标计数 countEsTargets、取页 fetchEsPage；DB NEW 联系人由 buildRetryableTargets 关联各层 profiles；材料提醒另走完整 snapshot，取 contact.expertEmail。
- E-09：现成 scrollExpertsFiltered 每批 callback、finally 清 scroll，可做大人群只读计数；原 searchExpertsFiltered 用 from/size，不具备全量深分页保证。
- IP-4：同一过滤判据贯通 ES、新目标、NEW 重试、材料提醒、预估。禁止只改显示人数。

### task_execution / runtime
- 请求通过 BatchSendControlService.launchFromSnapshot→TaskExecutionService.runAndRecordWithResult 序列化到 request_payload；历史 controller 原样读 JSON（E-11/E-13/E-14）。本次不改 schema/写入服务，新增快照字段由 Jackson 自然携带。
- 旧请求缺字段按 false；独立手动请求不依赖来源配置。现代定时入口没有 manageRuntimeStatus=true，不能声称会自动关闭后续 cron。

## 实现方案

### T1：迁移及配置全链（I-1/I-2/I-3）
文件：BatchSendTaskConfig.kt、BatchExecutionModels.kt、BatchSendTaskConfigService.kt、V142__add_exclude_verified_unavailable_emails.sql。

```sql
ALTER TABLE batch_send_task_config
    ADD COLUMN exclude_verified_unavailable_emails BOOLEAN NOT NULL DEFAULT FALSE;
```

当前最高版本为 V141（E-15）；实施前再次检索版本，若 V142 已占用须修订计划的确切路径，不改已应用迁移。

- entity/view/snapshot/RecipientScope 增默认 false 的同名属性；create command=true，update command nullable=null。
- service.update 合并 `cmd.excludeVerifiedUnavailableEmails ?: existing.excludeVerifiedUnavailableEmails`；normalized/configFields、三个 toFields、create、copy update、toView 全量逐点传递；旧 updateLegacyConfig 显式 existing 值。
- fromSnapshot、toExecutionSnapshot 原样携带；仅派生 BatchSendConfig 的旧 KV DTO 不新增该字段，过滤读取 snapshot/scope，避免拉大旧设置接口。
- 不改 controller：它已直接接收 Command/Snapshot，没有额外请求 DTO（E-14）。scheduler 同样无需修改。

### T2：一个只读批量过滤 helper 贯通目标（I-2/I-3/I-5）
文件：ManualInitialOutreachService.kt。

在该 service 内用 01 helper 对目标集合的实际收件邮箱批量查集合，然后按规范化邮箱 filterNot；不新建服务层/缓存层。入口固定北京 now，传入各候选 helper；可用默认参数保留旧 typed 调用的 false 行为。

- 重试：先沿用原联系人/状态/绑定/范围判定与去重，再批量排除。保留原先 seenOrcids 的优先级，防同一个重试目标从 ES 重新入队。仅对原本命中的候选计排除数量。
- 材料提醒：在当前最终 sendableTargets 构建后，按 contact.expertEmail 批量排除；保留原 10000 上限，不扩容量；同时返回派生排除计数。
- ES 预估：off 保持 `_count` 快路径；on 使用已有 scrollExpertsFiltered（500/批）读取相同 buildEsFiltersForLevel 条件，每页经同 helper 得保留/排除数量，只累加计数。支持预估大于 10000 的候选，不用 from/size 扫全量。两类 countBySnapshot 组装相同新增返回字段。
- `pending + retryable = totalSendable`。本次不修复既有跨层/双来源估算可能重复的问题；继续明确为候选预估，不虚称“精确成功发送数”。排除数按原本候选条目计，不按不同邮箱个数计。
- runIntroductionFromSnapshot 的 totalEstimate 采用同一计数 helper；原过滤开关为 false 的方法分支保持旧行为，避免全量扫描回归。

### T3：页内过滤接入执行（I-3/I-4/I-5）
文件：OutreachTargetIterator.kt、ManualInitialOutreachService.kt。

新增默认恒等的 `filterPage: (List<ExpertProfile>) -> List<ExpertProfile>`；原始页先判断末页，按原规则从 seenOrcids 筛出未见目标，再对未见目标一次批量过滤。全页排除后按原始页长度推进并继续，非空 buffer 仍保持原 offset 重置行为。使用 callback 输入/输出保序，禁止重新排序、扩大集合。

过滤 on 时传入 T2 helper；off 保持默认回调、零历史查询。同执行刚完成的新验证结果可被后续批次读到；不做跨页永久负缓存。

长扫描需可取消：增加默认 false 的 `shouldStop` callback，在 hasNext/loadNextEsPage 取页前检查，执行方传现有 progressStore 取消检查；停止后 runIntroductionFromSnapshot 收尾再次读取取消标记，持久化 CANCELLED，不误报 COMPLETED。不引入后台线程。

代码已有 offset 分页的深分页限制，见 E-09；本计划不承诺消除整个旧引擎的深分页限制，也不改查询排序/scroll 引擎。验收须证明“过滤不制造新的短页截断”，而非把既有大规模分页问题藏入本修复。

### T4：配置与执行回归（I-1～I-5）
文件：清单中的四个测试文件。复用 ConfigServiceTest 已有 toExecutionSnapshot 断言（E-30），补旧JSON反序列化、snapshot/scope 映射；原 RuntimeIntegrationTest 只运行不修改。FlywayMigrationIntegrationTest 增 V141→V142 旧行 false、新列保存 true/readback、NOT NULL/default 验证，并将该文件所有“迁移至最新”的 targetSchemaVersion 141 断言同步为142；历史明确 target 的测试边界不改（原命中全集 E-30）。不为此新建测试框架。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | entity/view/create/update 新字段 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | snapshot/scope/toExecutionSnapshot 新字段 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | create/update/legacy/view 全链传递 |
| `src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql` | 一个布尔列，存量默认 false |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 预估、新目标、重试、材料提醒共同过滤 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` | 原始页判末页后批量过滤；取消响应 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 默认、合并、legacy 保留 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 双来源、材料地址、预估、额度及副作用测试 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt` | 整页过滤不提前结束、数据收缩及取消 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | V141→V142 存量默认/新列实际写读；最新版本断言更新 |

共 10 个文件；一个批量发送子系统，一列配置变更。

## 验收标准

- I-1：存量/历史快照=false；新 create 缺省=true；update null/missing 保留；显式 false 关闭；旧 typed update、enable/disable 不变；scheduled/manual/config-to-snapshot/JSON roundtrip 值一致，手动覆盖不更新配置行。
- I-2：四种组合过滤×实时验证覆盖；无 API key + 过滤 on/验证 off 能预估和发送正常候选；两者 off 保持旧零验证服务调用断言；材料提醒允许历史过滤但仍拒绝实时验证 true。
- I-3：未联系与重试同邮箱同结论都被排除；地址变更到新邮箱不被旧记录误排除；材料 contact 邮箱与 profile 邮箱刻意不同，按 contact 的结果过滤；同 now 一年边界一致。
- I-4：前一整页全部排除、下一页有可投递目标仍能发送；整页重复也继续；末页全部排除能退出；数据发送后缩小仍不漏人；取消后不再查下一页且 taskFinalStatus=CANCELLED。
- I-5：固定无重叠 fixture：ES 3 人（1 坏），retry 2 人（1 坏）→ pending=2/retryable=1/totalSendable=3/excludedVerifiedUnavailable=2；只发三位；两位被排除者零验证行/零发送行/零新联系绑定/零标签变更。关闭时回到原 3+2；材料同样验证。排除人数≠邮件唯一数。
- 性能结构：每 500 个不同邮箱最多一次批量查询，不出现每邮箱 SELECT；预估通过 scroll callback 分批计数，不积累全量 profiles；全部命中被过滤时正常返回 0，执行不选号不发信。
- 命令：
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest test`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
  - 由上述 FlywayMigrationIntegrationTest 新增场景验证真实旧行 false、新列 SQL 写读 true 及 column NOT NULL/DEFAULT FALSE；服务层 repository.save/read 的字段链由 ConfigServiceTest 捕获实体和查询回显共同验证。禁止仅凭 DTO 单测宣称迁移通过。
  - `git diff --check`
- 若真实数据库迁移验证无法运行，必须列为阻塞，不能宣称完成。

## 人工验收清单

### A-1：存量与新建配置
- 前置条件：独立测试库先按 V141 创建一条测试任务，再迁移 V142。
- 操作步骤：1. GET 旧配置。2. POST 新配置，省略新字段。3. 将新配置置 false，PUT 更新。4. 打开后用旧 typed API 改轮次数。5. 启停任务再 GET。
- 预期结果：旧=false；新=true；显式 false 能保存；旧 API 与启停不重置已开启状态。
- 覆盖：I-1，IP-1/IP-2；旧行为兼容。

### A-2：同范围预估与执行
- 前置条件：使用隔离 SMTP sink，准备验收 I-5 的 5 个无重叠目标，两个坏邮箱已有 01 可用历史记录；账号/模板具备发送条件。
- 操作步骤：1. POST `/api/mail/batch-send/recipients/preview`，过滤 true、实时验证 false。2. POST manual-executions 同快照。3. 对照 SMTP sink 和验证表。4. 过滤 false 再预估，不发送。
- 预期结果：2/1/3/2 的预估；执行只向三人投递；被排除两人无本次验证行与绑定写入；关闭预估回到 3/2/5/0。
- 覆盖：I-2/I-3/I-5、IP-3/IP-4；无付费/无误标。

### A-3：材料地址、门禁和翻页
- 前置条件：材料联系人邮箱 A=坏、profile 邮箱 B=好；另备介绍候选第一整页坏、后页好；退订和已发目标各一位，均用 SMTP sink。
- 操作步骤：1. 材料过滤 true 预估并执行。2. 介绍过滤 true 执行。3. 观察退订/已发目标。4. 扫描中取消。
- 预期结果：材料 A 被排除；后页好邮箱可发送；退订/已发仍不发送；取消后不继续取页且显示 CANCELLED；不改变账号选择/成功额度。
- 覆盖：I-3/I-4/I-5、IP-4；原门禁回归。

### A-4：手动快照独立
- 前置条件：保存过滤=true 的定时配置，自动发送关闭，使用该配置派生手动请求。
- 操作步骤：1. 手动请求显式 false。2. 执行并查看历史 requestSnapshot。3. GET 来源配置。
- 预期结果：执行快照=false，来源配置仍=true；历史缺字段的请求按 false 读取；定时生成快照仍取配置值。
- 覆盖：I-1/I-3，IP-3；手动不回写规则。
