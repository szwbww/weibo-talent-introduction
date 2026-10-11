# Child 01 — Execution Log

(待实现；由 implementer 追加完整结果。)

---

## Execution Result: READY_FOR_VERIFICATION

| 项 | 值 |
|---|---|
| Plan（权威合同） | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master/docs/plans/2026-10-11/contact-profile-01-storage.md` |
| Plan SHA-256 | `ab553ca8388ecaa154b0e07134601b6295474aee661469add7ca8c0b0b2e9a3c`（执行前/执行后各核一次，未变化） |
| Execution ID | `<worktree>/docs/plans/2026-10-11/contact-profile-01-storage.md@ab553ca8…` |
| Execution epoch | NEW（无同 identity 的既往执行证据） |
| Approval basis | child 01 brief（`docs/plans/fast/2026-10-11-contact-profile-development-master/children/01/brief.md`；child_base_sha `91a79dcda9d4ddf55525c801f9c9b9b454321f63`）+ master plan |
| Executor | Impl01（fast-p child 01 worker） |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master` |
| Target branch | `fast/2026-10-11-contact-profile-development-master` |
| Worktree ID | `<worktree>@fast/2026-10-11-contact-profile-development-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master` |
| Pre-execution code SHA | `29ca201e8e892b656a68b23789de5f425a44fcb2` |
| Post-execution code SHA | `bd7053979bea2cc123c58ffb070abf7092917e98` |
| Evidence HEAD | N/A（本报告不提交；证据提交由 controller 统一完成） |
| Implementation boundary | `29ca201..bd70539`，仅 9 个授权文件（1 改 + 8 新建） |
| 提交 | `bd7053979bea2cc123c58ffb070abf7092917e98` `feat(fast-p): implement 01`（本地一次提交，未 push/merge/rebase/amend） |

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| V153 迁移（19 列/`expert_key` PK `utf8mb4_bin`/NOT NULL·默认/无外键） | IMPLEMENTED | `src/main/resources/db/migration/V153__create_expert_manual_profile.sql` | IT `V153 creates the expert manual profile table and preserves note and draft rows`（152→153，真实 MySQL 8.0.36） |
| 领域白名单/枚举/异常/DTO（I-3/I-5–I-23） | IMPLEMENTED | `.../expert/domain/ExpertManualProfile.kt` | `ExpertManualProfileServiceTest`（19 字段样例/空值/上限/超限）、`ExpertManualProfileControllerTest`（键集逐字比对） |
| 身份核验 `requireKnown`（I-1） | IMPLEMENTED | `.../expert/service/ExpertProfileIdentityResolver.kt` | `ExpertProfileIdentityResolverTest`（11 用例：空/超长/EMAIL 大小写/原始+规范值/跨层顺序/`_id` 不参与/404/503/已有行可读） |
| 唯一写者与 CAS（I-2/I-3） | IMPLEMENTED | `.../expert/service/ExpertManualProfileService.kt` | `ExpertManualProfileServiceTest`（组列隔离、`version=version+1`、影响 0 行→409、首次 INSERT 默认值+version=1、重复键/死锁→409）；IT 真实并发（1 成功 + 1×409）与同事务回滚 |
| GET/PATCH API 与局部错误映射（I-1/I-3/I-4） | IMPLEMENTED | `.../expert/controller/ExpertManualProfileController.kt` | `ExpertManualProfileControllerTest`（9 用例：完整 19 字段 JSON、401/403、400 契约、400/404/409/503/500 固定码、内部信息不外泄） |
| Flyway IT 增改（迁移 152→153、全新库→153、并发持久化） | IMPLEMENTED | `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt` | 29 处「迁移到最新」断言 152→153；V152 测试名与所有历史 target 保留；新增 3 个用例 |

## Commands（本 invocation 内新跑，均在目标 worktree）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test` | PASS | exit code **0**；**Tests run: 37, Failures: 0, Errors: 0, Skipped: 0**（Resolver 11 / Service 17 / Controller 9）；BUILD SUCCESS（日志 `/tmp/child01-unit-final.log`） |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit code **0**；**Tests run: 42, Failures: 0, Errors: 0, Skipped: 0**（基线 39 + 新增 3）；BUILD SUCCESS（日志 `/tmp/child01-it3.log`） |

补充只读证据（无写入）：

- 唯一写者：`grep -rn "INSERT INTO expert_manual_profile\|UPDATE expert_manual_profile" src/main src/test` → `src/main` 命中仅 `ExpertManualProfileService.kt`（4 条 UPDATE + 1 条 INSERT）；`src/test` 命中均为断言字符串。
- 新表引用（`grep -rn expert_manual_profile src/main/kotlin`）→ 仅 Service（读写）与 Resolver（1 条 `SELECT COUNT(*)` 存在性读）；既有 `expert_contact`/ES 三层索引无新增字段、无新增写路径。
- 迁移文件不含 `${}` 占位符（`grep -c '\${'` = 0），不触发 `K-flyway-placeholder-replacement` 的生产启动风险。
- `git status --porcelain`（提交前）仅 9 个授权文件；提交后工作区仅剩本报告（未跟踪，按 brief 不提交）。

## Changed Files

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V153__create_expert_manual_profile.sql` | 新建 | InnoDB/utf8mb4；`expert_key VARCHAR(64) COLLATE utf8mb4_bin` PK；19 业务列（仅 `company_match_status` NOT NULL DEFAULT 'NOT_MATCHED'）；`version BIGINT NOT NULL DEFAULT 0`、`updated_by VARCHAR(64) NOT NULL`、`updated_at DATETIME(3) NOT NULL`；无外键、无人才编号唯一约束 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertManualProfile.kt` | 新建 | 19 字段元数据（apiName/column/上限/是否多行）、4 分组白名单、`ResearchAbility`/`CompanyMatchStatus`、`ExpertManualProfileDto`（8 顶层键 + 4 组）、`ExpertManualProfilePatch`、5 个专用业务异常 |
| 3 | `.../expert/service/ExpertProfileIdentityResolver.kt` | 新建 | `requireKnown(expertId, level): String`：规范化 + 1–64 校验 → 本表存在性 → `existsByOrcidId`（原始/规范去重）→ ES 按请求层 + 其余 RAW/CANDIDATE/APPLICATION、原始/规范值去重查询；命中记录规范化后必须等于请求 key；全层未命中 404；任何查询失败 503 |
| 4 | `.../expert/service/ExpertManualProfileService.kt` | 新建 | 新表唯一写者：GET（无行→默认 DTO+version=0，不插入）、PATCH（组键集校验 → 逐字段规范化 → 事务内 CAS UPDATE/首次 INSERT → 同事务回读）；固定组 SQL、参数化 |
| 5 | `.../expert/controller/ExpertManualProfileController.kt` | 新建 | `GET/PATCH /api/experts/manual-profile`；会话用户名注入审计；局部 `@ExceptionHandler` 固定 `{code,message,detail}` |
| 6 | `src/test/kotlin/.../expert/service/ExpertProfileIdentityResolverTest.kt` | 新建 | 11 用例 |
| 7 | `src/test/kotlin/.../expert/service/ExpertManualProfileServiceTest.kt` | 新建 | 17 用例（19 字段参数化、边界、冲突、错误映射、无内部信息外泄） |
| 8 | `src/test/kotlin/.../expert/controller/ExpertManualProfileControllerTest.kt` | 新建 | 9 用例 |
| 9 | `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 | 29 处 latest 断言 152→153；新增 V153 DDL/迁移保全 + 真实服务 19 字段往返 + 并发首次插入/同版本更新 3 个用例（+393/−29） |

## Deviations（实现解释；均未改变计划要求的可观察结果，请 verifier 按此判读）

1. **`documentNumber` 的 GET 形态**：GET 返回**持久化原文**（保留前导零），「非编辑态仅显示末四位、其余 `•` 遮罩」由 02 前端展示层实现。理由：I-3 要求 PATCH 提交「指定组全部字段」，若 GET 只返回遮罩值，02 的表单回写会用遮罩覆盖真实证件号（数据损坏），且子计划 A-1 的预期是「19 项逐项返回提交值」。01 侧只保证保留原文、长度校验与**证件值不进日志/异常消息**（`ExpertManualProfileServiceTest` 有 secret 子串断言）。
2. **`level` 参数缺省**：GET/PATCH 的 `level` 可省略，缺省 `RAW`；仍只接受 `RAW`/`CANDIDATE`/`APPLICATION`（PATCH body 内非法值 400；GET query 非法值由类型转换失败映射为 400）。`level` 只影响 ES 回退层的查询顺序，不改变任何字段语义。
3. **`companyMatchStatus` 的空值语义**：该列为 `NOT NULL DEFAULT 'NOT_MATCHED'`，因此 `null`/空白归一为 `NOT_MATCHED`（I-3「空白=清空」在该列的等价形态即「未匹配」），不是 400。其余字段空白/`null` 一律清空为 SQL NULL。
4. **长度上限的计量顺序**：按 I-4 字面顺序，先对**原始输入**做 Unicode code point 计长，再做 CRLF/CR→LF 与 trim；因此「首尾填充空白导致原始串超限」会 400，而不是被静默接受（与 `expert_contact_note` 的既有口径一致）。单行列在规范化后仍含换行即 400。
5. **首次 INSERT 的并发冲突面**：IT 实测 MySQL 8 对「同 `expert_key` 两连接同时 INSERT」可能返回 `ER_LOCK_DEADLOCK`（`DeadlockLoserDataAccessException`）而非 `ER_DUP_ENTRY`。本表除主键外无其它唯一约束，故 `insertFirstRow` 同时把 `DuplicateKeyException` 与 `PessimisticLockingFailureException` 收敛为 **409 PROFILE_CONFLICT**（与计划「首次插入冲突与旧版本统一 409」一致）；两类分支均有单测（duplicate-key / deadlock）。
6. **本 controller 的未预期异常**：局部 `@ExceptionHandler(Exception::class)` 也映射为 500 `PROFILE_STORE_FAILED`（固定消息），以保证「内部 SQL/路径不外泄」；`400/404/409/503` 的业务码不受影响。
7. `ExpertManualProfileFields.column` 为「白名单 ↔ DDL」的单一映射来源，除服务 SQL 外由 Flyway IT 逐列核对真实库（防两处清单漂移）。

## Downstream interface facts

**GET `/api/experts/manual-profile?expertId=…&level=RAW`**（登录会话必需）

```json
{"expertId":"<规范化 expert_key>","version":0,
 "identity":{"talentCode":null,"chineseName":null,"birthDateText":null,"documentNumber":null,"sourceCountry":null},
 "education":{"graduatedSchool":null,"schoolRanking":null,"preChinaOrganization":null,"organizationRanking":null,
              "postdocOrganization":null,"postdocPeriod":null,"professionalTitle":null,"administrativeTitles":null},
 "technology":{"industry":null,"technicalExpertise":null,"programCategories":null},
 "matching":{"researchAbility":null,"companyMatchStatus":"NOT_MATCHED","matchedCompanies":[]},
 "updatedBy":null,"updatedAt":null}
```

- 无行 = 上例（`version=0`、`companyMatchStatus='NOT_MATCHED'`、`matchedCompanies=[]`，**不插入**）；`matchedCompanies` 空数组对应库内 NULL，不用空字符串作为第二空值表示。
- `updatedAt` 为服务端 Asia/Shanghai 毫秒的 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`（如 `2026-10-11T21:39:36.123+08:00`）。
- 顶层键恰好 8 个；四组键集逐字等于 01 白名单（identity 5 / education 8 / technology 3 / matching 3 = 19）。

**PATCH `/api/experts/manual-profile`**：body `{expertId,level,group,expectedVersion,values}`

- `group` ∈ `identity|education|technology|matching`（大小写敏感）；`values` 必须**恰好**包含该组全部键（缺键/多键 400，额外顶层键被忽略，客户端无法借此触碰其它组或审计字段）；`expectedVersion ≥ 0`。
- `null`/空白 = 清空（`companyMatchStatus` 例外见偏差 3）；字符串按原始输入 code point 计长；CRLF/CR→LF 并 trim；单行列换行 400；`birthDateText` 只接受 `YYYY` 或真实 `YYYY-MM-DD`（年份 0001–当年、不晚于当天、不补 1 月 1 日）；`matchedCompanies` 为字符串数组（去空白/空行、精确去重、保序、库内 `\n` 连接、每项不得含换行、总文本 ≤2000、`MATCHED` 至少一家）。
- 成功 200 返回**完整最新 DTO**（与 GET 同形）。`updated_by` 固定取会话用户名（≤64），`updated_at` 服务端生成。

**错误映射（本 controller 局部，响应恒为 `{code,message,detail}`）**

| HTTP | code | 触发 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 无会话/空用户名（`AuthInterceptor` 或 controller 自身） |
| 400 | `INVALID_REQUEST` | 字段/分组/版本/请求体非法、枚举非法（消息只含字段名与规则，不含字段值） |
| 404 | `EXPERT_NOT_FOUND` | 全层均未命中（不新建空专家、不猜 `_id`） |
| 409 | `PROFILE_CONFLICT` | 版本不匹配或首次插入冲突（含 InnoDB 死锁/锁等待面）；message 固定 `资料已被更新，请重新加载后再编辑` |
| 503 | `EXPERT_LOOKUP_UNAVAILABLE` | 身份查询服务失败（DB 或 ES），绝不伪装 404 |
| 500 | `PROFILE_STORE_FAILED` | 数据库读写失败或未预期异常；固定消息，内部 SQL/路径不外泄 |

**04 复用面**：`ExpertProfileIdentityResolver.requireKnown(expertId: String?, level: ExpertIndexLevel): String` —— 返回规范化 expert_key；空/超长 → `400` 语义（`ExpertManualProfileInvalidRequestException`），未知专家 → `ExpertProfileNotFoundException`，查询失败 → `ExpertProfileLookupUnavailableException`。已有档案行存在时不访问 ES（ES 临时故障仍可读写）。**03 无新增接口依赖**（复用既有 contact-notes）。

## 执行过程要点（自检迭代，供 verifier 参考）

1. 首轮单测暴露 Kotlin+Mockito 约束：Kotlin 非空参数不接受返回 null 的 argument matcher（`Mockito.any(Class)`/`eq()` 会撞 `requireNotNull`），改为「按真实入参逐值打桩」；多行 SQL 常量以 `"""` 起始导致 `startsWith("UPDATE …")` 类断言失败，改为 `val … .trimIndent()`。
2. Flyway IT 迭代：① `company_match_status`/`updated_by` 是 NOT NULL 列，不能进「IS_NULLABLE=YES」参数表（已改为单独断言并补齐类型/长度）；② 同 key 并发 INSERT 实测为 `ER_LOCK_DEADLOCK` → 服务侧补 `PessimisticLockingFailureException → 409`（偏差 5）。
3. 最终两条 required 命令均在**最终文件状态**下重跑通过（见 Commands 表）。

## Freshness

- Plan identity rechecked: YES（执行前/执行后 SHA-256 相同）
- Worktree identity rechecked: YES（提交前用 `--expect-root/--expect-branch/--expect-git-dir` 复核）
- Reported commits reachable from target branch: YES（`HEAD` 为 `fast/2026-10-11-contact-profile-development-master` 祖先）
- Required commands run this invocation: YES（两条命令均在本 invocation 内、最终状态下运行）
- Historical evidence used only as baseline: YES（IT 基线 39 → 42）

## Remaining Blocker / 遗留风险（NOT RUN 均不记 PASS）

- 子计划 A-1…A-5 **人工验收未执行**（需真实环境、真实专家与浏览器操作）；机器证据只覆盖其子集。
- `documentNumber` GET 明文/遮罩职责划分（偏差 1）需 verifier 与人工确认；若判定 GET 必须返回遮罩，则需同时明确「组内全字段 PATCH 不得用遮罩覆盖原文」的契约（本 child 未定义 mask-echo 语义）。
- 未运行全量 `mvn test`（brief 仅要求两条命令）；本 child 未触碰前端资产、JS 门禁与其它模块测试。
- Flyway IT 只证明 V152→V153 与全新库→V153；线上 `flyway_schema_history` 未核实（E-7 提醒；本 child 仅新增 V153，不改写任何已应用迁移）。
- 「邮件更新前后档案字段不变」（A-3）与「跨层晋降层后同 key 可读」只有单元/IT 级证据（Resolver 已有行短路、IT 断言 `expert_contact`/`mail_record` 行数与内容不变），真实邮件链回归留待人工验收。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
