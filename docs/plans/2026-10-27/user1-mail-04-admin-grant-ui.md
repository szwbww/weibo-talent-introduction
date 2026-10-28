# 04：管理员在专家会话头部授权

状态：待审阅。依赖：01–03通过。后续：05。子系统：管理员邮件前端（1）；实施文件3个。

## 需求描述

admin在当前专家会话头部点击“授权 user1 查看”或“取消 user1 授权”，立即看到服务端结果。保留原材料/管理/排期/回复/附件/草稿入口；不做用户管理页面、批量授权或自定义角色，不给user1新增操作。

## 关键不变量

### Invariant I-1: 授权对象与操作者不能错位
- Rule: 只对instance.user精确admin渲染此入口；目标contactId捕获于本次点击，固定target=user1；不能读取按钮请求后的当前选中ID再写，不能由DOM传入actor绕过后端。
- Applies to: renderHeader、授权GET/PUT/DELETE及事件处理。
- Violation consequence: 授权错专家或出现自授权入口。
- 来源: K-mailbox-draft-cache-owner-capture。

### Invariant I-2: 服务端状态为准
- Rule: 首次选择专家GET状态；加载/写入时禁用按钮。PUT/DELETE成功才显示返回的canRead，未成功不乐观翻转；响应username/contactId/canRead必须匹配预期，否则错误状态。读取失败不默认为已授权或未授权，显示重试。
- Applies to: header授权状态、写入完成/错误分支。
- Violation consequence: UI声称撤销但数据库仍允许，或错误授予。
- 来源: original。

### Invariant I-3: 跨会话/专家异步结果隔离
- Rule: 每次换专家、unmount、身份变化作废读请求代次；响应只有实例/用户/contact/generation匹配才写当前DOM。写请求可以完成点击时明确指定的旧专家授权，但不得回写到新专家UI；回到旧专家重新GET。
- Applies to: selectExpert、renderHeader、applyOptions/unmount、grant回调。
- Violation consequence: 快切专家时按钮状态与实际权限错位。
- 来源: K-mailbox-draft-cache-owner-capture；K-master-plan-shared-file-sequential-gates。

### Invariant I-4: admin原功能与reader隔离
- Rule: 不替换原header actions，不删原data-action；新操作只调用01授权接口，不改关注/挂起/邮件/草稿。reader继续03独立模块，无grant请求。index版本统一更新为`20261027-user1-mail-access`。
- Applies to: header DOM追加、click分派、新状态、index资源。
- Violation consequence: 新授权入口影响发信或旧浏览器继续用无入口版本。
- 来源: K-ui-removal-retires-obsolete-contract-tests；K-frontend-cache-key-triad。

## 样式契约

### S-1: 在当前专家头部追加按钮
- 复用：现有mailbox-chat.js:3635–3647的`.mc-actions`；其布局mailbox-chat.css:38为flex/wrap/gap8；普通按钮styles.css:802–837，mc范围disabled/focus见65–68；`.mc-badge`最终规则:29、105；完整CSS与现有header DOM原文见证据14。
- 新增CSS：**无**。所有既有class使用点见16，规则均不修改。不要新增模态框/inline style/图标/自造颜色。
- 在原actions最后追加以下DOM（原材料、管理、排期按钮全部保留）：

```html
<button class="button" type="button" data-action="mc-toggle-user1-read-access" data-contact-id="当前专家ID" disabled>授权状态加载中…</button>
<span class="mc-badge" data-role="mc-user1-read-access-status" aria-live="polite">user1：读取中</span>
```

动态固定文案：

|状态|按钮|徽标|可点击|
|---|---|---|---|
|加载|授权状态加载中…|user1：读取中|否|
|canRead=false|授权 user1 查看|user1：未授权|是|
|canRead=true|取消 user1 授权|user1：可查看|是|
|保存|保存中…|保留上次已确认文字|否|
|读取/写入失败|重试授权状态|user1：授权操作失败，请重试|是，先GET|

失败后重试仅GET重新对账，不自动重发上次PUT/DELETE。按钮状态只改变textContent/disabled/aria-busy，目标ID数据转义；新增两个节点及它们各状态均归S-1。

## 现状审计

共同审计前端章节及证据04/09/14/16为本节基线。`mailbox-chat.js:3621–3654` renderHeader会重建actions.innerHTML，原有材料/管理/排期按钮；`:3460`选择专家推进seq/convEpoch；`:9720`unmount销毁实例。新状态必须参与这些生命周期，不能只在一个按钮点击里缓存。

存储：本步新增实例内存状态`readAccess(contactId,phase,canRead,generation)`，写者为选择专家初始化/授权回调/销毁；读者为header渲染与click分派。无本地持久化、无module跨用户授权Map。数据库写入口全部在01Service，本步只消费接口。交叉点：前端点击→01数据库写→02下一请求授权范围；页面重渲染→内存确认状态；切专家→异步结果失效。

此前grant表无查询入口的事实见06；这一步依赖01已部署API，不猜测现有conversation DTO已经带canRead。每次选中只读一个专家，不做每列表行N次查询或新增批量接口。

## 实现方案

1. **状态生命周期（I-1/I-2/I-3，S-1）**：在mailbox-chat.js实例对象中新增readAccess；selectExpert确认新contact时置loading并获取状态。renderHeader仅读当前状态，不在每次render里重复触发请求；完成后只更新当前两个授权节点。选中同专家的显式refresh可重新GET，但禁止render→GET→render循环。
2. **UI与事件（I-1/I-2/I-4，S-1）**：admin的actions原DOM末尾追加S-1；click handler捕获actor/contact/seq，在禁用/加载时直接返回。未授权PUT、已授权DELETE；失败仅允许GET重试。`hostApi()`沿用带session的宿主；不传body用户/角色。响应按I-2校验。
3. **竞态（I-3，S-1）**：切专家/unmount中取消GET或作废generation；写请求不靠DOM变化重新确定目标。写成功但当前专家已变时不更新新header；不调用旧状态渲染旧专家。恢复时GET最新状态。
4. **版本与测试（I-4，S-1）**：index所有已有本地?v=统一更新`20261027-user1-mail-access`，保留03新增脚本和顺序。新增mailboxMailReadAccess.test.js覆盖真实生成DOM片段/点击网络/异步顺序；原admin行为测试无需删改。

## 变更文件清单

|#|路径|动作|
|---|---|---|
|1|src/main/resources/static/mailbox-chat.js|admin授权状态/按钮/事件|
|2|src/main/resources/static/index.html|统一缓存版本|
|3|src/test/js/mailboxMailReadAccess.test.js|新增授权行为/样式契约测试|

## 验收标准

- I-1：admin当前专家A点击时URL固定A；user1或未知身份无按钮/无grant请求；伪造按钮不改变后端admin校验。
- I-2：GET false→PUT成功→true→DELETE成功→false；双击只有一笔在途写；GET/PUT/DELETE失败或异常响应形状进入重试，不能误显示成功；重试仅GET。
- I-3：A慢GET→选B，晚A不改B；A发PUT后选B，PUT仍只写A，B显示自己的状态；unmount后响应不能新建节点；回A重新GET最新状态。
- I-4：原materials/manage/schedule等action节点存在且原click路径保留；授权不触发draft flush/发信；03reader网络仍无grant；index版本一致。
- S-1：class/层级/固定文案符合契约；没有新CSS/inline style；disabled opacity .45，焦点outline2px #3b82f6；1440px/390px换行不遮挡原按钮。
- 命令：`node --check src/main/resources/static/mailbox-chat.js`；`node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxServerDrafts.test.js`。与03共享index需串行，不覆盖03更改。

## 人工验收清单

### A-1: UI授予和撤销闭环
- 前置条件: admin已登录，A有往来邮件、user1无授权；另一浏览器使用reader测试session。
- 操作步骤: 1. admin打开A。2. 点“授权 user1 查看”。3. reader刷新。4. admin点“取消 user1 授权”。5. reader再次刷新。
- 预期结果: admin徽标依次“未授权→可查看→未授权”；reader列表“无A→有A→无A”，正文撤销后不能新请求成功；按钮原有样式，原材料/管理按钮仍在。
- 覆盖: I-1/I-2/I-4；S-1；grant写→列表/详情读。

### A-2: 快切与失败
- 前置条件: A/B均有邮件；浏览器限速，能暂时断网。
- 操作步骤: 1. 打开A立即切B。2. 在A点授权后立即切B。3. 断网点B授权，再恢复网络点重试。
- 预期结果: A回包不覆盖B；写入目标仍是A；失败文字明确、按钮不谎报已授权；重试先读取实际状态。
- 覆盖: I-1/I-2/I-3；S-1。

### A-3: 原管理行为与手机布局
- 前置条件: admin具有受控测试草稿/附件，390px窗口。
- 操作步骤: 1. 打开专家头部。2. Tab键遍历按钮。3. 保存草稿、查看材料、发送受控邮件。4. 换reader。
- 预期结果: 新按钮与旧按钮自然换行，无重叠；焦点可见；原管理操作仍可用；reader无授权/材料/发送操作。
- 覆盖: I-4；S-1。
