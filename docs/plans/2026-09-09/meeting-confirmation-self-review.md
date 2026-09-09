# 专家会议确认计划自检

2026-09-09；检查对象为计划和证据，不是生产功能。总入口00，实施子计划01..05。

| 检查项 | 结果/证据 |
|---|---|
| 所有子计划必需章节及顺序 | PASS；结构脚本核对6份计划（含总入口） |
| 文件范围 | PASS；01=9、02=8、03=10、04=7、05=9；各阶段≤2子系统 |
| 共享存储新增字段 | PASS；02只有mail_record.calendar_attachment_json；04只给draft增加meeting聚合对象，revision/state位于其中；无新增attempt列 |
| 每个新增状态/字段不变量 | PASS；01 I-2/I-3/I-6、02 I-1、03 I-3/I-4、04 I-5/I-7 |
| 原始读写/schema审计 | PASS；D1模板、D3尝试、D4邮件、D5草稿，原始grep与源码SHA保留；只读身份输入D2；旧meeting副作用明确排除 |
| 最新工作区复核 | PASS；研究期间检测到mailbox-refinement合并，保留.initial证据，最新123个源码SHA全部匹配；HEAD单独记录 |
| 模板语法/普通发送隔离 | PASS；现有${...}与新会议{{...}}明确区分，01列表+直接id服务双门禁；不改旧MEETING_CONFIRMATION |
| 时间/日历/指纹跨阶段字段 | PASS；MeetingInput与Preview/Snapshot字段在01定义，02/03/04引用相同命名；codec在01，不在Controller创建反向service依赖 |
| 快照/下载 | PASS；同一个生成snapshot进入SMTP与finalize四分支；03仅SENT原件读，04独立binary适配保留contextPath/登录错误，不让JSON api解析ICS |
| 草稿和异步状态 | PASS；外层user/accountScope/contact+内层targetKey；捕获旧Map/revision，QA采用/重定向/取消/失败/晚响应均有明确策略 |
| 完整样式契约 | PASS；04 S-1全文CSS与证据副本字节相同，S-2完整dialog DOM副本存在；S-3/4/5包含入口/草稿/候选/历史卡全部节点；class白名单/禁inline检查通过 |
| hover/active/disabled/focus/error/loading/mobile | PASS；CSS逐字规则+T2状态表，800/420断点、ready/stale/sending/sent和两级Esc明确；不以截图代替合同 |
| 既有class影响 | PASS；不修改styles.css/mailbox-chat.css，均派生meeting类；04追加app mount参数与专用binary函数，不全局换肤 |
| 缓存和测试 | PASS；05同步9资源及原7固定键测试；03controller新增行号影响守卫已列白名单；旧WebMvcTest依赖mock已列白名单 |
| 人工验收 | PASS；子计划共16场景，R/M/IP映射见下表；不生成验收勾选衍生文件 |
| 链接/源文件/样式副本检查 | PASS；脚本输出见meeting-confirmation-evidence/plan-validation.json |
| 生产功能测试 | NOT_RUN；本轮只计划，无生产代码实现，未运行Maven/SMTP/数据库写入，不把预览结果冒充生产验收 |

## 需求与验收映射

| 合同 | 权威验收 |
|---|---|
| R1 入口与搜索 | 04 A-1/A-2/A-8，05 A-1 |
| R2 模板/ICS预览与下载 | 01 A-1/A-2/A-3，04 A-3 |
| R3 草稿、编辑、移除 | 04 A-3/A-4/A-5/A-6 |
| R4 实际发送与历史下载 | 02 A-1/A-2，03 A-1，04 A-7 |
| M1 原主题/富文本/QA/安全 | 04 A-4/A-5/A-7/A-8，03 A-2 |
| M2 草稿隔离/失败保留 | 04 A-6/A-7/A-8，02 A-2 |
| M3 最新收发件箱/材料 | 04 A-1/A-7，03 A-2，05 A-1 |
| M4 旧会议与无来信 | 01 A-2，04 A-8，总计划A-2 |
| IP-1 模板写→读 | 01 A-1、04 A-8 |
| IP-2 身份→预览/发送 | 01 A-2/A-3、03 A-1/A-2 |
| IP-3 规范内容→指纹/MIME | 02 A-1/A-2、03 A-1、04 A-7 |
| IP-4 快照→历史下载 | 02 A-1/A-2、03 A-1/A-2、04 A-7 |
| IP-5 原材料隔离 | 02 A-2、03 A-2、04 A-7 |
| IP-6 草稿→上下文/发送 | 04 A-3..A-8 |
| IP-7 dialog→搜索/键盘/布局 | 04 A-1/A-2 |
| IP-8 资源→运行 | 04 A-1/A-8、05 A-1 |

## 知识写回

消费25条，列表保存在knowledge-use.json；计数/last_used已更新。纠正K-outbound-thread-headers-single-seam的过期“全库没有写头”结论；新增3条：出站附件与材料owner边界、草稿Map异步所有权、下载contextPath注入。新条目只记录当前源码和可复用约束，不将拟新增calendar列当成已存在事实。

K-mail-record-source-inbound-id本次达到10次，已向CLAUDE增加一行回链；JS执行条目已经有回链，不重复添加。没有5条语义相同的规则可合并；没有过期低命中知识被用于本计划。agents/、templates/角色目录不存在，未新建角色配置。其余已有dirty文档不清理、不回滚。
