## Epoch 1 — Round 1/3
- Findings: F-1, F-2
- Before: 8c91c656d6530972ff8fc242705c928715c5b4d5
- Fix commit: ae5d947b7257bf714e1d70e93dafbaf1894cbff6
- Authorized files changed: `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`, `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`（仅这 2 个；无任何 `src/main/kotlin` 或迁移改动）
- Commands:
  - `JAVA_HOME=<zulu-11> mvn test -Dtest=OperatorStatusWriteSeamGuardTest,UnmatchedInboundTrustWorkbenchTest` -> exit 0；`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`（OperatorStatusWriteSeamGuardTest 1/0/0，UnmatchedInboundTrustWorkbenchTest 12/0/0）
  - `JAVA_HOME=<zulu-11> DOCKER_API_VERSION=1.44 mvn test` -> exit 0；`Tests run: 3452, Failures: 0, Errors: 0, Skipped: 13`，`BUILD SUCCESS`（修复前的 1 failure + 4 errors 全部消失）
  - `JAVA_HOME=<zulu-11> DOCKER_API_VERSION=1.44 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` -> exit 1；`Tests run: 23, Failures: 0, Errors: 1, Skipped: 0`，唯一 error 为既有基线缺陷 `V124 allows material attached promotion audit trigger:233->execute:1216 » SQLIntegrityConstraintViolation`（`fk_eap_contact` 外键，无迁移播种 `expert_contact` id=1，由基线提交 `6ab8eb3` 引入；按指令**未修复**，保持为 pre-existing 残项）
- Result: FIXED
- Notes: F-1 只改行号与注释，两处 pin 的 context 字符串逐字不变，且提交前逐行核对命中：`UnmatchedInboundMailController.kt:221` = `            operatorStatus = request.operatorStatus,`（context `operatorStatus = request.operatorStatus`）、`:1137` = `    operatorStatus = operatorStatus,`（context `operatorStatus = operatorStatus`）；平移量与 06 提交 `82a46dc` 的插入行数一致（import 两行 :5/:47 → 219→221；加形参/转发/helper 共 +12 → 1125→1137）。F-2 在四处 Mockito 桩尾部按真实形参顺序补齐第 22/23 个 matcher：`attachmentIds: List<String> = emptyList()`（非空 Kotlin 参数，按本仓约定用新增的 `anyValue(emptyList())` 兜实值，先例 `MailboxConversationControllerTest.anyValue`）、`authenticatedUsername: String? = null`（可空，沿用 `Mockito.isNull()`）；21 matcher 的陈旧注释保留，新增 06 注释说明，未删除或放宽任何既有功能断言。测试侧 `-DmigrationIt`/`-DmysqlIt` 组不在本轮授权内，未改动。

Evidence note: recorded together with `execution.md` and `verify-log.md` in the child-08 evidence commit.
