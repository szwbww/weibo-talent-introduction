# 计划创建自检（非实施验证）

日期：2026-10-27；结论：计划结构检查通过，尚未实施、尚未运行开发测试、尚未上线。

## 结构与范围

- 五个子计划均按要求顺序包含需求、不变量、审计、方案、精确文件清单、机器验收与人工清单；03/04另有样式契约。
- 每个子计划≤10文件、≤2子系统；计数10/6/7/3/4，去重28，包含测试。脚本校验见20。
- 只有一张新授权关系表；已有共享表不加字段；身份能力为派生响应，不做泛化RBAC。
- 新写者：01 grant服务、05 user1 bootstrap；字段/状态约束有对应不变量。已有账号登录时间写入继续沿用原服务。
- 共享AuthController/index的依赖、串行门禁和最后开放账号已明确；回滚必须失效session。
- 所有新增DOM都映射03 S-1/S-2/S-3或04 S-1；新增CSS全文固定；现有CSS不改，已留原文与覆盖链。
- 人工清单覆盖默认空授权、授权/撤销、直接越权、附件、身份切换、桌面/手机、admin和后台回归。
- 未提前生成任何-acceptance.md衍生清单。

## 事实复核与纠正

- 方法实际名为explainConversationsPage，已按源码修正。
- V121联系人FK没有CASCADE；新表CASCADE明确为设计，未伪称沿用既有行为。
- 07只查expert目录，已补15查campaign/discovery；删除材料/别名命中不能算删除联系人。
- grep的匹配行数不是接口/写路径数量；419是MVC映射匹配行数，证据限定范围。
- AuthFlowIntegrationTest需migration-it并启动MySQL Testcontainer；会话Controller/Calendar/DraftSend测试需mysql-it。默认mvn test会跳过这些类，已更正执行命令。
- initMailboxGroupPush.visibilitychange可绕过主刷新函数；03额外纳入阻断，源码原文保存在14。
- frontend effective CSS使用后置覆盖值，非首条声明；新增样式不使用半透明panel token造弹窗。
- 不把本地源码当线上部署证明；线上commit、数据库user1是否存在、认证/静态目录发布配置列为执行前实证项。

## 知识消费与回写

已读取并更新使用计数的条目：K-dom-stub-tests-hide-dangling-refs、K-js-test-invocation-surface、K-panel-bg-token-is-translucent、K-frontend-cache-key-triad、K-master-plan-shared-file-sequential-gates、K-plan-quantified-claims-need-grep-receipts、K-mailbox-draft-cache-owner-capture、K-shared-workbench-fixed-mode-host-adapter、K-ui-removal-retires-obsolete-contract-tests、K-sender-account-enabled-scope、K-line-number-guard-breaks-on-any-insertion、K-no-cache-framework-in-repo、K-flyway-gate-must-pass-on-fresh-db。

均已在审计或子计划标注来源。复核保留admin DOM/旧工作台而非重写其模式，复用该知识为范围约束。半透明token条目仅作为不新增不透明弹窗的约束。没有把旧知识的历史计数/Docker参数当当前已验证事实。

新增知识：auth/K-session-auth-does-not-imply-mail-authorization.md、frontend/K-mailbox-readonly-needs-lifecycle-audit.md，明确是计划阶段代码事实，未声称设计已实施。

本次使用条目均为新鲜条目，不归档。跨10次阈值的行号守卫条目已经在CLAUDE.md:94有推广，未重复插入；本次所用条目没有5项同主题可合并组，不执行全库清理。检索未发现可用于级联的agents/*.md或templates/project-CLAUDE.md；保留知识条目引用，不创建无关角色文件。

## 工作区保护

源码摘要复核无变化，见20。git diff仍显示原先已有的application.yml三行变动，不是本轮修改；其余预先存在的文档变动保留。此次仅写docs/plans及技能要求的docs/knowledge；未改业务源码、未创建账号、未改线上数据、未部署。
