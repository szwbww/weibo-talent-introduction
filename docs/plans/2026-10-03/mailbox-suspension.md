# 收发件箱挂起功能 — 开发总计划

> 2026-10-03 v6：已同步用户确认的中性灰预览、可选挂起原因、三类行内确认及当前登录身份。旧弹窗方案已从执行契约中移除；正式功能尚未实施。

状态：计划已更新；用户已确认v6视觉与交互，仅计划与独立前端预览已完成，正式功能未实施。日期：2026-10-03。

## 需求描述

1. 待处理专家可挂起，原因选填；列表和详情均可取消。
2. Tab 顺序固定：**全部 / 关注 / 待处理 / 已挂起 / 已回复 / 待匹配**。
3. 普通首次进入：有可处理、未挂起的专家选“待处理”；没有则选“关注”。
4. 挂起中的全部消息处理完，**提示是否结束挂起**。提示行在最后一条处理完成消息下方；选择“继续挂起”不结束、不移走。只有明确点击“结束挂起”或“取消挂起”才提交取消。
5. 取消后有待处理→待处理；无待处理→沿用现有归类。用户已明确：**保留“已回复排除已关注专家”的现有规则**。已关注专家在关注查看；不强制塞进已回复，不改变关注。
6. 移除列表和详情挂起操作按钮前的暂停图标；状态说明区域可保留。
7. 原因在挂起按钮对应卡片/详情头下方行内填写；消息“待处理”点击后原位出现“取消 / 确认”，确认成功才变“已处理”。不弹框、不输入操作人，后端使用当前Session账号权限。
8. 挂起色固定中性灰#64748b，提示底#f1f5f9；灰色描边取消按钮；已挂起选中Tab文字#475569/下划线#64748b。关注保持现有黄色，确认按钮蓝色#3762d8。

必须保持：收发信、人工处理、撤销处理、关注、移出已回复、排期、材料、标签、地理时间、最新回复时间、筛选分页、会话草稿与待匹配流程。

范围外：自动结束任务/调度器、自动取消数据库状态、批量挂起、定时恢复、原因编辑/历史记录、通知发送、业务状态重构、ES 字段、正式发布。

最新用户澄清覆盖此前“自动结束”要求。不得按旧预览 v1/v2 或旧对话描述实现。

## 关键不变量

### Invariant I-1: 手动确认是唯一结束入口
- Rule: 挂起存在与 pendingCount 独立；0 条待处理仍可保持挂起。读取和处理邮件不删除挂起记录。
- Applies to: 子计划 01 所有 SQL/API、子计划 02 行内提示与刷新。
- Violation consequence: 未经确认丢失用户保留的会话。
- 来源: 用户最新澄清。

### Invariant I-2: 保留既有归类
- Rule: 挂起操作不改 expert_follow、expert_replied_dismissal、operatorStatus、currentStatus 或 needsManualAttention；原人工处理服务的既有副作用保留；已回复原来的关注排除、成功发件、来信水位条件继续生效。
- Applies to: 01 查询/写接口；02 取消后的导航。
- Violation consequence: 已关注专家错误进入已回复，或收发流程受影响。
- 来源: 用户澄清；K-inbound-processing-write-paths；K-mailbox-inbound-source-authority。

### Invariant I-3: 分片执行
- Rule: 按 01 → 01b → 02；01和01b可单独部署，旧页面不启用新功能；每片独立验证。不得将预览的内存数据适配器用于正式页。
- Applies to: 执行与验收。
- Violation consequence: 伪持久化、前后端协议不一致。
- 来源: create-p 范围约束。

## 现状审计

- 源码基线与工作区 SHA：[baseline-sha256.txt](mailbox-suspension-evidence/baseline-sha256.txt)。本次为工作区快照，不冒充干净 HEAD。
- 已有并行移动端改动触及 app.js/mailbox-chat.js/styles.css/index.html/mailboxChatStyle.test.js，v6当前有 `20261003-mobile-core-03` 与 mailbox-chat.js的 `20261003-mobile-core-03-generic-followup` 两组缓存键；实施时保留这些改动，不回退或替换整文件。
- 后端暂无 suspension 字段/API；当前线上预览通过 GET 读取真实会话，挂起和处理仅内存模拟。源码位置 `docs/mockups/mailbox-suspend-preview/preview.js`、生成器 `build_preview.py`。
- 精确证据与交互点逐项列入 01/02；原始 grep 见 [write-read-paths.txt](mailbox-suspension-evidence/write-read-paths.txt)、[frontend-usage.txt](mailbox-suspension-evidence/frontend-usage.txt)。
- 取消自动结束后，现有 markResolved 服务只须被查询观察，无需植入挂起副作用、额外行锁或重写事务。实际HTTP入口信任body身份，01b只收紧此入口身份并附加成功回包，不重写原服务。

## 实现方案

| 顺序 | 子计划 | 正式变更文件 | 子系统 | 独立交付 |
|---|---|---:|---|---|
| 01 | [状态存储、查询与接口](mailbox-suspension-01-backend.md) | 9 | 邮箱后端 | 持久化挂起及真实 API，旧页面继续使用 |
| 01b | [人工处理当前登录身份](mailbox-suspension-01b-processing-identity.md) | 2 | 邮箱后端 | 原处理入口Session身份，兼容旧请求 |
| 02 | [前端交互与完整 CSS](mailbox-suspension-02-frontend.md) | 6 | 邮箱前端 | 接入 01/01b；Tab、默认进入、原因、行内处理及结束提示 |

每片开始前核对证据文件哈希和准确调用位置；行号变化不等于业务变化。若出现新存储/写入口或超过该片文件清单，修订该片计划后再实施。完整 CSS 在 02 的 S-4 代码块中，原样复制。

## 变更文件清单

总计划为索引，不授权额外源码修改。三份子计划各自列明唯一允许修改的文件（均 ≤10），不得合并成一个无界任务。本轮产物为四份计划、证据附件及独立预览。

## 验收标准

- I-1：处理至零后，数据库挂起行仍在；继续挂起没有 DELETE；确认才发 DELETE。
- I-2：已关注样本结束后仍 followed=true，在关注可见、已回复不可见；旧水位隐藏规则不变。
- I-3：01与01b分别测试通过后执行 02；02 通过 CSS 字节契约与真实浏览器交互验证，再人工验收。
- 当前只验证了预览：顺序、原因可空/可填写、处理完提示、保留挂起、结束回关注、有待处理默认选待处理、没有可处理专家默认关注。未声称正式后端已实现或测试通过。

## 人工验收清单

### A-1: 完整业务闭环
- 前置条件: 按 01 A-1 建立测试会话，再打开 02 正式前端测试环境。
- 操作步骤: 1. 执行 01 全部 A 项。2. 执行01b全部A项。3. 按顺序执行 02 全部 A 项。
- 预期结果: 每项记录 PASS；0 条未处理时可选择继续挂起，已关注关系不变，全部六个 Tab 顺序精确一致。
- 覆盖: I-1/I-2/I-3，需求 1–8 与全部必须保持项。

### A-2: 对照线上预览
- 前置条件: 已登录当前线上系统。
- 操作步骤: 1. 打开[预览](http://150.158.92.103/talent/previews/mailbox-suspend-20261003/index.html)。2. 点击挂起，填写原因。3. 模拟标记最后一条已处理。4. 选择继续挂起。
- 预期结果: 显示“所有消息已处理，是否结束挂起？”；继续后仍在已挂起，原因仍显示。正式信箱原消息状态没有改变。
- 覆盖: I-1/I-3；预览与正式功能边界。

人工验收开始时再从每份计划清单导出同前缀 `-acceptance.md`，本轮不生成勾选副本。

### 计划自检记录

- 01为9文件、01b为2文件、02为6文件，各1子系统；新表仅原因一个业务载荷，既有共享表不加字段。
- 必填章节顺序校验通过；前端完整CSS与target-suspension.css字节一致；所有新DOM对应S-1至S-4，已替换旧dialog DOM/CSS；A项覆盖I项、交互点和旧行为。
- 读取的知识用于账号口径、入站权威、分页、处理写路径、缓存、CSS字节、DOM-stub、背景/文字、迁移顺序与守卫边界；行号守卫只作为不触碰专家状态路径的范围约束，无需改守卫测试。
- 新知识K-mailbox-replied-membership已写回；K-mailbox-inbound-source-authority本次hit_count达到10，已向CLAUDE.md晋升一行。其它已超过阈值的条目原有指针保留。相关知识没有五份同主题重复可合并项；未匹配需归档条目。项目没有agents/或templates/角色文件，未为级联另造目录。
- 源码证据和当前工作区并行变化分开留档。没有执行正式后端/前端实现、未发布正式WAR、未写生产业务数据。

### v6 本轮更新与证据

- 02 S-4已全文写入中性灰CSS；与[目标CSS](mailbox-suspension-evidence/target-suspension.css)字节一致。已固定[用户确认截图](mailbox-suspension-evidence/approved-slate-v6.png)及预览CSS/JS/生成器快照；旧v3样式仅历史取证，不是执行目标。
- 三类行内交互：挂起原因section、消息footer原位处理确认、归零消息footer后的结束提示；继续挂起仍保留原因与结束按钮，无自动取消。
- 当前代码证明原处理弹窗要求操作人，HTTP入口信任body；因此单列01b两文件修订，避免在01九文件基础上超出范围。证据：[源码摘录](mailbox-suspension-evidence/revision-v6-source.md)、[完整grep](mailbox-suspension-evidence/revision-v6-grep.txt)、[工作区哈希](mailbox-suspension-evidence/revision-v6-sha256.txt)。新回包只供本次处理标签，未设计历史身份回填或新存储。
- 更新仅写计划、证据与相关知识；未执行正式功能开发、数据库迁移或生产发布。01/01b/02验收需实施后执行。
