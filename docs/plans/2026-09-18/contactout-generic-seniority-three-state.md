# ContactOut 通用职级三态标记

授权依据：用户在确认“高级岗位明确不标红；明确初级且无历史高级经历标红；职级不明标黄”后要求“好的 就按这个来修改 你改吧”。本文件将该已批准修订落实为执行契约，取代尚未实施的 contactout-generic-production-rd-rules 方案；不实现旧方案的单纯保护词模式。
目标工作树：`/Users/lukai/IdeaProjects/weibo-talent-introduction`。不提交、不推送、不更新实际 Dia 安装／浏览器 storage。

## 需求描述

1. 通用高级生产技术／研发候选不按企业或行业过滤；当前或历史已解析职务有明确高级技术线索时保持原色。
2. 当前明确初级或命中明确非目标职务，且可见履历未发现历史高级技术线索、没有未展开／无法识别的任职时，标红。职级不明、普通 Scientist/Engineer/Chemist、Associate、Scientist II/III、履历未展开或解析不完整时标黄待核实。
3. 配置可编辑、导入、导出；新默认三态规则显式保存后应用；旧 v1 自定义规则仍可使用，不静默替换。

不变：专家缓存键和数据、全部完整邮箱采集／导出、掩码过滤、去重、已有 more 展开范围、权限、原始文件。不新增自动获取邮箱、自动翻页、网络接口、ES 写入或发送。颜色只是人工筛选线索，不宣称政策资格，也不改变导出范围。

## 关键不变量

### Invariant I-1: 三态与优先级
- Rule：返回状态 keep/review/exclude。任何当前或历史已解析职务符合高级技术条件先 keep；随后检查当前职位／历史是否完整，缺失则 review；最后仅当每个当前职务均明确初级或命中配置负向完整职位规则才 exclude；其余 review。
- Rule：英文大小写不敏感、规范化横线和空白；英文词匹配必须有字母数字边界，中文为规范化包含。只检查任职 title，不检查公司、技能、姓名、年龄或学历。
- Rule：高级技术条件 = （高级词与技术词同时命中，或命中高级完整职务白名单），并且不含初级词、歧义词或非研发职能词。Fellow 使用完整白名单而不是片段，Postdoctoral Research Fellow 不自动认作企业高级。Senior Scientist II 可保留，普通 Scientist II/III 待核实；Principal Associate Scientist、Assistant Director 等因职级歧义待核实。
- Applies to：core.classifyProfileJobs、collector.refresh。
- Violation consequence：高级误排、普通误认高级或方向性排除。
- 来源：original（用户批准三态规则）。

### Invariant I-2: 配置协议兼容
- Rule：v2 在规则 store 只新增一个顶层对象 classification，含六个必填字符串数组 seniorTerms/technicalTerms/seniorTitleEquals/juniorTerms/ambiguousTerms/nonResearchTerms。每数组允许 0–100 项，每项 1–200 字符，去重，拒绝通配／正则控制字符；未知字段、类型和版本报错。v1 不允许此字段且原有语义不变。v2 classification 缺失报错。
- Rule：rules 仍为精确当前职务负向列表（及可选历史正向条件），原字段约束不变；保护优先于负向规则。空 v2 词表不会推断高级；空负向规则不关闭黄色待核实。
- Rule：首次安装使用 v2；已有 v1 不自动覆盖，提示手动恢复默认并保存。导入与恢复仅填框，保存才写规则键。
- Applies to：core 默认／校验／评估，popup 初始化、保存、导入、导出、重置。
- Violation consequence：旧配置损坏或用户升级后误以为新规则已生效。
- 来源：K-browser-extension-config-default-is-not-saved-state。

### Invariant I-3: DOM 状态安全
- Rule：v2 新增黄色及理由；keep 无标记。more 未展开、Present 段缺失／不完整、无法识别已显示历史任职，不能标红，除已有可靠高级线索时 keep。标记流程不点击按钮。
- Rule：当前页 DOM 更新后重算；清除同时删除红黄属性、说明及 observer；说明使用 textContent 并排除于采集正文；技能中的职务词不参与判断。
- Applies to：collector.visibleJobs/refresh/clearReviewMarks。
- Violation consequence：复用卡片旧标记污染、导出泄漏、误判或无限 observer 循环。
- 来源：original。

### Invariant I-4: 缓存与自动化边界
- Rule：专家键 contactout-visible-export-v1 不变；规则键 contactout-ignore-rules-v1 不变。保存规则不修改名单；red/yellow 都不影响已显示邮箱的采集／导出。原采集函数逻辑不变。无新增 View email、phone、翻页点击／fetch／接口。
- Applies to：popup 规则写入和采集／清空回归、collector 标记、core 导出。
- Violation consequence：名单丢失、邮箱漏采或新增外部操作。
- 来源：original。

### Invariant I-5: 交付版本
- Rule：manifest 为 1.3.0，不增加权限；版本化 ZIP 只含扩展运行文件和文档，不含个人 CSV、缓存、测试。旧 ZIP 不覆盖。测试新跑；未做 Dia 真人验收不得宣称已在用户浏览器生效。
- Applies to：manifest、文档、打包及执行报告。
- Violation consequence：错版交付或误报完成。
- 来源：original。

## 样式契约

### S-1: 保持布局，更新说明
- 复用 popup.html 既有 #mark/#unmark/#rules-panel .hint/首个 .hint/#status；popup.css:1–2 完全不修改，宽460px、padding20px、字号13px、按钮圆角8px、紫色#5437cb、disabled opacity .5 保留。
- popup.js 在启动时更新上述既有元素 textContent：按钮“标记职级（红／黄）”“取消标记”；提示“高级岗位明确：不标记；明确初级／非目标：红色；职级不明或履历不全：黄色。仅供复核，不影响采集。”；配置说明指出 v2 可编辑 classification、v1 为旧模式、保存才生效。
- 禁止新增 DOM/class、inline style、改变 popup.html/popup.css。状态统计复用 #status 显示保留／红色／黄色数量，v1 明示旧模式。

### S-2: 黄色卡片
- 复用 collector.js:171–172 原红色 CSS 与 badge 样式，原字面保持。仅追加以下完整规则，不增删属性：
```css
[data-contactout-review="review"]{background-color:#fffbeb!important;outline:2px solid #d97706!important;outline-offset:-2px!important}[data-contactout-review="review"] [data-contactout-review-badge]{background:#fef3c7!important;color:#92400e!important}
```
- DOM：红卡仍有 data-contactout-ignore="yes"，黄卡设置 data-contactout-review="review"；badge 仍是 div[data-contactout-review-badge]，通过 prepend 放入卡片，textContent 为“待核实（scope）：reason”或“可忽略候选（scope）：reason”。keep 不插入 badge。旧 v1 使用旧“本轮可忽略”提示。
- 清除查找两种属性并删除，不能保留黄色框。两种提示均由现有 ignored selector 排除出 visibleText。
- 来源：K-dom-stub-tests-hide-dangling-refs（真实 DOM 用例不可只靠 stub）。

## 现状审计

### Storage／配置文件
- core.js:defaultIgnoreConfig/validateIgnoreConfig 当前为 v1，字段 schemaVersion/scope/revision/rules，规则支持 id/enabled/reason/currentTitleEquals/pastTitleEqualsAny。
- `rg -n 'storage.local|RULES_KEY|const KEY' tools/contactout-visible-export/popup.js`：2 专家键、3 规则键、53 saveRules 写规则、79 capture 重读专家、82 capture 写合并名单、139 clear 删除专家、153 初始化读两个键。
- 读路径：popup 内 reviewConfig→markIgnored/saveRules 注入 collector；rules-json 导入/恢复/保存/导出；rows→render/download。无自动迁移。新增 classification 经现有配置保存路径消费，不新增 storage 键。
- 写读交互：编辑→校验→保存→重开→评估；旧配置启动→提示→恢复默认仅填框→保存 v2；标记不影响专家导出。

### 页面与评估
- core.ignoredTitleReason 当前仅负向精确匹配；collector.visibleJobs 从 div/p/li/span 最小任职块解析 title/current；refresh 检查当前 Present 数量并按全部当前命中才红。历史未展开时没有完整性保护。
- collector.moreButton 为精确 more/show more 控件；现有 expandAndCollectContactOutVisible 仅对已显示邮箱卡片点击。该采集流程不改。
- clearReviewMarks 当前仅清除红色属性与 badge；新状态需补黄属性清理。MutationObserver 在 refresh 前断开、之后重新监听。
- baseline 样式见 S-1/S-2；不重排 DOM、不改变采集 selector 语义。

### 测试／知识
- core.test.cjs 当前旧默认排除 data/hair/fragrance，改为显式 v1 fixture 保留旧语义回归，另外测试 v2 三态。
- browser.test.cjs 真实虚构页面覆盖提取、more、红卡、DOM 复用；新增黄卡、历史保护和取消清理、双颜色导出无提示污染及点击计数为零。
- popup.test.cjs 当前覆盖保存/导入/恢复和专家缓存；新增 v1 保存态不被默认覆盖、显式应用 v2、统计文案与真实 DOM 标签。
- 已加载配置默认非保存态知识，审计再次确认；旧 DOM stub 知识用于浏览器测试。既有工作树有无关修改和未跟踪 tools，需保留；不 git add/commit。

## 实现方案

### T-1: 核心规则（I-1/I-2/I-4）
- 改 core.js 和 tests/core.test.cjs；先新增分类器缺失的失败测试，再实现 classifyProfileJobs(jobs, config, {incomplete})。
- v2 默认 scope“通用高级生产技术／研发人才”，revision“2026-09-18-three-state-v1”。
- seniorTerms：Senior、Sr、Staff、Principal、Distinguished、Lead、Chief、Manager、Director、Head、VP、高级、资深、首席、主任、总监、经理、负责人、总工程师。
- technicalTerms：Engineer、Engineering、Scientist、Scientific、Chemist、Chemistry、Research、R&D、Technical、Technology、Technologist、Materials、Process、Production、Manufacturing、Development、研发、研究、工程、工艺、生产、技术、科学、化学。
- seniorTitleEquals：Fellow、Technical Fellow、Corporate Fellow、Distinguished Fellow、CTO、Chief Technology Officer、Chief Scientific Officer。
- juniorTerms：Junior、Intern、Internship、Trainee、Apprentice、初级、实习、见习、学徒。
- ambiguousTerms：Associate、Assistant、Postdoctoral、Postdoc、助理、博士后。
- nonResearchTerms：Sales、Recruiter、Recruitment、Human Resources、Payroll、Operator、Technician、销售、招聘、人事、操作工、技工。
- 默认 rules 为完整职位 operator（Manufacturing Operator 及 I–IV、Production Operator、Machine Operator、Assembly Operator）、sales（Sales Representative、Sales Manager、Senior Sales Manager、Account Executive）、recruitment（Recruiter、Senior Recruiter、Talent Acquisition Manager、Human Resources Manager）、administration（Administrative Assistant、Office Administrator、Receptionist）。不按方向排除；命中只表示可忽略候选，不是正式否定职业资格。
- classifyProfileJobs 对 v1 保持旧规则与不确定不红行为；v2 高级保护优先，junior 或负向每个当前均命中才红，否则黄。不以工作年限或 III 自动判高级。

### T-2: 页面三态（I-1/I-3/I-4，S-2）
- 改 collector.js 和 tests/browser.test.cjs；在刷新中计算 incomplete：more 可操作、无当前任职、当前 Present 数不符、已显示任职型文本存在无法解析内容。保守回退黄色，避免猜测未展示历史。
- 调用分类器；保留不渲染；红沿用、黄用新属性；返回 scanned/marked/unknown/kept。取消和复用卡片清理两种颜色。
- 保持渲染文本安全和过滤 badge；测试中无真实 ContactOut 请求，不打开用户账号。

### T-3: 配置交互与交付（I-2/I-4/I-5，S-1）
- 改 popup.js、tests/popup.test.cjs：显示三态统计、旧配置提醒、配置说明；原缓存路径保留。
- 改 manifest 为1.3.0；README/IGNORE_RULES 完整说明 v2（三态对象取代未落地的 protectTitleKeywords 示例）、使用现有导出已保存规则按钮获得 JSON，不另造自动白名单或删除名单。
- 打包指定版本 ZIP。配置通过“恢复默认到编辑框→保存并应用规则”启用，不清空缓存、不卸载扩展。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| tools/contactout-visible-export/core.js | 三态与v1/v2 |
| tools/contactout-visible-export/collector.js | 红黄刷新清理 |
| tools/contactout-visible-export/popup.js | 文案与统计 |
| tools/contactout-visible-export/manifest.json | 1.3.0 |
| tools/contactout-visible-export/tests/core.test.cjs | 核心测试 |
| tools/contactout-visible-export/tests/browser.test.cjs | DOM测试 |
| tools/contactout-visible-export/tests/popup.test.cjs | 配置交互测试 |
| tools/contactout-visible-export/README.md | 更新使用说明 |
| tools/contactout-visible-export/IGNORE_RULES.md | v2配置规范 |
| outputs/contactout-visible-export/contactout-visible-export-1.3.0.zip | 交付包 |

规划元数据、执行证据报告为 docs/plans/2026-09-18/contactout-generic-seniority-three-state-execution.md，不是额外实现文件。临时基线、截图可放 /private/tmp。禁止新增其他实现／测试文件及修改旧方案。

## 验收标准

- I-1：keep：Senior Scientist II、Technical Staff Engineer、Principal Engineer、R&D Manager、Senior Manufacturing Chemist、Chief Engineer、Senior Fragrance Scientist；review：Engineer、Scientist、Associate Scientist、Scientist II/III、Principal Associate Scientist、Assistant Research Director、Postdoctoral Research Fellow；exclude：Junior Engineer、Intern 且履历完整无历史高级；历史 Senior Scientist 否决当前 Junior／Sales 的红色。多当前一个未知则黄。Senior Sales Manager 不能keep。
- I-2：v1旧行为、v2缺失/多余字段/数组长度/非法正则、规范化/边界、空列表测试；导入/重置未保存不变；保存后重开一致；旧v1原样保留。
- I-3：more未展开初级卡黄；展开后能按真实历史变保留／红；skills不当职务；不完整历史黄；DOM复用和取消清理所有红黄；badge不进入导出，XSS当文本；点击计数为零。
- I-4：重复采集保留多邮箱、原more测试全部保留；保存无效/有效规则均不损名单；无新增网络／邮箱／翻页点击。
- I-5：版本与权限确认、ZIP清单及源码哈希比对，包不含私人资料；旧包保留。
- S-1/S-2：真实浏览器截图查看红#fff1f2/#dc2626、黄#fffbeb/#d97706，原紫色弹窗不变；黄色CSS字面与契约一致；真实DOM按钮及文案存在。
- 命令：`node tools/contactout-visible-export/tests/core.test.cjs`；另两个测试使用 `PLAYWRIGHT_MODULE=/Users/lukai/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright TEST_BROWSER_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' node tools/contactout-visible-export/tests/browser.test.cjs` 和同前缀的 popup.test.cjs。
- 新跑全部命令；若 sandbox 阻断 Chrome，按权限机制申请后重试。对实现范围 diff/check，并用临时基线核对未跟踪源文件变更。

## 人工验收清单

### A-1: 升级与显式启用
- 前置条件：备份CSV和规则，保留原扩展目录与身份，以新包文件就地更新并重新加载。
- 操作步骤：打开插件→旧v1提示→恢复默认到编辑框→未保存关闭重开→再恢复默认并保存→重开。
- 预期结果：未保存仍为旧配置；保存后schemaVersion2、通用scope；专家及邮箱数量不变。
- 覆盖：I-2/I-4/I-5、配置读写交互。

### A-2: 高级、初级、未知三类
- 前置条件：通用规则已保存；虚构测试页面或真实页面包含Senior Scientist、Junior Engineer、Scientist III，且有可靠完整任职。
- 操作步骤：点击“标记职级（红／黄）”。
- 预期结果：高级卡无色、初级红色、Scientist III黄色，并显示原因；统计包含保留、红色、黄色；行业不同不改变判断。
- 覆盖：I-1/I-3、S-1/S-2。

### A-3: 未展开与历史保护
- 前置条件：初级当前卡历史藏在more，内有Senior Scientist。
- 操作步骤：启用标记→手动展开more→翻页观察新卡→取消标记。
- 预期结果：未展开黄色；历史高级出现后恢复无色；新卡无旧残留；取消后红黄框和提示消失；插件没有自动展开或点击获取邮箱。
- 覆盖：I-1/I-3/I-4、DOM更新交互。

### A-4: 缓存与导出
- 前置条件：已有名单，当前有红／黄卡且用户已显示完整邮箱，有只显示掩码卡。
- 操作步骤：采集→导出CSV→修改配置保存→再次导出。
- 预期结果：红黄不影响完整邮箱采集，多个完整邮箱保留，掩码不导出；不含标记提示；缓存不减少；原采集more范围不变；无自动获取邮箱、翻页、发送或ES写入。
- 覆盖：I-4、must-not-change。

### A-5: 配置编辑与视觉
- 前置条件：已保存v2，编辑区打开。
- 操作步骤：导出JSON→导入但不保存→输入非法版本保存→重新打开→观察并取消标记。
- 预期结果：导入仅填框；非法输入不覆盖；重新打开读有效配置；红黄如S-2，弹窗布局如S-1；专家数不变。
- 覆盖：I-2/I-3、S-1/S-2。

## 自查与交接

10个实施文件，单一本地扩展；规则store仅新增classification字段，无专家字段新增。阶段T-1至T-3覆盖I-1至I-5及S-1/S-2。用户已批准三态改动；执行以本文件哈希绑定，不借用旧计划的未批准单态方案。执行自测不是独立verify-p或用户Dia验收。
