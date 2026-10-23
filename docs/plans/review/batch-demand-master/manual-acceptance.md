# Manual Acceptance — batch-demand-master

## Epoch 2 — 2026-10-23T09:44:09.378649+08:00

- Reviewed code boundary: d403caeb5dcc4810067293c10600ee57f94e276b..147b3eafcdf62b70bb811d2068227277ec999a4a
- Machine report epoch: 2
- Status: PENDING
- Governing master identity: sha256 c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1 (approved A1–A4)
- Sources: master manual checklist incorporates the six child A-n checklists as authoritative. All results below await human-originated verdict and evidence/note. No manual execution or acceptance is claimed.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| MASTER-A-1 | Yes | 缺陷真正修复且资格未放宽 | 只有合法且其它条件通过者收到1封；补全后日期为字符串；另外3名无邮件；无历史全库回刷。 | PENDING | — | — | — |
| MASTER-A-2 | Yes | 大名单只取本次额度 | 成功最多30，达到30即停止；无执行前全量统计；跨空页仍补足；退订/历史保护不失效；日志显示计划成功30而非全库目标。 | PENDING | — | — | — |
| MASTER-A-3 | Yes | 缓存生命周期与模板编辑 | 第1步不全扫；第2步生成带时间的缓存100；第3步时间不变；第4步旧缓存不套新指纹；失败不显示伪0/伪最新时间；第6步仍可按实时条件发30。 | PENDING | — | — | — |
| MASTER-A-4 | Yes | 历史、取消及UI回归 | 旧目标1757/剩余1710不变；新卡片不出现虚假全库剩余；取消后不新投递/不虚增skip；原因样本与partial保留；卡片/按钮/间距符合S契约。 | PENDING | — | — | — |
| 01-A-1 | Yes | 数组老档无需回刷即可读取 | 档案分类可读、符合类型条件；捕获箱1封；ES原数组未被读操作改写。 | PENDING | — | — | — |
| 01-A-2 | Yes | 补全写新值 | 两种更新形式的已有层 classifiedAt 均为 yyyy-MM-dd HH:mm:ss 字符串；APPLICATION仍404；姓名/邮箱/运营状态未变。 | PENDING | — | — | — |
| 01-A-3 | Yes | 不放宽错误数据和业务规则 | 四份均不得外发；时间错误仍是解析失败，不补当前时间；分类规则和退订/地区规则未改。 | PENDING | — | — | — |
| 02-A-1 | Yes | 长名单分页 | 完整遍历11001个不同docId；提前关闭只请求所需页，最终PIT关闭；没有from>=10000请求。 | PENDING | — | — | — |
| 02-A-2 | Yes | 发送期间状态变化与NEW分页 | 原有后页不因前页缩小而跳过；新高id不混入本次；未改写任何联系人。 | PENDING | — | — | — |
| 02-A-3 | Yes | 续期/故障/释放 | 正常续期不预取人；故障明确报错而非返回空名单；close幂等，后台任务停止。 | PENDING | — | — | — |
| 03-A-1 | Yes | 历史记录不变 | “目标1757、成功30、失败0、跳过17、剩余1710”保留；无统计警告；历史数据不被改写。 | PENDING | — | — | — |
| 03-A-2 | Yes | 运行日志到最终结果 | 两阶段均显示实际处理25、计划30、未完成配额10；原因/账号/timeline仍可见；运行中取消按钮存在，完成后隐藏；无全库剩余。 | PENDING | — | — | — |
| 03-A-3 | Yes | 卡片目测 | 宽屏3列、窄屏2列；padding10px、gap8px、圆角10px；成功绿、失败红、跳过橙；不新增别的面板样式。 | PENDING | — | — | — |
| 04-A-1 | Yes | 足额即停与跨空页 | 每次首轮成功30，过滤空页不提前终止；达到30后不继续取候选，无_count和全量初始化；重跑不对同一首封/同一材料收件人重复投递。 | PENDING | — | — | — |
| 04-A-2 | Yes | 跳过补人、容量与取消 | 第1次成功30、失败5、跳过5、实际处理40、未完成配额0；第3步最多成功10；第4步取消后不新增PREPARED或SMTP，未处理候选不加入skip，PIT关闭。 | PENDING | — | — | — |
| 04-A-3 | Yes | 发送前事实变化与系统异常 | 禁止的地址/绑定不发；邮箱变化显示“候选信息已变化”；模板旧启动令牌继续被拒；ES失败不显示候选耗尽，最终分别FAILED/PARTIAL_SUCCESS。 | PENDING | — | — | — |
| 04-A-4 | Yes | 材料全量统计只读 | 全量目标11001（不是10000），统计阶段SMTP/新增mail_record/attempt/联系人绑定均0；执行阶段只发30；缓存并非前置条件。 | PENDING | — | — | — |
| 05-A-1 | Yes | 首次统计、复用、刷新 | 第1步MISS/无summary/0全扫；第2步READY且totalSendable100；第3步cachedAt逐字不变且0全扫；第4步重新计算并更新cachedAt。全程0邮件、0联系人/attempt新增。 | PENDING | — | — | — |
| 05-A-2 | Yes | 条件及引用片段变化 | 第1步复用原缓存；第2/3步MISS，不展示前key的人数；原启动模板令牌API仍按旧合同工作。 | PENDING | — | — | — |
| 05-A-3 | Yes | 失败、陈旧、重启不阻断发送 | 第1步stale=true、原时间保留；第2步503/ERROR、原100和时间保留；第3步成功30，不要求先刷新统计；第4步MISS而非READY0。 | PENDING | — | — | — |
| 05-A-4 | Yes | 大名单与并发 | 同key只1次全量计算；最多2个不同key同时运行，额外key503；totalSendable30001，诊断partial=true且标明上限，不误称全部排除分析已完成。 | PENDING | — | — | — |
| 06-A-1 | Yes | 页面不全扫、按钮才刷新 | 首次仅cacheOnly且显示尚未统计；按钮才全扫，显示缓存目标100和缓存时间；两处相同条件复用同时间，重开不全扫；再次刷新才更新时间。 | PENDING | — | — | — |
| 06-A-2 | Yes | 快速切换与异常 | B显示尚未统计而不是100；A失败仍显示原100/原缓存时间及“刷新失败，保留上次缓存”；C明确显示0；无自动全扫。 | PENDING | — | — | — |
| 06-A-3 | Yes | 门禁、诊断与发送 | 第1步仅当前gate=true一次请求；保留partial和“原因人数可重叠”；第3步使用gate=false且不因缓存缺失阻止；成功最多30，执行卡片为03语义。 | PENDING | — | — | — |
| 06-A-4 | Yes | 样式与安全文本 | 控制区padding12px 14px、gap16px，hint12px/1.6、圆角10px；按钮高32px、disabled opacity .7；窄屏纵排；危险文本不执行；缓存时间与诊断时间标签不同。 | PENDING | — | — | — |

Master acceptance criteria also require the human to supply the read-only before/after classification difference evidence (no fixed recovery count inferred). This is recorded with MASTER-A-1; no agent execution or result is claimed.


### MASTER-A-1: 缺陷真正修复且资格未放宽

- Source: [batch-demand-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md)
- 前置条件: 独立测试环境准备合法数组分类专家1名、非法日期1名、任务排除地区1名、退订1名；SMTP捕获箱。
- 操作步骤: 1. 用研发筛选查看4档；2. 发1封；3. 对合法档做补全后检查ES。
- 预期结果: 只有合法且其它条件通过者收到1封；补全后日期为字符串；另外3名无邮件；无历史全库回刷。
- 覆盖: I-1、X1、需求1及安全规则。


### MASTER-A-2: 大名单只取本次额度

- Source: [batch-demand-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md)
- 前置条件: 11001名测试候选、前500名被确定性过滤、后续足够合格；首封/材料各有独立夹具，每轮30、1轮，账号容量100。
- 操作步骤: 1. 分别执行两类型；2. 检查捕获箱、查询记录和日志；3. 插入已退订及重复历史样例重跑。
- 预期结果: 成功最多30，达到30即停止；无执行前全量统计；跨空页仍补足；退订/历史保护不失效；日志显示计划成功30而非全库目标。
- 覆盖: I-2/3、X2/X3、需求2。


### MASTER-A-3: 缓存生命周期与模板编辑

- Source: [batch-demand-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md)
- 前置条件: 固定100名测试候选，缓存空；两面板、模板编辑页可用。
- 操作步骤: 1. 打开页面；2. 点击统计；3. 重开两面板；4. 编辑引用片段正文；5. 刷新统计时注入失败；6. 无缓存直接执行30。
- 预期结果: 第1步不全扫；第2步生成带时间的缓存100；第3步时间不变；第4步旧缓存不套新指纹；失败不显示伪0/伪最新时间；第6步仍可按实时条件发30。
- 覆盖: I-2、X4/X5、需求3。


### MASTER-A-4: 历史、取消及UI回归

- Source: [batch-demand-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md)
- 前置条件: 历史target1757的脱敏fixture、新v2运行fixture；宽1200px/窄600px页面。
- 操作步骤: 1. 比较旧/新日志；2. 运行中取消；3. 展开缓存诊断原因；4. 对照03/06样式实值。
- 预期结果: 旧目标1757/剩余1710不变；新卡片不出现虚假全库剩余；取消后不新投递/不虚增skip；原因样本与partial保留；卡片/按钮/间距符合S契约。
- 覆盖: I-3/4、X3/X5、全部must-NOT-change与UI回归。


### 01-A-1: 数组老档无需回刷即可读取

- Source: [batch-demand-01-classification.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-01-classification.md)
- 前置条件: 隔离测试ES三层中各建一份合法分类档案，type=ACADEMIC_RND，classifiedAt=[2026,10,22,2,47,14,390627000]；其余条件满足。关闭真实SMTP，指向本地捕获箱。
- 操作步骤: 1. 打开专家列表；2. 用同一研发类型做发送预估；3. 在测试环境仅发1封。
- 预期结果: 档案分类可读、符合类型条件；捕获箱1封；ES原数组未被读操作改写。
- 覆盖: I-1/I-3、X2、需求恢复旧档。


### 01-A-2: 补全写新值

- Source: [batch-demand-01-classification.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-01-classification.md)
- 前置条件: 测试环境两档案分别为普通/已发现，RAW+CANDIDATE存在，APPLICATION不存在，补全上游用固定响应。
- 操作步骤: 1. 运行测试补全；2. 查看ES原始_source及不存在层。
- 预期结果: 两种更新形式的已有层 classifiedAt 均为 yyyy-MM-dd HH:mm:ss 字符串；APPLICATION仍404；姓名/邮箱/运营状态未变。
- 覆盖: I-2/I-3、X1、需求不改其他事实。


### 01-A-3: 不放宽错误数据和业务规则

- Source: [batch-demand-01-classification.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-01-classification.md)
- 前置条件: A-1复制四份，分别改时间为[2026,13,22,2,47]、type为未知值、国家为任务排除国家、邮箱为退订。
- 操作步骤: 1. 启动测试1封任务；2. 查看筛选/发送记录。
- 预期结果: 四份均不得外发；时间错误仍是解析失败，不补当前时间；分类规则和退订/地区规则未改。
- 覆盖: I-1/I-3、X3、must-NOT-change。


### 02-A-1: 长名单分页

- Source: [batch-demand-02-cursors.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-02-cursors.md)
- 前置条件: 独立测试ES7.14索引由ExpertSearchCursorTest对应测试夹具写入11001条，含相同orcid/相同排序时间；禁止指向生产。
- 操作步骤: 1. 运行游标黑盒测试；2. 查看请求记录与返回docId数量；3. 提前读完第1页后关闭再查看上下文。
- 预期结果: 完整遍历11001个不同docId；提前关闭只请求所需页，最终PIT关闭；没有from>=10000请求。
- 覆盖: I-1/I-3、X2、按页能力。


### 02-A-2: 发送期间状态变化与NEW分页

- Source: [batch-demand-02-cursors.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-02-cursors.md)
- 前置条件: 测试MySQL相同updated_at的NEW联系人501条；当前maxId冻结。
- 操作步骤: 1. 读第1页；2. 将其中已处理联系人改为WAITING_REPLY，再新增1条NEW；3. 读后续页。
- 预期结果: 原有后页不因前页缩小而跳过；新高id不混入本次；未改写任何联系人。
- 覆盖: I-4、X1、must-NOT-change。


### 02-A-3: 续期/故障/释放

- Source: [batch-demand-02-cursors.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-02-cursors.md)
- 前置条件: 测试ES连接代理可注入超时，游标时钟可控制；使用本地测试，不用真实邮箱。
- 操作步骤: 1. 等价推进6分钟并续期；2. 再取页；3. 注入PIT404/分片失败；4. 调用close两次。
- 预期结果: 正常续期不预取人；故障明确报错而非返回空名单；close幂等，后台任务停止。
- 覆盖: I-2/I-3、X3、安全边界。


### 03-A-1: 历史记录不变

- Source: [batch-demand-03-progress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-03-progress.md)
- 前置条件: 测试环境导入脱敏历史结果fixture：target1757、success30、failure0、skipped17、remaining1710，不含execution。
- 操作步骤: 1. 打开批量任务执行记录；2. 切换列表/详情；3. 刷新页面。
- 预期结果: “目标1757、成功30、失败0、跳过17、剩余1710”保留；无统计警告；历史数据不被改写。
- 覆盖: I-1/3、X3、历史兼容。


### 03-A-2: 运行日志到最终结果

- Source: [batch-demand-03-progress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-03-progress.md)
- 前置条件: 测试fixture先建立RUNNING记录，details含v2计划30、成功20、失败2、跳过3、remainingQuota10、scanned50；再可用测试fixture将相同结果写入resultSummary并标完成。
- 操作步骤: 1. 打开运行中详情；2. 查看取消按钮、账号和timeline；3. 写入最终fixture后刷新详情。
- 预期结果: 两阶段均显示实际处理25、计划30、未完成配额10；原因/账号/timeline仍可见；运行中取消按钮存在，完成后隐藏；无全库剩余。
- 覆盖: I-2/3、X1/X2、must-NOT-change。


### 03-A-3: 卡片目测

- Source: [batch-demand-03-progress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-03-progress.md)
- 前置条件: A-2测试页面，窗口宽1200px和600px。
- 操作步骤: 1. 分别观察v1/v2；2. 对照卡片与告警。
- 预期结果: 宽屏3列、窄屏2列；padding10px、gap8px、圆角10px；成功绿、失败红、跳过橙；不新增别的面板样式。
- 覆盖: S-1/2、可观察新计数。


### 04-A-1: 足额即停与跨空页

- Source: [batch-demand-04-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-04-execution.md)
- 前置条件: 隔离测试ES/MySQL与SMTP捕获箱；夹具11001名候选，前500名按规则不合格、后100名合格；配置每轮30、1轮、测试账号容量>=100，关闭真实外发网络。
- 操作步骤: 1. 分别运行首封与材料（材料夹具已有合格contact及binding）；2. 看捕获箱和查询记录；3. 重跑同一任务。
- 预期结果: 每次首轮成功30，过滤空页不提前终止；达到30后不继续取候选，无_count和全量初始化；重跑不对同一首封/同一材料收件人重复投递。
- 覆盖: I-1/2/3/5、X1/X2、按需需求与幂等回归。


### 04-A-2: 跳过补人、容量与取消

- Source: [batch-demand-04-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-04-execution.md)
- 前置条件: 测试捕获箱；候选足够，注入5个投递失败、5个进入处理后的实时skip，之后至少30名成功候选；另备容量10及暂停页读取的夹具。
- 操作步骤: 1. 运行1轮30；2. 查看详情并刷新；3. 容量10时重跑新任务；4. 在阻塞夹具中点取消后释放读取。
- 预期结果: 第1次成功30、失败5、跳过5、实际处理40、未完成配额0；第3步最多成功10；第4步取消后不新增PREPARED或SMTP，未处理候选不加入skip，PIT关闭。
- 覆盖: I-1/4/5、X3、额度/取消/事务保留。


### 04-A-3: 发送前事实变化与系统异常

- Source: [batch-demand-04-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-04-execution.md)
- 前置条件: 测试夹具在候选页读取后可暂停；分别准备退订、邮箱改动、绑定改动、模板启动版本变化；SMTP捕获箱。
- 操作步骤: 1. 在暂停后改变相应事实再恢复；2. 单独让下一页ES返回超时；3. 分别在0成功及已成功10封时重现。
- 预期结果: 禁止的地址/绑定不发；邮箱变化显示“候选信息已变化”；模板旧启动令牌继续被拒；ES失败不显示候选耗尽，最终分别FAILED/PARTIAL_SUCCESS。
- 覆盖: I-3/4、X1、must-NOT-change安全护栏。


### 04-A-4: 材料全量统计只读

- Source: [batch-demand-04-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-04-execution.md)
- 前置条件: 测试环境11001名符合材料提醒的联系人/专家，SMTP捕获箱为空。
- 操作步骤: 1. 调用显式预估接口；2. 查看统计和DB审计计数；3. 再执行只发30的任务。
- 预期结果: 全量目标11001（不是10000），统计阶段SMTP/新增mail_record/attempt/联系人绑定均0；执行阶段只发30；缓存并非前置条件。
- 覆盖: I-5、X4、只读统计。


### 05-A-1: 首次统计、复用、刷新

- Source: [batch-demand-05-cache.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md)
- 前置条件: 测试环境固定100名符合候选，缓存空，SMTP捕获箱；API body使用同一保存配置snapshot。
- 操作步骤: 1. POST statistics?cacheOnly=true；2. POST statistics；3. 再POST同样请求；4. POST statistics?refresh=true。
- 预期结果: 第1步MISS/无summary/0全扫；第2步READY且totalSendable100；第3步cachedAt逐字不变且0全扫；第4步重新计算并更新cachedAt。全程0邮件、0联系人/attempt新增。
- 覆盖: I-1/3/4、X1、参考缓存需求。


### 05-A-2: 条件及引用片段变化

- Source: [batch-demand-05-cache.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md)
- 前置条件: A-1已缓存；模板引用测试片段，可通过模板后台编辑正文。
- 操作步骤: 1. 只改每轮数量30→50后cacheOnly；2. 修改地区后cacheOnly；3. 恢复地区，修改引用片段增加必填占位符后cacheOnly。
- 预期结果: 第1步复用原缓存；第2/3步MISS，不展示前key的人数；原启动模板令牌API仍按旧合同工作。
- 覆盖: I-2、X4、模板合同不变。


### 05-A-3: 失败、陈旧、重启不阻断发送

- Source: [batch-demand-05-cache.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md)
- 前置条件: 已缓存100；测试代理可令ES读失败，fakeClock可前移24h；发送使用隔离捕获箱。
- 操作步骤: 1. 前移时间并cacheOnly；2. ES故障时refresh；3. 恢复ES，执行只发30；4. 重启服务再cacheOnly。
- 预期结果: 第1步stale=true、原时间保留；第2步503/ERROR、原100和时间保留；第3步成功30，不要求先刷新统计；第4步MISS而非READY0。
- 覆盖: I-1/3/4、X2/X3。


### 05-A-4: 大名单与并发

- Source: [batch-demand-05-cache.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md)
- 前置条件: 测试夹具30001名符合候选，计算可暂停；新键至少3个。
- 操作步骤: 1. 20个请求并发刷新同key；2. 并发另两个key；3. 完成统计并查看结果。
- 预期结果: 同key只1次全量计算；最多2个不同key同时运行，额外key503；totalSendable30001，诊断partial=true且标明上限，不误称全部排除分析已完成。
- 覆盖: I-3/5、X1、大名单真实口径。


### 06-A-1: 页面不全扫、按钮才刷新

- Source: [batch-demand-06-ui.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-06-ui.md)
- 前置条件: 测试固定100名合格候选、缓存空；浏览器网络面板和服务端查询记录可见。
- 操作步骤: 1. 打开配置编辑；2. 点击统计并缓存；3. 关闭再打开；4. 在手动发信中用相同条件打开；5. 点击刷新统计。
- 预期结果: 首次仅cacheOnly且显示尚未统计；按钮才全扫，显示缓存目标100和缓存时间；两处相同条件复用同时间，重开不全扫；再次刷新才更新时间。
- 覆盖: I-1/2、X1、缓存需求。


### 06-A-2: 快速切换与异常

- Source: [batch-demand-06-ui.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-06-ui.md)
- 前置条件: A条件缓存100、B条件无缓存；网络代理可延迟A或使refresh503。
- 操作步骤: 1. A请求未返回前改B；2. 释放A；3. 切回A让refresh失败；4. 打开无候选但已成功缓存0的C条件。
- 预期结果: B显示尚未统计而不是100；A失败仍显示原100/原缓存时间及“刷新失败，保留上次缓存”；C明确显示0；无自动全扫。
- 覆盖: I-2/3、X2、缺失/零/失败区别。


### 06-A-3: 门禁、诊断与发送

- Source: [batch-demand-06-ui.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-06-ui.md)
- 前置条件: 测试候选30001，diagnostic partial=true；SMTP捕获箱；手动任务每轮30。
- 操作步骤: 1. 开启门禁刷新；2. 查看请求和样本；3. 关闭门禁不刷新，直接执行；4. 查看捕获箱与日志。
- 预期结果: 第1步仅当前gate=true一次请求；保留partial和“原因人数可重叠”；第3步使用gate=false且不因缓存缺失阻止；成功最多30，执行卡片为03语义。
- 覆盖: I-1/4、X3、验证/启动合同与诊断保留。


### 06-A-4: 样式与安全文本

- Source: [batch-demand-06-ui.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-06-ui.md)
- 前置条件: 两表单宽1200px/600px；测试原因文本含尖括号，缓存时间与诊断采样时间不同。
- 操作步骤: 1. 查看控制区、hint、原因展开；2. 点击刷新看disabled；3. 比较两个时间和特殊字符。
- 预期结果: 控制区padding12px 14px、gap16px，hint12px/1.6、圆角10px；按钮高32px、disabled opacity .7；窄屏纵排；危险文本不执行；缓存时间与诊断时间标签不同。
- 覆盖: S-1/2、I-2/3。

## Human Sign-off

- Decision: PENDING
- Boundary: 147b3eafcdf62b70bb811d2068227277ec999a4a
- Accepted governing master identity: PENDING — c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1
- Reporter: —
- Timestamp: —
- Note: After every mandatory item has a human verdict and evidence/note, explicitly accept this exact reviewed code boundary and governing master identity. Until then remain AWAITING_HUMAN_ACCEPTANCE.
