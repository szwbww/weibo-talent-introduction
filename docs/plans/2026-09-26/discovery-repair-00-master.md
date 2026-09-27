# 深度发现联合修复开发计划

状态：**01～08 用户告知已由另一 agent 执行，完成状态以其台账为准；09 为 2026-09-27 新追加计划，尚未实施**。create-p；原计划2026-09-26；目标 main。
代码基线：`64c0394a940bd79c2ecc04e5c497650f045faa75`。
本文件是顺序和范围索引，**不是允许一次修改所有文件的执行计划**；每次执行以对应子计划的文件清单和不变量为边界。


> **执行agent重读入口（2026-09-27追加）**：当前正在执行的01～08继续按各自文件清单完成；新增[09 专业范围修复](discovery-repair-09-subject-scope.md)排在08之后，按09a→09b→09c→09d执行。09必须基于前序实际产物，不从旧HEAD覆盖共享文件。09修改了此前“资格完全不变”的边界，仅按下述明确例外生效；原计划的解析/身份验收真值保持。当前开发/验证台账不因本次改文档自动标完成。

## 需求描述

1. 可恢复的元数据故障先有限重试，去重失败不再跳过未完成页或误报成功。
2. 恢复原文中已经证明的姓名—邮箱关系，补足合法邮箱线索；不以数量目标牺牲归属正确性。
3. 执行记录区分身份未确认、抓取失败、来源停止及首选方式，避免将解析损失解释成人才资格不符。
4. **2026-09-27追加09**：恢复ORCID真实专业检索，统一深度发现专业准入与补全后候选复评。用户已确认沿用工程/材料/计算机/化工/能源/物理六类，保留相关高校科研人员。

必须保持：

- M1：有唯一来源证据才绑定身份；同名不合并，共享/冲突不绑定；姓名、邮箱、ORCID/OpenAlex ID不得拼装不同人。
- M2：01～08保持现有邮箱验证、人才资格、重复不覆盖、create写与批量发送配置。09仅按新增明确授权修复discovery自动专业准入：未知留RAW、补全确认后晋升、范围外条件撤下候选副本；不加隐藏首发规则、不改可见类型勾选。人工override及非discovery资格不在09自动规则内。
- M3：人工暂停、额度账本、下载大小/时限；新批次仍由用户手动通知验证，不建定时任务。
- M4：此计划不授权清洗存量、删除数据、数据库迁移、切换运行模式、部署或推送远端。

范围外：

- DD-08：同步路径逐篇原文长期存储、保留/容量管理及查询界面。现在确有追溯缺口，但既有队列表明确不存PDF。不能把建设一个原文档案系统藏进解析修复；本组只保存测试夹具与离线验收产物，DD-08仍保持未解决。
- DD-10：通用的元数据—正文身份校验/自动纠错。已确认一篇来源错配，没有量化线上错写；本组用该原文作防误绑负例，不做标题相似度阈值、LLM判断或自动改名。完成负例不代表解决上游来源质量。
- 新增有偿Content API、浏览器绕过验证码、OCR、跨论文身份图谱、全页扫描、新配置工作台、统计大改、历史回填、重构整个发现架构。

## 关键不变量

### Invariant I-1：证据分级不能变成产量承诺
- Rule：每项关联现有代码、原始证据与可执行验收。构造输入只证明规则行为，真实少量样本不代表整体失败率；“找到邮箱”“确认归属”“邮箱有效”“人才合格”“新增专家”分别报告。原22项发现中20项进入01～08；新增DD-23～26进入09。合计26项、24项纳入，DD-08/DD-10仍保留，不宣称全部已解决。
- Applies to：所有子计划、夹具、最终验证报告、缺陷关闭记录。
- Violation consequence：重复统计、承诺无法证实的恢复数量。
- 来源：K-named-fixture-must-use-real-row；K-plan-quantified-claims-need-grep-receipts。

### Invariant I-2：最小改动和顺序基线
- Rule：按01→02→03→04→05→06→07→08→09a→09b→09c→09d顺序。共享生产/测试文件的后计划以已验证前计划产物为基线，不并行从旧基线实现。每份≤10文件、≤2子系统，清单外变动先修订计划；发现新问题不能临时塞入本批。
- Applies to：每个子计划执行、验证、合并与回滚。
- Violation consequence：互相覆盖规则、文件范围膨胀。(来源: K-master-plan-shared-file-sequential-gates)
- 来源：create-p硬限制。

### Invariant I-3：复用既有持久化语义
- Rule：01～08不新增数据库/ES字段，不改query hash、lease/generation、email主键或历史ORCID关联键。01新增DEDUP_INCOMPLETE是既有stopReason值；03～07更新EXTRACTION_VERSION，证据VERSION不变；06新增公开配置tailPages。09限定例外：三层只新增researchFieldIds事实字段；仅ORCID同步/队列来源query hash增加编码语义标记；分类规则版本升为rnd-v3-20260927；复用既有候选层与filterResult/filterRejectReason。09不改身份/抽取版本、数据库schema、其他来源key、全局流水线hash、发送配置。
- Applies to：checkpoint、队列抽取结果、专家层、任务详情与配置读取。
- Violation consequence：修解析却改变数据权威/发信选择。
- 来源：K-identity-cache-version-and-admission；K-historical-identity-orcid-fallback。

### Invariant I-4：前后端事实相同
- Rule：任务bySource仍来自现有SourceStats。08复用已有过滤/失败/stopReason，不新增实际方式统计；extractionMethod只称首选方式。既有后台只保存最多20项原因，前端展示“完整已存原因”而不是冒充无限原始明细。
- Applies to：SourceStats→进度/历史→来源表；日志。
- Violation consequence：同一个任务在不同位置被解释成不同结果。
- 来源：代码审计DD-15～17。

### Invariant I-5：回归与发布分开
- Rule：每份有独立机器验证和人工清单；机器通过≠人工通过≠已上线。每份可在其前置基线上独立验证；09各片改善范围见09索引，09d及联合回归前不能宣称专业准入闭环；部署不是本次授权。解析子计划都包含版本兼容用例；旧缓存不会自动重抽，发布前必须只读清点旧版本活跃结果并报告，不能擅自清理。
- Applies to：03～07缓存变动、全部发布/验证记录。
- Violation consequence：旧缓存失败被掩盖或声称线上已修复。
- 来源：会话要求；K-identity-cache-version-and-admission。

## 样式契约

### S-1：仅08执行的来源表契约
- 复用：styles.css:987～991的table-wrap，:3421～3456的data-table及details/summary规则；完整实值见审计附件，规则不改。无新增CSS/class，禁止inline style。
- DOM：逐字采用[08的S-1](discovery-repair-08-source-report.md#样式契约)中完整HTML骨架：table-wrap→data-table→thead/tbody；8列，末列details/summary和过滤/失败/停止三个div。主计划不另定义第二套结构。
- 验证：08 DOM/class检查及A-4人工视觉验收；新资源URL不新增DOM元素。

## 现状审计

[完整审计附件](discovery-repair-audit.md)是本节组成部分，包含schema/mapping、写路径、读路径、交互点X1～X8、前端样式基线及逐行代码引用。[检索回执](discovery-repair-evidence/)记录实际命令/输出；[代码SHA](discovery-repair-evidence/baseline-sha256.json)定位基线。现有诊断中的13项通过是此前直接调用编译测试方法，不是本计划实现或全量测试通过。

关键结论：

- 论文/ORCID循环已处理RAW写失败与补全入队失败，唯独未把dedupErrors增量纳入页完成判定；不必重构消费架构。
- OpenAlex同步搜索临时异常直接切源；队列已有5分钟来源延期，PDF/PMC已有FetchRetry。因此新增修复仅放在缺失的同步OpenAlex元数据循环。
- XML/PDF/HTML已有来源证据与唯一候选消解。扩展被证实的结构，保持冲突拒绝；不恢复邮箱拼写猜姓名。
- HTML获取已有OA候选→备用PDF→Unpaywall链。已知挑战页改为INVALID_CONTENT即可复用回退；本组不增网络获取平台。
- 任务bySource已包含过滤和停止信息；08只补展示/文案，方式列改名即可，无需增加一套统计。
- 旧队列extraction_json非空会跳过抽取，过期版本消费会FAILED。此次不另做缓存迁移；每个解析子计划明确披露这一限制。

## 实现方案

### 顺序、独立范围和验证门禁

执行每份前：核对前置产物、确认工作树无冲突改动；复核该份触及的方法和schema仍与审计一致。顺序门禁不要求重新请求已经给出的授权；本线程只追加计划，不代替当前执行agent实施。用户将通知执行agent重读；不要中断或重做已通过的01～08。

| 顺序 | 子计划 | 对应发现 | 文件数 | 验证重点 |
|---|---|---|---:|---|
| 01 | [discovery-repair-01-page-replay](discovery-repair-01-page-replay.md) | DD-18 | 2 | 论文/ORCID去重500，失败页重放幂等 |
| 02 | [discovery-repair-02-search-retry](discovery-repair-02-search-retry.md) | DD-11 | 2 | 同游标最多3次、暂停/额度、队列不叠加重试 |
| 03 | [discovery-repair-03-email-text](discovery-repair-03-email-text.md) | DD-06、DD-12、DD-14 | 8 | brace/断行/(at)；只补线索不猜归属 |
| 04 | [discovery-repair-04-xml-route](discovery-repair-04-xml-route.md) | DD-07、DD-19、DD-20、DD-21、DD-22 | 10 | PMC路由、逗号姓名、XML3个真联系人及合成结构 |
| 05 | [discovery-source-contact-recall](discovery-source-contact-recall.md) | DD-01、DD-02、DD-03 | 10 | 3篇原PDF恢复4对，另4条共享仍未知 |
| 06 | [discovery-repair-06-pdf-coverage](discovery-repair-06-pdf-coverage.md) | DD-05、DD-13 | 10 | 前2+末1页、5对明确联系人、11个mailto线索 |
| 07 | [discovery-repair-07-html-contact](discovery-repair-07-html-contact.md) | DD-04、DD-09 | 10 | Springer3篇2人；真实挑战页触发回退 |
| 08 | [discovery-repair-08-source-report](discovery-repair-08-source-report.md) | DD-15、DD-16、DD-17 | 6 | 真实20240已存原因、首选方式、两处日志 |
| 09 | [专业范围与候选准入](discovery-repair-09-subject-scope.md)（含09a～09d） | DD-23、DD-24、DD-25、DD-26 | 每片6/10/9/9 | 查询真实URI与旧游标隔离、六类事实、分类、补全后资格闭环 |

每份完成后：对应定向测试→fix-v独立机器验证→记录结果；人工验收从该计划A项导出，不提前生成副本。失败只在本份范围修复；若需要第11个文件或第三子系统，回到create-p拆分。最终机器检查跑JDK11 `mvn clean package`（含仓库配置的JS测试），不把每份重复全量构建当额外质量证据。

### 共享文件与版本衔接（I-2/I-3/I-5）

下表来自各子计划实际文件清单统计，具体命令：读取plan-scope.json，对files做集合并按path分组；不是估算。

| 共享文件 | 修改计划 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 01→02→08 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 05→06 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 05→06→07 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 03→05→07 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 03→04→05→06→07 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 01→02→03→04→05→06→07→08 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 04→07 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 05→06→07 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 03→05→07 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 03→04→05→06→07 |

| 解析计划 | EXTRACTION_VERSION | 证据 VERSION |
|---|---:|---:|
| 03 | 20260927 | 20260925（不变） |
| 04 | 20260928 | 20260925（不变） |
| 05 | 20260929 | 20260925（不变） |
| 06 | 20260930 | 20260925（不变） |
| 07 | 20261001 | 20260925（不变） |

兼容版本是规则标识，不代表实施日期。若实际执行时main已有更高抽取版本，先统一修订此表和相关用例，禁止版本倒退。源码改动和对应测试/夹具为每份原子单元，不能只上线parser不带同份版本修改。无需等08界面才能使用01修复，旧界面也能经现有任务详情看到终态。

回滚：只能回滚**最近一个已上线前缀之后的完整子计划**，不能越过共享文件后的修改单独revert早期补丁；优先前向小修。回滚解析版本会使较新缓存不兼容，同样先报告缓存清点结果；不回滚/覆盖专家数据，不做队列批量清空。

### 09共享文件补充与原验收口径衔接

- ExpertDiscoveryService.kt / Test：原01→02→08，随后09a（仅来源query规范化接线）→09c（事实写入）→09d（准入/复评）。
- OpenAlexDataSource.kt / Test：原04→07，随后09c。不得覆盖04的PMC路由或07的回退修复。
- OperatorStatusWriteSeamGuardTest：09b→09d，仅机械行号修正，不扩大运营写入口。
- 原01～08故障/原文解析验收记录是各自完成时的事实，不倒改成09结果。最终consumer测试中“立即晋升”的预期由09d调整为“先RAW、专业补全后晋升”；原始姓名邮箱识别结果保持不变。
- 09不用新队列/判定平台，只给已有作者响应增加一个结构化字段并接入现有分类/资格。无可信作者ID者仍留RAW，不能为提高产量猜ID。

### 不过度设计的具体约束

- 元数据重试放现有service循环；不新建重试队列、通用框架或每专家状态机。
- 姓名“姓,名”分别在两个适配器现有拆分点做窄处理；不新建姓名解析服务。
- PDF只有一个小型布局工具，服务于本次已验证作者/联系块；不用OCR或全篇版面平台。
- PMC只从已返回的可信链接取编号，不增加DOI/PMID映射请求；失败仍走现有回退。
- HTML只新增已验证的通讯区与挑战页判别，不用“全文质量评分”。
- 界面用现有data-table/table-wrap/details，方式列改“首选方式”；不新增真实方式计数、监控面板或样式体系。
- 06唯一新增配置PDF_TAIL_PAGES默认1、只能0/1；写在既有pdf-extraction配置，不进入批量发送资格，0可恢复原首部页采样。

### 发现覆盖和关闭条件（I-1）

| 发现 | 证据级别 | 归属/处理 | 关闭条件 |
|---|---|---|---|
| DD-01 PDF 作者与邮箱脚注标记未关联 | 真实原文已复现 | 子计划05 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-02 不支持唯一作者姓名缩写 | 真实原文已复现 | 子计划05 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-03 机构描述、换行联系段落超出窄格式 | 真实原文已复现 | 子计划05 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-04 HTML 通讯作者姓名与 mailto 关系未利用 | 真实原文已复现 | 子计划07 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-05 只读 PDF 前两页漏掉文末作者信息 | 真实原文已复现 | 子计划06 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-06 跨行邮箱字符串漏抽 | 真实原文已复现 | 子计划03 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-07 姓逗号名格式被首空格拆分 | 代码与原文已确认；恢复数量未验证 | 子计划04 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-08 本轮同步发现不留逐篇原文及拒绝明细 | 生产与代码已确认 | 明确延期：原文存储另立需求 | 本组不能关闭；先确定存储位置、容量上限、保留周期和查询方式再立计划 |
| DD-09 反爬页面可计为获取成功并终止公开来源回退 | 真实页面与代码已确认 | 子计划07 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-10 上游元数据与下载正文身份不一致 | 来源异常已确认；并非已证明本系统写错 | 来源异常，06纳入真实负例 | 本组只确认未误绑；上游一致性修复未实施，不关闭来源异常 |
| DD-11 临时元数据网络故障直接结束来源 | 生产故障已确认 | 子计划02 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-12 花括号邮箱组不支持空格、数字、点及分号 | 真实原文已复现 | 子计划03 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-13 PDF 的 mailto 注释链接未读取 | 真实PDF已复现 | 子计划06 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-14 邮箱反混淆与姓名匹配未使用同一规范化 | 合成最小复现；真实影响未量化 | 子计划03 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-15 来源表不展示归属过滤和停止原因 | 真实任务数据驱动原JS已复现 | 子计划08 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-16 日志把归属未确认统称资格淘汰 | 生产日志与代码已确认 | 子计划08 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-17 方式列是来源默认方式，不是实际逐篇方式 | 代码已确认；显示语义问题 | 子计划08 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-18 去重查询失败仍推进检查点并报告成功 | 故障注入已复现；未证明本次生产已发生 | 子计划01 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-19 JATS 数字脚注标签阻断唯一关联 | 合成最小复现；真实影响未量化 | 子计划04 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-20 JATS name-alternatives 包装导致整个作者被忽略 | 合成最小复现；真实影响未量化 | 子计划04 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-21 OpenAlex 已返回PMC链接却未利用其编号 | 真实元数据及实际解析函数已复现 | 子计划04 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |
| DD-22 JATS 正文贡献者信息区明确邮箱未解析 | 真实公开XML已复现 | 子计划04 | 该子计划对应原始/合成用例及防错回归通过；生产影响数仍不外推 |

新增发现及关闭边界：

| 发现 | 已有证据 | 处理 | 关闭条件 |
| --- | --- | --- | --- |
| DD-23 ORCID重复URL编码使专业查询扩散 | 最终URI+公开API对照，部署class一致 | 09a | 单次编码且同步/队列旧ORCID查询位置隔离 |
| DD-24 深度发现直接晋升未检查目标专业 | 生产时序+自动写路径代码 | 09d | 所有discovery自动入口统一准入，未知留RAW |
| DD-25 补全范围外仍留候选、任务成功先于资格复评 | 75条生产候选+完成顺序代码 | 09d | 资格复评先于Success，条件撤副本、故障重试 |
| DD-26 STEM/科研分无法证明六类专业 | 真实专业样本+分类计分代码 | 09b/09c | 结构化field进入三层/画像/分类；六类高校保留，非目标不判研发 |

DD-08/DD-10延期不是“无需处理”，也不能在最终报告合并成已完成。未知真实影响数量（DD-14/19/20、DD-18线上发生与否）保留未知。新增4+5+3+HTML2对不能直接相加当预计新增专家：有重复、验证、资格和已有记录多重影响。

## 变更文件清单

本master本身执行文件数=0。原01～08分别2/2/8/10/10/10/10/6个文件，去重31个：生产13、测试代码9、测试资源9。新增09a～09d分别6/10/9/9个文件，去重29个（生产14、测试代码14、资源1），其中4个与原计划重叠。**联合总去重56个：生产25、测试代码21、测试资源10。** 下表保留原01～08全集，后表列新增25个；只是范围索引，不能代替各子计划≤10文件边界。

| 文件 | 分类 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 生产 |
| `src/main/resources/application.yml` | 生产 |
| `src/main/resources/static/app.js` | 生产 |
| `src/main/resources/static/index.html` | 生产 |
| `src/test/js/taskRecordsSemantics.test.js` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 测试代码 |
| `src/test/resources/discovery/email-text-recall.json` | 测试资源 |
| `src/test/resources/discovery/html-contact-recall.md` | 测试资源 |
| `src/test/resources/discovery/html-contact-recall.zip` | 测试资源 |
| `src/test/resources/discovery/pdf-contact-coverage.md` | 测试资源 |
| `src/test/resources/discovery/pdf-contact-coverage.zip` | 测试资源 |
| `src/test/resources/discovery/source-contact-recall.md` | 测试资源 |
| `src/test/resources/discovery/source-contact-recall.zip` | 测试资源 |
| `src/test/resources/discovery/task-20240-by-source.json` | 测试资源 |
| `src/test/resources/discovery/xml-route-recall.zip` | 测试资源 |


09在原31个之外新增的25个文件（重叠4个已在原表）：

| 文件 | 分类 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 生产 |
| `src/main/resources/es/orcid_info_application.json` | 生产 |
| `src/main/resources/es/orcid_info_candidate.json` | 生产 |
| `src/main/resources/es/orcid_info_raw.json` | 生产 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 测试代码 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 测试代码 |
| `src/test/resources/discovery/rnd-scope-evidence.json` | 测试资源 |

本线程本次实际写入只有计划/审计/知识文档；以上为**计划范围**，不代表另一agent的实际完成清单。09新增候选资格服务改动，01～08保持原范围。没有迁移SQL、发送服务或线上修数据脚本。测试夹具新增源自已存原始字节，容量以复制后的实际文件为准，不猜测新ZIP大小。

## 验收标准

- I-1：联合plan-scope.json覆盖24个唯一DD编号；原01～08台账覆盖20个、新增09覆盖DD-23～26；DD-08/DD-10明确不关闭。每个原文命名测试有原始SHA；不拿受控重排文本替代PDF。
- I-2：逐份清单≤10、子系统≤2；共享文件按序执行，最终联合diff属于56文件集合（原01～08仍为31文件）；08 CSS文件diff=0（复用样式）。计划外机械修复仍先补清单，不用总集合给当前子计划越权。
- I-3：01～08无schema/新字段；09只增加researchFieldIds的三层mapping并隔离ORCID来源查询语义，按09的限定例外验收；DB迁移0。既有身份VERSION不变、抽取版本按表单调，分类版本由09c单独升级；新增配置只有tailPages。
- S-1：逐字继承08的DOM契约，源码CSS diff=0，来源表无inline style，人工检查padding/字体/展开行为。
- I-4：真实任务最终快照635邮箱/6有效/4收录/4晋升，能查看IDENTITY_UNRESOLVED:629和SEARCH_FAILED；没有用1000篇中间快照代替最终值。
- I-5：每份独立验证记录、人工结果单独记录；最终JDK11完整构建通过；旧缓存行为有实际测试结果，生产新批次由用户手动通知后检查。测试通过不能写“已上线”。
- M1～M4：共享/同名/缩写冲突负例无新增错绑；同邮箱重放不覆盖；邮箱无效RAW=0、资格拒绝RAW=1候选=0；09之后专业未确认也为RAW=1候选=0且正常补全入队，补全合格才晋升。发送配置/DB迁移/人工生产清洗/自动化新增均0；09d运行时代码可按明确规则撤下候选副本，但执行本计划不触发线上复评。

全量门禁命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
```

验收结果需注明所用提交、实际测试数/跳过数/失败数；不能搬用先前4072后端或1193前端测试结果。本线程此次规划未运行实现测试；另一agent的已运行结果以其执行台账为准。

## 人工验收清单

### A-1：故障恢复结果
- 前置条件：完成01/02机器验证，隔离测试已生成01.json/02.json，含ES500与OpenAlex暂时网络故障；不用生产注入故障。
- 操作步骤：1. 查看01末页失败与第二次重放；2. 查看02请求序列、停止/配额/暂停场景。
- 预期结果：末页去重错误ACTIVE/DEDUP_INCOMPLETE/PARTIAL_SUCCESS；重放后总RAW=2候选=2且旧身份不变；可恢复网络失败总尝试≤3，证书错误1次；暂停/额度阻止后续请求。
- 覆盖：需求1、I-2/I-3/I-5、M2/M3、X1/X3/X5/X6/X7。

### A-2：原文关系与线索区分
- 前置条件：03～07机器验证完成，原PDF/HTML/XML与03～07验收JSON可读。
- 操作步骤：1. 对照PDF四对、末页五对、XML三对、Springer三篇两邮箱；2. 查看brace、断行、mailto线索；3. 对照共享/同名/来源错配负例。
- 预期结果：明确关系逐对等于各子计划真值；无归属线索姓名为空，不生成专家；源错配负例不继承错误metadata；不同姓名归属冲突仍未知。
- 覆盖：需求2、I-1/I-3、M1、X2/X4/X8。

### A-3：原业务保护与09明确变化
- 前置条件：隔离consumer回归结果中有验证失败、资格拒绝、同邮箱重复、同名不同邮箱及一人多邮箱样例；变更diff可读。
- 操作步骤：1. 检查各组RAW/候选结果与原身份；2. 查看发送配置/发送服务/schema/线上脚本diff；3. 在既有配置界面核对没有新增首发黑名单或资格开关。
- 预期结果：无效RAW=0；资格拒绝RAW=1候选=0；重复新增0且原身份不变；同名不同邮箱2条、一人两明确邮箱2条仍可写RAW；09最终未知先RAW，补全达到六类研发条件才晋升；未新增隐藏首发逻辑；无人工线上数据写入/部署动作。
- 覆盖：M1/M2/M4、I-3/I-5、X4/X5。

### A-4：可读的执行记录
- 前置条件：08完成，target/discovery-plan-acceptance/08.html采用原最终快照。
- 操作步骤：1. 浏览器打开并展开原因详情；2. 对照实时/历史两份表；3. 核对学术补全旧分支、横向滚动和字体。
- 预期结果：635/6/4/4不变；629身份未确认及SEARCH_FAILED可见；首选方式FULLTEXT_XML；不相加嵌套失败计数；样式遵循08的S-1，未改其他表。
- 覆盖：需求3、I-4、X7。

### A-5：缓存与后续上线边界
- 前置条件：03～07旧/新版队列测试结果和发布前只读活跃缓存清点结果可读；若尚未决定发布，生产清点标“待发布前执行”。
- 操作步骤：1. 查看新旧抽取版本消费结果；2. 确认结果是否自动重抽；3. 查看发布记录/自动化列表是否新增动作。
- 预期结果：旧结果IDENTITY_EXTRACTION_VERSION_UNSUPPORTED/FAILED，新结果按原规则消费；没有把旧结果伪装成本版，也没有自动清空队列；本计划阶段发布/自动化新增0。用户通知后才核验新生产批次。
- 覆盖：I-3/I-5、M3/M4、X2。

### A-6：09专业范围与资格闭环
- 前置条件：09a～09d机器验证完成，实际09a/09b/09c/09d.json可读；使用隔离数据，不调用生产复评。
- 操作步骤：1. 查看实际ORCID URI和新旧游标；2. 查看六类高校样本；3. 查看兽医/艺术/商务、无专业字段样本；4. 查看补全后候选变化及409/500重试；5. 对照联系人、邮件、申请层和发送勾选快照。
- 预期结果：查询一次解码还原原q且不继承旧ORCID offset；六类高校足够科研证据→ACADEMIC_RND；已知非目标→OUT_OF_SCOPE/候选0；未知→RAW保留/候选0；失败不报SUCCEEDED；联系人/邮件/申请层/发送配置不变。
- 覆盖：需求4、09 I-1～I-3、DD-23～26、X9-1～X9-6。

人工验收开始时，从本节导出discovery-repair-00-master-acceptance.md；子计划同理。此时不生成任何人工勾选结果或把验收标通过。

## 自查与知识回写

- 范围/编号/链接/文件存在性通过脚本核对，见discovery-repair-evidence/plan-review.json；它只证明文档结构及引用，不证明代码修复完成。
- 原代码审计为8份切片；追加09含4份，合计12份执行切片；无单计划>10文件，含前端的08有S-1与逐字DOM，无新增CSS。
- 新沉淀：页完成判据必须覆盖所有可重试消费失败；队列与同步恢复不能混用。写入K-page-commit-must-cover-consumer-failures，供以后修改检查点引用。
- 已有知识9条已加载更新；未因历史经验直接假定代码仍存在。没有本轮需要归档的过期匹配项，没有满足同题5条的合并；已经推广的计数规则不重复写CLAUDE。仓库没有agents/或templates/目录，不创建额外角色基础设施。

## 2026-09-27修订记录

- 用户追加专业范围修复并确认六类高校科研保留；新增09入口与四个受限子计划，不中断/重写其他agent的01～08工作。
- M2与I-3只有09所列专业准入/一个事实字段/ORCID查询语义/分类版本例外，M1身份与发送配置保护保持。
- 联合清单56个文件；原01～08清单已留存为 `discovery-repair-evidence/plan-scope-01-08.json`，新的plan-scope.json兼容追加09a～09d；旧plan-review.json仅代表原8片，09追加自查另见scope-09/plan-review.json。
- 本次未修改生产代码、未执行数据清洗/部署；75条已标范围外记录的实际处理仍需另行产出可复核操作清单，不能由计划追加视为已经删除。
