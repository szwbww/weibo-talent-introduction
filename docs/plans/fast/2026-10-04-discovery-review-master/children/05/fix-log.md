# Child 05 Fix Log

## Epoch 1 — verifier PAUSE（未消耗修复轮次）

- 写者 `ImplDiscoveryReview05` 完成 10 个授权文件并提交 `6a54f008e1b907df77572791679a74cb80175e3a`；声明计划实现方案 5 的「模板版本冻结 / 过期预览令牌拒绝 / 账号变量缺项预检」未实现。
- 核实者 `VerifyDiscoveryReview05`：四门禁 1/3/4 PASS、门禁 2 **PAUSE** —— 独立确认该缺口无法在 10 个已授权文件内实现，需授权 `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`（launch 版本/令牌拒绝 + request_payload 快照）与 `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`（模板内存快照渲染 seam）；非 AUTO_FIX。
- `fix_round` 保持 0（AUTO_FIX 轮次未发生；等待人工批准 A3 扩权或降级决定）。

## Epoch 2 — verifier PAUSE（未消耗修复轮次）

- Epoch 2（写者 `ImplDiscoveryReview05E2`，commit `f34d59ade2ae823f3cea3e3d7853680761bc4a1d`）实现 A3 全部六项 + 预览 DTO；必跑命令 209 tests / 0F / 0E。
- 核实者 `VerifyDiscoveryReview05E2`：门禁 1/3/4 PASS，门禁 2 **PAUSE** —— 全量 `mvn test` 4653 / 9F / 21E；本 child 引入 2 类失败：`MailOpenTrackingPersistenceTest` 2E（compose 第 4 默认参数 → Mockito 3/4 matcher）、`LegacyDiscoveryApprovalTest` 4F（移除 matchesDiscoveryOutreach 后旧契约断言过时），最小修复文件均不在 12 文件授权内；O-4（`DiscoveryReviewAllPagesTest` 5F）为 child 04 回归、O-5（`ExpertContactLocationServiceTest` 19E）为 master base 既有。
- `fix_round` 保持 0（等待人工批准 A5 扩权或降级决定）。

## Epoch 3 — A5 修复（未消耗修复轮次）

- 写者 `ImplDiscoveryReview05E3` 提交 `b699750b9a84ed56224541e3137cdf1c79f77e7e`（3 文件：`MailOpenTrackingPersistenceTest.kt` 补第 4 个 Mockito matcher、`LegacyDiscoveryApprovalTest.kt` 4 条过时断言改写为新契约、`DiscoveryReviewService.kt` 修复 04 回归——投影异常记 CANDIDATE_SYNC_FAILED 且保持 APPLIED，不静默吞错）。
- 命令 1–3 全绿（209 tests / 22 tests）；全量 `mvn test` 4653 / 0F / 19E，仅余 master base 既有 `ExpertContactLocationServiceTest` 19E。
- 核实者 `VerifyDiscoveryReview05E3`：四门禁 + A5 三项专项校验全部 PASS；`LIGHT_PASS_WITH_NOTES`（O-6 既有 19E 记录）。
- `fix_round` 保持 0（epoch 内修复，非 AUTO_FIX 轮次）。
