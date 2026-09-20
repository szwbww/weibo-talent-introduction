# ContactOut 企业评分分层采集门槛

授权依据：用户确认企业分数分层后要求“好的 你开始修改吧”，并要求按新规则复核上一批德邦相关专家。目标工作树：`/Users/lukai/IdeaProjects/weibo-talent-introduction`。不提交、不推送、不修改实际 Dia storage。

## 需求描述

插件新增可导入、校验、保存和导出的企业评分库；每段任职必须将该段企业与该段岗位一起判断。世界五百强可采集普通技术／研发人员；非五百强 80–100 分要求 L2 高级人员，60–79 分要求 L3 骨干／技术经理，低于 60 分要求 L4 顶尖／负责人。

必须不变：专家缓存、所有已显示完整邮箱的采集和导出、掩码过滤、去重、原采集 more 范围、现有 v1/v2 规则可继续使用、权限、红黄视觉语义。不新增平台接口、自动获取邮箱、自动翻页、ES 写入或发送。企业打分依赖人工审核后导入的配置，不在插件中联网猜测。批次分析和企业评分 JSON 属用户数据产物，不写入扩展默认值。

## 关键不变量

### Invariant I-1: 企业分数与门槛
- Rule：企业配置由 `developedEconomy`、`global500`、`global500Year`、`highSalaryIndustryPoints`、`priorityIndustryPoints`、证据来源组成。非五百强得分为发达经济体 20 分／其他经济体 10 分，加高薪产业 0/10/20 分，加重点产业 0/40/60 分；五百强得分固定 100 且独立标记为直通。五百强要求 L1，非五百强 80–100 要求 L2、60–79 要求 L3、0–59 要求 L4。
- Applies to：core 企业配置校验、计算、匹配和 profile 评估。
- Violation consequence：同为 100 分的五百强与累计高分企业被错误使用同一职级门槛。
- 来源：original。

### Invariant I-2: 同段经历组合
- Rule：一段经历只用该段 `job.company` 匹配的企业评分与该段 `job.title` 的级别判断。不得把甲公司的五百强身份与乙公司的职称拼接。任一当前或历史经历明确达标即 keep；L4 明确技术岗位对任何企业分数都达标。
- Rule：企业匹配只做规范化后的完整名称／别名相等，不做模糊包含和自动集团继承。未匹配企业的 L1–L3 技术经历、歧义职称、履历不完整均 review；配置完整且所有技术经历低于门槛或当前明确初级／非技术才 exclude。
- Applies to：core 级别判断与 collector 页面评估。
- Violation consequence：跨公司拼接、子公司误继承、潜在合格历史经历误排。
- 来源：original。

### Invariant I-3: 职级层次
- Rule：v3 职级按最高匹配层返回。L1 为明确技术／研发词；L2 为 Senior/Sr/高级/资深 + 技术词；L3 为 Staff/Principal/Lead/Manager/主任/经理/负责人 + 技术词；L4 为 Distinguished/Chief/Director/Head/VP/首席/总监/总工程师 + 技术词，或 Fellow/Technical Fellow/Corporate Fellow/Distinguished Fellow/CTO/Chief Technology Officer/Chief Scientific Officer 完整职称。
- Rule：初级词不可成为 L1；Associate/Assistant/Postdoctoral/Postdoc 为歧义 review；非研发职能词阻止技术级别。`Manager`、`Director` 单独出现不能通过，必须同时有技术词。现有精确负向 rules 仍可用于当前岗位。
- Applies to：core 默认 v3、校验与分类。
- Violation consequence：普通管理、销售工程或博士后被错误认作企业高级技术岗位。
- 来源：original。

### Invariant I-4: 配置与存储兼容
- Rule：岗位配置键继续 `contactout-ignore-rules-v1`，专家键继续 `contactout-visible-export-v1`；新企业键仅为 `contactout-enterprise-ratings-v1`。v1/v2 岗位配置保持原行为，不静默改为 v3。首次安装岗位默认 v3、企业默认空库；导入／恢复只填编辑框，显式保存才写对应键。
- Rule：企业配置 schemaVersion 1，未知字段、重复 id、重复规范化别名、非法分值、五百强缺年度、无有效 http(s) 来源、非法日期均拒绝；失败不覆盖岗位配置、企业配置或专家缓存。清空专家只删除专家键。
- Applies to：core 校验、popup 初始化／导入／保存／导出／恢复／清空。
- Violation consequence：已存配置或专家名单丢失，或无法追溯企业评分。
- 来源：K-browser-extension-config-default-is-not-saved-state。

### Invariant I-5: 页面与采集边界
- Rule：v3 keep 无色；明确不达采集门槛为红；企业未收录、职级歧义、履历未展开／解析不完整为黄。badge 显示匹配企业、分数／五百强、要求层级、岗位层级及原因，使用 textContent；badge 不进入导出。
- Rule：标记流程零点击；MutationObserver 更新后重算并清除旧状态。红黄不影响任何完整邮箱采集／导出；原采集流程仍只对已经显示完整邮箱的卡片展开 more。
- Applies to：collector refresh/clear，core 导出回归。
- Violation consequence：页面误标、提示泄漏、平台操作扩大或邮箱漏采。
- 来源：K-dom-stub-tests-hide-dangling-refs。

### Invariant I-6: 交付版本
- Rule：manifest 为 1.4.0，权限仍恰为 `activeTab/scripting/storage`；新 ZIP 只含 8 个运行／文档文件，不含测试、CSV、评分库、缓存或分析结果，不覆盖 1.3.0。
- Applies to：manifest、README、打包。
- Violation consequence：错版交付、私有数据混包或安装权限扩大。
- 来源：original。

## 样式契约

### S-1: 企业配置编辑区
- 复用：`popup.css:1` 的 `.actions`、`.actions button`、button 状态；`popup.css:2` 的 `textarea`、`#rules-panel`、`#rules-panel label`。CSS 文件完全不修改。
- DOM 结构：在现有 `#rules-panel` 内、岗位配置控件之后追加：
```html
<label for="enterprise-json">企业评分库 JSON</label><textarea id="enterprise-json" rows="10" spellcheck="false"></textarea>
<div class="actions"><button id="save-enterprises">保存并应用企业评分</button><button id="reset-enterprises">清空企业编辑框</button></div>
<div class="actions"><button id="export-enterprises">导出已保存企业评分</button><button id="import-enterprises">导入企业 JSON</button></div>
<input id="enterprise-file" type="file" accept=".json,application/json" hidden>
```
- 既有 summary 改为“编辑岗位与企业规则”；岗位 label 改为“岗位门槛 JSON”。不新增 class、inline style，不改 popup.css。

### S-2: 页面红黄标记
- 复用 `collector.js` 现有红 `#fff1f2/#dc2626`、黄 `#fffbeb/#d97706` 和 badge 样式，逐字不改。只更新 badge 文案内容，不增加第三种颜色或 DOM 类型。

## 现状审计

### 浏览器本地存储
- Schema：popup.js:2 专家键，:3 岗位规则键；企业键尚不存在。
- Write paths（`rg -n 'storage.local|RULES_KEY|const KEY' tools/contactout-visible-export/popup.js`）：:62 保存岗位规则；:92 合并写专家；:149 清空专家。新企业保存路径必须独立。
- Read paths：:89 采集前重读专家；:163 初始化读专家和岗位规则。新企业库初始化和页面标记均需读取同一内存校验结果。
- Interaction：企业导入→编辑器→显式保存→页面标记；专家清空不得删除两个配置键。上述行号和命中来自本轮 grep，不凭旧计划推断（来源：K-plan-quantified-claims-need-grep-receipts）。

### 页面评估
- core.js:25 默认 v2；:40 校验 v1/v2；:96 `classifyProfileJobs` 先历史高级保护，再完整性，再红黄。
- collector.js:175 注入入口只收岗位配置；:199 对每卡调用分类器。`visibleJobs` 已保留 title/company/current，可直接满足同段组合，无需解析公司外的页面字段。
- 红黄属性、observer、badge 过滤及 `clearContactOutIgnoreMarks` 已存在；采集函数在 collector 前半段，实施不得修改其点击范围。

### 前端样式盘点
- 基线 DOM：popup.html:8–14 单个 `#rules-panel`；本计划将企业配置放在同一 details 内复用样式。
- token：宽 460px、padding 20px、按钮圆角 8px、紫色 `#5437cb`、disabled opacity .5；本计划不改 CSS。
- 真实 DOM 风险：popup 测试必须断言新增 id 在 popup.html 中存在，浏览器测试必须运行真实合成页面（来源：K-dom-stub-tests-hide-dangling-refs）。

## 实现方案

### T-1: 核心评分和职级（I-1/I-2/I-3/I-4）
- 修改 `core.js`、`tests/core.test.cjs`。先增加失败测试，再实现 v3 岗位配置、企业 schema、分数／门槛、精确别名匹配、同段任职评估；保留 v1/v2 回归。
- 档位测试：五百强 L1 keep；非五百强累计 100 的 L1 exclude/L2 keep；用可配置的 80/70/60/50 覆盖三个非五百强门槛档；L4 未收录企业 keep；L1–L3 未收录黄；历史经历使用自身企业；跨段不可拼接；Manager/Director 无技术词不通过；重复别名和非法来源拒绝。

### T-2: 页面评估（I-2/I-5，S-2）
- 修改 `collector.js`、`tests/browser.test.cjs`。注入两个配置；在 badge 呈现公司得分和门槛。保留 more、未知历史和 observer 安全。
- 合成 DOM 覆盖：五百强 Engineer 无色，100 分非五百强 Engineer 红、Senior Scientist 无色，70 分 Staff 保留/Senior 红，50 分 Chief 保留/Principal 红，未知企业黄，历史企业独立达标，跨段拼接不达标，重复刷新无残留，零点击和导出无 badge。

### T-3: 企业库上传和交付（I-4/I-6，S-1）
- 修改 `popup.html`、`popup.js`、`tests/popup.test.cjs`、`manifest.json`、`README.md`。增加独立企业编辑／导入／导出／保存／清空编辑框；保存后标记时传两个配置。岗位 v1/v2 提示恢复默认并保存 v3；企业空库提示先导入。
- 专家缓存、岗位配置、企业配置三键相互隔离；非法企业 JSON 不覆盖。更新 1.4.0 并打包版本 ZIP。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| tools/contactout-visible-export/core.js | v3 职级、企业评分和同段判断 |
| tools/contactout-visible-export/collector.js | 双配置页面三态 |
| tools/contactout-visible-export/popup.js | 企业库读写和状态 |
| tools/contactout-visible-export/popup.html | 企业 JSON 控件 |
| tools/contactout-visible-export/manifest.json | 1.4.0 |
| tools/contactout-visible-export/tests/core.test.cjs | 核心边界与兼容 |
| tools/contactout-visible-export/tests/browser.test.cjs | 真实 DOM 分层测试 |
| tools/contactout-visible-export/tests/popup.test.cjs | 三键隔离、上传流程 |
| tools/contactout-visible-export/README.md | 配置、评分、升级说明 |
| outputs/contactout-visible-export/contactout-visible-export-1.4.0.zip | 新交付包 |

规划元数据和执行报告不计为实施文件。批次分析工作簿与企业评分 JSON 是独立用户数据产物，不属于扩展实施文件；不得打入 ZIP。

## 验收标准

- I-1/I-2/I-3：核心测试覆盖所有门槛边界、五百强直通区别、同段组合、历史保护、未知企业和层级歧义。
- I-4：v1/v2 配置原样可用；v3 和企业配置校验失败不改三个 storage；导入／恢复未保存不生效；保存后重开一致；清空专家不清配置。
- I-5/S-2：真实 Chromium 测试断言红黄 RGB、badge 内容、DOM 复用清理、零标记点击、原 more 测试和多邮箱导出全部通过。
- I-6：manifest 1.4.0、权限数组逐项相同；ZIP 恰含 core/collector/popup.js/manifest/popup.html/popup.css/README/IGNORE_RULES 八项且逐字节等于源码；不含测试、CSV、JSON 数据或缓存。
- S-1：popup.html 存在所有新增 id 和指定层级；popup.css 与执行前基线逐字相同；截图确认既有 460px 紫色弹窗可读。
- 命令：`node tools/contactout-visible-export/tests/core.test.cjs`；另两项使用 `PLAYWRIGHT_MODULE=/Users/lukai/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright TEST_BROWSER_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' node tools/contactout-visible-export/tests/browser.test.cjs` 和同前缀的 popup.test.cjs；再跑 node --check、diff/check、ZIP 清单／哈希。

## 人工验收清单

### A-1: 升级与配置隔离
- 前置条件：1.3.0 有专家缓存和已保存 v2；先导出名单和规则。
- 操作步骤：原目录覆盖 1.4.0 并重新加载；打开弹窗；恢复岗位默认但不保存后重开；再保存 v3；导入企业 JSON 但不保存后重开；再导入并保存。
- 预期结果：未保存两次均保持原配置；显式保存后显示 v3 和企业数量；专家人数／邮箱数始终不变。
- 覆盖：I-4/I-6/S-1。

### A-2: 四档门槛
- 前置条件：测试企业分别为五百强、非五百强 100、70、50 分；页面分别显示 Engineer、Senior Engineer、Staff Engineer、Chief Engineer。
- 操作步骤：点击“标记职级（红／黄）”。
- 预期结果：四人无色；把 100 分企业岗位改为 Engineer、70 分改为 Senior Engineer、50 分改为 Principal Engineer 后三人红色；统计同步变化。
- 覆盖：I-1/I-3/I-5/S-2。

### A-3: 同段与未知证据
- 前置条件：一人甲公司五百强销售、乙公司普通 Engineer；一人未知公司 Staff Engineer；一人历史已配置 70 分公司的 Staff Engineer。
- 操作步骤：启用标记，手动展开履历。
- 预期结果：第一人不能因跨段拼接保留；第二人黄色；第三人无色且理由指向历史公司及 70 分/L3。
- 覆盖：I-2/I-5。

### A-4: 采集回归
- 前置条件：页面有红、黄、无色卡，部分已显示多个完整邮箱，另有掩码邮箱。
- 操作步骤：采集、导出 CSV、取消标记、清空名单第一次点击后等待，再执行双击确认清空测试。
- 预期结果：颜色不改变完整邮箱采集；多个邮箱保留、掩码跳过、履历无 badge；取消清理红黄；第一次清空不删除，确认后只删专家，岗位与企业配置保留。
- 覆盖：I-4/I-5。

### A-5: 企业配置错误
- 前置条件：已有有效企业库。
- 操作步骤：依次导入重复别名、非法 15 分高薪值、五百强缺年度和无来源 URL 的 JSON 并保存。
- 预期结果：每次显示具体错误；重新打开仍是先前有效企业库，专家缓存不变。
- 覆盖：I-4。

## 自查与交接

10 个实施文件、两个子系统（核心／页面评估；popup 配置／交付）。一个新共享 store：企业评分键。所有写读路径和交互均有不变量、自动测试与人工场景；无额外字段写入专家缓存。执行自测不等于独立 verify-p 或真实 Dia 验收。
