# ContactOut 企业核查改用 DeepSeek

授权：用户明确要求将现有 Gemini 查询服务改为 DeepSeek。工作树为 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不提交、不部署、不调用真实付费 API。

## 需求描述

企业评分本机服务改用 DeepSeek 负责基于已检索网页证据的结构化事实提取；网页检索改由 Tavily 完成。插件、分数、职级门槛、企业缓存和邮件/ES流程均保持原状。未配置两项服务端密钥或证据不足时必须返回待核实，绝不以模型常识补分。

不变：只查询企业名称；服务仅监听回环地址；插件不上传专家资料/邮箱，不点击 ContactOut 邮箱按钮，不发送邮件。

范围外：真实 API 调用、购买额度、修改 ContactOut 页面逻辑、改动邮箱采集或线上发送。

## 关键不变量

### Invariant I-1: 检索与模型职责分离
- Rule: Tavily `POST /search` 只接收企业核查查询并返回的公开结果；DeepSeek Chat Completions 只接收这些结果以提取事实 JSON。没有有效 URL、网页片段或确切实体证据，一律 `review`。
- Applies to: `rating-server.cjs:deepseekGroundedResearch`。
- Violation consequence: 模型可能把不可复核的常识错误写成企业评分。
- 来源: original。

### Invariant I-2: 可核验事实不变
- Rule: 每个非 null 事实必须带至少一个零起点来源索引；其 evidence 至少八字符，且逐字存在于该来源的 Tavily `content`。世界五百强仍须近两年 Fortune 官方 URL。评分仍只能由 `research.assess` 确定性计算。
- Applies to: `rating-server.cjs`、`research.js`。
- Violation consequence: 来源链接不能支持得分，或上游模型直接操纵分数。
- 来源: original。

### Invariant I-3: 缓存隔离
- Rule: 联网规则版本升级为 `online-v2-deepseek`，旧 Gemini 结果不会在新提供商下被当作新鲜评分；仍保持 30 天上限、年界、200项上限、review 10分钟和失败刷新清除旧结果。
- Applies to: `research.js`、`popup.js` 经已有 `fresh` 读取路径、`rating-server.cjs:createLookup`。
- Violation consequence: 用户看到标签为 DeepSeek、实际来自旧 Gemini 的缓存。
- 来源: original。

### Invariant I-4: 凭据与失败边界
- Rule: 只从服务进程环境读取 `DEEPSEEK_API_KEY` 与 `TAVILY_API_KEY`；二者永不写入扩展、日志、HTTP 返回或 ZIP。429 无自动重试；上游其他失败返回通用可操作错误。
- Applies to: `rating-server.cjs`、`README.md`。
- Violation consequence: 密钥泄露或查询风暴。
- 来源: original。

## 现状审计

### 企业联网评分缓存
- Schema/mapping: 浏览器 local storage 的 `contactout-online-rating-v1`；值含 `enabled`、本机连接 token、以规范化企业名为键的 `cache`。结果新鲜性由 `research.js:fresh` 的 `RULE_VERSION`、`checkedAtMs`、`expiresAt` 决定。
- Write paths:
  1. `popup.js` 的联网查询成功/失败路径写入或删除对应缓存条目。
  2. `rating-server.cjs:createLookup` 是服务进程内临时缓存写入路径。
- Read paths:
  1. `popup.js` 读取缓存后调用 `research.fresh`，再生成临时企业配置并标记当前页。
  2. `research.js:enterpriseConfig` 只消费 fresh 且 rated 的结果。
- Interaction points: 规则版本变化必须同时让弹窗缓存与服务内缓存失效，且不触碰旧手动企业配置。

### 联网服务
- Schema/mapping: `rating-server.cjs:groundedResearch` 当前使用 Gemini Google Search 两次调用，输出 `{ facts, sources }` 交给 `research.assess`；`createRatingServer` 仅暴露受 token 保护的 `POST /rate`。
- Write paths: `groundedResearch` 输出给 `createLookup`，后者进入内存 Map。
- Read paths: HTTP `/rate` 返回结果给 `popup.js`；测试从 `rating-server.cjs` 导入 `groundedResearch`。
- Interaction points: 上游替换必须保持 `/rate`、错误状态、来源数组、缓存合并及请求验证不变。

## 实现方案

1. I-1、I-2、I-4：在 `rating-server.cjs` 以 `deepseekGroundedResearch` 替代 Gemini 实现：先调用 Tavily Search，限制结果数与请求超时；把结果的 URL/title/content 作为唯一资料传入 DeepSeek `chat/completions`，要求 JSON；验证每条证据在来源片段中；默认 lookup 使用新函数。保留回环 HTTP、队列、token/Origin/体积校验。修改 `tests/research.test.cjs`，以假 fetch 验证端点、认证头不泄露、请求顺序、无搜索结果 review、伪证据 review、429 不重试。
2. I-3：在 `research.js` 将规则版本提升为 `online-v2-deepseek`，只改变版本隔离而不改既有分数表或门槛。测试旧版本失效、新版本可 fresh。
3. I-4：在 `README.md` 更新为 DeepSeek + Tavily 配置、启动命令、两个密钥各自用途及不能把密钥填入插件。将 `manifest.json` 版本升为 1.5.1，生成仅含运行文件的 1.5.1 ZIP。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `tools/contactout-visible-export/rating-server.cjs` | DeepSeek + Tavily 检索/提取服务 |
| `tools/contactout-visible-export/research.js` | 提供商隔离的规则版本 |
| `tools/contactout-visible-export/tests/research.test.cjs` | 联网服务和缓存回归测试 |
| `tools/contactout-visible-export/README.md` | 配置与使用说明 |
| `tools/contactout-visible-export/manifest.json` | 版本 1.5.1 |
| `outputs/contactout-visible-export/contactout-visible-export-1.5.1.zip` | 可安装包 |

## 验收标准

- I-1：测试断言搜索发生在模型提取前；无搜索结果不评分；模型未收到浏览器数据。
- I-2：缺片段、来源索引非法、证据不在片段、Fortune 非官方 URL 都为 review；既有各评分档位不变。
- I-3：`online-v1` 缓存不能 fresh；`online-v2-deepseek` 正常受 TTL/年界约束。
- I-4：缺任一密钥明确 503；429 单次返回，无重试；测试输出不含密钥。
- 回归命令：`node --test tools/contactout-visible-export/tests/core.test.cjs tools/contactout-visible-export/tests/research.test.cjs tools/contactout-visible-export/tests/browser.test.cjs tools/contactout-visible-export/tests/popup.test.cjs`；`node --check` 两个修改 JS；`git diff --check`；ZIP 内容仅运行文件和文档。

## 人工验收清单

### A-1: 首次 DeepSeek 联网评分
- 前置条件: 解压 1.5.1，服务端仅设置有效 `DEEPSEEK_API_KEY` 与 `TAVILY_API_KEY`。
- 操作步骤: 1. 运行 `node rating-server.cjs`。2. 将输出的连接码填入插件并保存。3. 在 ContactOut 选择明确企业后打开插件。
- 预期结果: 弹窗显示该企业、可点击来源、分数及 L1-L4门槛；终端不显示 API 密钥。
- 覆盖: I-1、I-2、I-4。

### A-2: 旧缓存隔离与既有功能回归
- 前置条件: 浏览器留有 1.5.0 联网缓存及已有专家名单。
- 操作步骤: 1. 升级覆盖并重新加载扩展。2. 打开同一企业。3. 关闭自动评分，采集一张已显示邮箱的资料卡并导出。
- 预期结果: 企业重新查询或明确待核实，旧 Gemini 分数不显示为缓存；名单人数不丢失；导出仍只有完整可见邮箱。
- 覆盖: I-3、I-4、需求描述不变项。
