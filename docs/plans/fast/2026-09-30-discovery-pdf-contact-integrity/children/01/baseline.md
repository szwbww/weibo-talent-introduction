# Child 01 — Controller Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-30-discovery-pdf-contact-integrity`
- Branch: `fast/2026-09-30-discovery-pdf-contact-integrity`
- Baseline revision: `8aa82c87848273313bd9239249eb77cff6c05c14` (seed boundary; product code byte-identical to master base `a37efe970e4446242b121c5628db02daa631fc92`)
- Runner: controller (fast-p setup), JDK `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- Run at: 2026-09-30 (+08:00); wall time 225.21 s; Maven verdict `BUILD SUCCESS` (exit 0)

## CMD1 — 阶段 5 必需命令（fresh，在 seed 边界运行）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test
```

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ExpertDiscoveryServiceTest | 176 | 0 | 0 | 0 |
| PlainTextEmailExtractorTest | 6 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 39 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 47 | 0 | 0 | 0 |
| CoreDataSourceTest | 19 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 24 | 0 | 0 | 0 |
| JatsXmlEmailParserTest | 40 | 0 | 0 | 0 |
| DiscoveryIdentityTest | 7 | 0 | 0 | 0 |
| **Total** | **358** | **0** | **0** | **0** |

## 基线结论

- 基线为全绿（8 个类 358 测试，0 失败 0 错误 0 跳过）；本 child 之后任何新增失败都必须归因于新改动，不得改旧期望变绿。
- 计划阶段 1 的负例（三篇原文错绑、`∗ lun yue@msn.com` 后缀、旧缓存版本拒绝用例）在基线尚不存在，由实现阶段新增；修复前红必须由执行报告保留证据。
- 记录限制：本轮基线运行中 `tee target/fastp-baseline.log` 因 Maven 尚未创建 `target/` 而报错（`tee: target/fastp-baseline.log: No such file or directory`），不影响 Maven 构建结果；上表计数取自本次运行输出，Surefire 报告位于 `target/surefire-reports/`。
