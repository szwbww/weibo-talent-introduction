# 08：执行记录原因与方式文案

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-15、DD-16、DD-17。

前置：[07：明确 HTML 联系人与挑战页回退](discovery-repair-07-html-contact.md)完成机器验证后，以其产物为基线。

## 需求描述

来源表显示已保存的完整过滤原因、失败原因、停止原因；将方式明确标为首选方式；新日志不再把身份未确认称为人才资格淘汰。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不新增实际methodUsed统计字段、不改统计计算和人才资格、不追改历史日志、不保存逐邮箱明细。不声称恢复后台快照已截断的第21项以后原因；不改学术补全来源表。

## 关键不变量

### Invariant I-1：只展示已有事实
- Rule：过滤、失败、停止原因分块展示。数值原样读取已存快照，不能相加PDF_DOWNLOAD_FAILED与HTTP_403当失败人数；filtered也不能把多个资格原因相加复算。缺字段显示“未记录”，不补猜0。
- Applies to：bySource→实时/历史renderBySourceTable
- Violation consequence：界面制造新错误统计
- 来源：original

### Invariant I-2：方式和日志不冒充资格
- Rule：表头改“首选方式”，值仍extractionMethod；不声称实际使用XML。新两处漏斗日志将“资格淘汰”改“过滤（含身份未确认）”，同时带现有filterReasons；不计算无法区分的资格人数，不更改旧日志。
- Applies to：SourceStats日志与JS表格
- Violation consequence：把解析漏识别说成人才不合格
- 来源：original

### Invariant I-3：共享渲染和安全
- Rule：EXPERT_DISCOVERY实时与历史仍共用同一renderer，isEnrichmentBySource分支原样保留。所有外部原因/来源/停止原因escapeHtml；数值只接受有限非负数，缺失用未记录。无新业务字段或配置。
- Applies to：TaskProgressStore/TaskExecution result→DOM
- Violation consequence：XSS、跨任务语义混淆
- 来源：original

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 样式契约

### S-1：来源表和原因详情
- 复用：`table-wrap`（styles.css:987～991）、`data-table`（:3421～3456），以及其thead/th/td/details/summary派生规则。规则实值与所有使用位置见审计附件“前端样式盘点”及ui.txt；不修改class规则，无新增CSS。
- 新增：无新增class。复用后th/td纵向padding为6px（原inline为3/4px），这是明确的局部布局变化；table全局min-width=720px，wrapper可横向滚动。td继承13px，details11px，不承诺table的11px会覆盖td。
- DOM结构如下；每个th/td均属于data-table派生规则，div/thead/tbody/tr/details/summary使用下列层级，不增内联样式。方括号为escapeHtml后的数据占位，不是生成到UI的说明。

```html
<div class="table-wrap">
  <table class="data-table">
    <thead><tr><th>平台</th><th>首选方式</th><th>论文</th><th>邮箱</th><th>有效</th><th>收录</th><th>晋升</th><th>原因详情</th></tr></thead>
    <tbody><tr>
      <td>[平台]</td><td>[extractionMethod]</td><td>[papersSearched]</td><td>[authorsExtracted]</td><td>[emailsValid]</td><td>[indexed]</td><td>[promoted]</td>
      <td><details><summary>查看原因</summary><div>过滤：[全部已存filterReasons]</div><div>失败：[全部已存failureReasons]</div><div>停止：[stopReason]</div></details></td>
    </tr></tbody>
  </table>
</div>
```

- 禁止：inline style、未声明class、修改全局表格/其他data-table样式、额外tooltip/弹窗库。新DOM都映射到S-1。index.html现有11个带版本的资源URL缓存键须同时改为20260926-discovery-repair，不新增DOM或改变资源顺序；其余内容不改。

## 现状审计

ExpertDiscoveryService.kt:239～270已有filterReasons/failureReasons/stopReason字段，:850/:1106统称资格淘汰；app.js:2681～2724只取failureReasons前三且ellipsis。task20240原bySource有IDENTITY_UNRESOLVED=629、SEARCH_FAILED；真实JS复现见ui-reproduction.json。extractionMethod是source默认值，非实际逐篇methodUsed。交互X7，schema/读写见审计附件C5，前端样式见附件盘点。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

## 实现方案

### T-1：真实任务回放（I-1/I-2/I-3/S-1，I-4）

src/test/resources/discovery/task-20240-by-source.json：从审计source-stop.json读取task[0]，按制表符split(limit=5)取第5列JSON，再取stats.bySource；注明来源SHA，不手填635/629，也不使用snapshot3.json中1000篇中间进度；src/test/js/taskRecordsSemantics.test.js调用真实renderBySourceTable并保存验收HTML。

### T-2：修共享表格（I-1/I-2/I-3/S-1，I-4）

src/main/resources/static/app.js：移除slice(0,3)与截断cell，使用S-1既有class，末列改“原因详情”，details分块列出所有已存原因及stopReason；表头首选方式。src/main/resources/static/index.html：现有11个带版本资源引用缓存键同时改20260926-discovery-repair，不改资源加载顺序或其他内容。

### T-3：修两处日志与回归（I-1/I-2/I-3/S-1，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt：只改两处漏斗总结文案，输出既有filterReasons，不改counter；src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt捕获日志检查；src/test/js/taskRecordsSemantics.test.js回归历史无字段、补全分支、注入字符与11个缓存键一致。

### T-4：生成可核验的验收输出（I-1～I-4）

在已列出的JS测试中将实际渲染结果写入target/discovery-plan-acceptance/08.html，加载本地真实styles.css，并写08.json输入/输出摘要；不创建生产诊断API。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共6个文件，2个子系统（发现统计文案、任务表格）；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` |
| 2 | `src/main/resources/static/app.js` |
| 3 | `src/main/resources/static/index.html` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 5 | `src/test/js/taskRecordsSemantics.test.js` |
| 6 | `src/test/resources/discovery/task-20240-by-source.json` |

## 验收标准

- I-1：真实task20240详情能看到IDENTITY_UNRESOLVED:629、SEARCH_FAILED与原HTTP_403:323；原635邮箱/6有效/4收录/4晋升不改变；四个以上失败键均能查看。
- I-2：首选方式=FULLTEXT_XML；新日志含“过滤（含身份未确认）”，不再输出“资格淘汰629”；旧result_summary不重写。
- I-3：补全仍显示入队/成功/待补而非论文；未知旧字段为未记录；恶意原因字符串被转义；实时与历史同事实同文本。
- S-1：renderer生成DOM无style属性，无未声明class；CSS文件diff为0；现有11个缓存键同值且无旧键残留；实际浏览器视觉检查滚动/展开/字体与样式契约。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest
node --test src/test/js/taskRecordsSemantics.test.js
node --check src/main/resources/static/app.js
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：任务20240只读回放
- 前置条件：隔离测试加载真实task20240-by-source.json；执行JS测试生成target/discovery-plan-acceptance/08.html。不在生产插入或修改20240。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 浏览器打开验收HTML；展开OPENALEX原因详情，核对表格与原fixture。
- 预期结果：显示635/6/4/4、IDENTITY_UNRESOLVED:629、停止SEARCH_FAILED、HTTP_403:323；表头首选方式，值FULLTEXT_XML；不把下载总类与HTTP子类相加。
- 覆盖：I-1/I-2，X7，需求。

### A-2：历史与样式回归
- 前置条件：验收HTML包含实时/历史同数据、缺字段旧数据、学术补全、长原因与恶意字符串四组；浏览器宽度800px。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 展开/收起details，横向滚动表格；比较实时/历史文本；检查补全表和被转义字符。
- 预期结果：两入口同值；旧缺字段“未记录”；补全保留入队列；脚本不执行；单元格padding 6px 8px，表头11px、正文13px、details11px，summary主色#1e40af，横向可滚动。
- 覆盖：I-1/I-3/S-1，X7，不改补全回归。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
