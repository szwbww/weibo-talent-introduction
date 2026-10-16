# Fast-P Baseline — 2026-10-13-discovery-identity-recovery-master

- 授权：用户显式 `/fast-p docs/plans/2026-10-13/discovery-identity-recovery-master.md`（2026-10-13），批准以该 master 及其四份子计划（admission-relaxation、01-duplicate、02-replay、03-fallback）为本次执行合同。
- Master base: 9d353934b82ed3e36a193c70748e5efccb3d9e82（main HEAD 检出；主工作树含无关未提交改动，均未纳入本 run，保持原样）
- Seed 提交: b06a71898bc7850d2caeb244c9f8478f18a3b2ae（docs/plans/2026-10-13/ 五份计划；docs/plans-only）
- 迁移占用：本组四份子计划均无 Flyway 迁移、无 DB/ES 新增字段；迁移 IT 基线 N/A（无需运行）。
- 环境：JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home（zulu-11）

## B1 定向基线（16 个既有测试类，master base 干净 worktree）

命令（exit 0，BUILD SUCCESS，03:54 min）：

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest,OpenAlexDataSourceTest,CoreDataSourceTest,PdfEmailExtractorTest,SourceAuthorEmailResolverTest test
```

| 测试类 | Tests run | Failures | Errors |
|---|---:|---:|---:|
| DiscoveryAdmissionPolicyTest | 17 | 0 | 0 |
| DiscoveryReviewServiceTest | 27 | 0 | 0 |
| DiscoveryReviewAllPagesTest | 15 | 0 | 0 |
| DiscoveryReviewControllerTest | 12 | 0 | 0 |
| ExpertRevalidationServiceTest | 31 | 0 | 0 |
| BatchRecipientSelectionServiceTest | 8 | 0 | 0 |
| ManualInitialOutreachServiceTest | 183 | 0 | 0 |
| InitialOutreachServiceTest | 20 | 0 | 0 |
| DiscoveryIdentityTest | 13 | 0 | 0 |
| ExpertDiscoveryServiceTest | 179 | 0 | 0 |
| ExpertIndexWriterServiceTest | 45 | 0 | 0 |
| DiscoveryPipelineServiceTest | 47 | 0 | 0 |
| OpenAlexDataSourceTest | 85 | 0 | 0 |
| CoreDataSourceTest | 19 | 0 | 0 |
| PdfEmailExtractorTest | 40 | 0 | 0 |
| SourceAuthorEmailResolverTest | 25 | 0 | 0 |
| **合计** | **766** | **0** | **0** |

同一次 `mvn test` 的 JS 门禁（exec-maven-plugin，`node --test src/test/js/*.test.js` + 两个 `node --check`）：tests 1657 / pass 1657 / fail 0 / skipped 0。

## 基线结论

- 定向类失败面为空（全绿）；后续子计划定向命令中的任何 Kotlin/JS 失败均为新增回归。
- 各子计划 Required commands 为 `mvn -Dtest=<子集>` 定向执行；JS 门禁随每次 `mvn test` 自动执行，失败数必须保持 0（总量不低于 1657）。
