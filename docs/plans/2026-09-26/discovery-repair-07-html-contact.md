# 07：明确 HTML 联系人与挑战页回退

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-04、DD-09。

前置：[06：PDF 文末联系区与 mailto 注释](discovery-repair-06-pdf-coverage.md)完成机器验证后，以其产物为基线。

## 需求描述

恢复真实Springer通讯作者mailto关系；已确认反机器人页面不再终止现有公开全文回退链。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不绕过验证码/付费墙，不建爬虫或浏览器获取服务。不声称识别所有落地页/登录页；本次只修已证实挑战页及空内容。普通可读HTML无邮箱保留既有NO_EMAIL_IN_HTML成功口径，全面正文分类另行验证。

## 关键不变量

### Invariant I-1：结构化具名联系条目
- Rule：只新增真实见到的corresponding-author-list联系区（要求同区域corresponding-author标题）中独立a[href=mailto:]。anchor可见全名必须唯一对应metadata作者；只解析一个目标邮箱，不从aria-label/链接顺序猜名字。多个独立具名链接可分别绑定；共享无名列表仍未知，重复姓名候选或矛盾claims拒绝。href邮箱须加入最终found集合并走现有黑名单。
- Applies to：HTML DOM→claims/results
- Violation consequence：只产生claim但最终丢邮箱或错绑作者
- 来源：K-author-identity-needs-email-evidence

### Invariant I-2：已知挑战页才判无效
- Rule：已观察Anubis挑战页：挑战script标识与挑战标题结构联合命中；只出现论文正文单词bot/challenge不拒绝。另拒绝去script/style后无可见内容的空HTML。结果failureReason=PDF_DOWNLOAD_FAILED、downloadFailureCategory=INVALID_CONTENT、fulltextObtained=false、emails=[]，由既有OA回退链继续。普通有内容无邮箱的HTML仍NO_EMAIL_IN_HTML/true，不以邮箱数判真假全文。
- Applies to：PdfEmailExtractor.extractFromHtml→OpenAlex fallback
- Violation consequence：反爬页面阻断备用来源或正常文章误拒绝
- 来源：original

### Invariant I-3：不扩请求与身份边界
- Rule：仍最多3个去重全文URL、共享单篇deadline、禁止计量内容主机；真实HTML识别结果继续原验证/资格/去重。EXTRACTION_VERSION=20261001（单调兼容编号，不代表上线日期），VERSION不变。
- Applies to：HTML→OA回退→cache→consumer
- Violation consequence：无限重试或外部ID推断
- 来源：K-identity-cache-version-and-admission

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

SourceAuthorEmailResolver.kt:28～53只支持ltx_role_author/schema Person联系人，最终found来自visibleText，忽略href邮箱；真实Springer有h3#corresponding-author与p#corresponding-author-list中的具名mailto。PdfEmailExtractor.kt:148～156对HTML恒fulltextObtained=true；OpenAlexDataSource.kt:162立即返回。真实W4381304672页面title=Making sure you're not a bot!，含script#anubis_challenge和anubis_version。交互X2/X4/X8及获取回退，审计附件C2/C3/C6。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

缓存影响：抽取版本从前一子计划产物升级至20261001。当前pipeline非空旧缓存不会重抽，会以IDENTITY_EXTRACTION_VERSION_UNSUPPORTED失败；本计划保留并测试这项现有行为，不新增兼容白名单。上线前要只读统计旧版本活跃job；若存在需要保留的结果，先单独决定处置，不能以本计划授权清空队列。schema及payload_version不变。

## 实现方案

### T-1：准备原HTML证据（I-1/I-2，I-4）

src/test/resources/discovery/html-contact-recall.zip及同名.md：W3094704314、W3135028703、W3194730353、W4381304672完整原HTML/metadata/manifest；保留已有Edward/Hang夹具不覆盖。src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt真实解析RED。

### T-2：最小DOM分支（I-1/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt：复用现有Node/ParserDelegator；新增具名联系区候选并合并found，邮箱URI处理保留local-part中的+，拒绝多收件人/CRLF，删除query，百分号解码不按form把+变空格。冲突仍统一消解。

### T-3：挑战页回退（I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt在resolveHtml前做已知挑战/空内容判别；src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt测真实挑战页与含challenge学术正文反例；src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt首地址挑战、次地址真PDF、重复URL、截止、无邮箱正文终止等组合。无需修改OA回退生产代码。

### T-4：消费、版本与旧入口（I-1/I-2/I-3，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt用实际HTML输出过consumer；src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt、src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt升级版本；正反例从src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt继承。

### T-5：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/07.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共10个文件，1个解析子系统及直接版本/测试边界；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` |
| 9 | `src/test/resources/discovery/html-contact-recall.zip` |
| 10 | `src/test/resources/discovery/html-contact-recall.md` |

## 验收标准

- I-1：W3094704314→Vijay Kumar/vijaykumarchahar@gmail.com；W3135028703、W3194730353→Iqbal H. Sarker/msarker@swin.edu.au；3个论文关系、2个独立邮箱，不报3位专家。
- I-1：锚点姓名重名/共享说明/不同作者矛盾均不绑；HTML Edward两个明确邮箱保持；Hang共享邮箱为空身份。
- I-2：真实挑战页面false/INVALID_CONTENT；随后真PDF被请求；纯学术challenge字词与正常无邮箱HTML不误拒绝。
- I-3：截止/第三URL后额外请求0；第三篇重复Iqbal只计重复，总RAW=2候选=2；新旧缓存版本边界明确。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SourceAuthorEmailResolverTest,PdfEmailExtractorTest,OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,DiscoveryPipelineServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：真实通讯作者
- 前置条件：三篇HTML原文及metadata；外部验证/资格通过、隔离ES空。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试并看 target/discovery-plan-acceptance/07.json；逐条打开corresponding-author-list原片段。
- 预期结果：Vijay和Iqbal两对符合上列真值；论文关系3、独立邮箱2；按3篇依次消费RAW=2候选=2、重复=1。
- 覆盖：I-1/I-3，X2/X4/X8，需求。

### A-2：挑战页到备用正文
- 前置条件：同篇首地址返回归档Anubis挑战，第二地址返回已知真PDF；另有正常无邮箱HTML对照。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 查看报告请求地址序列、fulltextObtained、失败子类与最终结果。
- 预期结果：挑战阶段false/INVALID_CONTENT并请求第二地址；正常无邮箱HTML=true且不再请求第二地址；整个链仍最多3个URL。
- 覆盖：I-2/I-3，获取回退，需求。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

### A-4：队列新旧结果
- 前置条件：隔离pipeline fixture中各放一个前版和本版20261001的已缓存明确邮箱结果，metadata/payload_version合法；不操作生产队列。
- 操作步骤：1. 跑本计划定向命令；2. 看报告versionCases的消费与最终状态。
- 预期结果：本版正常经过原邮箱/资格判断；前版错误IDENTITY_EXTRACTION_VERSION_UNSUPPORTED且专家写入0，既有pipeline转FAILED，不会自动重抽；证据VERSION仍20260925。
- 覆盖：版本不变量，X2。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
