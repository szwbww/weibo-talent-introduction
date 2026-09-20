# ContactOut 直连企业评分 API

授权：用户明确接受 API 密钥存入 Dia 扩展本地存储，要求取消本机服务。工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不提交、不部署、不发真实付费请求。

## 需求描述

扩展弹窗直接调用 Tavily 搜索和 DeepSeek Chat Completions，以企业名取得可核验评分，不再要求 `node rating-server.cjs`、端口8766或连接码。密钥仅用于这两个 HTTPS 域名，保存于 `chrome.storage.local`，页面明确提示本地明文存储风险。

不变：评分算法、来源证据校验、30天缓存、手动企业库、专家名单、邮箱采集、more点击范围、ES/邮件流程。

范围外：加密浏览器本地存储、密钥同步、后台代理、真实密钥/真实 API 调用、删除旧开发目录里的服务脚本。

## 关键不变量

### Invariant I-1: 直连权限最小化
- Rule: Manifest 仅允许 `https://api.tavily.com/*`、`https://api.deepseek.com/*` 两个评分上游，移除 localhost host permission；请求只发送企业名称、Tavily 返回的公开来源和模型提取要求，绝不发送专家、邮箱、ContactOut cookie 或页面原文。
- Applies to: `manifest.json`、`popup.js`。
- Violation consequence: 扩展有额外网络能力或泄露人才数据。
- 来源: original。

### Invariant I-2: 密钥存储及泄露边界
- Rule: `DEEPSEEK_API_KEY`、`TAVILY_API_KEY` 仅保存在 `chrome.storage.local` 的独立 online 配置，使用 password input 录入；不出现在导出、来源、状态、异常、console、页面注入参数或 ZIP。初次保存明确显示“本机明文存储”。
- Applies to: `popup.html`、`popup.js`、`README.md`。
- Violation consequence: 密钥被误显示、导出或发送至 ContactOut。
- 来源: original。

### Invariant I-3: 可核验评分不降级
- Rule: 先 Tavily Search，后 DeepSeek；每个非 null fact 的 evidence 至少8字符且存在于 cited Tavily source excerpt，之后只由 `research.assess` 算分。搜索/模型失败、429、无来源、无效 JSON、证据不匹配均 review/错误，不以模型常识补分。
- Applies to: `popup.js`、`research.js`。
- Violation consequence: 企业评分不可追溯或错误变低分。
- 来源: original。

### Invariant I-4: 迁移隔离
- Rule: online ruleVersion 提升为 `online-v3-direct`；旧 token 不再被读取或保存，旧结果不 fresh。清空专家仍只删除专家键，绝不删除密钥/评分缓存/规则。
- Applies to: `research.js`、`popup.js`、现有 popup 测试。
- Violation consequence: 把旧服务结果冒充直连结果，或清空名单时丢失密钥。
- 来源: original。

## 样式契约

### S-1: 直连密钥区
- 复用：`.actions`（`popup.css:1`）和 `textarea`（`popup.css:2`）不修改。
- 新增：在 `popup.css` 原样追加：
```css
#online-panel{border:1px solid #d9d5e7;border-radius:8px;padding:10px;margin-bottom:16px}#online-panel summary{cursor:pointer;color:#43319d;font-weight:600}#online-panel label{display:block;margin:8px 0 6px}#online-panel input[type="password"]{width:100%;font:12px/1.5 ui-monospace,monospace;border:1px solid #d9d5e7;border-radius:6px;padding:8px;background:white;color:#25233d}
```
- DOM结构：`details#online-panel > label[for=online-deepseek-key] > input#online-deepseek-key[type=password]`；Tavily 同构；其后为 `.hint` 风险文案、公司 textarea、现有按钮/状态/结果。
- 禁止项：inline style、未声明新 class、改动 `.actions`/`textarea`。

## 现状审计

### online 评分配置
- Schema/mapping: `contactout-online-rating-v1` 当前为 `{enabled, token, cache}`；弹窗读取、`saveOnlineState`写入、`research.fresh`读取 cache；专家键、岗位键、手动企业键分离。
- Write paths:
  1. `popup.js:saveOnlineState` 更新 cache。
  2. `popup.js:online-save` 更新 enabled/token。
  3. `popup.js` 初始化迁移读取 online 配置。
- Read paths:
  1. `popup.js:runOnline/detectOnlineCompany` 决定自动查询与临时企业配置。
  2. `research.js:enterpriseConfig` 消费 fresh rated cache。
- Interaction points: key字段替换必须同时覆盖保存、初始化、是否可自动检测、错误文案和 popup 测试；专家清空路径保持无关。

### 上游查询
- Schema/mapping: `rating-server.cjs` 当前 Tavily→DeepSeek、验证 evidence→source excerpt 后调用 `research.assess`；`popup.js` 当前只向 localhost 传 company + token。
- Write paths: 新 `popup.js` 直接请求两上游，结果仅进入 existing online cache。
- Read paths: `displayRatings`显示 title/url；`applyOnlineSnapshot`仅读临时 enterpriseConfig。
- Interaction points: 把请求移入 popup 后仍不能信任模型数值、仍不允许失败回退陈旧结果。

## 实现方案

1. I-1、I-2、I-3、I-4/S-1：改 `popup.html`、`popup.css`、`popup.js` 为双 password key 设置与直连查询；删除 token/localhost流程，实施 Tavily→DeepSeek、严格证据验证、无密钥/限流的非泄露错误，保存明确风险提示。
2. I-3、I-4：改 `research.js` 为 `online-v3-direct`；改 `manifest.json` 为两外网权限并升版 1.5.2；改 `README.md` 为直连设置与风险/限额说明。
3. I-1-I4/S-1：改 `tests/research.test.cjs` 的 popup 网络模拟与 `tests/popup.test.cjs` 的存储隔离断言；生成不含 `rating-server.cjs` 的 1.5.2 ZIP。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `tools/contactout-visible-export/popup.html` | 直连密钥输入及风险提示 |
| `tools/contactout-visible-export/popup.css` | S-1 样式 |
| `tools/contactout-visible-export/popup.js` | 直连请求、存储迁移、错误边界 |
| `tools/contactout-visible-export/research.js` | `online-v3-direct` 隔离 |
| `tools/contactout-visible-export/manifest.json` | 最小外网 host permission 与 1.5.2 |
| `tools/contactout-visible-export/README.md` | 直连使用说明 |
| `tools/contactout-visible-export/tests/research.test.cjs` | 直连请求/证据/缓存回归 |
| `tools/contactout-visible-export/tests/popup.test.cjs` | 密钥配置与专家清空隔离 |
| `outputs/contactout-visible-export/contactout-visible-export-1.5.2.zip` | 直连安装包 |

## 验收标准

- I-1：manifest 仅为两个上游域名；模拟请求正文不含专家、邮箱、ContactOut URL；先 Tavily 后 DeepSeek。
- I-2：password input；导出与状态无密钥；专家清空不删 online 配置。
- I-3：无来源、伪 evidence、无效 JSON、429 不能产生 rated score；四档分数仍由 research 确定性计算。
- I-4：v2 result 不 fresh，v3可 fresh；旧 token被清除且不影响名单/规则。
- S-1：CSS 逐字匹配、DOM id/type匹配、现有 `.actions`和`textarea`不变。
- 命令：全部四个 Node test；两 JS `node --check`；`git diff --check`；ZIP 文件清单不含 `rating-server.cjs`。

## 人工验收清单

### A-1: 直连查询
- 前置条件: 1.5.2 已加载；在扩展输入两项有效 API key。
- 操作步骤: 1. 勾选自动评分。2. 保存。3. 在 ContactOut 选择 Henkel 并点击重新联网查询。
- 预期结果: 无需终端和连接码；显示企业评分及来源链接。
- 覆盖: I-1、I-3。

### A-2: 密钥与名单隔离
- 前置条件: 已保存直连密钥，且本地有一位已采集专家。
- 操作步骤: 1. 点击一次清空、再点击确认。2. 重新打开扩展。
- 预期结果: 专家数为0；两个 password 输入仍有保存值且不会明文显示；评分缓存/规则仍存在。
- 覆盖: I-2、I-4。
