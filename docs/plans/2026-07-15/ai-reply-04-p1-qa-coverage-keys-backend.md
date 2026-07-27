# P1-4：QA 规则覆盖能力标签后端

## 需求描述

为每条 QA 规则增加机器可读的 `coverageKeys`，表示该规则正文已审核覆盖哪些业务事实。关键词继续负责“是否可能相关”，coverage key 负责“是否足以回答某个 intent”，两者不得混用。

Out of scope：intent 拆分、模型 prompt、前端确认弹窗、自动补全缺失 QA 事实。

## 关键不变量

### I-1：单一合法 key 目录
- 后端 `QaCoverageKeyCatalog` 是 key/中文标签/说明的唯一事实源；前端不得硬编码另一份。
- 未知 key 在 create/update 时拒绝。

### I-2：兼容旧客户端
- create 请求缺 `coverageKeys` 时保存空集合。
- update 请求缺字段时保留 existing 值；显式传空数组才清空，避免旧客户端无意擦除。

### I-3：存储与 API 分离
- 数据库存逗号分隔的 canonical keys；domain 保持 Spring Data JDBC 可映射 String。
- API 始终返回有序去重的 `List<String>`。

### I-4：不改变自动回复
- `QaMatchService.match/suggestComposition` 仍按 keywords/matchMode/priority/enabled 工作。
- coverageKeys 只由后续 AI intent matrix 消费。

### I-5：全写路径覆盖
- schema/seed：V76。
- runtime create/update：`QaRuleManagementService`。
- enable/disable 通过 immutable copy 自动保留；delete 无额外资源。
- 旧 Flyway seed 不修改。

## coverage key 目录

第一版至少包含：

- `general.answer`
- `company.legal_name`、`company.registered_location`、`company.verification_evidence`
- `programme.purpose`、`programme.structure`、`programme.tracks`、`programme.scope`
- `researcher.selection`
- `enterprise.matching`、`enterprise.project_types`
- `role.responsibilities`、`role.deliverables`
- `contract.party`、`contract.terms`
- `finance.government_funding`、`finance.enterprise_compensation`
- `ip.arrangements`
- `application.required_materials`、`application.steps`、`application.timeline`
- `work.remote_arrangement`、`work.travel_arrangement`、`work.relocation`
- `fees.policy`、`confidentiality.materials`

key 名发布后视为 API/数据契约，不随意重命名；废弃需新增兼容迁移。

## 现状读写审计

### qa_rule 写路径
1. Flyway `INSERT/UPDATE qa_rule`。
2. `QaRuleManagementService.createRule()` → `save(command.toDomain())`。
3. `updateRule()` → `existing.copy(...)`。
4. `setRuleEnabled()` → `existing.copy(enabled=...)`，应自然保留新字段。
5. delete 删除整行，无子表。

### 主要读路径
- QA 管理列表/编辑；QaMatchService；AI draft；自动回复；人工单规则/组装回复；模板服务；邮件标签/监控。
- 新字段 additive，除 AI intent 与管理接口外其余读路径必须无行为变化。

## 实现任务

### T1：V76 schema 与已知规则回填
文件：`src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`

- 新增 `coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`。
- 按稳定 id/reply_subject 回填，不以 keyword 推断。
- 公司身份新规则：legal_name + registered_location。
- credentials：verification_evidence。
- Program overview/about：purpose/structure/tracks/scope；只有正文真实包含时才加 funding、remote/travel/no-fee/confidentiality。
- Partner company：matching，不得标 project_types。
- Responsibilities：responsibilities，不得标 deliverables。
- Contract/IP：contract.party/terms + ip.arrangements；仅“compensation terms later”不得标完整 finance。
- Funding support：government_funding + enterprise_compensation。
- Application criteria/process/materials 按正文分别标 selection/steps/timeline/required_materials。
- 未知/薄弱规则保持空，不补造事实。

### T2：domain 与目录
文件：
- `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt`
- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt`

- QaRule 添加 `coverageKeys: String = ""`。
- 目录提供 `all() / normalizeAndValidate() / parseStored() / serialize()`；固定按 catalog 顺序输出。

### T3：管理 service 全写路径
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`

- create command：nullable/list input归一化后进入 domain。
- update command：`null` 保留 existing，空 list 清空。
- 验证未知 key、重复、单项空白；限制总长度不超过列宽。
- enable/disable/delete 现有逻辑不变。

### T4：管理 API 与 metadata
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`

- create/update request 添加 nullable coverageKeys。
- response 添加 `coverageKeys: List<String>`。
- 新增 `GET /api/qa/coverage-keys` 返回 `{key,label,description,group}`。

### T5：测试
文件：
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementControllerTest.kt`（不存在则新增）

- create、update、missing-field preserve、explicit-empty clear、unknown reject、enable preserve。
- metadata 与 response 的顺序/字段。
- V76 静态/集成检查：列存在、关键规则回填准确、partner 无 project_types、responsibility 无 deliverables。

## 变更文件清单（7）

1. `V76__add_qa_rule_coverage_keys.sql`
2. `QaRule.kt`
3. `QaCoverageKeyCatalog.kt`
4. `QaRuleManagementService.kt`
5. `QaRuleManagementController.kt`
6. `QaRuleManagementServiceTest.kt`
7. `QaRuleManagementControllerTest.kt`

## 验收标准

- 所有 runtime 写路径保存/保留 coverageKeys 正确。
- 自动匹配测试无变化。
- V75/V76 顺序正确，旧迁移未修改。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRuleManagementServiceTest,QaRuleManagementControllerTest,QaMatchServiceTest test
```

## 人工验收清单

### A-1：API round trip
- create 带两个 key → list/get 返回 canonical 顺序；update 不传 key → 保留；传空 → 清空。

### A-2：非法 key
- 提交 `finance.guaranteed_amount`。
- 预期：400，数据库不写入。

### A-3：线上回填抽查
- 抽查 Company、Credentials、Partner、Responsibilities、Contract/IP、Funding、Process。
- 预期：只标正文真实覆盖能力。
