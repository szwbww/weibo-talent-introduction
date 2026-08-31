# 子计划 01：专家材料状态、接口与邮件变量

> 依赖：无。
> 后续计划：[`02-expert-material-tags-frontend.md`](./02-expert-material-tags-frontend.md)。
> 交付边界：本计划只交付持久化、API 与 `${pendingExpertMaterials}`；不修改任何邮件模板与前端页面。

---

## 需求描述

**Observable outcome**

1. 每个已有 `expert_contact` 固定拥有 7 个材料项：简历、护照、学位、工作、出版、专利、研究；操作员可把每项即时设为“待提供 / 已提供 / 暂不愿提供”，刷新后状态不丢失。
2. 新增邮件变量 `${pendingExpertMaterials}`。它只输出仍为“待提供”的材料英文正文，按固定目录顺序重新从 1 编号；“已提供”和“暂不愿提供”均被忽略；没有待提供项时输出空字符串。
3. 专家联系 API 提供完整 7 项列表与单项状态更新，供子计划 02 的标签 UI 使用。

**What must NOT change**

1. 不修改现有 `expert_contact` 字段、状态机、发件账号绑定、邮件发送状态或专家索引层级。
2. 不修改任何现有邮件模板主题、正文、内容块或 `mail_type`；本计划只注册并组装变量。
3. 不从附件、文件名、`expert_document` 或 AI 分析结果自动识别材料状态。
4. 无 `expert_contact.id` 的冷启动专家/随机 ES 预览中，该变量必须是空字符串；不得错误输出全部 7 项。
5. `MEETING_INVITATION`、`MEETING_CONFIRMATION` 等直接调用 `renderByCode(..., mapOf(...))` 的既有邮件路径不接入本变量，且现有输出不得变化。
6. 第八项“中国机构合作证明”不进入目录、接口、数据库或变量。

**Out of scope**

- 自定义新增、删除、排序或改名材料项。
- 文件上传、文件与材料项绑定、附件自动识别、AI 自动判定。
- 操作日志、批量修改、权限细分、乐观锁、状态修改备注。
- 邮件模板设计或默认模板数据迁移。
- 把 `${pendingExpertMaterials}` 接入会议类专用变量 Map。

---

## 关键不变量

### Invariant I1-1：目录固定且只有 7 项
- Rule：唯一目录及顺序固定为 `CV/简历`、`PASSPORT/护照`、`DEGREE/学位`、`EMPLOYMENT/工作`、`PUBLICATIONS/出版`、`PATENTS/专利`、`RESEARCH/研究`；目录由后端单一常量定义，数据库不保存目录正文；不得出现第八项。
- Applies to：材料列表 API、状态更新校验、变量组装、后端测试。
- Violation consequence：页面顺序、持久化代码和邮件正文会漂移，或重新引入已删除的第八项。
- 来源：original

### Invariant I1-2：缺行即 `PENDING`
- Rule：`expert_material_status` 只保存 `PROVIDED` 或 `DECLINED`；某联系人某材料无行时唯一解释为 `PENDING`。把状态改回 `PENDING` 必须删除该行，禁止显式保存 `PENDING`。
- Applies to：新表全部写路径、列表读取、变量读取。
- Violation consequence：需要给所有存量联系人回填 7 行；显式/隐式双表示会产生冲突并增加迁移风险。
- 来源：original

### Invariant I1-3：状态域严格为三值
- Rule：API 状态只接受 `PENDING`、`PROVIDED`、`DECLINED`；数据库持久行只允许后两者；未知材料 code 或状态在写入前以 `IllegalArgumentException` 拒绝，不得落库。
- Applies to：`ExpertMaterialService.updateStatus()`、控制器请求 DTO。
- Violation consequence：前端无法映射视觉状态，变量过滤结果不可预测。
- 来源：original

### Invariant I1-4：每个联系人每种材料至多一行
- Rule：数据库以 `(expert_contact_id, material_code)` 唯一约束；更新已有状态时保留其 `id` 走 Spring Data JDBC update，新状态首次出现才 insert。
- Applies to：V111 schema、repository 查询、`updateStatus()`。
- Violation consequence：同一材料出现互相冲突的多状态，读取结果依赖行顺序。
- 来源：original

### Invariant I1-5：变量过滤、顺序和编号唯一
- Rule：`${pendingExpertMaterials}` 只取解析后状态为 `PENDING` 的项；先按 I1-1 的目录顺序过滤，再对过滤结果从 1 连续编号。输出仅为英文编号行，以 `\n` 连接，不加标题、前言、结尾或空行。
- Applies to：`ExpertMaterialService.renderPendingMaterials()`、`MailVariableService.buildVariables()`。
- Violation consequence：邮件会继续索取已提供/拒绝材料，或出现断号和模板外文案。
- 来源：original

### Invariant I1-6：7 条英文正文逐字固定
- Rule：目录正文必须逐字为：
  1. `Your latest English CV, including education, employment, publications, patents, projects, awards, and honors.`
  2. `A copy of the personal information page of your valid passport.`
  3. `Your PhD degree certificate. Master’s and bachelor’s degree certificates may also be required.`
  4. `Proof of your current position and recent employment, such as employment letters, contracts, appointment letters, or official institutional documents.`
  5. `A list of your recent publications, patents, projects, awards, and other professional achievements.`
  6. `Supporting certificates for important patents, awards, qualifications, or editorial/reviewer roles, if available.`
  7. `A brief description of your recent research achievements and proposed research topic.`
- Applies to：后端目录常量、变量精确值测试。
- Violation consequence：邮件对专家的材料要求与已确认产品定义不一致。
- 来源：original

### Invariant I1-7：变量 key 永远存在，不泄漏占位符
- Rule：`MailVariableService.buildVariables()` 返回的 Map 必须始终包含 `pendingExpertMaterials`：真实联系人且材料服务可用时为 I1-5 输出；无 contact、contact.id 为 null 或测试构造未注入材料服务时为空字符串。生产的联系型 `renderForContact/renderHtmlForContact/renderPreview` 不得残留字面量 `${pendingExpertMaterials}`。
- Applies to：中心变量生产器、手动模板发送、待发送邮件渲染、自动回复渲染、模板/QA/AI 预览。
- Violation consequence：邮件正文泄漏模板语法，或把无跟踪上下文的专家误判成 7 项全缺。
- 来源：K-unsubscribe-variable-injection-sites、K-renderText-all-callers

### Invariant I1-8：变量元数据允许直接使用
- Rule：`MailPlaceholderService` 注册 key=`pendingExpertMaterials`、label=`待专家提供材料`、`esField=null`、非 nullable；它不得加入 `MailPlaceholderService.EXPERT_KEYS`，因此 `${pendingExpertMaterials}` 无需 fallback 即通过校验。示例值须使用 I1-6 的英文编号格式。
- Applies to：变量元数据、占位符校验、模板编辑器变量菜单、预览变量表。
- Violation consequence：用户无法保存直接占位符，或系统把运行时联系人状态误当 ES 字段。
- 来源：K-mail-placeholder-labels-are-semantic-contracts

### Invariant I1-9：旧联系人零回填可用
- Rule：V111 只建表和约束，不 INSERT 任何联系人材料行；迁移后所有存量联系人自然解析为 7 项 `PENDING`。
- Applies to：V111、列表读取、变量读取。
- Violation consequence：迁移体量随联系人数量放大，且回填中断会产生半成品状态。
- 来源：original

### Invariant I1-10：既有非中心渲染路径不漂移
- Rule：`IntroductionMailComposer.buildVariables(account, expert)` 因无 contact 得到空材料变量；`MeetingScheduleService`、`MeetingInvitationMailComposer`、`AutoMailReplyService.sendMeetingInvitation()` 的专用 Map 与当前模板输出保持不变。V1 只承诺联系型中心变量路径消费真实材料状态。
- Applies to：所有 `buildVariables/renderForContact/renderHtmlForContact/renderPreview/renderByCode` 调用点。
- Violation consequence：初次开发材料提醒却改变首封或会议邮件。
- 来源：K-manual-expert-mail-sender-only-variables、K-renderText-all-callers

---

## 现状审计

### `expert_contact` 与新 `expert_material_status` 表

- Schema/mapping：`V1__create_business_tables.sql:79-95` 定义 `expert_contact.id BIGINT AUTO_INCREMENT`，并以 `(campaign_id, orcid_id)` 唯一；现有子表外键均引用 `expert_contact(id)`，例如 `V8__add_expert_contact_status_history.sql:1-12`、`V9__create_meeting_schedule_and_template.sql:1-15`，均未声明级联删除。`ExpertContact` 映射位于 `campaign/domain/ExpertContact.kt:7-35`。
- 迁移序号：全量 `find ... | sort -V` 的最高文件是 `V110__require_expert_types_on_batch_send_task_config.sql`，因此本计划唯一合法新序号为 V111。
- Flyway：`application.yml:8-13` 已设置 `placeholder-replacement:false`；本迁移不含模板 `${...}`，且不得修改该配置（来源：K-flyway-placeholder-replacement）。
- Existing write paths：生产代码未发现删除 `expert_contact` 的入口；现有 repository 对联系人做 save/update，但本计划不修改这些路径（来源：K-expert-contact-two-write-sites，已重新 grep）。
- New write paths：仅 `ExpertMaterialService.updateStatus()`：`PROVIDED/DECLINED -> repository.save()`；`PENDING -> repository.deleteById()`。
- New read paths：`ExpertMaterialService.listMaterials()` 为 API 读取；`renderPendingMaterials()` 为邮件变量读取。
- Interaction points：状态 API 写入 → 列表 API 重新读取；状态 API 写入 → `MailVariableService` 组装邮件变量。

### 邮件变量注册与渲染

- Producer：`MailVariableService.buildVariables()`（`mail/service/MailVariableService.kt:117-159`）当前组合 sender、expert、unsubscribe 三组 Map，是新增 key 的唯一生产点（来源：K-unsubscribe-variable-injection-sites）。
- Metadata：`MailPlaceholderService.variableMetadata()`（`:7-15`）从 `VARIABLE_LABELS/VARIABLE_EXAMPLES/ES_FIELD_BY_KEY` 生成元数据；`:41-56` 仅要求 `EXPERT_KEYS` 使用 fallback。变量 label 属语义契约（来源：K-mail-placeholder-labels-are-semantic-contracts）。
- Contact-backed consumers：
  1. `ManualExpertMailService.composeComposeTemplate()`（`:200-225`）传入真实 contact 调用完整 `buildVariables()`；`:253-264` 将带换行的纯文本安全转换为 HTML。
  2. `PendingMailOperationService`（`:222-246`）通过 `renderForContact/renderHtmlForContact` 渲染主题、文本和 HTML。
  3. `AutoMailReplyService:598`、`AutoReplyPreviewService:122` 调用 `renderForContact`。
  4. `MailComposeTemplateService.previewDraft()`（`:221-279`）、`QaRuleManagementController:42-50`、`AiReplyDraftPreviewService:25` 调用 `renderPreview`。
- No-contact consumer：`IntroductionMailComposer:55-56` 调 `buildVariables(account, expert)`，不传 contact。
- Bypass consumers：`MeetingScheduleService:119-132`、`MeetingInvitationMailComposer:14-19`、`AutoMailReplyService:989-993` 直接把专用 Map 传给 `renderByCode`，不经过中心变量服务。全仓未发现 `${pendingExpertMaterials}`，所以现状无漏替换；本计划不得把新 key 写入现有模板。
- Legacy test fallback：`ManualExpertMailService:215-220` 在 `mailVariableService == null` 时以 `MailVariableService.EXPERT_KEYS` 生成空专家值；`MailVariableService.EXPERT_KEYS` 需覆盖新 contact-derived key，保证旧构造路径也不泄漏字面量（来源：K-manual-expert-mail-sender-only-variables）。
- Interaction points：材料表读取 → 中心 Map → `MailComposeTemplateService.renderText()`（`:611-619`）唯一替换器 → 手动发送/待发送/自动回复/预览（来源：K-renderText-all-callers）。

### 专家材料 API

- Existing controller：`ExpertContactManagementController` 根路径为 `/api/expert-contacts`（`:42-51`）；详情 GET 位于 `:71-73`，PUT 注解已用于会议更新（`:211-217`）。
- Error mapping：`GlobalExceptionHandler:16-26` 把 `IllegalArgumentException` 映射 400、`NoSuchElementException` 映射 404。
- Existing test seam：`ExpertContactManagementControllerTest:16-25` 直接构造 controller，新增必需依赖必须同步补 mock；该文件适合断言 GET/PUT 的参数透传和响应。

---

## 实现方案

### Task 1：建立稀疏材料状态表（I1-1、I1-2、I1-3、I1-4、I1-9）

修改 `src/main/resources/db/migration/V111__create_expert_material_status.sql`：

```sql
CREATE TABLE expert_material_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    material_code VARCHAR(32) NOT NULL,
    material_status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_expert_material_contact_code (expert_contact_id, material_code),
    CONSTRAINT chk_expert_material_code
        CHECK (material_code IN ('CV', 'PASSPORT', 'DEGREE', 'EMPLOYMENT', 'PUBLICATIONS', 'PATENTS', 'RESEARCH')),
    CONSTRAINT chk_expert_material_status
        CHECK (material_status IN ('PROVIDED', 'DECLINED')),
    CONSTRAINT fk_expert_material_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
```

- 不加默认状态、不插入 7 项、不加 `ON DELETE CASCADE`；分别遵守 I1-2、I1-9 和现有外键基线。
- `UNIQUE KEY` 同时覆盖按 `expert_contact_id + material_code` 的点查，不再增加重复索引。
- 修改 `campaign/repository/FlywayMigrationIntegrationTest.kt`：把 5 个“迁移至当前最新版本”的旧 `28` 期望统一更新为 `111`（显式 target 23/24 的断言保持不动），新增 V111 测试断言表、6 个字段、唯一键、两个 CHECK 和外键存在，且新表初始行数为 0。该测试当前仍把最新版本写死为 28；若不更新，开启 `migrationIt` 后会在实际迁移到 V111 后误报失败。

### Task 2：定义实体与唯一 repository（I1-2、I1-3、I1-4）

1. 新增 `campaign/domain/ExpertMaterialStatusRecord.kt`：字段严格对应 V111：`id`、`expertContactId`、`materialCode`、`materialStatus`、`createdAt`、`updatedAt`，使用 `@Table("expert_material_status")` 与 `@Id`。
2. 新增 `campaign/repository/ExpertMaterialStatusRepository.kt`，只声明：
   - `findAllByExpertContactId(expertContactId)`；
   - `findByExpertContactIdAndMaterialCode(expertContactId, materialCode)`；
   - save/deleteById 复用 `CrudRepository`。
3. 不暴露全表扫描、批量覆盖或按 label 查询。

### Task 3：实现固定目录、状态解析和变量正文（I1-1 至 I1-6、I1-9）

新增 `campaign/service/ExpertMaterialService.kt`：

1. 定义 `ExpertMaterialCode` 枚举，按 I1-1 顺序携带 `label` 与 I1-6 `requestText`；定义 `ExpertMaterialProvisionStatus { PENDING, PROVIDED, DECLINED }`；定义 API 可返回的 `ExpertMaterialItem(code, label, status)`。
2. `listMaterials(contactId)` 先用 `ExpertContactRepository.findById` 校验联系人存在，再一次读取全部稀疏行，以 `materialCode` 建 Map；遍历 enum 产生完整 7 项，缺行解析为 `PENDING`。
3. `@Transactional updateStatus(contactId, rawCode, rawStatus)`：先解析并校验 code/status；校验联系人；`PENDING` 删除已有行；其余状态保存新行或 `existing.copy(materialStatus=..., updatedAt=now)`；最后返回完整 7 项。
4. `renderPendingMaterials(contactId)` 读取稀疏行但不重复查询联系人（调用方已有真实 contact），按 I1-5 过滤、重新编号并 `joinToString("\n")`；空集合返回 `""`。
5. Service 是目录、状态转换、英文正文的唯一真源；controller 与前端不得复制英文正文。

### Task 4：增加 GET/PUT API（I1-1、I1-3、I1-4）

修改 `campaign/controller/ExpertContactManagementController.kt`：

1. 构造器新增必需 `ExpertMaterialService`。
2. `GET /api/expert-contacts/{contactId}/materials` 调 `listMaterials()`，返回按目录排序的 7 个 `ExpertMaterialItem`。
3. `PUT /api/expert-contacts/{contactId}/materials/{materialCode}` 接收 `{ "status": "PENDING|PROVIDED|DECLINED" }`，调 `updateStatus()` 并返回更新后的完整 7 项。
4. 不把 materials 塞进现有 `ExpertContactDetailResponse`，避免详情主接口与子资源状态耦合；子计划 02 并行请求该资源。
5. 修改 `ExpertContactManagementControllerTest.kt`：补构造器 mock；分别断言 GET、PUT 路径方法把 contactId/code/status 原值传给 service 并返回 7 项。

### Task 5：注册并组装 `${pendingExpertMaterials}`（I1-5、I1-6、I1-7、I1-8、I1-10）

1. 修改 `MailPlaceholderService.kt`：在三个有序 Map 的末尾、`unsubscribeUrl` 前加入 `pendingExpertMaterials`，label=`待专家提供材料`、`esField=null`、example=两条以上 I1-6 编号英文；不得加入该类自己的 `EXPERT_KEYS`。
2. 修改 `MailVariableService.kt`：
   - 构造器末尾新增可空默认依赖 `expertMaterialService: ExpertMaterialService? = null`，沿用当前 `unsubscribeTokenService? = null` 的测试兼容范式；Spring 生产环境注入真实 bean。
   - `buildVariables()` 增加始终存在的 `materialVars`：仅 `contact?.id` 与 service 均非 null 时调用 `renderPendingMaterials`，否则 `""`。
   - 把公开 `MailVariableService.EXPERT_KEYS` 定义为 profile keys 加 `pendingExpertMaterials`，但构造 expert-null Map 时仍只使用 private profile keys；这样 `ManualExpertMailService:219` 的旧测试 fallback 自动包含空值，同时 `MailPlaceholderService.EXPERT_KEYS` 的 nullable 校验语义不变。
3. 不修改 `MailComposeTemplateService.renderText()`；真实 contact 的中心变量 Map 已保证 key 存在，未知占位符仍保持现有“原样保留”语义。
4. 修改 `MailVariableServiceTest.kt`：增加材料 service/repository mock 场景，覆盖默认 7 项、三态写入/删除、过滤重编号、全排除为空、真实 contact 渲染无残留、无 contact 为空、元数据非 nullable/null ES 字段/固定 label；保留并扩展 `VARIABLE_LABELS.keys == variables.keys` 断言。

---

## 变更文件清单

| # | 文件 | 操作 | 作用 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V111__create_expert_material_status.sql` | 新增 | 稀疏状态表与唯一/FK 约束 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertMaterialStatusRecord.kt` | 新增 | Spring Data JDBC 实体 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertMaterialStatusRepository.kt` | 新增 | 材料状态唯一读写入口 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt` | 新增 | 固定目录、三态、列表与变量正文 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 | GET/PUT 子资源接口 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt` | 修改 | 中心 Map 注入变量 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt` | 修改 | 注册变量元数据与校验语义 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt` | 修改 | 服务状态语义与变量测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | 修改 | controller 构造与 GET/PUT 透传测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 | 最新版本期望与 V111 schema 集成断言 |

文件数：10。子系统数：2（campaign 材料状态；mail 变量）。不允许执行阶段修改表外文件。

---

## 验收标准

- I1-1：单测断言 GET/list 始终精确返回 7 项、固定 code/中文 label/顺序，且全仓 `rg "Chinese institutions|合作证明"` 不在新功能代码中命中。
- I1-2：repository mock 断言新联系人不 save；`PENDING` 对已有行只 `deleteById`；列表缺行显示 `PENDING`。
- I1-3：未知 code/status 单测抛 `IllegalArgumentException`，且 verify repository 零写入；V111 同时有 code/status CHECK 约束。
- I1-4：V111 文本含唯一键；更新既有状态传给 save 的实体保留非空 id。
- I1-5：给定 CV=`PROVIDED`、PASSPORT=`PROVIDED`、EMPLOYMENT=`DECLINED`，变量精确等于 4 行并从 1 连续编号：学位、出版、专利、研究。
- I1-6：对 7 项全 pending 做整串精确相等断言，包含弯引号 `Master’s`，不接受近似文案。
- I1-7：真实 contact 的 plain/html/preview 至少各断言一次不含 `${pendingExpertMaterials}`；无 contact 与 null service 断言 key 存在且值为 `""`。
- I1-8：metadata 断言 label=`待专家提供材料`、nullable=false、esField=null、example 非空；`validatePlaceholders("\${pendingExpertMaterials}")` 返回空列表。
- I1-9：V111 不含 `INSERT`；Flyway 集成测试确认空库迁移目标版本 111、新表为 0 行、表/列/唯一键/两个 CHECK/外键存在（来源：K-flyway-placeholder-replacement）。
- I1-10：现有 introduction、meeting、manual mail 测试保持通过；全仓现有模板迁移不出现新 key。
- Interaction：PUT 写 `PROVIDED/DECLINED` → GET 返回相同状态 → `renderPendingMaterials` 同步排除；改回 `PENDING` → GET 恢复待提供且变量重新包含。
- 自动命令：
  1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -q -DskipNodeTests=true -Dtest=MailVariableServiceTest,ExpertContactManagementControllerTest,ManualExpertMailServiceTest,IntroductionMailComposerTest,MeetingScheduleServiceTest test`
  2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
  3. 可用 Docker 时：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`

---

## 人工验收清单

### A1-1：新/存量联系人默认 7 项
- 前置条件：部署 V111；选择一个从未修改材料状态的已有 contactId。
- 操作步骤：1. 请求 `GET /api/expert-contacts/{contactId}/materials`。2. 记录响应顺序。
- 预期结果：HTTP 200；依次为 `CV/简历`、`PASSPORT/护照`、`DEGREE/学位`、`EMPLOYMENT/工作`、`PUBLICATIONS/出版`、`PATENTS/专利`、`RESEARCH/研究`；7 项 status 均为 `PENDING`。
- 覆盖：I1-1、I1-2、I1-9、Observable 1/3

### A1-2：三态即时持久化
- 前置条件：A1-1 的 contactId。
- 操作步骤：1. PUT `CV` 为 `PROVIDED`。2. PUT `EMPLOYMENT` 为 `DECLINED`。3. 重新 GET。4. PUT `CV` 为 `PENDING`。5. 再次 GET。
- 预期结果：步骤 3 中简历=`PROVIDED`、工作=`DECLINED`，其余=`PENDING`；步骤 5 中简历恢复=`PENDING`；任何一步都没有重复材料项。
- 覆盖：I1-2、I1-3、I1-4、Interaction API 写→API 读

### A1-3：变量过滤并重新编号
- 前置条件：把简历、护照设为 `PROVIDED`，工作设为 `DECLINED`，其余为 `PENDING`；准备一个正文只含 `${pendingExpertMaterials}` 的联系型模板预览。
- 操作步骤：以该 contactId 打开服务端模板预览。
- 预期结果：正文精确为 4 行；第 1 行是学位正文，第 2 行是出版正文，第 3 行是专利正文，第 4 行是研究正文；不出现简历、护照、工作正文，不出现 `${pendingExpertMaterials}`。
- 覆盖：I1-5、I1-6、I1-7、Interaction API 写→变量读

### A1-4：无待提供项为空
- 前置条件：把 7 项分别设为 `PROVIDED` 或 `DECLINED`；模板正文为 `Before\n${pendingExpertMaterials}\nAfter`。
- 操作步骤：以真实 contactId 预览。
- 预期结果：变量位置为空，不出现编号行和占位符字面量；`Before`、`After` 保留。
- 覆盖：I1-5、I1-7、Observable 2

### A1-5：无 contact 上下文不误索取
- 前置条件：模板含 `${pendingExpertMaterials}`；选择仅有 ORCID、无 contactId 的随机 ES 专家预览。
- 操作步骤：执行模板预览。
- 预期结果：变量值为空；不出现 7 条默认材料正文，不出现占位符字面量。
- 覆盖：I1-7、I1-10、Must NOT change 4

### A1-6：非法输入与不存在联系人
- 前置条件：任意有效 contactId 和一个不存在的 contactId。
- 操作步骤：1. PUT code=`UNKNOWN`。2. PUT status=`DONE`。3. GET 不存在 contactId。
- 预期结果：步骤 1、2 HTTP 400 且数据库无新增/修改；步骤 3 HTTP 404。
- 覆盖：I1-3

### A1-7：现有邮件路径回归
- 前置条件：保留一份当前 introduction、meeting invitation、meeting confirmation 的基准预览；现有模板不加入新变量。
- 操作步骤：升级后用同一专家、发件账号和模板再次预览三类邮件。
- 预期结果：三类主题与正文与基准一致；手动 MATERIAL_REMINDER 模板在显式加入 `${pendingExpertMaterials}` 后可读取真实联系人状态。
- 覆盖：I1-10、Must NOT change 1/2/5

### A1-8：不做文件自动识别
- 前置条件：某联系人 7 项均 `PENDING`。
- 操作步骤：给该联系人上传名为 `passport.pdf` 的附件并刷新材料 API。
- 预期结果：护照仍为 `PENDING`；只有显式 PUT 才改变状态。
- 覆盖：Must NOT change 3、Out of scope 文件识别

### A1-9：联系人状态机不受材料更新影响
- 前置条件：记录某联系人的 `currentStatus`、`operatorStatus`、`currentIndexLevel`、`boundSenderAccountCode` 与 `autoReplyEnabled`。
- 操作步骤：依次把简历设为 `PROVIDED`、工作设为 `DECLINED`，再重新请求联系人详情。
- 预期结果：5 个已记录字段逐值不变；材料 PUT 不新增邮件记录、不触发状态流转。
- 覆盖：Must NOT change 1
