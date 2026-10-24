# 01 · 审核数量一次异步汇总

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：四张数量卡片由一次后台扫描同时产生；显示统计范围、扫描进度、时间和失败码。保持当前 RAW/discovered 全范围、仅 initialized=true 的统计口径；列表与人工审核行为不变。不扩大超时，不引入缓存框架，不把 SQL admission GROUP BY 当等价替换。本子计划仅修数量统计，既有带 decision 的同步列表大范围超时不宣称已修；后续重审固定所有页直接走 scanAll。

## 关键不变量

### I-1：一次扫描同口径
- 规则：同一任务仅一次 scanAll(RAW,discovered,null,null,null)，每批500，enrich后仅 initialized=true 计六种 decision，再合并四组；未初始化单列。只有成功结束的结果可当完整数量。
- 适用：CountsService、ScanService、前端
- 违反后果：漏计、重复扫描或把失败显示成0。
- 来源：K-discovery-admission-read-write-parity

### I-2：状态与范围明确
- 规则：PREPARING/RUNNING 只显示已扫描数量；SUCCESS 产生完整四卡与asOf、startedAt；FAILED/INTERRUPTED返回错误码。旧成功结果保留时间并标“旧统计”；不冒充实时一致性快照。
- 适用：task_execution写者和轮询读者
- 违反后果：让用户误判准入回滚。
- 来源：需求及下列源码审计

### I-3：独立副作用与并发
- 规则：只写task_execution审计；一个全局在途计数任务、重复刷新复用；不更新admission、verification、ES。滚动结果不是跨ES/MySQL事务快照，明确统计时间区间。
- 适用：CountsService执行器、控制器
- 违反后果：统计变成业务动作或拖垮后端。
- 来源：K-no-cache-framework-in-repo

## 样式契约

S-1：四卡复用现有 `.dr-counts`，新增区域置四卡之后，不动工具栏。DOM如下；新增CSS必须逐字落地，无其他class或inline style。

```html
<div id="discoveryReviewCountStatus" role="status" aria-live="polite" data-state="PREPARING"><span data-count-message>正在统计全部发现档案…</span><button class="button secondary" type="button" data-count-refresh>刷新数量</button></div>
```

```css
#discoveryReviewCountStatus{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:8px 0 12px;color:#475569;font-size:12px;line-height:1.7}
#discoveryReviewCountStatus[data-state="FAILED"],#discoveryReviewCountStatus[data-state="INTERRUPTED"]{color:#b91c1c}
#discoveryReviewCountStatus button{min-height:32px}
#discoveryReviewCountStatus button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
#discoveryReviewCountStatus button:focus-visible{outline:2px solid #1e40af;outline-offset:3px}
#discoveryReviewCountStatus[hidden]{display:none!important}
```

## 现状审计

直接证据：app.js:2995–3037 六种分类逐页全扫；ScanService:109–128 500条分批、20秒预算，:140–188 已有 scroll+finally清理；:238–295 为实际准入语义。部署类复现21.23秒超时，详见共享审计。
存储：task_execution V4/V137；新增task_type，不增字段，写入走现有runAndRecordWithResult/updateProgressCounts；读 TaskExecutionService.getExecution及已有最近任务查询。RAW、准入/审核/核验只读，完整写者/读者见共享审计对应节。交互是既有准入更新→统计和统计→卡片。
前端基线逐字：`<div class="dr-counts" id="discoveryReviewCounts" aria-live="polite">`，四个 strong 初始 `—`；保留原结构。复用 `.dr-counts` styles.css:12752–12767、`.button.secondary`，不修改其全站规则。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 在新增 DiscoveryReviewCountsService 中用专用单线程、队列0执行器，复用 TaskExecutionService.runAndRecordWithResult，类型 DISCOVERY_REVIEW_COUNTS；完整结果置 resultSummary，progress只用successCount表示scanned且DTO明确命名scanned。一次scanAll同时累积六decision/四组/uninitialized。每批检查任务仍属当前执行者；失败抛出保留cause类别、不得结果清零后SUCCESS。（I-1/2/3）
2. 新增 CountsController：登录态沿用 ReviewController.sessionUsername；POST `/api/discovery/review/counts/refresh` 返回202 `{requestKey,executionId?,state}`，同一在途任务复用；GET `/api/discovery/review/counts?requestKey=...` 查持久task请求/结果。requestKey UUID，先在执行器提交受理，onStarted才发布真实id；未建任务前不得虚构id。执行器拒绝返回409/COUNTS_BUSY。无隐式GET启动；重启未完成由现有恢复置INTERRUPTED，新刷新另建任务，不返回假成功。（I-2/3）
3. ScanService.scanAll 增加响应完整性校验：ES timed_out=true、_shards.failed>0、null body、完整页却无scrollId均报明确错误，finally清理；不得把半份数据写SUCCESS。不要全局重写listPage。（I-1）
4. app.js删除六路FetchDecisionTotal计数调用链，进入审核页发一次refresh，再2秒轮询，退出/切页销毁timer并校验requestSeq/countsSeq；保留旧成功四卡但标时间；无旧值显示—。失败显示如“数量统计失败（REVIEW_SCAN_FAILED）；列表仍可使用”，不可吞cause。已有手工动作成功仅触发一次刷新，不覆盖discoveryReviewResult。（I-1/2，S-1）
5. index插入独立status区；styles原样复制S-1；全部17资源key改为`20261024-review-counts`。字面量反查未发现测试命中，但实施发现`discoveryApolloSettings.test.js`从旧计划动态提取全局key。按2026-10-25用户授权，修正该过期断言：移除对旧计划key的绑定，保留资源同键、DOM/CSS及Apollo行为断言，不将旧键换成另一硬编码键，不修改旧计划。（S-1）

## 变更文件清单

仅下列11个文件；本次授权仅增加既有资源断言测试，独立控制器测试保留。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewCountsService.kt` | 新增异步汇总与DTO |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsController.kt` | 新增受理/读取 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | scroll错误不吞 |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 新增计数专用executor |
| `src/main/resources/static/app.js` | 单任务计数轮询 |
| `src/main/resources/static/index.html` | 独立status、统一版本键 |
| `src/main/resources/static/styles.css` | S-1完整CSS |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewCountsServiceTest.kt` | 新增服务/失败/并发测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt` | 新增登录/受理/读取测试 |
| `src/test/js/discoveryReview.test.js` | 六路消除、迟到回包、失败不清零 |
| `src/test/js/discoveryApolloSettings.test.js` | 修正从旧计划读取全局资源键的过期断言，保留同键与Apollo行为约束 |

## 验收标准

- I-1：15501条测试输入分32批，六decision已初始化计数与一次同输入enrich逐条oracle相同；不得调用listPage；未初始化不计卡片；ES每页仅一轮扫描。原15,398只用于线上报告参考。
- I-2：模拟第11页失败，状态FAILED、无完整四卡；旧结果仍带旧asOf；数据库故障、滚动丢失、分片失败和重启中断分别可见。
- I-3：20个同时refresh只有一个扫描；业务表和ES写调用0；GET不调度；滚动finally清理；专用执行器不占邮件/采集池。
- S-1：CSS逐字比对、DOM无新inline style，17资源同key；node --test src/test/js/discoveryReview.test.js；两个现有资源测试及`node --test src/test/js/discoveryApolloSettings.test.js`必须通过；完整Maven绑定JS不得有已知失败。

## 人工验收清单

### A-1：计数不再等待六次全扫
- 前置条件：测试环境导入15501条RAW/discovered：AUTO=100、NEEDS=200、MANUAL=30、LEGACY=20、HOLD=5、REJECT=6，其余15140条未初始化。
- 操作步骤：打开专家审核并刷新两次；观察网络及卡片。
- 预期结果：只有一个后台扫描；完成卡片100 / 200 / 50 / 11；另示未初始化15140。列表仍能打开。
- 覆盖：I-1/3

### A-2：错误与旧值
- 前置条件：先完成A-1，再让ES测试代理在第11页返回503。
- 操作步骤：再次刷新，等待失败。
- 预期结果：保留100/200/50/11并注明旧统计时间；失败码可见；没有卡片变0；恢复ES后点击重试可完成。
- 覆盖：I-2/3

### A-3：样式与人工操作回归
- 前置条件：A-1环境，桌面1280与窄屏640。
- 操作步骤：看四卡和状态行；人工暂缓一名待审核者，刷新。
- 预期结果：四卡结构不变；正文12px/#475569；新计数100/199/50/12；人工操作结果区未被计数消息覆盖。
- 覆盖：I-1/2，S-1

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。

## 修正记录

- 2026-10-25，用户“授权 继续”：依据总计划I-M4，增加`discoveryApolloSettings.test.js`，01文件上限仅此例外为11；只修正过期全局资源键断言，不删除Apollo功能回归，不合并独立控制器测试。授权与提交身份详见`../fast/discovery-recheck-master/ledger.md`的Amendments。
