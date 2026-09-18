# 材料索取：独立 5 项状态接口

依赖 `01-material-request-cache-fixtures.md` 仅用于后续 UI 发布；本后端计划自身可单独部署。UI 计划见 `03-material-request-ui.md`。

## 需求描述

- 可观察结果：一个专家的材料索取接口固定返回 5 项（代表性论文、科研项目、专利、荣誉奖项、学位），带三态和用户给出的英文正文；可逐项手动改为待提供、已提供、暂不愿提供。
- 必须保持：旧 7 项状态记录、`${pendingExpertMaterials}` 的旧 7 项模板变量、RAG 对 `CV` 的判断、`GET /materials` 上传文件分页接口、旧状态 PUT 不变。
- 不做：文件自动识别、发送邮件、修改模板、把旧 7 项状态自动换算为新 5 项状态。旧新材料描述不等价，故不得猜测映射。

## 关键不变量

### Invariant I-1：新旧状态隔离
- Rule：新目录代码严格为 `REQ_PUBLICATIONS, REQ_PROJECTS, REQ_PATENTS, REQ_AWARDS, REQ_DEGREES`，按此顺序返回；旧 7 代码与已有行原样保留。新旧目录均使用同一稀疏表，但从不互相推断状态。
- Applies to：V128 约束、`ExpertMaterialService` 新读写、旧 `updateStatus` 与 `renderPendingMaterials`。
- Violation consequence：旧「出版清单已提供」可能误判为新「代表性论文副本已提供」，或旧 `CV` 状态丢失导致 RAG 错索简历。
- 来源：original；`RagProcessContextResolver.kt:31` 已复核旧 `CV` 读取。

### Invariant I-2：三态稀疏语义
- Rule：新 5 项缺行 = `PENDING`；`PROVIDED/DECLINED` 各存一行；改回 `PENDING` 删除该行。数据库 CHECK 仍只允许两种存储状态，唯一键仍为 `(expert_contact_id, material_code)`。
- Applies to：新 PUT、复用的状态保存分支、V128。
- Violation consequence：状态重置失败或同一材料出现两份状态。
- 来源：V111、`ExpertMaterialService.kt:70-122` 现有语义。

### Invariant I-3：五条正文唯一源
- Rule：五条 `requestText` 由服务端新目录一次定义并在 GET 中返回，逐字为用户指定的英文五条；前端不再抄写。状态 GET 只读，不触发文件识别或发送。
- Applies to：新目录、GET、前端消费者。
- Violation consequence：选择预览与最终填入的文字不同或内容漂移。
- 来源：original。

### Invariant I-4：接口无路径碰撞
- Rule：新状态路由使用 `/api/expert-contacts/{contactId}/material-requests` 及 `/{code}`；原 `/materials` GET 保持文档分页对象，不恢复旧同路径状态 GET。
- Applies to：`ExpertContactManagementController` 新 GET/PUT。
- Violation consequence：Spring Ambiguous mapping，或上传列表覆盖操作栏状态（已在生产复现）。
- 来源：`ExpertContactManagementController.kt:243-253`、`ExpertMaterialController.kt:27-45`。

## 现状审计

### MySQL `expert_material_status`
- Schema/mapping：`V111__create_expert_material_status.sql:7-20`：主键、contact 外键、代码 CHECK 固定旧 7 项、状态 CHECK 仅 `PROVIDED/DECLINED`、contact+code 唯一键、时间列；不建初始状态行。`ExpertMaterialStatusRecord.kt:11-20` 字段与表一致。
- Write paths（`rg 'expert_material_status|ExpertMaterialStatusRepository|findAllByExpertContactId|findByExpertContactIdAndMaterialCode'` 对 main/migration/scripts 复核）：① V111 建表/约束，无数据行；② `ExpertMaterialService.updateStatus:89-122` 经仓储 `save` 或 `deleteById` 写旧 7 状态；③ 本计划新增 `updateRequestStatus` 经同一私有保存分支写新 5；④ V128 只扩大代码 CHECK，不删/改行。没有发现其它对该表的 DML 脚本或服务写路径。
- Read paths：① `ExpertMaterialService.listMaterials:70-81` 按旧目录返回七项；② `ExpertMaterialService.renderPendingMaterials:130-138` 只按旧目录组装编号；③ `MailVariableService:158,274-277` 将②注入 `${pendingExpertMaterials}`；④ `RagProcessContextResolver:31-49` 直接读取 `CV`；⑤ 本计划新 GET 按新目录返回五项。旧 `ExpertMaterialController` 查的是 `expert_document`/附件，不读这张状态表。
- Interaction points：新 PUT → 新 GET → 操作栏与弹窗；旧 PUT → 旧变量和 RAG 保持原值；V128 CHECK → 新 PUT 可写项目/奖项且旧行仍合法。

### HTTP 路由
- `ExpertContactManagementController.kt:243-253` 的旧 `listMaterials` 已去掉 `@GetMapping`，仅留直接调用方法；旧 PUT `/materials/{materialCode}` 仍在。
- `document/controller/ExpertMaterialController.kt:27-45` 占用 `GET /materials`，返回 `ExpertMaterialPage`；`expert-materials.js:275` 使用分页参数。故新 API 必须另起路径，不可让 UI 对该响应执行 `Array.isArray`。

## 实现方案

### T1：数据库代码域扩展（I-1、I-2）
- 文件：`src/main/resources/db/migration/V128__add_material_request_codes.sql`。
- 新迁移只替换 `chk_expert_material_code`，允许 V111 旧 7 代码 + 上述 `REQ_*` 5 代码；不得改唯一键、状态 CHECK 或历史数据。当前最高迁移为 `V127__add_outbound_attachments_snapshot.sql`（`rg --files ... | sort -V`）。
- 研究门禁：在项目 MySQL Flyway 集成测试里实际执行新迁移并检查新旧 12 代码、非法代码拒绝；若数据库方言的 CHECK 删除语法不符，先调整此迁移并重跑，不直接部署。

### T2：五项目录与状态 API（I-1 至 I-4）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`。
- 在旧 `ExpertMaterialCode` 之外新增五项请求目录/DTO；英文正文为：
  1. `Copies of your representative publications`
  2. `Supporting documents for research projects`
  3. `Patent certificates`
  4. `Certificates of honors and awards`
  5. `Bachelor’s, master’s, and doctoral degree certificates`
- GET 返回 `[ {code,label,status,requestText}, ... ]` 五项；PUT body 沿用 `{ "status": "PENDING|PROVIDED|DECLINED" }`，响应同样返回五项。两路径都校验 contact 存在与 code/status 合法；共用现有 `save/deleteById` 规则，不复制分叉的持久化逻辑。
- 新 GET 读的是新 PUT 写的 `REQ_*` 行；旧变量与 RAG 继续读旧 7 行，不调整消费者。

### T3：针对性验证（I-1 至 I-4）
- 文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialRequestServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。
- 覆盖缺行五项、逐项转换/删除、非法 code/status 无写、旧 `CV`/旧 7 目录不变、GET/PUT 路由契约、V128 CHECK 实施。现有 Controller 测试是直接调用方法的单测；新路由须在该文件加 `MockMvcBuilders.standaloneSetup(controller)` 的 GET/PUT 路径与 JSON 断言，不能以直接方法调用冒充 HTTP 映射验证。不得把上传文件分页对象作为五项状态测试数据。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V128__add_material_request_codes.sql` | 扩展 CHECK，零数据改写 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt` | 新五项目录及状态读写 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 新独立 GET/PUT |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialRequestServiceTest.kt` | 新状态服务测试 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | 新路由/委托测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新旧代码约束测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | A1 扩权：仅行号 564→578 |

共 7 文件（A1 将第 7 个列入授权），1 个后端状态子系统；`MailVariableService.kt`、RAG 和上传文档 API 明确不修改。

## 验收标准

- I-1：新 GET 代码严格依次为五个 `REQ_*`；旧 `listMaterials` 仍为 7 项；旧 `CV=PROVIDED` 经新五项 PUT 后，RAG 解析仍为 `RECEIVED`；旧变量仍按七项编号。
- I-2：五项初次读取皆 `PENDING`，改 `PROVIDED/DECLINED` 后重读保持，改回 `PENDING` 后数据库缺行；并发重复状态受唯一键约束。
- I-3：API 中五条英文与 T2 逐字相等；GET 前后表行数相等。
- I-4：`GET /materials` 返回 `ExpertMaterialPage`，新 GET 返回五项数组；应用上下文启动无 Ambiguous mapping。
- 命令：JDK 11 下定向跑新服务/Controller 测试；`mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`（需要本地 Docker）；`git diff --check`。没有 Docker 时如实记录未过迁移门禁，不把跳过写成通过。

## 人工验收清单

### A-1：初始五项
- 前置条件：测试环境已执行 V128；选择一个有专家联系记录、从未设置 `REQ_*` 的 contactId；用登录会话访问 API。
- 操作步骤：1. GET `/api/expert-contacts/{contactId}/material-requests`。
- 预期结果：HTTP 200、五项顺序为代表性论文/科研项目/专利/荣誉奖项/学位，五项均为 `PENDING`，正文分别等于 T2 所列英文。
- 覆盖：I-1、I-3、需求结果 1。

### A-2：手动三态持久化
- 前置条件：A-1 的联系人。
- 操作步骤：1. PUT `/api/expert-contacts/{contactId}/material-requests/REQ_AWARDS` body `{"status":"DECLINED"}`；2. GET 新接口；3. PUT 同路径 body `{"status":"PENDING"}`；4. 再 GET。
- 预期结果：第 2 步荣誉奖项为 `DECLINED`，第 4 步恢复 `PENDING`，其它四项不变。
- 覆盖：I-2，新 PUT → 新 GET。

### A-3：旧变量与 CV 读取不变
- 前置条件：测试环境存在同一联系人；在模板编辑页准备含 `${pendingExpertMaterials}` 的测试模板，不发送。
- 操作步骤：1. PUT 既有 `/api/expert-contacts/{contactId}/materials/CV` body `{"status":"PROVIDED"}`；2. PUT 新 `/material-requests/REQ_AWARDS` body `{"status":"DECLINED"}`；3. 再 PUT 第 1 步相同请求并读取响应；4. 用该联系人预览测试模板。
- 预期结果：第 3 步的旧七项响应仍含 `CV=PROVIDED`；第 4 步 `${pendingExpertMaterials}` 仍生成旧目录的编号英文行，首行是护照而非新「科研项目」/「荣誉奖项」。RAG 对同一 `CV` 的 `RECEIVED` 映射由 I-1 定向测试验证，不依赖 LLM 文案推断。
- 覆盖：I-1、必须保持项；新写路径 → 旧变量/RAG 跨模块。

### A-4：上传文件 API 不变
- 前置条件：A-1 的联系人，登录会话有效。
- 操作步骤：1. GET `/api/expert-contacts/{contactId}/materials?page=0&size=10`；2. GET 新状态接口。
- 预期结果：第 1 步响应含分页 `items`，第 2 步是五项状态数组；两路均 HTTP 200。
- 覆盖：I-4、必须保持项。

人工验收开始时再导出本节勾选文件；本轮不生成。

## 修正记录

- A1（`docs/plans/fast/material-request/ledger.md`）：T2 在 `ExpertContactManagementController.kt` 新增两个路由方法（含 1 个 import，共 14 行）后，`OperatorStatusWriteSeamGuardTest.kt:69` 钉死的 `NoiseSite(ExpertContactManagementController.kt, 564, "operatorStatus = operatorStatus")` 位移到 578，守卫测试报「排除名单已失效」。按 K-line-number-guard-breaks-on-any-insertion，将该守卫文件列入授权（第 7 个），仅更新被移动的行号 564→578，路径与片段文字不变；不新增行为、不改断言语义。审批：HUMAN:2026-09-18T09:12+08:00。
