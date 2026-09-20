# ContactOut 手动企业库自动分析

## 需求描述

手动企业库保存一次后，在 ContactOut 结果首次加载和每次翻页/结果替换后，扩展自动展开当前已加载资料卡的 `more` 并按已保存规则重标，不再要求打开弹窗或重复点击“保存并应用企业评分”。

不得点击 `View email`、`View phone`、AI 写信、分页或发送；不得自动采集邮箱；启用联网评分时不得让手动自动分析与联网结果竞争；企业评分不修改分数档位。

## 关键不变量

### Invariant I-1: 当前已评分企业优先

- Rule: 在 schema v3 企业分层中，已收录企业的低于门槛任职（`knownBelow`）优先于其它未收录技术任职的待核实；已有任职达到门槛或未收录 L4 的保留逻辑不变。
- Applies to: `core.js:classifyProfileJobs` 的 popup 手动标记、自动内容脚本和原有页面 MutationObserver。
- Violation consequence: Henkel 等已导入公司仍被不相关历史雇主覆盖为黄色。
- 来源: original

### Invariant I-2: 自动分析严格限于当前页资料详情

- Rule: 自动流程只调用现有 `expandContactOutReviewDetails` 和 `markContactOutIgnored`；仅点击严格匹配 `more / Show more` 的当前已加载资料卡控件，不点邮箱/电话/AI/翻页，不采集或写专家名单。
- Applies to: 新内容脚本、`collector.js` 展开器。
- Violation consequence: 越权触发 ContactOut 操作或产生发送/采集副作用。
- 来源: original

### Invariant I-3: 已保存手动配置是自动分析唯一来源

- Rule: 自动流程每次从 `chrome.storage.local` 读取已保存的岗位规则和企业库；仅当两者均可校验且 `online.enabled !== true` 时运行。导入但未保存的编辑框数据不得参与。
- Applies to: 新内容脚本、popup 保存路径。
- Violation consequence: 用户必须重复保存，或手动库与联网缓存混用。
- 来源: original

### Invariant I-4: SPA 结果变化只触发一次稳定分析

- Rule: 结果 DOM 变化和存储变更采用去抖；扩展自身展开/标记产生的 DOM 变动必须抑制回调，避免循环。内容脚本初次加载及每次稳定的外部结果变更均重新分析当前已加载卡片。
- Applies to: 新内容脚本。
- Violation consequence: 翻页漏分析、重复点击 more 或持续 CPU/网络活动。
- 来源: original

## 现状审计

### 手动企业评分库 `contactout-enterprise-ratings-v1`

- Schema: `schemaVersion:1`，企业字段经 `core.js:validateEnterpriseConfig` 规范化。
- Write paths: `popup.js:saveEnterprises` 保存编辑框；导入按钮只填编辑框。
- Read paths: `popup.js` 初始化后赋给 `enterpriseConfig`；`collector.js:markContactOutIgnored` 传入后交给 `core.js:classifyProfileJobs`。
- Interaction: 现有 popup 关闭后没有内容脚本读取该库，因此翻页不会自动分析。

### 企业分层分类

- `core.js:classifyProfileJobs` 当前将 `unknownTechnical` 置于 `knownBelow` 前返回，导致某段未收录历史技术任职覆盖 Henkel 的已收录低职级结论。
- `collector.js:expandContactOutReviewDetails` 已提供当前已加载资料卡、严格 more 控件、每张卡 80ms 间隔和展开确认；不需要复制邮箱逻辑。

### 前端样式盘点

- 不新增 DOM/UI/CSS；复用 `collector.js:markContactOutIgnored` 的现有红黄标记样式。内容脚本仅调用既有函数。

## 实现方案

1. 修改 `tools/contactout-visible-export/core.js`，遵守 I-1：在不存在保留结论后，先返回 `knownBelow`，再返回 `unknownTechnical`；添加回归用例证明 Henkel 当前低职级不被无关未收录历史职级覆盖。
2. 新建 `tools/contactout-visible-export/autopilot.js`，遵守 I-2/I-3/I-4：在 ContactOut 页面读保存的规则/企业库；联网模式启用时停止；页面稳定后先展开再标记；监听 `storage.onChanged` 和 DOM 结果变化；抑制自身写入引发的循环。
3. 修改 `tools/contactout-visible-export/manifest.json`：注册 `core.js`、`collector.js`、`autopilot.js` 为仅 ContactOut 页面运行的内容脚本，并将 ContactOut 精确 host match 添加到 host permissions；升级版本 1.5.5。不得扩大到其它站点。
4. 修改 `tools/contactout-visible-export/tests/core.test.cjs` 与 `tests/browser.test.cjs`，覆盖 I-1 以及初始分析、模拟分页结果替换、只点击 more、在线模式不运行、保存配置变化重跑。
5. 修改 README，说明保存一次、关闭联网评分、刷新现有 ContactOut 标签后自动运行；升级包版本为 1.5.5。打包只包含扩展运行文件和新 `autopilot.js`。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `tools/contactout-visible-export/core.js` | 企业/未知历史任职优先级 |
| `tools/contactout-visible-export/autopilot.js` | 新增当前页自动展开与标记 |
| `tools/contactout-visible-export/manifest.json` | ContactOut 内容脚本、精确权限、版本 |
| `tools/contactout-visible-export/tests/core.test.cjs` | 优先级回归 |
| `tools/contactout-visible-export/tests/browser.test.cjs` | 自动内容脚本回归 |
| `tools/contactout-visible-export/README.md` | 使用与版本说明 |
| `outputs/contactout-visible-export/contactout-visible-export-1.5.5.zip` | 发布包 |

## 验收标准

- I-1: 已保存 Henkel 30 分/L4 下，当前 `R&D Scientist at Henkel` 加历史未收录 `Senior R&D Scientist` 返回红色 Henkel L1<L4；未收录 L4 和达标已收录岗位仍保留。
- I-2: 自动测试只记录 `more` 点击，邮箱/电话/AI/分页点击次数为零；专家本地名单不写入。
- I-3: 首次加载读取已保存 Henkel 库，无需 popup 保存按钮；`online.enabled:true` 时无自动标记。
- I-4: 模拟替换 25 张资料卡后自动展开并重标一次，无重复循环。
- 运行完整 `browser.test.cjs`、`core.test.cjs`、`popup.test.cjs`、`research.test.cjs`；检查 manifest 仅包含 ContactOut 与既有两条 API 域权限。

## 人工验收清单

### A-1: 保存后的 Henkel 当前页

- 前置条件: 手动企业库已保存 Henkel，自动企业评分关闭。
- 操作步骤: 刷新 ContactOut 的 Henkel 结果页，保持页面 5 秒。
- 预期结果: 资料卡 more 自动展开；当前 `R&D Scientist at Henkel` 显示红色“Henkel 30分，岗位 L1 低于 L4”，不显示“企业未收录”。
- 覆盖: I-1、I-2、I-3。

### A-2: 翻页

- 前置条件: 同 A-1，当前为第一页。
- 操作步骤: 手动点 ContactOut 下一页，等待页面结果稳定。
- 预期结果: 新页资料卡自动展开 more 并出现红黄标记；未自动点击 View email、View phone、AI 写信或下一页。
- 覆盖: I-2、I-4。

### A-3: 联网模式隔离

- 前置条件: 已保存手动企业库。
- 操作步骤: 打开插件，勾选自动企业评分并保存；翻页。
- 预期结果: 自动手动标记不运行；联网功能仍仅在打开插件后触发。
- 覆盖: I-3。
