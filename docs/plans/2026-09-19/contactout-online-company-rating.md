# ContactOut 自动联网企业评分

授权：用户要求按已讨论方案修改插件。工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`，不提交、不部署线上。缺服务凭据不妨碍实现和离线联调；交付注明真实联网状态。

## 需求描述
插件弹窗启用自动评分后，从 ContactOut 企业筛选读取名称，查询联网服务，展示证据、分数与找人门槛，并用于同段任职标记。企业评分按名称、规则版本缓存30天，无需逐企业编辑JSON。弹窗打开期间检测筛选变化；关闭期间不自动操作页面。
保留邮箱采集、more范围、三份已有storage与手动配置；不自动获取邮箱、不发信、不改ES。不调用聊天会话或使用网页登录凭据。

## 关键不变量
### I-1 联网与证据
- 服务仅监听127.0.0.1:8766；服务端环境变量保存Gemini密钥；查询仅发送企业名称。Google搜索返回实际groundingChunks/supports后，第二步提取结构化事实；引用须指向已检索来源。缺证据、重名、子公司关系不明确均review，不推断为0分。
- 官方REST契约：https://ai.google.dev/gemini-api/docs/generate-content/google-search 。固定上游HTTPS地址，不接受客户端指定抓取URL。
### I-2 通用算分
- 规则版本online-v1。IMF发达经济体20、其他10；内部产业薪酬代理分类：semiconductors/ai/software/biopharma/quantum=20，advanced_materials/automation/aerospace=10，other=0。不是个人薪酬事实。
- 重点产业：图片列明方向枚举命中60；仅四大领域明确40；明确其他0；unknown=null并待核实。五百强有同一实体、近年度官方证据才直通100/L1；其他按80/L2、60/L3、其余/L4。
- 不按模型直接输出分数；本地确定性计算。同段任职沿用core。不把子公司自动当母公司，评分只添加本次查询精确名称。
### I-3 缓存和失败
- 新storage键`contactout-online-rating-v1`保存连接token、enabled与查询缓存；旧三键不写。缓存最多200项、30天、规则版本隔离；review短缓存10分钟，失败不变为低分。强制刷新允许重查。
- 自动模式只用新鲜联网结果生成临时enterpriseConfig，过期/失败结果不回退旧手动高分。手动模式继续现有行为。（来源：K-browser-extension-config-default-is-not-saved-state）
### I-4 请求与页面
- 单次最多5家公司串行，服务合并同名进行中请求、全局并发1、队列上限5；429/失败不自动重试。切换企业/页面时旧结果可缓存但不应用到新页面。只从筛选区域读公司，不拿人才卡或Job title当筛选。
- 无法识别时明确提示填写公司名；显式“联网评分并标记”可用手动名称。自动模式用v3默认职级，保存的旧岗位规则保留。
### I-5 服务访问
- 扩展只新增`http://127.0.0.1/*` host权限。服务要求启动时随机生成的连接token、自定义请求头、严格Host/Origin/JSON/体积/名称校验；token不进入页面注入参数，不进入导出。无平台接口、无浏览器邮箱上传。

## 样式契约
### S-1
- 复用popup.css第1行`.actions/.hint/.card`与第2行textarea，CSS不改。
- popup在rules-panel前新增details#online-panel，summary“自动企业评分”，checkbox#online-enabled；textarea#online-token连接码、textarea#online-company识别失败时输入；`.actions`两个button#online-save（保存连接）/#online-refresh（重新联网查询）；p#online-status.hint与div#online-results。新增外部research.js脚本位于popup.js前。
- 来源只创建a和textContent，href仅http(s)。不插入模型HTML。搜索建议用无脚本、无same-origin权限sandbox iframe显示服务返回的搜索组件。

## 现状审计
- popup.js专家KEY只由capture写入、clear删除；岗位RULES_KEY由saveRules写入；企业ENTERPRISES_KEY由saveEnterprises写入。init读取三键，mark/save会注入两配置。新增联网缓存独立读写。
- collector.js profileCardsForReview/visibleJobs读取可见任职，markContactOutIgnored接收岗位+企业；企业筛选读取能力尚无。采集前半段禁止扩大点击。
- 无现成联网搜索后端；HttpLlmDraftClient仅聊天接口。实现独立零依赖Node侧服务，不扩大现有认证/邮件代码。
- popup 460px、padding20、按钮圆角8、紫色#5437cb；新区域复用控件，不改CSS。真实DOM测试验证元素，防stub掩盖缺失（来源：K-dom-stub-tests-hide-dangling-refs）。

## 实现方案
- T1/I1,I2,I5：research.js统一枚举、事实校验、确定性评分、缓存有效期；rating-server.cjs实现Google联网、提取、鉴权、队列和缓存。
- T2/I3,I4/S1：collector读取筛选公司；popup接线、显示评分证据、单请求与过期隔离、自动模式标记。
- T3/I1-I5：research.test.cjs使用假提供商+真实HTTP/DOM测试，现有core/browser/popup回归；README说明启动、连接和缺凭据边界；版本1.5.0打包。

## 变更文件清单
路径均在tools/contactout-visible-export，最后一项除外：
1. research.js
2. rating-server.cjs
3. tests/research.test.cjs
4. collector.js
5. popup.js
6. popup.html
7. manifest.json
8. README.md
9. tests/popup.test.cjs（必要的新增脚本/控件回归兼容）
10. outputs/contactout-visible-export/contactout-visible-export-1.5.0.zip

## 验收标准
- I1/I2：无grounding、伪造来源、unknown、母子实体不确定不评分；四档门槛和优先产业0/40/60可重现。
- I3/I4：TTL/规则版本隔离，陈旧响应不标记新页，专家缓存原样；新公司自动查询、同公司缓存复用、多公司上限；无平台点击。
- I5：错误token/Origin/超长body拒绝，服务缺密钥明确503，不输出密钥。
- S1：真实HTML含新增id，原CSS逐字不变，检查截图。
- 命令：node --test tools/contactout-visible-export/tests/core.test.cjs tools/contactout-visible-export/tests/research.test.cjs；用既有PLAYWRIGHT_MODULE/TEST_BROWSER_PATH运行browser.test.cjs和popup.test.cjs；node --check；ZIP比对；git diff --check。

## 人工验收清单
- A1：Node22+设置GEMINI_API_KEY启动rating-server.cjs，复制启动显示连接码到插件保存；ContactOut选择Henkel后打开弹窗，出现来源/各项分/门槛并自动标记。覆盖I1-I5/S1。
- A2：同企业重新打开复用缓存，换企业重新查询；点击刷新重查。资料未匹配仍黄，历史段只用各自公司。覆盖I2-I4。
- A3：输入重名/不存在公司、停服务或错误token，显示待核实/具体错误，不套用旧100分。覆盖I1/I3/I5。
- A4：关闭自动模式恢复手动配置；更新前后专家人数/邮箱数不变；采集仍只展开已显示邮箱的more，清空专家不删除规则。覆盖I3/I4。
- A5：紫色460px弹窗控件可读，来源可点击，联网过程中翻页/切公司不应用旧结果。覆盖S1/I4。

## 实施边界
联网模式需要可用Gemini API凭据；当前环境未检测到凭据。完成实现与离线端到端验证后，如仍无凭据，明确交付为待真实联网联调，不声称已上线。
