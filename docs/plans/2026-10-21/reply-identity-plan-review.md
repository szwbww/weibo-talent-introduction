# 称呼与签名计划自审记录

2026-10-21；create-p计划阶段自审。未实现、未部署；未运行新功能测试。本记录不代表fix-v通过或人工验收完成。

## 结构检查回执

执行 `/tmp/check_reply_identity_plans.py`，检查顶层章节顺序、实施路径存在/明确新增、单阶段≤10文件、编号连续、人工条目前置/动作/实值/覆盖字段、相对文档链接及证据源SHA256。

|子计划|实施文件数|I条数|S条数|A条数|
|---|---:|---:|---:|---:|
|reply-identity-03-global-settings.md|5|4|0|4|
|reply-identity-01-backend.md|10|6|0|7|
|reply-identity-02-frontend.md|5|6|4|10|

回执：PASS；不同实施路径20个；代码证据源24个文件指纹与采集时一致。上述数量由文档清单解析得到，不由通读估算。03/01/02分别1/1/2个独立子系统；新增持久字段0。

## 关键自审结论

- 03必须覆盖旧create/update/setEnabled/setDefault/delete的读改写事务起点；只给新增PUT加锁的初稿已修正。旧无版本表单仍last-write-wins，未承诺不可能实现的保护。
- getIdentityDefaults单查询快照；新inspect不用跨多查询的defaultSelectableFrame拼期望值。默认字段沿用现有数据，不新增双事实源。
- 新设置不得通过通用update清空变体。按ID引用模板也读新主内容，已写入设置说明、交互点和A-2。
- GlobalExceptionHandler的catch-all已取证；新409明确局部handler+HTTP测试，未假设抛异常即得到正确状态码。
- 01明确两MIME、最终渲染顺序、NORMAL软确认、SENT早退、真实锚点、引用尾部核实。有限人名/HTML识别无法确定时UNCERTAIN，不猜测自动补全。
- buildVariables可能创建unsubscribe_token已取证；只读inspect显式传unsubscribeEmail=null，引用该变量时报告不可完整预览；不改变正式发送原有token逻辑。
- 02两个发送入口都列出；ESC增加cancel/close生命周期、只resolve一次；取消不经过失败alert/成功清稿分支。
- 设置未保存内容使用独立defaults-preview，避免名为实时预览实际只渲染旧默认；迟到回包按dialog/edit序号及会话身份隔离。
- 前端所有新增DOM映射S-1–S-4；新增CSS逐字块；旧规则不改。已修正局部padding11px、非primary无shadow的级联事实；完整旧规则/DOM在E14–E16/E26。
- 状态分支、共享读写交互和保持项均安排机器验证及人工清单。人工验收导出文件尚不生成。

## 需求与验收映射

|要求/交互|阶段权威清单|
|---|---|
|所有专家同默认、可改选择与内容|03 A-1/A-3；02 A-7/A-8|
|保留变体/其他字段、共享模板影响|03 A-2/A-4|
|补全缺项、不覆盖已有、重复点击不重复|01 A-6/A-7；02 A-1/A-2|
|软确认/取消/ESC/原STRONG|01 A-1/A-4/A-5；02 A-5/A-6/A-10|
|真实账号/锚点、引用、两个MIME|01 A-2/A-3；02 A-3|
|草稿、富文本、附件、会议、QA/RAG、幂等|01 A-5；02 A-3/A-10|
|跨专家/登录人/异步结果隔离|02 A-4/A-8|
|设置失败/缺默认/移动布局/焦点|02 A-6/A-9|
|只读接口及未保存内容预览|01 A-6；02 A-8|

## 知识加载及回写

已复核并计数：K-reply-identity-followup-quote-boundary、K-manual-send-fingerprint-complete-identity、K-mailbox-draft-cache-owner-capture、K-plan-quantified-claims-need-grep-receipts、K-manual-frame-three-consumers、K-selectable-reply-frame-server-resolved-snapshot、K-frontend-cache-key-triad、K-explicit-outbound-anchor-server-revalidation。均已更新last_used；本次匹配条目不做过期归档。

修正K-manual-frame-three-consumers的旧前端结论并更新created；K-mailbox-draft-cache-owner-capture达到10次后补充CLAUDE.md一行指针；新增K-variable-preview-can-write-unsubscribe-token及K-snippet-config-narrow-write-and-lock-boundary。未进行无关知识域的合并或归档；本次新主题不满足5条重叠合并条件。项目未发现agents角色文件/共享模板，无新增级联目标。

## 实施时必须实际验证

真实MySQL事务/空表/并发写行为；含全局advice的HTTP状态；JDK解析器对富文本结构的识别；浏览器Range插入保留节点；新旧入口ESC；真实SMTP测试桩和草稿状态回归。当前只有方案与源码证据，不把设计推演当运行证明。若源码SHA或接口签名已变化，先重查证据再实施。
