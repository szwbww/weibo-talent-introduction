# 03｜联系详情显示收发件箱备注

状态：待评审；依赖02；不新增表或写接口。

## 需求描述

在联系详情人工档案顶部显示当前contactId的收发件箱备注、修改人和时间；可手动刷新。无联系人显示“尚无收发件箱备注”。

保持：收发件箱编辑入口与2000字限制、按联系人归属、原档案编辑均不变。范围外：本页编辑备注、跨campaign合并备注、专家级备注副本、轮询。预览中的备注试填不作为正式编辑承诺；用户明确需求是“这里也要能看到”。

## 关键不变量

### Invariant I-1: 单一来源
- Rule: 仅GET /api/mail/contact-notes/{contactId}；本页无PUT、无localStorage副本、无专家级note列。真实contactId为空不请求、不新建联系人。
- Applies to: 加载/刷新/切换
- Violation consequence: 备注分叉
- 来源: K-bound-expert-cross-campaign-read

### Invariant I-2: 状态与错误
- Rule: 加载中/成功有内容/成功空内容/失败/无联系人五态区分；失败显示“备注加载失败，请重试”，不得显示无备注；成功空内容显示“暂无备注”。
- Applies to: 备注读取及重试
- Violation consequence: 把服务错误当作业务空值
- 来源: 本次需求与代码审计

### Invariant I-3: 隔离与编码
- Rule: 沿用02 generation+key+contactId；文本转义且换行可见。每次进入详情读取，手动刷新再读；不混合相同expertId不同contactId记录。
- Applies to: GET响应/渲染
- Violation consequence: 跨联系人泄露或XSS
- 来源: 本次需求与代码审计

## 样式契约

### S-1：只复用02样式

无新增CSS、不改共享class。复用02 S-1/S-2的ec-section/ec-heading/ec-text/ec-message/ec-error和styles.css:802按钮。插入到ec-profile最前面（nav之前）：

```html
<section class="ec-section" data-role="expert-shared-note">
 <div class="ec-heading"><h3>收发件箱备注</h3><button type="button" class="button" data-ec-refresh-note>刷新</button></div>
 <div class="ec-text" data-role="note-content"></div>
 <span class="ec-message" data-role="note-meta" role="status" aria-live="polite"></span>
 <span class="ec-error" role="alert" hidden></span>
</section>
```

无contact隐藏刷新按钮；加载中禁用刷新；无新图标、inline style、其它DOM或class。

## 现状审计

- [审计](contact-profile-audit.md) E-3：V150 note主键contactId；唯一应用写者 ExpertContactNoteService.save；空值DELETE、非空UPSERT；父contact删除外键CASCADE。完整读写命中`notes-paths.txt`。
- 当前 mailbox-chat.js GET/PUT都用contactId；同一专家不同campaign不是同一备注。新增读路径是当前详情宿主→同GET→同service.get。无schema变更。
- X1：收发件箱PUT→详情GET；X2：contact选择变化→读取哪个备注；X3：备注请求失败→档案/材料仍可用。
- 样式基线与完整规则继承02及审计E-6；已有页面没有备注DOM，本计划追加S-1，不替换旧卡片。

## 实现方案

1. `app.js` 在02共用挂载函数中独立加载备注，不加入使全部详情一起失败的Promise.all；I-1–I-3/S-1。
2. `app.js` 刷新触发一次GET，复用api()/现有鉴权；绑定当前contactId，仅更新该section；400/404显示不可读取而非清空；401走既有登录处理；失败重试沿用刷新按钮。I-1–I-3/S-1。
3. `index.html` 11项资源键同步为 `20261011-contact-profile-05`。`expertContactSharedNote.test.js` 验证I-1–I-3、S-1及下面交互。

## 变更文件清单

| 文件 | 操作 / 职责 |
|---|---|
| `src/main/resources/static/app.js` | 修改：共用详情内共享备注只读读取/刷新 |
| `src/main/resources/static/index.html` | 修改：11项资源缓存键同步 |
| `src/test/js/expertContactSharedNote.test.js` | 新建：备注归属、错误与隔离 |

共 **3 个文件**；未列文件禁止顺手修改。测试证据为执行产物，不扩张产品修改白名单。

## 验收标准

- I-1：网络只GET，0次PUT；无contact零请求；数据库无新表/列。
- I-2：五态逐一测试；备注失败不阻断四组编辑/旧材料。
- I-3：同expert不同contact、延迟返回、HTML字符串与换行；当前contactId必须同时进入守卫。
- S-1：DOM结构与02复用规则符合，无CSS更改，无inline style；缓存键11项统一。
- `node --test src/test/js/expertContactSharedNote.test.js src/test/js/expertManualProfile.test.js`；加浏览器人工检查。

## 人工验收清单

### A-1: 共享内容
- 前置条件: 测试联系人能进入收发件箱。
- 操作步骤: 1. 收发件箱备注保存“首次沟通\n等待企业反馈”。2. 进入该联系人联系详情。3. 收发件箱改为“已约会议”。4. 详情点刷新。
- 预期结果: 先显示两行原备注及修改人/时间，刷新后显示已约会议；详情没有独立保存按钮。
- 覆盖: I-1/I-3；X1；收发件箱编辑保持

### A-2: 无联系与不同campaign
- 前置条件: 测试环境同一expertId有两个campaign联系人，分别在收发件箱写“甲活动”“乙活动”；另选无contact专家。
- 操作步骤: 1. 从各联系人入口打开详情。2. 再打开无contact专家。
- 预期结果: 分别显示甲活动、乙活动；无contact显示尚无收发件箱备注，不请求contact-notes/null，不增加联系人。
- 覆盖: I-1/I-3；X2

### A-3: 独立失败与样式
- 前置条件: 浏览器阻止contact-notes请求。
- 操作步骤: 1. 打开联系详情。2. 编辑译名并保存。3. 解除阻止点刷新。4. 390px查看备注长文本。
- 预期结果: 错误为备注加载失败，请重试；译名仍可保存；刷新恢复；长文本换行，14px标题、12px元信息、白底，无横向溢出。
- 覆盖: I-2；X3；S-1；档案保持

## 修正记录

- 2026-10-12（fast-p amendment A4）：§实现方案 3 的缓存键字面量 `20261011-contact-profile-03` → `20261011-contact-profile-05`。原因：05 阶段键 bump 后，本计划的静态测试（从本文件解析期望键）失效，而该测试不在 05 授权内；依据 master「执行门槛与顺序」2「每阶段缓存键统一11项」，经人工批准以 R1 同一口径把 03 计划字面量同步到当前阶段键。

