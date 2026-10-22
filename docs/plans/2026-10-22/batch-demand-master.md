# 批量发信：误过滤修复、按需取人与统计缓存——主计划

日期：2026-10-22。状态：**待批准，尚未实施**。代码基线：HEAD `d403caeb5dcc4810067293c10600ee57f94e276b` 加当前工作树；源码行号以证据账本快照为准。本次只创建计划/知识沉淀，不修改业务代码、线上数据，不补发邮件。

## 需求描述

1. 修复合法分类日期被解析丢失、导致大量专家被误排除的问题。
2. 首封与材料提醒按本次成功额度分批取人；足额停止，失败/跳过补取，不在每次执行前全量统计。
3. 全量参考人数按条件缓存一次复用，展示缓存时间，提供显式刷新；无缓存也能发信。

必须保持：原分类/准入/地区/退订/绑定/发送历史/邮箱验证/账号配额/模板启动检查/幂等与事务；旧执行记录不重解释。不得把“已验证标签”当作免除其余准入条件。

不在范围：收信/IMAP、SMTP协议及账号调度算法、自动回复、专家筛选业务口径重定义、批量改ES历史数据、mapping/DDL/索引新增、数据库迁移、Redis、后台全库定时刷新、生产发布与自动补发。用户最初描述“收发信箱”，当前证据只支持批量发信选人与统计问题；不扩大到收信链路。

## 关键不变量

### Invariant I-1: 修缺陷而非放宽资格
- Rule: 只兼容合法历史日期格式并收口未来写法；其余规则保持。修后人数可以增加，但并非把所有30013人直接变成可发送。
- Applies to: 01及全链路验收。
- Violation consequence: 掩盖真实业务排除或批量越权发信。
- 来源: original


### Invariant I-2: 统计与执行分离
- Rule: 缓存是参考，不是候选白名单或成功配额；执行只读当前所需批次并重复安全检查。全库无合格人时仍允许扫描至耗尽，不能承诺任何输入下都只读取30条。
- Applies to: 02/04/05/06。
- Violation consequence: 缓存数字再次控制发送数量。
- 来源: original


### Invariant I-3: 历史与新计数共存
- Rule: 历史target/remaining保持；新任务用版本化execution区分计划成功、实际处理与未完成配额。缓存目标估计单独显示。
- Applies to: 03/04/06。
- Violation consequence: 把历史1757改成30或伪造全库剩余。
- 来源: original


### Invariant I-4: 逐段范围门禁
- Rule: 严格01→02→03→04→05→06，每段独立机器验证；每段最多10文件、2子系统、每共享store最多1个新字段。超清单先修订计划，不顺手扩展。
- Applies to: 所有实施/验证。
- Violation consequence: 修复范围失控。
- 来源: K-plan-quantified-claims-need-grep-receipts



## 样式契约

本文件是编排索引，不直接授权前端修改。03的S-1/S-2约束日志卡片；06的S-1/S-2约束缓存控制/提示。已有DOM/CSS逐字基线在证据账本；无新增CSS，无全局样式调整。实施必须按子计划骨架/实值，不得仅凭主计划概述改UI。

## 现状审计

### 已证实的缺陷及数值边界
- 写端：[ExpertDiscoveryService.kt:3275](../../../../src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt#L3275) 把含LocalDateTime的分类领域对象直接置入doc；普通更新与discovered脚本共用。
- 读端：[ExpertSearchService.kt:544](../../../../src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt#L544) 时间解析失败直接返回null，整个分类丢失；:566-588没有数组分支。列表ES按原始type筛，发送按解析对象筛，产生口径分裂。
- 对照写法：ExpertIndexWriterService:361-370已有classificationNode统一字符串输出。
- 本地使用项目WAR依赖的真实RestTemplate converter已复现 `classifiedAt:[2026,10,22,2,47,14,390627000]`；不是依据截图猜测。
- 前序同日只读诊断快照：30013→地区23500→日期解析失败排除21738→1762→准入缺失27→1735→缺姓4→1731→已知不可达5→1726；17点任务目标1726。**不是本轮重跑数字，也不能反推16点1757的每层历史人数。** 修后必须重新只读统计，不能预告固定恢复21738人全部可发。
- 16点截图成功30、skip17、remaining1710，等式1757=30+17+1710成立；当前单轮30成功配额解释本次发30，不证明SMTP/收信故障。

### 全量开销与交互点
- 首封执行初始化全量retry、countEsTargets、lazy preflight母集；roundQuota依赖estimatedRemaining。材料独立全量snapshot、10000限制及独立循环。见证据账本E-05。
- “只把数量缓存”不够：全量数参与控制流；需改取人、停止条件、计数展示三处。
- X1分类补全writer→converter→ES→Search→类型门禁：01。
- X2联系人/ES状态更新→游标枚举→实时安全检查→事务结果：02/04。
- X3进度/最终JSON→两reader→前端新旧指标：03/04。
- X4模板/片段/变体编辑→统计指纹→缓存隔离：05。
- X5缓存响应→两表单→按需执行不依赖缓存：05/06。
- 三层mapping、DB DDL、所有受影响writer/reader检索及截图对应样式原文均在[证据账本](batch-demand-evidence.md)。线上ES7.14.2/PIT能力已只读核验；只创建/关闭临时查询上下文，无业务写入。
- 新发现旧scroll缺body会静默return/break，不能直接作为缓存完整成功依据：05新增strict统计分支使用02游标；旧preview合同保留。
- 已有知识逐条复核；本轮读取条目计数更新，未将历史经验当代码证据。缓存框架知识中的“只有两条路/仅一种缓存”过度断言将收窄为已证实无框架依赖。

## 实现方案

### 顺序与独立部署
| 顺序 | 子计划 | 文件数 | 交付边界 |
|---|---|---:|---|
| 01 | [分类时间读写修复](batch-demand-01-classification.md) | 6 | 先修真实bug；旧数组无需全库回刷 |
| 02 | [有界候选读取基础](batch-demand-02-cursors.md) | 7 | 新PIT+NEW keyset接口，暂不切发送 |
| 03 | [进度协议兼容](batch-demand-03-progress.md) | 9 | 先部署可选新reader/UI；不启用新producer |
| 04 | [按需发送](batch-demand-04-execution.md) | 10 | 两发送循环切换，启用新producer；含逐次页大小游标扩展与追踪调用方回归 |
| 05 | [统计缓存后端](batch-demand-05-cache.md) | 8 | 新统计端点与指纹，旧preview仍可用 |
| 06 | [缓存页面](batch-demand-06-ui.md) | 6 | UI切新端点；显示缓存时间、单条件刷新 |

每段严格按自身I/S约束实施、测试后再进入下一段。01可以先独立上线，但生产发布仍需另行确认；其余按顺序部署避免06先于05、04先于03。故障回退按相反依赖顺序；数据库无迁移，不能回退01的读兼容后继续信赖旧人数。

### 已选设计与边界
- ES：每层独立PIT，完整search_after排序尾键；每页≤500，keep_alive5m/每60秒续期，close/failure/cancel资源释放；线上已验证能力，>10000深分页与长时间续期仍属实施期测试。
- NEW：updated_at DESC,id DESC keyset+起始maxId，不用offset；无跨MySQL/ES事务快照承诺。查询性能须测试环境EXPLAIN，若需DB索引另申请变更，不在本计划擅加DDL。
- 执行：每轮成功配额min(roundSize,实时容量)，有效轮数按oneRoundOnly/roundsPerRun；最后一轮后禁止为hasNext额外预读。所有真正投递前的保护保留。
- 缓存：进程内LRU64、2个不同key并发、同key单飞、24小时标陈旧；这些是明确设计参数，不是既有配置事实。重启后MISS，手动刷新；不永久保证全库人数实时。
- 模板：统计专用完整fingerprint，不改变旧启动versionToken语义；引用正文变化也换cache key。
- 诊断：全量参考totalSendable与最多20000的诊断母集分开显示。诊断部分结果仍partial；本次不扩建全量逐人原因分析。
- 可观测计量：新execution嵌套字段分别进入现有两个JSON store，无新列；旧记录不改写。
- 参数/时间均可注入测试，禁止用真实SMTP做自动化验收。

## 变更文件清单

本主计划无独立业务修改清单；执行清单为上述6份子计划的精确表格，不能一次性按34文件宽泛施工。去重合计**34个文件：12个生产代码/前端，22个测试**；其中新建2个生产文件（游标、缓存服务），7个测试文件。若实施时新增必要文件，先修订相应子计划并保持≤10限制。

本轮计划产物为主计划、6子计划、证据账本；知识沉淀文档不属于未来业务实施文件预算。保留工作树已有application.yml、CLAUDE.md及其它未提交改动；禁止覆盖、reset或把它们算作本次代码实现。

## 验收标准

- I-1：01的真实wire回放、合法数组和损坏数据测试通过；只读重算可列出修前/修后分类差异，不更改准入规则。
- I-2：04两类任务成功30后无额外页/全量计数；05/06缓存MISS、0、陈旧、失败都不阻塞按需执行；统计零外发。
- I-3：03/04运行中与最终结果同协议，历史1757保留，新的“未完成配额”不冒充全库剩余。
- I-4：每段独立测试、差异审计与人工清单；无清单外业务文件修改，前端遵守03/06全部S条款。
- 整体验证命令：使用Java11运行mvn test；node --test src/test/js/*.test.js；MySQL IT显式启用并记录环境。**本轮只跑了wire复现/只读能力探针，尚未运行这些新增测试，不得标PASS。**
- 数据快照验证：测试环境真实ES+MySQL+SMTP捕获箱，覆盖>10000、NEW>500、过滤空页、重复身份、发送前退订/地址变化、并发刷新与重启。
- 上线闸门：先全部机器验证，再按下面人工清单验收；线上只读统计需要沿用户已授权的配置来源/腾讯云ES，不打印保存凭据；发布/补发不包含在本轮。


### 知识输入与自审回执

已使用并按create-p更新last_used/hit_count的知识（均经本轮代码检索复核）：
- K-outreach-raw-page-filter-contract、K-batch-send-round-loop-symmetry、K-batch-send-filter-retry-parity：02/04原始页、双循环、重试同口径。
- K-batch-send-launch-template-revalidation：05统计指纹不改变启动令牌；K-progress-log-batchonly-two-readers：03两JSON读取入口。
- K-enrichment-write-three-layers、K-es-dynamic-false、K-expert-profile-source-sync、K-promotion-source-passthrough：01三层读写/线上mapping核实/透传旧数据；本次不新增profile字段，sourceFields保持。
- K-no-cache-framework-in-repo：05无框架缓存实现，并纠正该知识中过度穷举的旧结论。
- K-plan-quantified-claims-need-grep-receipts、K-js-test-invocation-surface、K-frontend-cache-key-triad、K-line-number-guard-breaks-on-any-insertion：证据回执、JS函数提取、资源key及01/02行号守卫。
- 本轮匹配条目均为新近使用，不触发90天低频归档；没有新跨越10次提升阈值的条目，也无5条同主题可安全合并项。新增2条可复用知识；仓库无agents/或templates/project-CLAUDE.md，域提示放CLAUDE.md既有团队沉淀节，不新建角色目录。

自审：6份子计划必需章节顺序完整；I条款24条、人工场景21项；每计划清单6/7/9/7/8/6文件，去重33，新增2生产+7测试。每新状态/字段有I约束；03/06有S与UI人工项。声明修改的所有路径现已存在，标为新增的9文件尚不存在。文件表外修改禁止。文档结构脚本errors=[]，git diff --check无输出；这只说明文档/差异检查，不是业务测试PASS。

## 人工验收清单

本节为总体端到端清单；各子计划A-n是分段权威清单。人工验收开始时分别导出同前缀-acceptance.md，记录验收人/日期/结果；本轮不预生成打勾文件。

### A-1: 缺陷真正修复且资格未放宽
- 前置条件: 独立测试环境准备合法数组分类专家1名、非法日期1名、任务排除地区1名、退订1名；SMTP捕获箱。
- 操作步骤: 1. 用研发筛选查看4档；2. 发1封；3. 对合法档做补全后检查ES。
- 预期结果: 只有合法且其它条件通过者收到1封；补全后日期为字符串；另外3名无邮件；无历史全库回刷。
- 覆盖: I-1、X1、需求1及安全规则。

### A-2: 大名单只取本次额度
- 前置条件: 11001名测试候选、前500名被确定性过滤、后续足够合格；首封/材料各有独立夹具，每轮30、1轮，账号容量100。
- 操作步骤: 1. 分别执行两类型；2. 检查捕获箱、查询记录和日志；3. 插入已退订及重复历史样例重跑。
- 预期结果: 成功最多30，达到30即停止；无执行前全量统计；跨空页仍补足；退订/历史保护不失效；日志显示计划成功30而非全库目标。
- 覆盖: I-2/3、X2/X3、需求2。

### A-3: 缓存生命周期与模板编辑
- 前置条件: 固定100名测试候选，缓存空；两面板、模板编辑页可用。
- 操作步骤: 1. 打开页面；2. 点击统计；3. 重开两面板；4. 编辑引用片段正文；5. 刷新统计时注入失败；6. 无缓存直接执行30。
- 预期结果: 第1步不全扫；第2步生成带时间的缓存100；第3步时间不变；第4步旧缓存不套新指纹；失败不显示伪0/伪最新时间；第6步仍可按实时条件发30。
- 覆盖: I-2、X4/X5、需求3。

### A-4: 历史、取消及UI回归
- 前置条件: 历史target1757的脱敏fixture、新v2运行fixture；宽1200px/窄600px页面。
- 操作步骤: 1. 比较旧/新日志；2. 运行中取消；3. 展开缓存诊断原因；4. 对照03/06样式实值。
- 预期结果: 旧目标1757/剩余1710不变；新卡片不出现虚假全库剩余；取消后不新投递/不虚增skip；原因样本与partial保留；卡片/按钮/间距符合S契约。
- 覆盖: I-3/4、X3/X5、全部must-NOT-change与UI回归。

## 修正记录

- 2026-10-22，人工选择“扩大两文件授权，保持按需公式”：04加入已有的 `ExpertSearchCursor.kt` / `ExpertSearchCursorTest.kt`，范围9文件、2子系统；同一PIT支持逐次页大小，保留无参合同，原按需公式不变。满足I-2及I-4；去重总清单不增加新路径。原自审回执为修订前历史快照。精确批准与Before/After提交身份见 `docs/plans/fast/batch-demand-master/ledger.md` Amendments。
- 2026-10-22，人工选择“批准第10文件并继续”：04加入 `MailOpenTrackingPersistenceTest.kt`，10文件/2子系统；仅迁移受影响调用方夹具并保留追踪断言，完成原授权嵌套材料用例迁移；新执行轮次使用已安装JDK11.0.32.1，不改时区业务代码。去重清单新增1测试路径至34（原33路径表的专用grep回执及该新增路径见child04执行证据/本轮批准记录），原自审回执不改写。批准与Before/After身份见ledger Amendments。

