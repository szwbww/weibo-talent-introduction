# create-p 自查记录

日期：2026-09-25。这是计划结构自查，不是实现验收或独立代理审计。

## 结构与范围

- 四子计划均有需求、不变量、现状审计、实现方案、精确文件清单、机器验收与人工验收；前端另有逐字CSS/DOM契约。
- 文件数10/10/9/4，各不超过10；去重31文件，20生产/11测试。每阶段最多两个子系统；唯一既有业务表新增列为mail_record.open_tracking_id，设置KV只新增一个key。
- 不变量7/4/5/5条，各有验收；人工场景5/3/4/5条，覆盖每个可观察目标、保持项及跨路径交互。
- 单一共享SMTP出口接入；真实回复位覆盖全部审计到的隐含回复链；四条非回复成功链明确透传，失败/回复保持null。
- 前端不改旧全局CSS，不改一级导航、原首发表格和其它业务页。详情采用行内区域，未引入额外弹窗体系。
- 不创建人工验收衍生勾选文件；未来开始人工验收才导出。

## 校验结果

- `mail-open-tracking-evidence/plan-structure-check.json`：章节顺序、manifest匹配、I-n验收匹配、引用文件和CSS变量检查通过；源码哈希漂移列表为空。
- `git diff --check -- CLAUDE.md docs/knowledge docs/plans/2026-09-25`：通过。
- 当前V140最高；预留V141仍须开工前重查；旧“最新版本”断言与历史target分别处理。
- AuthInterceptor明确未登录401；formatPercent实际一位小数，人工验收显示50.0%；补齐hidden按钮CSS，避免button display覆盖hidden；snapshot显式REPEATABLE_READ，不依赖数据库外部默认设置。
- 本轮未运行拟实现功能测试。所有mvn/node/MySQL命令是未来实施验收要求，不是已通过结果。
- 单封临时实验为额外已执行验证：独立探针200；SMTP接受；17:11:26邮件专属像素GET200、42字节。该证据不替代正式路由、数据库、开关和UI验收。

## 知识消费与回写

本轮按相关条目更新使用元数据；没有把知识中的旧行号当源码证明。条目及处理：

| 条目 | 用途/结论 |
|---|---|
| K-mail-record-save-sites | 用于查全成功/失败/入站/回复写方，audit §2 |
| K-outbound-thread-headers-single-seam | 复核MIME与调用方；纠正过时“部分Auto传头”陈述 |
| K-mail-record-source-inbound-id | 明确来源身份，未把processing.id当来信记录ID |
| K-outbound-message-id-single-factory | 否决“当前全路径统一工厂”旧事实，按代码纠正，跟踪不改Message-ID |
| K-mail-body-display-sites | 审计正文展示集合；新像素只进wire副本，因此无需改遍所有展示点 |
| K-unsubscribe-token-plaintext-email | 旧主路径已改opaque token；纠正为仅legacy兼容编码邮箱 |
| K-dual-outreach-paths | 两种介绍发送入口均接成功ID透传 |
| K-plaintext-reply-client-reflow | 保持text/HTML及附件结构，纯文本只在投递副本补HTML |
| K-smtp-idempotency-reservation-before-delivery | 随机token不进指纹，预留独立提交且不得引起SMTP重发 |
| K-cold-outreach-html-asymmetry | 实际intro已HTML+text，否决旧“纯文本intro”事实并纠正 |
| K-panel-bg-token-is-translucent | 明确浅色详情底；hit_count到10，单行规则晋升CLAUDE.md |
| K-frontend-cache-key-triad | 反查当前键与全部资源；本次匹配测试0，实施重查 |
| K-flyway-version-follows-deploy-order | 新迁移只能接真实最高版本，不编辑历史 |
| K-plan-quantified-claims-need-grep-receipts | 数量/路径均有raw证据及manifest；不虚构覆盖率/性能 |
| K-js-tests-run-via-exec-plugin | Node单文件/全量/语法检查及Maven入口均纳入验收 |

新增可复用条目K-reply-context-not-smtp-headers：回复业务身份不等同于SMTP头；随机投递元数据与canonical正文分离。已在相关计划审计解释，不声称字段已实现。

四个证实过时的条目已原位修正文案并更新created；相关条目本轮刚使用，不归档。所选条目按主题没有达到5条同义重叠合并阈值，不进行全知识库整顿。仓库未发现agents/或templates/project-CLAUDE.md角色载体，不为本功能新建角色文档。

## 当前边界

功能计划尚未实施；已有其它任务改动保持。临时测试的公网静态文件与SMTP发送单独记在live-test文档，不混同于产品代码修改。
