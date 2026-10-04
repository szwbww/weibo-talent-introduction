# 收发件箱三态标记：开发主计划

日期：2026-10-04。状态：待审阅，尚未实施正式功能。使用 create-p。
代码基线：`e28e53fd898edd62905a0d45a6bf90396b18b1bf`；逐文件 SHA256 见 [source-manifest.json](mailbox-progress-evidence/source-manifest.json)。证据来自当前工作区源码，不把独立预览的内存模拟当成后端能力。

## 需求描述

1. 原“关注”改为“跟进中”，新增“已提供”。顺序固定：**全部 / 已提供 / 跟进中 / 待处理 / 已挂起 / 已回复 / 待匹配**。
2. 每张已关联专家卡片右上角显示当前状态文字按钮，提供三态控制并真实持久化；详情中没有重复入口，没有开关、星标、勾号、额外箭头或撤销按钮。
3. 菜单目标文案为“跟进中”“已提供”，不出现“进入跟进”“进入提供”；取消仍叫“取消跟进”“取消提供”。
4. 七个 Tab 及现有待处理/已挂起计数完整显示，窄屏允许自然换行，不依赖横向滚动，不缩小字体隐藏问题。

| 当前状态 | 菜单第一项 | 菜单第二项 |
|---|---|---|
| 未标记 | 跟进中 | 已提供 |
| 跟进中 | 取消跟进 | 已提供 |
| 已提供 | 跟进中 | 取消提供 |

必须保留：当前登录用户归属；原关注迁移；搜索/账号/日期/标签过滤、服务端分页排序；挂起、消息处理与人工回复；已回复的真实来信/发件/移出水位规则；待匹配队列；手机列表/详情导航与草稿。

范围外：批量标记、状态日志、新权限、新表、新 ES 字段、自动判断“已提供”、根据材料或邮件自动转换、通知、撤销、批量发信规则调整。**已提供是人工标记，不表示系统已经上传/发送材料。**

## 关键不变量

### Invariant I-1: 一个存储事实
- Rule：沿用 `expert_follow` 的用户+专家唯一行；新增唯一字段 `progress_status`，行值 FOLLOWING/PROVIDED；无行=NONE。跟进和提供不可同时为真。
- Applies to：迁移、现有 follow 接口、新状态接口、列表与挂起读取。
- Violation consequence：取消后丢入口、两队列同时命中、旧用户数据丢失。
- 来源：original；`V121`、`ExpertFollowService` 实证。

### Invariant I-2: 业务边界与列表一致性
- Rule：只写标记表；所有列表筛选在 SQL 聚合分页前执行，取消仅删除当前用户标记。已回复继续排除本用户存在标记行的专家，因此跟进中、已提供均不在已回复；取消后还必须满足原有已回复条件。
- Applies to：状态写入、count/page/explain、挂起返回归类。
- Violation consequence：伪造已回复、总数与分页不一致、串用户。
- 来源：K-mailbox-replied-membership、K-group-before-pagination。

### Invariant I-3: 真实页面完整闭环
- Rule：全部中的未标记专家始终有入口；六条状态转换均可达。修改保留当前页签和筛选；不符合当前筛选的行消失。状态页空页向前回退，不能因切换另一行而串换当前详情。
- Applies to：前端卡片、写入回包、列表重查、选中项协调。
- Violation consequence：只有取消没有进入，或详情标题和正文属于不同专家。
- 来源：original，现有 `refreshListWithFallback` / `resolveFocusAndSelection`。

## 样式契约

权威逐字契约在 [前端计划 S-1～S-4](mailbox-progress-02-frontend.md#样式契约)。保留蓝色 `#1e40af` 状态按钮、白色实底菜单；不复制预览整套页面皮肤。保留正式页已有完整换行规则，见 `styles.css:12648`；是否完整以真实 DOM 几何验证，不以截图看似齐全或单条 CSS 判断。

## 现状审计

- 现有 DB 是 `expert_follow(username, expert_contact_id, created_at)`，唯一生产写者为 `ExpertFollowService.setFollowed`，不是 `expert_contact` 或 ES。详见后端审计及 [完整 grep](mailbox-progress-evidence/grep-audit.txt)。
- `MailboxConversationRepository` 有 page、explain 两份 followed 投影、followed 筛选及已回复排除；`MailboxSuspensionService` 另有一次存在性读取。只改按钮或列表 SQL 会漏掉挂起结束后的归类。
- `styles.css:12648` 的 `#view-mailbox.mc-refined .mail-chat .mc-filters` 比 `12654` 的 selector 多一个 class，**前者优先级更高**，不能错误认定后置 nowrap 覆盖前者。预览没有正式页 `#view-mailbox.mc-refined` 宿主，不能据预览推断正式 CSS 的最终值。
- `mailbox-chat.css` 被字节锁定测试覆盖；新增业务类应放 `styles.css`。11个资源共用缓存键，当前键在 `src/test` 精确反查0命中。（来源：K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad）
- 基线样式测试33项通过：`node --test src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js`。未运行数据库写测试，未验证尚未实施的新接口。

## 实现方案

按顺序执行，不把16个实施文件交给一个无边界任务：

1. [01 后端与迁移](mailbox-progress-01-backend.md)：10个文件，一个邮件数据/API子系统。可独立提供三态API并保留旧接口；新增 API 未被调用时，旧数据和旧页面行为保持兼容。
2. [02 前端与验收](mailbox-progress-02-frontend.md)：6个文件，一个前端子系统。依赖01通过，接入真实接口。每个子计划独立验证，不并行改同一契约。

接口契约：`PUT /api/mail/mailbox/conversations/{id}/progress-status`，JSON `{ "status": "NONE|FOLLOWING|PROVIDED" }`；返回 `{ "contactId": id, "progressStatus": "...", "followed": boolean }`。列表新增 `providedOnly=true`；保留 `followed=true` 表示跟进中。列表/挂起响应补 `progressStatus`，`followed` 始终等于 FOLLOWING。

编号协调：当前实体迁移最高V147；另一个已存在计划 `discovery-review-02-review.md:47,63` 已预留V148。01暂定V149，**发布前必须核清V148的交付顺序或完成双方编号修订**；不打开Flyway outOfOrder，不覆盖任何迁移。此为明确的并行编号条件，不假称V148已经上线。

## 变更文件清单

本主计划仅为调度与契约索引，不直接执行源码修改。

| 子计划 | 精确实施文件数 | 独立子系统 | 门禁 |
|---|---:|---|---|
| 01 | 10 | MySQL标记/API/查询 | 迁移与真实MySQL测试 |
| 02 | 6 | 收发件箱前端 | JS行为、样式、浏览器与人工验收 |

所有实施路径以各子计划清单为唯一授权范围。本文、两个子计划和证据属于规划产物，不计入子计划实施文件数。执行前如代码基线变化，先修订受影响清单与证据。

## 验收标准

- I-1：01迁移前后原关注数/用户名/专家/created_at一致；六条转换与跨用户隔离真实落库。
- I-2：01覆盖分页、计数、explain、已回复排除和挂起返回；02消费同一状态字段。
- I-3：02验证全部页入口、取消后恢复未标记、修改另一行不串详情、失败与慢回包。
- S-1～S-4：执行02逐字CSS检查及320/393/760/1024/1440/1920px真实布局检查。
- 完成判定：两个子计划的机器门禁通过，再按各自人工清单验收；不能用独立线上预览代替正式功能验收。

## 人工验收清单

### A-1: 六条转换与持久化
- 前置条件：01/02部署到隔离验收环境，准备一位有真实往来邮件的测试专家及两个登录用户。
- 操作步骤：在全部页依次操作 未标记→跟进中→已提供→未标记→已提供→跟进中→未标记；每步刷新页面；换用户检查。
- 预期结果：状态依次准确持久化；任一步最多命中一个状态页；另一个用户保持原状态；没有“进入跟进/进入提供”文案。
- 覆盖：I-1/I-2/I-3、需求1～3。

### A-2: 全量界面与既有流程
- 前置条件：使用02 A-1～A-7给出的数据和窗口宽度；至少一位带待处理/挂起和手工草稿的专家。
- 操作步骤：完成02全部人工场景，尤其最窄窗口、分页末行、切换另一专家状态、挂起结束、未匹配与草稿恢复。
- 预期结果：七个Tab完整显示，窄屏自然换行；原流程和隔离规则符合各场景实值，不发额外邮件、不修改专家资料。
- 覆盖：全部must-NOT-change、S-1～S-4、跨子计划接口。

人工验收开始时才从子计划的权威清单导出对应 `-acceptance.md`；此阶段不生成双份清单。

## 规划自查记录

- 2026-10-04完成范围与结构检查：[plan-self-review.json](mailbox-progress-evidence/plan-self-review.json)。01为10文件/6条不变量/4条人工场景；02为6文件/6条不变量/4条样式契约/7条人工场景。
- 原表仅加1列；完整生产读写grep、源码哈希、DOM/CSS逐字基线已留存。复核源码哈希未变化，本次未实施或部署正式功能。
- 已纠正列表fetchList失败返回null的语义，前端T-2明确失败与过期结果短路；Tab优先级和正式宿主依赖按源码证据描述。
- 知识回写：K-expert-follow-existence-readers、K-mailbox-tabs-root-specificity；使用过的知识计数已更新，本轮无条目新跨越10次推广阈值，未发现需要合并的5条同题知识。
- 尚待实施后验证：真实MySQL迁移/持久化、新状态API、真实浏览器布局；现有33项基线通过不能替代这些验收。
