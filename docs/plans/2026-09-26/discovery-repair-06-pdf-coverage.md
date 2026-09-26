# 06：PDF 文末联系区与 mailto 注释

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-05、DD-13。

前置：[05：PDF 明确作者联系关系（修订原方案）](discovery-source-contact-recall.md)完成机器验证后，以其产物为基线。

## 需求描述

保留前2页解析，额外有界读取末页明确作者信息；读取已选页的mailto注释邮箱线索。真实62页论文的5个独立作者邮箱块可识别。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不扫描全书/全部页面，不跑OCR，不增加下载地址数，不按注释距离猜作者；mailto新增11个线索不承诺11个专家。

## 关键不变量

### Invariant I-1：页预算可见且有界
- Rule：现有maxPages含义保留为首部页数；新增PDF配置tailPages，默认1，application.yml显式pdf-extraction.tail-pages: ${PDF_TAIL_PAGES:1}，有效值0或1。选页=前maxPages页∪最后tailPages页去重，默认62页文档只读1、2、62；tailPages=0退回原行为。非0/1在属性初始化校验失败。解析页间检查同一deadline，不重置下载预算；不宣称能中断一次PDFBox内部调用。
- Applies to：PDF配置→选页→解析
- Violation consequence：无界扫描或偷偷减少前两页覆盖
- 来源：original

### Invariant I-2：注释是线索而非身份
- Rule：只读已选页的PDAnnotationLink/PDActionURI mailto目标，剔除query，不解析cc/bcc，单个合法邮箱经现有黑名单和规范化去重。mailto注释只新增未知邮箱；若正文/明确联系块已证明同一邮箱归属，沿用其结果，不能根据点击矩形最近姓名补身份。
- Applies to：PDF邮箱集合→现有claims消解
- Violation consequence：把链接位置猜成归属
- 来源：K-author-identity-needs-email-evidence

### Invariant I-3：文末仍需明确作者块
- Rule：末页只对明确作者联系信息区/独立作者-Email块使用05规则；不是任意文末文字。姓名必须唯一对应本篇metadata；引用文献/异文作者不能绑定。EXTRACTION_VERSION=20260930，VERSION不变。
- Applies to：末页布局→cache→consumer
- Violation consequence：引用邮箱/错配正文带入metadata身份
- 来源：K-identity-cache-version-and-admission

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

PdfEmailExtractor.kt:270～274只处理前maxPages页，没有PDAnnotationLink读取。W4288039037/contact-page.png和原PDF第62页有5个独立块；missed-pdf-links.json有3篇PDF第1页11个mailto目标。W4292779060属于DD-10来源错配，必须作为空身份负例；不是可修复专家真值。交互X2/X4/X8；审计附件C2/C3/C6。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

缓存影响：抽取版本从前一子计划产物升级至20260930。当前pipeline非空旧缓存不会重抽，会以IDENTITY_EXTRACTION_VERSION_UNSUPPORTED失败；本计划保留并测试这项现有行为，不新增兼容白名单。上线前要只读统计旧版本活跃job；若存在需要保留的结果，先单独决定处置，不能以本计划授权清空队列。schema及payload_version不变。

## 实现方案

### T-1：保存真实多页/链接夹具（I-1/I-2/I-3，I-4）

src/test/resources/discovery/pdf-contact-coverage.zip及同名.md：W4288039037、W4292779060、W4385245566、W4293584584原PDF/metadata/manifest；src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt用完整原PDF及实际注释，不裁掉中间页冒充62页。

### T-2：有界加末页（I-1/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt与src/main/resources/application.yml：显式tailPages参数；src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt将既有deadline传至私有PDF解析入口，并在页间检查；src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt仅支持所选页作者联系块。默认1/2/62，不按邮箱有没有额外扫页。

### T-3：读mailto并去重（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt：读取PDAnnotationLink的单邮箱URI，与已有文字邮箱合并；不给注释单独制造身份。src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt补重复文本/链接、多收件人、query、正文错配、引用负例。

### T-4：验证消费与回退（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt、src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt升级版本；src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt用真实第62页5条结果过consumer，测试tailPages=0及同一EOF页去重；报告选页数组/链接邮箱/最终身份。

### T-5：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/06.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共10个文件，1个解析子系统及直接版本/测试边界；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt` |
| 4 | `src/main/resources/application.yml` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` |
| 9 | `src/test/resources/discovery/pdf-contact-coverage.zip` |
| 10 | `src/test/resources/discovery/pdf-contact-coverage.md` |

## 验收标准

- I-1：62页fixture默认selectedPages=[1,2,62]；1页PDF=[1]；2页=[1,2]；tailPages=0时[1,2]。不扩大10MiB下载上限，过期不解析下一页。
- I-2：missed-pdf-links.json中的11个目标出现在三篇输出集合；同邮箱文本+注释只一条；query/cc/bcc不产生额外作者；W4292779060两邮箱不继承不匹配元数据身份。
- I-3：Salvatore Cuomo/salvatore.cuomo@unina.it；Vincenzo Schiano Di Cola/vincenzo.schianodicola@unina.it；Fabio Giampaolo/fabio.giampaolo@unina.it；Gianluigi Rozza/grozza@sissa.it；Maziar Raissi/mara4513@colorado.edu五对正确；外部验证资格通过RAW=5候选=5。旧版本拒绝。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,SourceAuthorEmailResolverTest,OpenAlexDataSourceTest,CoreDataSourceTest,DiscoveryPipelineServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：第62页与资源上限
- 前置条件：fixture为完整62页原PDF，默认tailPages=1；另外准备tailPages=0、1页PDF。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试；打开 target/discovery-plan-acceptance/06.json与原PDF第62页，查看selectedPages及5对结果。
- 预期结果：默认[1,2,62]且上述5对完整；tailPages=0为[1,2]；1页只读取一次；每篇HTTP下载仍1次。
- 覆盖：I-1/I-3，X4，需求。

### A-2：链接不能猜姓名
- 前置条件：三篇原PDF/真实metadata与mailto目标在资源中；W4292779060保留原错配metadata。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 比对报告的11条mailto邮箱及最终作者字段，查看同邮箱重复出现案例。
- 预期结果：11条可作邮箱线索；不因mailto自动赋姓名；错配论文两条身份为空；重复邮箱输出1次；第62页5条外部资格通过可写RAW=5候选=5。
- 覆盖：I-2/I-3，X2/X4/X8，需求。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

### A-4：队列新旧结果
- 前置条件：隔离pipeline fixture中各放一个前版和本版20260930的已缓存明确邮箱结果，metadata/payload_version合法；不操作生产队列。
- 操作步骤：1. 跑本计划定向命令；2. 看报告versionCases的消费与最终状态。
- 预期结果：本版正常经过原邮箱/资格判断；前版错误IDENTITY_EXTRACTION_VERSION_UNSUPPORTED且专家写入0，既有pipeline转FAILED，不会自动重抽；证据VERSION仍20260925。
- 覆盖：版本不变量，X2。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
