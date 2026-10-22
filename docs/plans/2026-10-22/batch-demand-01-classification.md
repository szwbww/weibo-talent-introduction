# 01 分类时间读写兼容修复

状态：待用户批准；本文件不是执行记录。依赖：无。范围：6 文件、分类 ES 读写一个子系统。证据：[账本 E-01/E-02](batch-demand-evidence.md)。

## 需求描述

恢复因合法数组时间而被误丢的研发分类；补全后新写分类时间固定为字符串。不修改分类规则、分数、准入、地区/状态/邮箱策略；不批量回刷 ES、不重跑分类、不修改全局 Jackson、不改 mapping 或 DB。

## 关键不变量

### Invariant I-1: 历史格式兼容
- Rule: 保留现有 yyyy-MM-dd HH:mm:ss、yyyy-MM-dd、epoch_millis；新增 Jackson LocalDateTime 的 5/6/7 项整数数组 [year,month,day,hour,minute,(second),(nano)]。缺秒/纳秒补0；每项必须整型且在 Int 范围内，交给 LocalDateTime.of 严格校验日期与纳秒。拒绝空、4/8项、浮点、溢出、非法日期；不得用当前时间兜底。
- Applies to: ExpertSearchService.parseClassifiedAt，三层 toExpertProfile。
- Violation consequence: 误放不合法数据，或继续把合法分类置空。
- 来源: original

### Invariant I-2: 固定写入表示
- Rule: updateExpertAcademicFields 使用 expertIndexWriterService.classificationNode(classify(enrichedProfile))，沿用已有 yyyy-MM-dd HH:mm:ss 格式；不直接序列化 ExpertClassification 对象。普通doc与discovered脚本params.doc共用同一分类节点。
- Applies to: ExpertDiscoveryService:3275-3286，已存在层的局部_update。
- Violation consequence: 新数据继续产生数组，修复无法闭环。
- 来源: K-enrichment-write-three-layers

### Invariant I-3: 保护原有事实与三层边界
- Rule: 只变 classifiedAt 的读兼容/写表示；身份条件脚本、HEAD404不创建层、null事实不覆盖、分类type/分数/证据/version/fingerprint完全保持。既有其他损坏分类仍fail-closed。
- Applies to: 学术补全、重核、分类回填、发现新建、晋升透传。
- Violation consequence: 跨层覆盖、身份保护被绕过或扩大业务资格。
- 来源: K-promotion-source-passthrough

## 现状审计

### 三层 ES expertClassification
- Mapping：raw:50-61、candidate:53-64、application:63-74，classifiedAt 为 date，格式三选一。线上 mapping/版本在 E-02；仓库 dynamic:false 不代表线上 RAW/CANDIDATE 也是 false。
- 新生成写入口：ExpertIndexWriterService.bulkUpdateClassifications:257 / classificationNode:361 / classifiedProjectionSource:817；ExpertRevalidationService:296；ExpertDiscoveryService:2505 使用 classificationNode。**补全 :3275 是已证实的对象直写差异**。
- 透传写入口：ExpertIndexWriterService.promoteToCandidate/promoteToApplication 拷贝 _source；ExpertDiscoveryService:3601-3622 读源再 PUT candidate；只拷贝历史形状，不是新生成分类。保持不改，靠统一读取兼容旧形状。
- 脚本新建：import_contactout_visible_candidates.py:119/181、build_contactout_enterprise_es_import.py:200/254、enterprise_batch/import_candidate_es.py:94/167、import_personal_email_candidates.py:89/157；均已 strftime，不改。原文检索回执见 E-01/guard。
- 读取：ExpertSearchService:512/526-588 解析整个对象，时间返回null导致分类整体null；RecipientScope.matchesExpertType:269、InitialOutreachService:68 读取解析后type；ExpertIndexController:495-497展示；DiscoveryAdmissionPolicy:256-266读取。ES粗筛 expertTypePredicate:148只读原始type，所以与发送内存判断分裂。
- 交互 X1：补全→HTTP converter→ES→Search→类型门禁；X2：旧数组跨层晋升→Search兼容读取；X3：非法分类→原fail-closed不变。
- 本地实验已输出数组；仅捕获未序列化HttpEntity的mock不足以证明修复，必须检验实际converter输出。

## 实现方案

1. **读取修复（I-1/I-3）**：修改清单1；保持方法私有与原入口不变，追加isArray严格分支。不添加任意ISO格式猜测、不吞所有异常；测试清单3覆盖时间形式、纳秒边界、leap day、非整数/大整数/错长。
2. **写入收口（I-2/I-3）**：修改清单2的一处doc赋值，复用现有注入的writer；清单4覆盖普通doc和discovered脚本、三层存在/缺失、身份冲突noop。禁止改RestTemplateConfig或全局ObjectMapper。
3. **wire与发送判据联测（I-1/I-2/I-3）**：新增清单5，用项目RestTemplate真实JSON converter序列化捕获体，再回放到ExpertSearchService公开查询入口；固定分类字段、LocalDateTime=2026-10-22 02:47:14.390627000，断言输出 classifiedAt 为字符串，读取type保持，scope研发条件通过。数组旧档同样通过；无效类型/证据仍拒绝。
4. **机械守卫（I-3）**：清单6只修NoiseSite行号偏移并写来源注释，不改片段或忽略集合规则；未发生偏移则不改。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ClassificationWireFormatTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` |

## 验收标准

- I-1：5/6/7项数组、3种旧格式通过；所有非法数组返回null；不产生静默截断。
- I-2：真实HTTP converter输出两种updateBody均为字符串日期；直接领域对象直写的已知入口消失。
- I-3：三层不存在不新建、身份冲突不写；分类其余字段逐项相等；既有分类/晋升门禁测试通过。
- X1/X2/X3：由 ClassificationWireFormatTest 覆盖读写链与scope判定；不是只测日期helper。
- 命令：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest,ExpertDiscoveryServiceTest,ClassificationWireFormatTest,ExpertIndexWriterServiceTest,ExpertClassificationServiceTest,OperatorStatusWriteSeamGuardTest
```
- 生产前只读重算，不以21738作为恒定验收值；逐人对照“修复前因时间丢分类、修复后解析成功”的集合，其他缺项不得自动放行。

## 人工验收清单

### A-1: 数组老档无需回刷即可读取
- 前置条件: 隔离测试ES三层中各建一份合法分类档案，type=ACADEMIC_RND，classifiedAt=[2026,10,22,2,47,14,390627000]；其余条件满足。关闭真实SMTP，指向本地捕获箱。
- 操作步骤: 1. 打开专家列表；2. 用同一研发类型做发送预估；3. 在测试环境仅发1封。
- 预期结果: 档案分类可读、符合类型条件；捕获箱1封；ES原数组未被读操作改写。
- 覆盖: I-1/I-3、X2、需求恢复旧档。

### A-2: 补全写新值
- 前置条件: 测试环境两档案分别为普通/已发现，RAW+CANDIDATE存在，APPLICATION不存在，补全上游用固定响应。
- 操作步骤: 1. 运行测试补全；2. 查看ES原始_source及不存在层。
- 预期结果: 两种更新形式的已有层 classifiedAt 均为 yyyy-MM-dd HH:mm:ss 字符串；APPLICATION仍404；姓名/邮箱/运营状态未变。
- 覆盖: I-2/I-3、X1、需求不改其他事实。

### A-3: 不放宽错误数据和业务规则
- 前置条件: A-1复制四份，分别改时间为[2026,13,22,2,47]、type为未知值、国家为任务排除国家、邮箱为退订。
- 操作步骤: 1. 启动测试1封任务；2. 查看筛选/发送记录。
- 预期结果: 四份均不得外发；时间错误仍是解析失败，不补当前时间；分类规则和退订/地区规则未改。
- 覆盖: I-1/I-3、X3、must-NOT-change。

