# ContactOut 通用生产技术／研发候选保护规则

状态：待用户确认，未执行。目标工作树：`/Users/lukai/IdeaProjects/weibo-talent-introduction`。

## 需求描述

1. 默认规则不再按德邦或任何企业的行业需求标红。生产技术、科研、材料、配方、产品开发、工艺及相关技术管理岗位保留，不因专业方向不符而排除。
2. 当前职位不相关但历史有技术／研发线索时不标红；信息不足时保留待核实。完整名单是否符合“高级”职级仍由搜索条件和后续人工复核决定，插件本轮不宣称自动认证。
3. 给出可导入的通用 JSON 和更新包；用户主动导入／保存后应用。现有已保存配置不被静默覆盖。

必须不变：专家缓存键及内容；导出全部已显示完整邮箱、去重合并行为；规则标红不改变采集范围；已有 more 仅对已显示邮箱人员执行；无自动 View email、View phone、翻页、网络 API、ES 写入或邮件发送；不增加扩展权限；不改变弹窗及卡片样式。

不在范围：自动化获取、企业匹配标签、AI 分类、外部背景核查、人才政策资格认证、主动淘汰/删除已采集专家、线上部署、自动迁移用户已有自定义规则。不修改原始 CSV。

## 关键不变量

### Invariant I-1: 行业中立且保守
- Rule：新默认配置不包含按行业方向排除的 data、fragrance、hair、laundry、body-care、patent-information 规则。新默认负向规则只有完整职务精确匹配，不按公司、地点、姓名、技能或学历判断。技术岗位未标红不等于已通过高级资格认定。
- Applies to：core.defaultIgnoreConfig、配置文件、collector.markContactOutIgnored。
- Violation consequence：误伤其他企业所需专家。
- 来源：original。

### Invariant I-2: 当前及历史技术线索优先保护
- Rule：v2 配置新增一个顶层字段 `protectTitleKeywords`。当前或可见历史完整职务包含任一保护词时，ignoredTitleReason 返回空字符串，优先于任何负向规则。保护宽于高级认定，避免误删，不把保护结果当作合格标签。
- Rule：关键词只检查已解析的任职 title，不检查技能、姓名或正文。英文使用大小写无关的字母数字边界匹配，不能把 Engineer 匹配到 EngineeringSales 等拼接文本；中日韩词使用规范化后包含匹配。不执行用户正则、代码或远程规则。
- Rule：v2 且保护词非空时，存在尚未展开的受支持 more 按钮，不标红，并计入 unknown；本流程不自动展开。无可靠当前职务或多当前岗位存在不匹配负向规则者，继续不标红。
- Applies to：core.validateIgnoreConfig、core.ignoredTitleReason、collector.refresh。
- Violation consequence：当前商务／生产操作岗位掩盖历史研发经历导致误删。
- 来源：original。

### Invariant I-3: 配置兼容与显式应用
- Rule：新默认 schemaVersion 为 2，protectTitleKeywords 为必填数组，可空；最多 100 项，每项 1–80 字符，去重规范化，拒绝通配／正则控制字符。保留既有 scope、revision、rules、currentTitleEquals、pastTitleEqualsAny 的约束。
- Rule：v1 配置继续接受并保持原语义，不能自动套用 v2 保护条件。v1 携带 v2 字段拒绝；v2 缺失保护字段拒绝；未知版本及字段拒绝。无效输入不替换已保存规则。
- Rule：升级只改变默认值，不覆盖 `contactout-ignore-rules-v1` 中的用户配置。文档明确要求备份→导入通用 JSON 或恢复默认到编辑框→保存。导入／恢复但未保存不能改变生效规则。
- Applies to：core 校验与默认，popup 现有初始化、导入、保存、重置、导出消费者。
- Violation consequence：旧配置损坏、旧行业排除意外继续生效或用户配置被覆盖。
- 来源：original。

### Invariant I-4: 缓存与获取行为不变
- Rule：专家缓存继续使用 `contactout-visible-export-v1`。不增删专家字段，不过滤采集／导出，不删除邮箱。规则保存仍只写独立规则键。规则评估不点击任何平台按钮。
- Applies to：popup 现有读写路径、collector 采集路径、core.mergeRows/toCSV 的回归测试。
- Violation consequence：名单丢失、邮箱漏采或未经本轮授权自动消耗平台额度。
- 来源：original。

### Invariant I-5: 交付可复现
- Rule：版本更新为 1.2.1。新 JSON 内容与 defaultIgnoreConfig 深度相等。版本化 ZIP 内容与最终源码一致，不覆盖旧 ZIP。独立验证及人工验收前不宣称生产验收通过。
- Applies to：manifest、JSON、打包、测试证据。
- Violation consequence：用户安装旧规则或误以为保存默认配置已生效。
- 来源：original。

## 样式契约

### S-1: 不改变 UI 或样式
- 复用：`collector.js:171–172` 现有卡片样式；`popup.css:1–2` 现有弹窗、按钮、配置编辑区样式。不得新增 class、inline style 或更改规则块。
- 新增：无。
- DOM：保持 `popup.html:9–16` 的 `<details id="rules-panel">`、`#rules-json`、`#save-rules`、`#reset-rules`、`#export-rules`、`#import-rules`、`#rules-file`。不新增或修改 DOM 元素。
- 改动前基线：卡片仍设置 `data-contactout-ignore="yes"`；提示仍由 `badge.setAttribute('data-contactout-review-badge','')` 与 `textContent` 创建。
- CSS 基线：`background-color:#fff1f2!important;outline:2px solid #dc2626!important;outline-offset:-2px!important`；提示 `background:#fee2e2!important;color:#991b1b!important;font:600 13px/1.5 system-ui,sans-serif!important`。保留原完整规则块，不改变余下属性。
- 弹窗基准：宽 460px、padding 20px、基础字号 13px、按钮圆角 8px、边框 #d9d5e7、主按钮 #5437cb、hover #452bad、disabled opacity .5，均不修改。
- 来源：K-dom-stub-tests-hide-dangling-refs；测试使用真实页面 DOM，不用 stub 代替可见交互验收。

## 现状审计

### 本地专家缓存与规则缓存
- 专家键：`popup.js:2`，`contactout-visible-export-v1`；规则键：`popup.js:3`，`contactout-ignore-rules-v1`。
- 配置结构：`core.js:defaultIgnoreConfig/validateIgnoreConfig`，目前 v1 的 scope/revision/rules；各规则的 id/enabled/reason/currentTitleEquals/pastTitleEqualsAny，未知字段拒绝。
- 写路径：`popup.js:53 saveRules` 仅保存规则；`:82 capture` 保存 mergeRows 后的专家；`:139 clear` 删除专家键。导入与恢复默认只填编辑器，不写 storage。`:153` 初始化只读；无自动迁移。
- 读路径：`popup.js:153` 初始化读取两个键；`:79` 采集前重读专家键防旧快照覆盖；`:94–114 download/toCSV` 使用 rows；markIgnored/saveRules 将已验证配置传入注入脚本；collector.refresh 使用当前 title 和历史 title 调用 ignoredTitleReason。
- 交互点：默认／导入 JSON → validate → 规则保存 → 重开弹窗 → 页面标红；规则重置／导入 → 编辑框 → 显式保存；采集与规则分别存储，测试不得混写；v2 履历保护 → 页面 more 未展开时 unknown。
- 命中依据（来源：K-plan-quantified-claims-need-grep-receipts）：`rg -n 'storage.local|RULES_KEY|const KEY' tools/contactout-visible-export/popup.js` 对应上述 `2,3,53,79,82,139,153`。

### 采集与标红
- `collector.js:95 expandAndCollectContactOutVisible` 获取已显示邮箱的卡片并执行 more；本计划不改此函数。
- `collector.js:144 visibleJobs` 从最小完整任职块提取 title/company/current；不会把技能当任职。
- `collector.js:163 markContactOutIgnored` 每次清理旧标记并建立 observer；`:186` 当前职位逐个匹配，可见历史仅用于正向条件；目前没有全局技术历史保护。
- `core.js:70 ignoredTitleReason` 仅精确匹配负向规则，可选历史正向匹配。
- `rg -n 'hair|fragrance|laundry|body-care|data|patent-information' core.js` 默认相关规则位于 27、29–33 行；当前适用 scope 为德邦封装胶／膜。
- collector 代码修改不得新增 fetch/XHR、平台按钮点击；原 more 点击在采集函数中保留。

### 前端样式盘点
- 见 S-1，源码包含 `.actions`、`.hint`、`.summary`、`.card`、`.addresses`、`#rules-panel`，本次不修改 HTML/CSS。
- 本次仅修改前端判断函数，DOM 骨架保持不变；浏览器测试核验标红变化与取消标记，同时确认不存在邮箱／more／翻页点击。

### 现有测试与工作树
- core.test.cjs 当前断言默认标红 Senior Data Scientist、Fragrance Scientist、护发岗位；这些断言需要替换为通用保留，并另保留 v1 兼容测试，不直接删除旧语义覆盖。
- browser.test.cjs 当前默认标红 data/hair/operator/patent；需要新默认场景和 v1 显式场景分别覆盖。
- popup.test.cjs 已覆盖校验失败不写入、保存／导入／重置及名单不受影响；扩展 v1→v2 显式保存覆盖。
- 用户工作树存在无关修改与未跟踪 tools 文件，执行不得重置、覆盖无关文件、暂存、提交或推送。

## 实现方案

### T-1: 通用规则与兼容校验（I-1/I-2/I-3/I-5，S-1）
- 修改 `core.js`，先加 tests/core.test.cjs 的失败用例，再实现 v2 默认、校验、关键词保护。
- 默认 scope：`通用生产技术／研发人才（保守排除）`；revision：`2026-09-18-generic-v1`。
- protectTitleKeywords：`engineer, scientist, chemist, research, r&d, technical, technology, technologist, development, 研发, 研究, 工程师, 工艺, 技术, 科学家, 化学家`。
- 负向完整职务：
  - operator：Manufacturing Operator、Manufacturing Operator I、Manufacturing Operator II、Manufacturing Operator III、Manufacturing Operator IV、Production Operator、Machine Operator、Assembly Operator。
  - recruitment：Recruiter、Senior Recruiter、Talent Acquisition Specialist、Talent Acquisition Manager、Human Resources Manager、HR Manager。
  - sales：Sales Representative、Sales Manager、Senior Sales Manager、Account Executive、Account Manager。
  - administration：Administrative Assistant、Office Administrator、Receptionist。
  - finance：Accountant、Senior Accountant、Payroll Specialist、Finance Manager。
- 理由统一体现“当前可见履历偏某职能，未见技术／研发任职线索；人工复核”，不能声称本人必然不合格。
- 不把 Scientist/Engineer/Chemist/Technician、生产技术管理、质量工程或数据科研加入默认负向规则；保护结果不表示高级认定。
- 新建 `generic-production-rd-rules.json`，与默认函数输出完全一致。由现有 popup 导入→校验→保存→collector 消费，无新增 storage 写路径。

### T-2: 未展开历史防误判（I-2/I-4，S-1）
- 修改 `collector.js` refresh：仅对 v2 且有保护关键词的配置，当 `moreButton(card)` 返回可操作 more 时计为 unknown、跳过标红；不点击。
- 保持 current 数量校验、多当前职务否决、observer 重算、clear 行为；展开后自动根据新可见历史重判。
- browser.test.cjs 验证：仅有 Sales Manager 且尚有 more 不标红；手动展开后历史 Senior Scientist 不标红；纯销售历史无技术线索且可靠完整时标红；高级科研／工艺／制造技术岗位保留；技能里出现 Scientist 不作为保护。

### T-3: 配置应用回归与交付（I-3/I-4/I-5，S-1）
- popup.test.cjs 加入旧 v1 已保存配置在启动后保持原样、默认 v2 只填框、保存 v2 后重开仍读 v2、无效输入不覆盖、专家缓存深度不变。
- manifest 升级 1.2.1，不增权限；README 和 IGNORE_RULES 解释 v1/v2、保护、待核实与手动应用步骤。
- 打包新版本化 ZIP，不覆盖旧包；包含 manifest/core/collector/popup HTML JS CSS、README、IGNORE_RULES、通用 JSON；不打包测试、原始 CSV、私人名单或临时文件。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| tools/contactout-visible-export/core.js | 通用默认、v1/v2 校验、技术保护 |
| tools/contactout-visible-export/collector.js | 未展开历史保护 |
| tools/contactout-visible-export/tests/core.test.cjs | 判定与兼容测试 |
| tools/contactout-visible-export/tests/browser.test.cjs | 实际 DOM 行为回归 |
| tools/contactout-visible-export/tests/popup.test.cjs | 存储及配置交互回归 |
| tools/contactout-visible-export/manifest.json | 1.2.1 版本 |
| tools/contactout-visible-export/README.md | 使用说明 |
| tools/contactout-visible-export/IGNORE_RULES.md | 配置规范 |
| tools/contactout-visible-export/generic-production-rd-rules.json | 用户可导入配置 |
| outputs/contactout-visible-export/contactout-visible-export-1.2.1.zip | 版本化交付包 |

计划及知识计数属于规划阶段元数据，不属于实施清单。实施不得自行扩大上述文件范围。

## 验收标准

- I-1：高级生产技术、Senior Manufacturing Chemist、Senior/Principal/Staff Scientist、Chief Engineer、Senior Process Engineer、R&D Manager、护发／洗涤／香精科研、Senior Data Scientist 均不因方向标红；无默认企业名过滤。
- I-2：Sales Manager + 历史 Senior Scientist、Operator + 历史 Process Engineer 均不标红；技能文本不触发保护；more 尚未展开时 unknown，手动展开后自动重判；关键词边界、中文规范化、不匹配反例均测试。
- I-3：v1 原行为回归；v2 必填与非法字段测试；导入／默认只填框、保存才应用；旧自定义配置不被静默覆盖；invalid 配置不写入。
- I-4：专家缓存深度相等；完整邮箱合并和 CSV 防公式注入测试保留；标红流程点击计数为零；采集流程点击集合仍只有原先支持的已显示邮箱 more。
- I-5：JSON 与 defaultIgnoreConfig deepEqual；manifest 1.2.1；unzip -l 及解包比对最终源文件；ZIP 不含专家数据。
- S-1：HTML/CSS 没有 diff；collector style.textContent 与 DOM 创建骨架逐字不变；真实浏览器测试而非只跑 stub（来源：K-dom-stub-tests-hide-dangling-refs）。
- 必须新跑：`node tools/contactout-visible-export/tests/core.test.cjs`。
- 必须新跑：`PLAYWRIGHT_MODULE=/Users/lukai/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright TEST_BROWSER_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' node tools/contactout-visible-export/tests/browser.test.cjs`。
- 必须新跑：相同两个环境变量下 `node tools/contactout-visible-export/tests/popup.test.cjs`。
- 若真实浏览器受沙箱阻挡，报告受限，不把历史测试结果当本次通过。测试页面使用虚构资料，不在真实 ContactOut 上点邮箱。
- `git diff --check` 对本轮范围执行，并核对未跟踪文件内容；不把用户既有无关改动当本轮改动。

## 人工验收清单

### A-1: 显式切换到通用规则
- 前置条件：在 Dia 原扩展目录就地更新／重新加载，保留原扩展身份；已有一份旧规则和至少 1 条专家记录，先导出备份。
- 操作步骤：1. 打开插件查看名单数量。2. 导入 generic-production-rd-rules.json。3. 未保存前关闭重开。4. 再导入并点保存。5. 关闭重开。
- 预期结果：第 3 步仍为旧已保存规则；第 5 步显示通用 scope、schemaVersion 2；每一步专家和邮箱数量不变。
- 覆盖：I-3/I-4/I-5；默认→编辑器→保存→重读交互点。

### A-2: 跨行业高级技术岗位不标红
- 前置条件：通用配置已保存，页面有 Senior Scientist、Senior Manufacturing Chemist、Senior Process Engineer 或研发管理岗位资料卡。
- 操作步骤：1. 点击标红可忽略专家。2. 检查上述卡片，查看技能／专业不同者。
- 预期结果：上述岗位不因属于日化、生物医药、数据科研或非德邦方向而出现红底红边框；未标红不显示“已合格”字样。
- 覆盖：I-1/I-2、需求 1。

### A-3: 历史技术经历保护
- 前置条件：一张当前 Sales Manager、历史 Senior Scientist 的卡片，历史在 more 中；另有纯销售且履历完整的卡片。
- 操作步骤：1. 启用标红。2. 手工点第一张 more。3. 检查两张卡片。4. 翻页后再观察标记。
- 预期结果：第一张展开前、后都不标红；完整纯销售卡按精确规则出现浅红底和原因；旧卡片标记不会留在新复用卡片上；插件没有自行点击 more 或获取邮箱。
- 覆盖：I-2/I-4、observer 交互点。

### A-4: 采集与缓存回归
- 前置条件：已有缓存；当前页包含已显示两个完整邮箱的人及仅掩码邮箱的人。
- 操作步骤：1. 手工显示目标邮箱。2. 点击采集当前页已显示邮箱。3. 导出 CSV。4. 修改规则保存，再次导出。
- 预期结果：已显示邮箱卡依原行为展开 more；两个完整邮箱均导出；掩码不导出；修改规则不减少记录或邮箱；不点击 View email/phone、不自动翻页、不发送邮件或导入 ES。
- 覆盖：I-4，必须不变项。

### A-5: 样式与输入错误
- 前置条件：新版本及一份已保存通用配置。
- 操作步骤：1. 检查弹窗与标红样式。2. 输入未知 schemaVersion 保存。3. 关闭重开。4. 点击取消标红。
- 预期结果：弹窗维持 460px 宽、原紫色按钮；红卡为 #fff1f2 与 #dc2626 边框；无效配置提示失败且旧配置仍在；取消后红色与提示消失，名单不变。
- 覆盖：S-1/I-3/I-4。

## 规划自查

- 不变量、读写路径、样式基线、精确文件范围、回归与人工验收均已列出。
- 范围为单一本地扩展；实施文件清单 10 项，其中 ZIP 为派生产物，无共享专家字段新增，仅规则新增 protectTitleKeywords。
- 执行前必须用户批准本计划，之后按 execute-p 绑定计划哈希和工作树；未经批准不改实现文件。独立验证按 verify-p 另行执行。
