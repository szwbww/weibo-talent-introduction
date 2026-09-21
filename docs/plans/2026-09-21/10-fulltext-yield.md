# 开放全文回退与漏斗准确性实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：首选PDF失效或缺失时尝试其他开放版本，区分下载和邮箱提取失败，减少无效全文请求。
**依赖**：02、05。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

首选PDF失效或缺失时尝试其他开放版本，区分下载和邮箱提取失败，减少无效全文请求。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：回退有界
- Rule：按PMC XML、首选OA PDF、其他去重OA PDF、DOI→Unpaywall开放位置顺序；总计每篇最多3个全文地址，单篇总时限90秒，同URL只尝试一次。仅下载公开http(s)链接；不绕付费墙或验证码。
- Applies to：OpenAlex全文提取。
- Violation consequence：请求放大或无限回退。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：身份不漂移
- Rule：不同开放版本只提取当前论文作者邮箱，不改变05的可信身份规则；没有可靠归属时不附学术ID。
- Applies to：备用PDF/HTML/XML。
- Violation consequence：增量带来冒名指标。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：统计分层
- Rule：EmailExtractionOutcome新增可空fulltextObtained布尔（null表示旧适配器未声明、沿用兼容推导）与downloadFailureCategory信息；已取到HTML但无邮箱计入获取内容成功、单列NO_EMAIL_IN_HTML；明确标注不保证HTML是论文全文。919下载失败要按HTTP403/404/429/5xx、TLS、超时、无效内容分桶。
- Applies to：提取结果→SourceStats→task details。
- Violation consequence：把无邮箱错当下载失败。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：兼容边界
- Rule：保持10MB大小限制和默认解析前2页；此次不盲目加并发、放大PDF大小或开启专利请求。所有现有源结果构造新增字段均有默认值，以兼容方式逐来源推导获取情况。
- Applies to：共用提取器。
- Violation consequence：内存/时长暴涨或其他源统计回归。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

OpenAlex当前仅保留best_oa_location.pdf_url；PaperMetadata只有downloadUrl。UnpaywallClient已有找备用OA PDF逻辑，可复用。PdfEmailExtractor处理PDF/HTML、10MB流式上限、前2页；consumeOutcome仅为NO_EMAIL_IN_TEXT/FULLTEXT计fulltextObtained，遗漏NO_EMAIL_IN_HTML。共享结果被EuropePMC/CORE/arXiv/Crossref消费，新增字段必须默认兼容，不要求本轮改它们所有构造。此阶段只改内存DTO和已有JSON统计，无ES新增字段；source profile邮件写入仍走现有校验去重。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：备用位置
- 约束：I-1、I-2。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 在PaperMetadata新增候选URL列表默认emptyList；OpenAlex解析locations中的开放PDF，首选失败后有界遍历；必要时调用既有Unpaywall。每次请求使用剩余deadline，不能每URL重新给90秒。

```kotlin
val urls = (listOfNotNull(primaryPdf) + otherOaPdfs + unpaywallPdf).distinct().take(3)
for (url in urls) { if (deadlineExpired()) break; attemptWithRemainingTimeout(url) }
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：漏斗分类
- 约束：I-3、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 提取器返回实际获取情况和低基数错误类别；完整源方法仍提供统一最终failureReason，下载尝试次数单独计数，避免同一篇多次回退被计为多篇论文。

```kotlin
// 1 paper, 2 attempts, second succeeds:
// papersSearched += 1; fulltextObtained += 1; downloadAttempts += 2
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperMetadata.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/EmailExtractionOutcome.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClient.kt` | 对应生产实现 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClientTest.kt` | 新增或更新本计划回归验证 |

共 10 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：首PDF404、备用成功且唯一邮箱，最终仅1篇/1位；重复URL只请求一次；第三个失败后不访问第四个。
- V-2：HTML200无邮箱为内容已获取/无邮箱；403与TLS分类不同；累计deadline90秒；超10MB仍拦截。
- V-3：备用版本多作者歧义不附身份；EuropePMC/Crossref/CORE/arXiv旧构造及邮件校验行为保持。
- I-1：逐条检查规则“回退有界”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“身份不漂移”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“统计分层”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“兼容边界”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：备用全文成功
- 前置条件：测试论文首选地址404，第二地址PDF含唯一邮箱。
- 操作步骤：运行发现。
- 预期结果：1篇搜索、2次下载尝试、1次内容成功；唯一专家正确入库，无重复新增。
- 覆盖：I-1、I-2、I-3。

### A-2：资源限制回归
- 前置条件：准备HTML无邮箱及11MB PDF各一篇。
- 操作步骤：运行发现。
- 预期结果：HTML归无邮箱，11MB仍报PDF_TOO_LARGE；均不错误新增专家，任务可结束。
- 覆盖：I-3、I-4。

### A-3：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

