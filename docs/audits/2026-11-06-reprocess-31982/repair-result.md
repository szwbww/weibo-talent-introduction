# #30711 / #31982 本地修复验证

2026-11-06；main，基线 df3bfac。验证完成时修复尚未提交；随后用户授权本地提交。未发布，未修改生产业务记录；其他既有未提交改动保留。

## 修复

1. JSON null、缺字段、空白姓名不再参与完整身份冲突判断；对照与正式补录共用同一判断。修复此前40条误冲突的根因，不表示这40人一定可准入。
2. 支持有明确来源证据的跨行括号姓名、分号分隔的「全名, 邮箱」、居中/右对齐作者联系区和邮箱后的明确社交标识；修正侧栏与正文混行、字体位置重排交错。保留共享拥有者、章节/跨页/跨栏、过长条目和截断邮箱保护。
3. 已有非发现 RAW、已有候选但缺 RAW、真实姓名冲突分别显示原因；原写保护和阻断结果保留。
4. PDF 实现版本20261106，补录代码版本paper-reprocess-identity-20261106。身份凭证20260925、缓存兼容20261004保持不变。静态缓存统一20261106-paper-identity。

## 实测效果

冻结244份原文、337组独立确认的作者/邮箱组合，使用生产解析器离线重放：

| 项目 | 修改前 | 修改后 |
|---|---:|---:|
| 正确绑定组合 | 51 | 147 |
| 未绑定组合 | 286 | 190 |
| 新增正确绑定 | — | 96 |
| 原正确绑定回退 | — | 0 |
| 真值集内误绑 | 0 | 0 |

没有外部请求，没有构造业务写入服务。原始337组真值和原文字节未改写。对此前283个已确认漏绑目标，当前解析结果能覆盖96个；其余187个未宣称修复。3276个尚无独立绑定证明的目标不在本次效果承诺内。47个未确认分栏候选不属于真值集，不能声称这些候选已获得原文负例覆盖。

Linux服务器使用临时独立Java进程、同一原文件及元数据复验（不替换应用，不加载Spring）：

| 论文记录 | 修改前绑定 | 修改后绑定 |
|---|---:|---:|
| 31413 | 0 | 1 |
| 31863 | 0 | 1 |
| 32259 | 2 | 3 |

#31863最终版面邮箱与实际提取邮箱哈希一致，星号仍绑定唯一拥有者。临时目录执行后自动删除。

证据：repair-comparison.json、repair-tests.json、server-fresh-parse.json、server-repaired-parse.json、repair-source-hashes.json。新增绑定是解析机会，不是新增专家数或准入数。

## 编译与测试

- Java11 / Kotlin1.9.25：Maven完整编译及定向测试通过；最后增加的长度保护及2个回归类以同版本Kotlin编译器单独编译，随后通过Maven Surefire重新运行全部7个相关测试类。
- JVM：102项，99通过、0失败、3跳过。跳过项：另一个原始294样本库未提供；2个需显式paperReprocessIt的数据库集成用例未启用。不声称本次执行了数据库集成验收。
- Node：discoveryPaperReprocess.test.js，16通过。
- git diff --check通过，所有静态资源缓存键一致。
- 初始回归8项实际失败，覆盖空姓名、稳定ID空白、失败分类、换行姓名、分号联系条目和字符交错。长条目额外反例在修复前产生错误绑定，修复后被拒绝。

最终测试命令：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o -q -Dtest=PaperReprocessIdentityRegressionTest,PaperContactRecallRegressionTest,DiscoveryPaperLayoutRegressionTest,SourceAuthorEmailResolverTest,PdfEmailExtractorTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessApplyServiceTest surefire:test
node --test src/test/js/discoveryPaperReprocess.test.js
```

完整重建可使用相同测试列表运行mvn test；不要开启Kotlin增量编译，本仓本轮观察到其测试Java helper解析失败。

## fix-v 收尾核验

基线为本会话批准的诊断修复范围，记录于repair-contract.md。模式MECHANICAL_REPAIR（收尾阶段未再修改产品行为）。机器验证PASS；人工上线验收PENDING。

- R1通过：DiscoveryPaperReprocessService.kt:314/397/401、ApplyService.kt:181/368；两模式及来源选择使用同一规则；完整姓名和合法证明同时必要。
- R2/R3通过：PdfAuthorContactLayout.kt:166/221/421，SourceAuthorEmailResolver.kt:150；冻结原文回放及负例测试；Linux复验。
- R4通过：ApplyService.kt:268；保护条件仍在重新审核之前返回；前端原因映射同步。
- R5通过：Service.kt:386、PdfAuthorContactLayout.kt:12；Repository.retry原有版本检查仍阻止新版复用旧冻结解析。
- 写入审计：沿用原consumeArchivedPaper、身份修复、核验/准入写入器；未新增业务写入口。真实姓名/标识冲突、联系/人工/应用层保护不放宽。
- 状态机、累计收据、取消和租约：本次未改状态/统计/持久化协议；旧任务不能随代码升级被当作新版解析结果。
- No extras / scope：6个产品文件、3个测试文件及本目录证据；未改CSS、数据库结构、准入规则、Apollo配置、并发或部署。

上线后：在论文原文页选择来源任务#30711，新建「重新解析并补录」。旧运行的「重试」会因版本变化被拒绝，这是防止复用旧解析的既有保护。最终新增、更新、准入以新运行的持久收据对照，不套用96组解析增量。
