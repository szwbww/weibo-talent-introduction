# 04 新发现人物主档与邮箱成员

状态：设计，未实施。上级：[总计划](discovery-identity-recovery-master.md)。本文件不授权修改生产。下列路径以仓库根为基准。

## 需求描述

可靠确认为同一人的多个邮箱登记到一个稳定人物主档。保持旧 ES ID、每邮箱证据/准入、联系人、别名和邮件历史。仅 discovered；不做同名模糊匹配、全库去重、物理删除、自动改姓名。

## 关键不变量

### Invariant I-1：人物关系唯一且可追溯
- Rule：新增 personId 为不带邮箱语义的稳定 UUID；每个原 docId 只能隶属一个人物。主档只记录代表档案引用，不复制第二份准入或邮箱验证结论。主邮箱仅为展示默认值，不是发送许可。
- Applies to：新增三表、仓储、服务、05/10 调用者。
- Violation consequence：同人再次增生，或新旧资格真值分裂。
- 来源：总计划 I-5、V148 身份哈希规则。

### Invariant I-2：合并证据和并发边界
- Rule：只有有效绑定当前姓名/邮箱的凭证内 ORCID/OpenAlex 键可占用作者唯一键；全名规范化一致、每类作者键不冲突、层间身份一致才可归入。无稳定键或缺绑定证据的 discovered 记录可以按原 docId 一对一登记单例，但不创建作者键、不增加身份可信度或准入，不据同名归组。两个已有不同人物根被新证据连接时返回 NEEDS_REVIEW，不自动吞并根。按排序后的作者键/人物行加锁，唯一键冲突后重新读并复验，禁止捕获冲突后无条件当成功。
- Applies to：并行发现、重复消费、历史合并。
- Violation consequence：竞争产生多个主档或通过弱关联误合并。
- 来源：本轮高置信分组方法。

### Invariant I-3：范围和事实有效性
- Rule：登记输入必须由服务端核实发现来源、当前 discovered 标签、真实 docId 及证据指纹。成员指纹变化即失效待重核；读/发信不得盲信旧成员关系。跨范围 email/作者/联系人别名关联只返回 SCOPE_CONFLICT，不迁移范围外对象。
- Applies to：登记服务及所有读取者。
- Violation consequence：标签变化或身份改动后继续错误聚合。
- 来源：用户范围、总计划 I-4。

## 现状审计

- 三层 ES mapping 均 dynamic:false，email 单值，identityVerification 绑定单邮箱；ExpertProfile 只有 email，externalIds 不是人级主键。ExpertDiscoveryService 两个 identityFactory 强制 email hash ID。
- 现有写路径：ExpertDiscoveryService 新增/学术更新；ExpertIndexWriterService.indexToRaw、promoteToCandidate、promoteToApplication、projectDiscoveryCandidate、reconcileDiscoveryCandidate、demoteToCandidate、标签/运营字段写入；历史补证 01/02；这些继续写原成员事实，本子计划不改它们。
- 现有读路径：ExpertSearchService 全部列表/scroll/find/聚合；V148 准入、学术任务、发送按 docId；详情按 docId/contactId。关系新读者为 06 查询、08 选择、09 保护和 10 预览。
- V10 expert_email_alias.normalized_email 唯一，FK 指向 expert_contact；findContactByEmailOrAlias 可供只读冲突检查，不能复用为采集主档。V148 主键 expert_doc_id，不能把多个邮箱改成同一个 admission 行。
- 交互：ES 已写但 SQL 关系未写的中断；不同 worker 同一作者不同邮箱；作者 ID 不同但名字相同；SQL 成员指纹与 ES 当前事实变化。

## 实现方案

1. **独立存储（I-1— I-3）**：V156 新建 `discovery_person(person_id PK, primary_doc_id, revision, created_at, updated_at)`、`discovery_person_member(expert_doc_id PK COLLATE utf8mb4_bin, person_id FK, normalized_email, identity_fingerprint, evidence_json, created_at, updated_at)`、`discovery_person_author_key(author_key PK, person_id FK)`。email 仅索引、不以 email 全局唯一代替人证据；author_key 带 `ORCID:`/`OPENALEX:` 类型前缀。evidence_json 留存合并依据及规则版本，不替代原文和实时凭证；revision 仅人物关系/代表引用变化时递增。以上均为新独立存储，现有共享表/ES 增字段 0。
2. **事务登记（I-1— I-3）**：DiscoveryPersonRepository 使用显式事务及唯一约束；DiscoveryPersonService 只接收通过服务端校验的事实快照，返回 CREATED / ATTACHED / UNCHANGED / NEEDS_REVIEW / SCOPE_CONFLICT / STALE。后三者不写关系。首次代表按已有可追溯主档优先、否则最小 docId 固定；之后新增邮箱不悄悄换代表。生成失败事务回滚，不暴露空主档。
3. **读契约（I-1/I-3）**：提供批量 docId→personId、人物成员/作者键、revision、指纹查询；06/08 在使用前重核当前成员事实。05/10 是仅有登记调用者；GET 和发送预估不得登记。默认开关关闭，无旧数据隐式全表迁移。
4. **测试（I-1— I-3）**：真实数据库事务测试同时登记同作者不同邮箱，模拟唯一冲突与回滚；无证据作者号、同名不同人、跨范围别名、指纹更新分别断言具体结果。迁移编号按当前最大 V155 预留 V156；若实施时被占用，先同步修订计划，不覆盖已应用迁移。A11：同步授权 FlywayMigrationIntegrationTest.kt（全量迁移最新版本断言 31 处 155→156，并加 V156 三新表存在性断言）。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/resources/db/migration/V156__create_discovery_person.sql|新增|
|src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt|新增，主档/成员/结果 DTO|
|src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt|新增|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt|A11：31 处最新版本断言 155→156 + V156 三新表存在性断言|

共 7 文件（A11：第 7 个为迁移集成测试）；人物登记一个子系统。禁止顺手改现有 ES/联系人 schema。

## 验收标准

- I-1：3 个邮箱对应 1 主档、3 成员；重跑成员数不增；人工准入、邮件行逐值不变。
- I-2：两个并发事务只产生 1 主档；同名不同编号不合；两个已有人物根不自动合。
- I-3：未带标签/范围外关联/指纹过期均不给有效聚合关系。04 部署后开关关闭，旧读写无变化。

## 人工验收清单

### A-1：登记与重跑
- 前置条件：隔离数据库；通过测试夹具载入一人三邮箱、同名异人一邮箱、范围外一邮箱，各有明确来源快照。
- 操作步骤：调用登记测试入口两次，再用仓储只读查询成员及原业务表。
- 预期结果：前 3 邮箱为同一个 personId，后 2 不加入；第二次新增成员 0；业务表修改 0。
- 覆盖：I-1— I-3、现有数据保护。

### A-2：并发与过期
- 前置条件：同一作者两个有效邮箱；一个过期指纹；两个已存在人物根的桥接证据。
- 操作步骤：并发登记两邮箱；提交过期快照及桥接证据。
- 预期结果：前两邮箱最终 1 主档；过期返回 STALE；桥接返回 NEEDS_REVIEW，原两主档保持。
- 覆盖：I-2/I-3、跨存储/并发交互。
