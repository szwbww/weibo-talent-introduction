# ContactOut 未证实五百强默认非五百强

## 需求描述

联网评分在没有 Fortune Global 500 正向证据时，仍按非五百强、五百强分为 0 继续计算国家和产业分，并明确展示“低置信”。

不得把没有 Fortune 证据的企业判为世界五百强；不得放宽企业身份、国家或产业的来源核验；不得改变专家采集、邮箱读取、翻页或发送行为。范围外：增加新的搜索提供商、人工企业库、改变分数档位。

## 关键不变量

### Invariant I-1: 五百强只允许正向直通

- Rule: 仅 `global500.value === true` 且其来源是有效、当年或上一年 Fortune 官方 Global 500 证据时，企业得 100 分并走 L1；否则一律按 `global500:false`、五百强项 0 分继续计算。
- Applies to: `research.js:assess` 及其由弹窗直连和旧本机服务调用的评分路径。
- Violation consequence: 未核验企业会被错误放宽到普通 Engineer/Scientist 门槛。
- 来源: original

### Invariant I-2: 非五百强默认不等于完整评分

- Rule: 默认非五百强后，country、developedEconomy、sector、priority 仍必须有有效来源和合法值；任一缺失仍返回 `review`，不得写入企业评分配置。
- Applies to: `research.js:assess`、`enterpriseConfig`。
- Violation consequence: 低置信五百强状态会掩盖其他关键事实缺失。
- 来源: original

### Invariant I-3: 可见低置信说明与缓存隔离

- Rule: 非五百强默认结果的 reason 必须包含“世界五百强未证实，按非五百强 0 分（低置信）”；规则版本升级，旧缓存不可复用。
- Applies to: `research.js:assess`、`fresh`、弹窗已保存的在线缓存。
- Violation consequence: 用户无法识别推定项，或继续使用旧的待核实缓存。
- 来源: original

## 现状审计

### 联网企业评分缓存 `contactout-online-rating-v1`

- Schema/mapping: 浏览器 local storage 中保存 `enabled`、API key、cache；缓存由 `research.fresh` 的 `ruleVersion` 与过期时间读取。
- Write paths:
  1. `tools/contactout-visible-export/popup.js:researchCompanyDirect/runOnline` — 直连 Tavily/DeepSeek 后写入 `online.cache`。
  2. `tools/contactout-visible-export/rating-server.cjs:deepseekGroundedResearch` — 旧可选本机服务调用同一 `research.assess`，无独立规则实现。
- Read paths:
  1. `tools/contactout-visible-export/popup.js:onlineEnterprises` — 只将 fresh 且 rated 的记录转换为企业门槛配置。
  2. `tools/contactout-visible-export/research.js:enterpriseConfig` — 筛选 rated/fresh 企业记录。
- Interaction points: `assess` 的 rated/review 状态直接决定 popup 是否把 Henkel 写入临时企业门槛并重标当前页。

### 当前规则事实

- `research.js:24-43` 强制 `global500`、`global500Year` 有来源，并对 true/false 都要求 Fortune 域名，因此缺少正向证据的 Henkel 返回 review。
- `popup.js:47-73` 要求模型对未知字段返回 null，随后直接传给 `research.assess`；无须增加网络调用。
- `rating-server.cjs:52` 复用 `research.assess`，无需单独改动。

## 实现方案

1. 修改 `tools/contactout-visible-export/research.js`，遵守 I-1/I-2/I-3：提高 `RULE_VERSION`；仅在 `global500:true` 时验证年份和 Fortune 官方来源；其它 global500 值（包括缺失/null/false）默认 false，仍核验四个非五百强评分事实；在评分原因中写入固定低置信说明。
2. 修改 `tools/contactout-visible-export/tests/research.test.cjs`，遵守 I-1/I-2/I-3：覆盖缺失、null、无 Fortune 来源的 false 均可评分且包含低置信说明；保留并验证 true 但无 Fortune 证据仍 review，以及产业事实缺失仍 review。
3. 修改 `tools/contactout-visible-export/README.md`：说明五百强正向证明要求、未证实的默认 0 分与低置信含义；升级包版本为 1.5.4。
4. 修改 `tools/contactout-visible-export/manifest.json`：仅将扩展版本升到 1.5.4，以允许覆盖原目录后重新加载，且不改权限。
5. 生成 `outputs/contactout-visible-export/contactout-visible-export-1.5.4.zip`，只放当前扩展运行所需九个文件。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `tools/contactout-visible-export/research.js` | 非五百强默认与缓存版本 |
| `tools/contactout-visible-export/tests/research.test.cjs` | 规则回归测试 |
| `tools/contactout-visible-export/README.md` | 用户行为说明与版本号 |
| `tools/contactout-visible-export/manifest.json` | 版本 1.5.4 |
| `outputs/contactout-visible-export/contactout-visible-export-1.5.4.zip` | 发布包 |

## 验收标准

- I-1: `global500:true` 无 Fortune 官方来源仍是 review；缺失/null/false 不是五百强且不会得到 L1/100。
- I-2: 默认非五百强时 country、developedEconomy、sector、priority 任一缺失仍是 review，rated 记录可通过企业配置校验。
- I-3: 结果含固定低置信说明，旧 `online-v3-direct` 结果不再 fresh。
- 运行 `node --test tools/contactout-visible-export/tests/research.test.cjs`；再运行全部三个 ContactOut 浏览器/弹窗/研究测试。
- 解压清单与 manifest 均为 1.5.4；host permissions 不变。

## 人工验收清单

### A-1: Henkel 无五百强正向来源

- 前置条件: ContactOut 企业筛选为 Henkel，插件已填入可用 DeepSeek 和 Tavily Key。
- 操作步骤: 1. 打开插件。2. 点“重新联网查询”。3. 等待“已核查”。4. 点“标记职级（红／黄）”。
- 预期结果: Henkel 评分卡显示具体分数与 L 档，原因含“世界五百强未证实，按非五百强 0 分（低置信）”；不再显示“企业评分未录入”。
- 覆盖: I-1、I-2、I-3。

### A-2: 有 Fortune 正向证据的企业

- 前置条件: 使用能被 Fortune Global 500 官方页面明确证明的企业筛选。
- 操作步骤: 打开插件，点“重新联网查询”。
- 预期结果: 企业显示 100 分、L1，原因含“世界五百强直通”；未点击 View email、View phone 或翻页。
- 覆盖: I-1、需求描述“不得改变采集行为”。

### A-3: 非五百强但产业资料缺失

- 前置条件: 使用搜索结果无法支持国家或产业分类的企业。
- 操作步骤: 打开插件，点“重新联网查询”。
- 预期结果: 状态仍为“待核实”，不产生分数卡对应的企业门槛，不因默认非五百强而通过。
- 覆盖: I-2。
