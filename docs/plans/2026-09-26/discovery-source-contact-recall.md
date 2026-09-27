# 05：PDF 明确作者联系关系（修订原方案）

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-01、DD-02、DD-03。

前置：[04：PMC 路由与结构化作者联系信息](discovery-repair-04-xml-route.md)完成机器验证后，以其产物为基线。

## 需求描述

使已核对原页的4对作者邮箱得到正确解析：两条唯一星号脚注和两条独立机构联系段落；共享列表继续不绑定。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：本文件替代此前同路径未实施草案，纳入联合计划顺序。只读取当前前maxPages页，不做文末采样（留06）、不做全论文版面引擎/OCR/机器学习、无新增业务字段。

## 关键不变量

### Invariant I-1：唯一结构证据
- Rule：仅在PDF作者区与作者联系脚注/独立联系段落间建立关系。作者完整姓名须先唯一对应本篇metadata；唯一*、†、‡联系标记可连接一个作者与邮箱块，纯机构数字编号不算联系方式。多作者同标记/同缩写/同名或跨栏拼接全部为空身份。
- Applies to：PDF布局收集→resolver候选
- Violation consequence：恢复历史邮箱错绑
- 来源：K-author-identity-needs-email-evidence

### Invariant I-2：缩写必须有作者锚点
- Rule：联系段落必须有Email/e-mail显式标记且本段只有一个可识别作者；S. Pan/P. S. Yu须与本页作者区完整姓名、本篇metadata形成唯一姓+全部首字母匹配。不能用邮箱local-part、邻近距离或列表顺序挑作者。保留真实page/文本片段参与SOURCE_SHA256。
- Applies to：独立联系段落→AuthorEmail
- Violation consequence：共享机构段落被当单人
- 来源：K-source-recall-needs-original-layout

### Invariant I-3：保留现有规则和版本边界
- Rule：EXTRACTION_VERSION=20260929，VERSION不变；无版面CORE/HTML不启用PDF脚注推断；新claims与现有claims统一按邮箱冲突消解，不能让新分支firstOrNull覆盖旧冲突。现有前maxPages、大小/请求/下载时限不变。
- Applies to：PDF/CORE/HTML→cache→consumer
- Violation consequence：扩大规则导致其他来源错绑
- 来源：K-identity-cache-version-and-admission

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

PdfEmailExtractor.kt:267～275用PDFTextStripper取前2页后丢掉位置；SourceAuthorEmailResolver.kt:58～87只接受行首全名及窄分隔邮箱字段，拒绝机构描述/缩写。manual-review.json记录W3014974815 Klaus H. Maier-Hein、W2999309192 Davide Chicco；W2907492528 S. Pan/P. S. Yu独立段落。controls.json人工改写4/4成功，只证明窄格式有效，不能作最终验收。交互X2/X4/X5/X8，存储见审计附件C2/C3/C6。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

缓存影响：抽取版本从前一子计划产物升级至20260929。当前pipeline非空旧缓存不会重抽，会以IDENTITY_EXTRACTION_VERSION_UNSUPPORTED失败；本计划保留并测试这项现有行为，不新增兼容白名单。上线前要只读统计旧版本活跃job；若存在需要保留的结果，先单独决定处置，不能以本计划授权清空队列。schema及payload_version不变。

## 实现方案

### T-1：存档并先测RED（I-1/I-2/I-3，I-4）

src/test/resources/discovery/source-contact-recall.zip及同名.md：从诊断original-sources.zip提取三篇原PDF、当次metadata、独立真值manifest。src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt走实际下载替身→PDFBox，不得用重排PDF。

### T-2：最小版面证据（I-1/I-2，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt：小型内部工具，用现有PDFBox TextPosition收集当前页文本位置/基线/标记，产出有界作者块和联系块。保留列边界，不造通用布局DSL；阈值属于拟实施策略，必须用真实正例和跨栏负例验证后才可称有效。src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt在同一次PDDocument生命周期内调用，不重复下载。

### T-3：集中候选消解（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt接收布局产生的明确claims并与旧格式合并；src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt补同名/同缩写/共享标记/正文和引用负例。不得为四个作者写名单分支。

### T-4：版本和实际写入回归（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt、src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt更新版本；src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt以真实PDF实际解析输出过consumer与writer；验证、资格、ESHTTP允许替身。

### T-5：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/05.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共10个文件，1个解析子系统及直接版本/测试边界；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` |
| 9 | `src/test/resources/discovery/source-contact-recall.zip` |
| 10 | `src/test/resources/discovery/source-contact-recall.md` |

## 验收标准

- I-1/I-2：原PDF恢复k.maier-hein@dkfz.de→Klaus H. Maier-Hein、davidechicco@davidechicco.it→Davide Chicco、shirui.pan@monash.edu→Shirui Pan、psyu@uic.edu→Philip S. Yu。W2907492528其余4条共享邮箱仍未知。
- I-1/I-2：两个同名contrib/同缩写不同作者、共享*、跨栏错拼、参考文献邮箱均不绑定；每条正例能指到原页、作者区、联系块。
- I-3：真实4条过consumer，外部验证资格通过时RAW=4候选=4，重复新增0；旧CORE/HTML/JATS防错测试通过；新缓存20260929可用、旧缓存显式拒绝。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,JatsXmlEmailParserTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：四条原页核对
- 前置条件：资源ZIP含三篇原PDF和metadata，外部服务使用通过替身。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试，打开 target/discovery-plan-acceptance/05.json及ZIP原PDF第一页；逐项对照姓名、邮箱、标记/段落。
- 预期结果：上述4对全部匹配；综述另4条共享列表givenNames/familyNames=null；每条证据含页号和原始片段。
- 覆盖：I-1/I-2，X4，需求。

### A-2：真实输入到专家层
- 前置条件：使用同一4条真实解析输出，空ES；再准备重复邮箱及资格拒绝场景。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 查看报告请求与结果：首次、重复、资格拒绝分别一组。
- 预期结果：首次RAW=4候选=4；重复新增0、旧身份不变；新的明确作者资格拒绝时RAW=1候选=0；未知不调用验证且不入库。
- 覆盖：I-1/I-3，X2/X4/X5。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

### A-4：队列新旧结果
- 前置条件：隔离pipeline fixture中各放一个前版和本版20260929的已缓存明确邮箱结果，metadata/payload_version合法；不操作生产队列。
- 操作步骤：1. 跑本计划定向命令；2. 看报告versionCases的消费与最终状态。
- 预期结果：本版正常经过原邮箱/资格判断；前版错误IDENTITY_EXTRACTION_VERSION_UNSUPPORTED且专家写入0，既有pipeline转FAILED，不会自动重抽；证据VERSION仍20260925。
- 覆盖：版本不变量，X2。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
