# 09 · AI 所选材料获取与分析衔接

状态：待审阅/未执行。前置：08子计划通过独立验证。 范围：5个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

AI选件窗复用材料组件；仅明确确认后获取缺失所选文件，全部就绪后才分析；失败与不支持格式明确显示。

不得改变：原PDF/text解析、分析结果编辑/新增字段/重新评估、历史结果读取与审核状态。

范围外：不增加OCR、自动背景分析、解析结果审核状态、跨会话自动提交分析。

## 关键不变量

### Invariant I-1：显式选择快照
- Rule：打开AI仅GET元数据与历史结果；有已选材料则带入支持格式的所选项并列出排除原因，无已选默认CV/各学位；提交时冻结contactId+attachmentIds，不跟随之后材料筛选变化。
- Applies to：AI打开/选择/提交
- Violation consequence：误下载未选文件或分析另一专家
- 来源：original

### Invariant I-2：获取后分析
- Rule：全部所选STORED才调用既有ai-analysis；缺失先POST transfers；任何FAILED/SOURCE_UNAVAILABLE阻止整批分析并显示失败项，重试只请求失败项。
- Applies to：AI状态NEW/ACQUIRING/READY/ANALYZING/SUCCESS/ERROR
- Violation consequence：部分内容被当成全部完成
- 来源：original

### Invariant I-3：关闭与格式
- Rule：关闭未提交分析的窗口停止后续自动分析意图但已请求下载继续；PDF/text之外禁选并解释，空文本PDF明确无可读文字；不清历史结果。
- Applies to：窗口生命周期、提取结果处理
- Violation consequence：关窗后偷偷分析或虚构OCR
- 来源：original

## 样式契约

所有新DOM必须映射[完整契约S-1至S-5](ui-style-contract.md)，既有DOM/CSS逐字见[baseline](frontend-baseline.md)。禁止新增inline style、未声明class、修改既有全局规则。button复用styles.css:802/838；专家原卡复用:1659；可信内部样式复用:7329起。

### S-2：AI选件DOM

```html
<!-- S-2: 嵌入既有 #aiAnalysisModal 的选件内容区，沿用原弹窗标题/关闭/结果编辑结构 -->
<section class="em-analysis">
  <p class="em-analysis-note">仅分析所选文件。未获取的文件将在确认后下载到服务器；图片暂不支持文字识别。</p>
  <div data-material-picker><!-- S-1 相同 expert-materials，selectionOnly 模式，不渲染下载 footer/关闭按钮 --></div>
  <div class="em-analysis-actions"><span role="status">已选 2 份，已存 1 份，需获取 1 份</span><button class="button primary" type="button">获取所选文件并分析</button></div>
</section>
```

无新CSS；08已提供全部em-analysis规则与S-1选择组件，禁止另写选件表。S-5请求与焦点要求适用。

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E2/E6（解析能力/选件/结果写入）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

### 前端样式盘点

[改动前逐字DOM/CSS](frontend-baseline.md)保留source路径、行号及SHA256。基准primary #1e40af、bright #3b82f6、background #f5f7fb、text #1e293b/#475569/#94a3b8；按钮32px/12px字/7px圆角；资料字12/11px；原contacts-layout与全部全局class不就地改写。新namespace派生规则只作用本host，无需改变其他使用点；所有原模块事件和原class仍按S-4来源复用。

## 实现方案

1. [I-1，S-1/S-2/S-5] app.js openAiAnalysisModal改为新组件存在时读取06材料响应，复用selectionOnly；保留原get历史结果API。默认勾选跨完整专家集合的支持格式CV/学位ID（materials summary提供defaultAnalysisAttachmentIds）；若默认候选超过500，不悄悄截断，提示“默认材料超过500份，请分批选择”，暂不默认勾选。
2. [I-1/I-2，S-2] 保留startAiAnalysis函数及既有payload构造，增加明确prepare流程：所选已存N/需获取M；M=0按钮“开始分析”；M>0“获取所选文件并分析”。下载阶段在共享store监听，不另造下载状态；全部就绪且intentToken仍属于当前窗口才调用原AI一次；按钮disable避免重复提交。
3. [I-2/I-3，S-2/S-5] closeAiAnalysisModal销毁intentToken与订阅；已发往服务器的分析请求沿用现有语义，关闭后结果可通过历史接口重读，不宣称取消服务器分析。获取失败展示明确attachmentIds/文件名/原因；重试后仍按原快照全部校验再分析。换专家不复用前一个专家token。
4. [I-3，S-1/S-2] support由服务端能力返回，不能只按扩展名伪装可分析。JPEG显示“当前不支持图片文字识别”；扫描PDF返回无可读文字时显示该文件原因，不生成成功结论。结果页保留既有字段编辑/新增/重新评估函数及保存API，不重写结果schema。
5. [I-1..I-3，S-2/S-5] 只需改app.js与共享组件selection API，CSS使用08已落完整规则。测试验证打开零POST、获取N后只1次AI、失败0次AI、关窗后0次后续AI、旧结果不被前端先清空。
6. [I-2/I-3，S-2] ExpertDocumentAnalysisService在extract之后、构建prompt/调用LLM/deleteAll之前，若任何所选项unsupported或text为空，抛原AnalysisFailedException，message列明attachmentId/文件名及“不支持格式/无可读文字”；不再静默filter后把部分文件分析成功当全部成功。现有GlobalExceptionHandler保留ANALYSIS_FAILED映射，前端显示message。测试“一个可读PDF+一个空PDF”时LLM调用0、deleteAll调用0，旧结果保留；全可读路径和字段编辑/新增/清空不变。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 |
| 2 | `src/main/resources/static/expert-materials.js` | 新增 |
| 3 | `src/test/js/expertMaterialAnalysisFlow.test.js` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt` | 修改 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt` | 修改 |

## 验收标准

- I-1：有选带入、无选默认CV/学位；跨页默认不漏；超过500有明确提示；提交后快照不漂移。
- I-2：2所选1未存只获取1，全部就绪后一次AI；任何失败时AI调用0，重试仅失败ID。
- I-3：JPEG禁选、空PDF原因；关窗token销毁；原结果编辑/新增/重新评估事件仍生效。
- S-1/S-2/S-5：selectionOnly使用同组件和CSS；两按钮文案、进度、disabled、错误四视口截图。
- I-2/I-3：混合可读/空文本所选不允许静默部分成功；错误列明文件且不删除历史结果；不新增结果表字段。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：混合材料与按需分析
- 前置条件：专家A有1已存CV PDF、1未存学位PDF、1JPEG、1扫描空文本PDF；历史分析结果存在。
- 操作步骤：1. 材料列表选择前两件后打开AI；2. 确认获取并分析；3. 让未存文件失败，重试；4. 选择JPEG和扫描PDF观察。
- 预期结果：选2/已存1/需获取1；打开不POST，确认仅下载1；失败阶段0次AI；重试全部就绪才分析；JPEG禁选，空PDF明确无可读文字；历史结果不会先被清空。
- 覆盖：I-1/I-2/I-3/S-1/S-2；本项可观察需求与列明的回归/交互。

### A-2：关闭、默认选件与结果回归
- 前置条件：无材料勾选，CV/学位分布在不同页，另有历史结果可编辑。
- 操作步骤：1. 进入重新选件；2. 查看默认勾选；3. 获取中关闭，等完成再打开；4. 编辑保存原结果、新增字段、重新评估。
- 预期结果：默认覆盖支持格式CV/学位；关窗后下载继续但不自动提交新分析；重开显示已存状态；三个结果操作仍使用原接口且保存值可重读。
- 覆盖：I-1/I-2/I-3/S-2/S-5；本项可观察需求与列明的回归/交互。
