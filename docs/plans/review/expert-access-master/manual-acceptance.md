# Manual Acceptance — docs/plans/2026-10-29/expert-access-master.md

## Epoch 1 — 2026-10-30T23:24:44.872Z

- Reviewed code boundary: 59eb60cf00534a342c1019827a62f2b7b1db0f2b..39f38a6676c4d4d0fdb7cb679326834b4a17e97f
- Governing master identity: commit 04ba02e9f971cdebe26d8f37dd4de13a771eae93; SHA256 c1ca47e495a4f91959e3b467bd8f7b755b5003d7eb22d30308a7916df70e0638
- Machine report epoch: 1
- Status: PENDING
- Source: exact master A-1–A-15; machine evidence does not satisfy any human item.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---|---|---|---|---|---|---|
| A-1 | YES | READ标签可见且不可写<br>前置：测试库有授权READ专家甲，ES标签“新发现”“瑞典”，一封来信带QA标签“薪资”和CUSTOM标签“补充材料”。<br>1. user1登录，选甲。<br>2. 查看列表/头部与来信。<br>3. 用同会话请求新增/删除专家标签和邮件标签。 | 上述四个标签出现在对应区域；无新增/×控件；两类写接口403，admin刷新标签未变；无附件下载按钮。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-2 | YES | 等级切换与旧授权兼容<br>前置：升级前已有甲READ授权；管理员与user1各开一浏览器。<br>1. admin选甲的权限select为可写。<br>2. 刷新user1。<br>3. 发送一次旧无body PUT，再刷新。<br>4. 非admin请求授权接口。 | 旧授权初始READ；设置后FULL；旧PUT后仍FULL；user1改授权403。加载中select禁用，保存失败恢复上一个确认值。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-3 | YES | 新旧待处理分开<br>前置：甲有2封旧MANUAL_REVIEW归admin；乙有1封归admin。用测试IMAP/测试入口导入，不向真实专家发送。<br>1. admin授予甲FULL。<br>2. 给甲新增3封测试来信并重复补收其中1封。<br>3. 两账号看待处理与全部。 | admin待处理仍3封；user1为3封；admin All可看甲全部5封；重复补收不增行/不改归属；待处理专家数admin=2、user1=1。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-4 | YES | 降级、撤权和撤销完成<br>前置：甲FULL且有2封归user1的未处理、1封已完成；留一封历史admin待处理。<br>1. admin把甲降READ。<br>2. 刷新两端；admin撤销那封已完成邮件的完成状态。<br>3. 再升FULL。 | 降级后user1待处理0，admin新增2；重开再增1；旧admin待办始终保留；再升FULL不把已有admin待办迁给user1。READ仍能读甲邮件和标签。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-5 | YES | 同专家直接发送及越权拒绝<br>前置：甲FULL、乙READ、丙未授权；测试SMTP收集器启用；准备各自附件/排期/来信id。<br>1. user1对甲写人工回复并显式发送。<br>2. 把请求中的来信/附件/锚点/排期id分别换成乙或丙。<br>3. 对乙尝试发送。 | 甲收到一次测试发送记录；审计user1；越权请求403或既有资源不匹配错误，无新增SMTP/文件内容；乙没有发送能力。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-6 | YES | 三种附件归属与材料操作<br>前置：甲FULL，准备record-owner/processing-owner/manual-upload-owner各一附件；丙未授权，准备其附件。<br>1. user1从甲材料drawer预览/下载三类文件，上传一个测试附件。<br>2. 尝试丙文件直链、把甲path搭配丙attachmentId。<br>3. 降READ后刷新并重试甲直链。 | FULL甲三类原件可读；丙和错配不返回文件字节；降READ后甲直链403、附件投影为空；上传本身不产生SMTP。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-7 | YES | 资料、标签、排期管理<br>前置：甲FULL且有currentIndexLevel和有效ORCID；丙未授权；甲人工档案version已记录。<br>1. user1修改甲人工资料与标签，新增/改期/取消一个测试排期。<br>2. admin回读。<br>3. 用旧version保存，换丙expertId或scheduleId请求。 | admin看到同一甲资料/标签/排期；旧version409；跨专家无修改；普通保存/改期/取消不发送邮件；user1仍只有收发件箱tab。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-8 | YES | 原更多筛选完整保留<br>前置：有不同账号、标签、日期和方向的测试邮件，甲FULL；至少一份SUBMITTED审核草稿。<br>1. admin点搜索右侧⋯。<br>2. 核对专家筛选位于最上。<br>3. 选择user1可写并叠加账号/方向/标签/邮箱/关键词/日期。<br>4. 应用、Reset、切tab再返回。 | 原七字段均存在；Apply只有一个；筛选AND并回第1页；Reset恢复全部专家范围并保留原tab/q语义；刷新旁无新下拉。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-9 | YES | 角标不是筛选数或草稿数<br>前置：admin有3封未挂起待处理邮件、2位待处理专家、5份待审草稿；user1有1封待处理。<br>1. admin看⋯角标，user1看待处理入口。<br>2. admin设置两个普通筛选，切页。<br>3. admin处理完3封后刷新。 | admin图标3；user1待处理显示1位专家；admin待处理tab2、待审选项5条；设置两个筛选后图标仍3；清零后角标隐藏。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-10 | YES | 待审核筛选与同框审核<br>前置：已有草稿审核计划落地；甲有2份SUBMITTED、乙1份；admin有未发个人草稿。<br>1. admin在更多筛选选待审核并应用。<br>2. 选甲、选择一份用户草稿，翻译并批准。<br>3. 导入一封甲新来信，再点击发送。 | 左侧甲/乙各标2/1条；右侧对应专家，只有一个编辑框；批准不发送；对话更新先确认且仍需手动发送；原admin个人草稿不被覆盖。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-11 | YES | FULL升级不解锁旧审核稿<br>前置：甲READ，有UNSUBMITTED/SUBMITTED/RETURNED/APPROVED审核稿各一份。<br>1. admin升级甲FULL。<br>2. user1打开旧APPROVED稿尝试改删。<br>3. 新建人工稿并在测试SMTP显式发送。 | APPROVED仍不可改删；SUBMITTED仍待审，不自动发送；FULL新人工稿可以直接发送；降READ后旧人工稿不可再通过发送API绕过审核。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-12 | YES | 晚响应与撤权清理<br>前置：甲FULL、乙READ；浏览器网络工具延迟甲消息/权限/保存回包。<br>1. user1打开甲后立即切乙。<br>2. admin撤销甲授权，释放延迟响应。<br>3. user1刷新、切后台再回来、注销重登。 | 乙不出现甲正文/附件/发送按钮；甲在重新校验后消失；注销后无正文；不请求邮箱配置/任务列表；无恢复旧权限。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-13 | YES | 管理员与自动流程不回归<br>前置：admin现有人工草稿/挂起/筛选与测试自动收信可用；准备禁用真实邮箱和SIMULATOR。<br>1. admin按原流程保存并发测试草稿、查看附件、筛选挂起。<br>2. 执行测试收信、自动标签、UID补收。<br>3. 查看全局来信汇总及个人待处理。 | 旧人工草稿/幂等/附件保持；自动流程不因无user1 session失败；禁用真实邮箱邮件仍可见、SIMULATOR不计；全局汇总保留所有人，个人待处理分归属。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-14 | YES | UI尺寸及空错状态<br>前置：桌面1440px和手机390px；准备长姓名、长标签、无标签和模拟接口失败。<br>1. 对照本计划S-1–S-4观察。<br>2. 打开popover，切权限，模拟失败与无标签。 | popover桌面430px、手机不溢出；首项select桌面34px、手机沿既有min-height:44px；header权限select桌面最大180px、手机沿既有max-width:100%且不溢出；角标18px；无大权限卡；null标签显示“标签暂不可用”，[]不显示；失败不伪造授权成功。 | PENDING | 尚未收到人工结果 | N/A | N/A |
| A-15 | YES | 并发归属不被旧保存覆盖<br>前置：测试库准备甲FULL、user1待处理及可重开的完成行；用测试屏障延迟状态更新事务。<br>1. 并发执行甲降级与标完成/绑定/重开/自动响应重分类。<br>2. 查看两账号待处理，重试失败事务。 | 无owner被陈旧save恢复user1；每封尚未完成且已失权邮件都能在admin待处理找到；另一人仍有pending时专家needsManualAttention保持true。 | PENDING | 尚未收到人工结果 | N/A | N/A |

## Human Sign-off
- Decision: PENDING
- Boundary: 39f38a6676c4d4d0fdb7cb679326834b4a17e97f
- Accepted master identity: PENDING (governing commit 04ba02e9f971cdebe26d8f37dd4de13a771eae93; SHA256 c1ca47e495a4f91959e3b467bd8f7b755b5003d7eb22d30308a7916df70e0638)
- Reporter: N/A
- Timestamp: N/A
- Note: PENDING; human results must name each item, verdict and evidence/note, then explicitly accept this exact code boundary and governing master identity. No merge, push or deployment is authorized.
