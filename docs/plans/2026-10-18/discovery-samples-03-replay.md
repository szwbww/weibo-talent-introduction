# 03 · 离线回放与可核验对比

待实施；依赖01 schema，02接入后可读取真实新包。4文件、1个离线工具子系统。规范：[contracts C-3～C-6](discovery-samples-contracts.md)、[audit§5](discovery-samples-code-audit.md)。

## 需求描述

O-1：通过本地CLI按执行ID列出留存样本和正文覆盖，不需要新增后台页面。
O-2：同一输入离线跑A/B代码，得到逐绑定差异；有独立人工标注才给标注集准确率，能识别损坏/无正文与不可比输入。
必须保持：N-1 回放不访问网络/DB/ES/Apollo、不写业务状态；N-2 原样本只读、不改作者/正文、不改生产解析规则；N-3 不将采样子集、历史不同批次或下载错误推算为整批成功率改善。
范围外：生产重试队列、403绕过、自动修解析器、网页、历史归档转换器、人工标注平台、部署线上CLI端点。

## 关键不变量

### Invariant I-1: 原始输入与解析器一致
- Rule：先检schema/路径/大小/SHA，后解析；作者字段/顺序和bytes从包读取。PDF/HTML调用当前生产PdfEmailExtractor.extract，给原PdfExtractionProperties和只返回已保存字节的假BoundedHttpExecutor；deadline=null表示解析器离线模式，不能解释为重现生产超时。XML调用JatsXmlEmailParser.parse(bytes)，声明PARSER_ONLY、无OpenAlex后处理。禁止复制简化算法。
- Applies to：Replay和测试。
- Violation consequence：测试与生产不同算法，得出虚假优化效果。
- 来源：K-source-recall-needs-original-layout。

### Invariant I-2: 比较必须可追溯
- Rule：C-6报告字段/状态全部实施；先clean编译本工作树再启动，保存实际加载classes聚合SHA与源码状态。对比输入键集合、SHA及配置完全相同才计算差异；无真值accuracy=null，有真值只计算覆盖的标注集TP/FP/FN。生产outcome不冒充parser级A基线。
- Applies to：shell、list/replay/compare、runbook。
- Violation consequence：旧target冒充新代码、变换输入偷换基线、误绑定算成功。
- 来源：K-source-recall-needs-original-layout；K-author-identity-needs-email-evidence。

### Invariant I-3: 离线只读
- Rule：工具放src/test不随WAR部署，无Spring应用启动；所有HTTP请求工厂为拒绝实现，假BoundedHttp只服务当前已校验的body。XML生产解析器已禁外部实体加载，保留该约束。输出目录必须在样本root外；不修补、覆盖或清理样本。缺正文和损坏有独立状态及非零错误退出规则。
- Applies to：全部CLI模式与测试。
- Violation consequence：回放成生产任务或损坏原证据。
- 来源：总I-4。

## 现状审计

新reader读取01Store.commit提交的包；不消费queue JSON、task details或专家索引。IP-3下载字节→文件→parser；IP-4清理→只读工具，读取时消失必须报告EXPIRED_OR_REMOVED。

PdfEmailExtractor有现成BoundedHttpExecutor注入，测试PdfEmailExtractorTest已用fake实现；无需新增生产parse-only接口。JatsXmlEmailParser是object，parse(bytes)可直接调用；OpenAlexDataSource后处理不在本工具范围。pom已有exec-maven-plugin，但工具采用Maven构建test classpath再java启动，CLI参数按argv传递，避免exec.args转义导致路径错读；不改pom。

既有真实PDF测试资源包括ownership-20260930.zip、source-contact-recall.zip；沿用既有测试里的真实bytes+作者输入构造方式验证工具输出与直接extractor一致，不改其原始字节/真值。09-29原始6组档案证据仍可供后续人工标注/专项回归；首版不伪造它们属于任务26127，也不承诺直接读取任意旧zip格式。

## 实现方案

1. **CLI（I-1/I-2/I-3）**：新增清单1，以Kotlin object+@JvmStatic main提供list/replay/compare。参数固定见下；schema类型复用01 Models，Jackson不静默接受未知版本。只列root中的规范最终包；按执行ID过滤；所有错误含sampleId/attempt序号与固定错误码，不输出原URL/邮箱到普通运行日志。绑定详情仅写显式输出报告。禁止自动Spring注入/网络客户端创建。
2. **回放适配（I-1/I-3）**：在清单1内实现本地BoundedHttpExecutor，execute将指定bytes+原contentType/已知status交给生产responseExtractor；无状态的XML走JATS；任何非当前body请求立即报OFFLINE_NETWORK_FORBIDDEN。给RestTemplate配置拒绝网络的requestFactory作第二层保护。一个body一次调用；下载retry/回退不回放，缺正文不尝试重抓。源码算法不改，生产outcome单列供查，不进入A/B正确率。
3. **报告与对比（I-2）**：清单1实现C-6规范，结果JSON稳定排序。比较双方数据集必须相同；missing-body、corrupt、expired单列。labels按输入/body SHA绑定，错SHA拒绝，不使用文件名猜真值。list只统计当前留存量，不输出总体失败率。比较命令exit=0表示可比并成功生成报告，绑定变化不自动算测试失败；输入/校验错误exit=2，参数错误exit=64，构建失败沿原非0退出。
4. **构建入口（I-2/I-3）**：新增清单3，bash `set -euo pipefail`，定位自身仓库，JDK11检查，单工作树一次运行；`mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=<mktemp文件>`，随后java test/main classes+依赖classpath执行main，所有用户参数以`"$@"`逐项传递。构建期可使用Maven依赖仓库；回放运行期不联网。报告写classpath摘要/HEAD/dirtyDiffHash/Java版本，不把Git凭据或完整diff写进报告。不在生产checkout运行clean。
5. **测试与文档（I-1～I-3）**：清单2新增测试，程序化生成小manifest/HTML/JATS及读取已有PDF资源，不新增大zip。验证工具输出与直接生产parser结果相同，而不在测试里复制算法；包含真实PDF原文fixture、缺正文403、SHA损坏、清理竞态、XML外部实体、重复绑定、换输入不允许对比、labels缺失/错配。清单4写完整命令、schema边界、开启/停用/保留目录操作说明；配置初值明确“待实测”，不写成功率提升。

CLI接口（实现后提供，当前尚不存在）：

```bash
bash scripts/expert_discovery/replay_samples.sh list --samples /tmp/discovery-sample-acceptance/capture/samples --output /tmp/discovery-sample-list.json
bash scripts/expert_discovery/replay_samples.sh replay --samples /tmp/discovery-sample-acceptance/capture/samples --execution-id 9001 --output /tmp/discovery-sample-A.json
bash scripts/expert_discovery/replay_samples.sh compare --before /tmp/discovery-sample-A.json --after /tmp/discovery-sample-B.json --output /tmp/discovery-sample-diff.json
# 可选追加 --labels /绝对路径/labels.json；只由人工校对产生
```

真实新样本验证：先获准在已合并测试环境开启小任务；把指定execution目录只读复制到独立分析目录，核SHA；A/B工作树分别用同一只读输入运行。此动作需新产生的真实包，不能在本计划阶段声称已经测出优化效果。确认真实收益时另立有标注的解析修复计划。

## 变更文件清单

|#|文件|动作|
|---|---|---|
|1|`src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplay.kt`|新增|
|2|`src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplayTest.kt`|新增|
|3|`scripts/expert_discovery/replay_samples.sh`|新增|
|4|`docs/runbooks/discovery-sample-replay.md`|新增|

## 验收标准

- I-1：真实PDF fixture、合成HTML/JATS逐字段匹配直接parser输出；同bytes/作者/配置SHA对齐，未改原资源。PDF首尾页、mailto既有回归继续通过。XML报告无OpenAlex后处理假象；超时历史不进入网络改善指标。
- I-2：同代码两次报告除耗时/生成时间外一致；同输入A/B绑定差异0；输入或配置改变exit2 INPUT_MISMATCH；源码更改后classes指纹与报告新编译证据一致；无labels accuracy=null；带1条正确三元组与1条多余三元组合成结果时TP=1、FP=1、FN=0、precision=0.5、recall=1.0，仅标注集统计。
- I-3：requestFactory调用数0、无应用上下文/DB/ES依赖；禁外部实体仍通过；输出指向样本root拒绝；SHA错误不解析；并发清理能报告而不伪造空抽取成功；运行前后原包hash清单完全相同。
- 命令：`mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest -DskipNodeTests=true test`；`bash -n scripts/expert_discovery/replay_samples.sh`；执行一次完整shell构建+java路径验证，不只直接调用测试方法。

## 人工验收清单

### A-1: 零变化对照
- 前置条件：01～03完成，02 A-1产物存在；独立工作树有JDK11/Maven；无labels。
- 操作步骤：1. 用上面CLI list查看留存。2. 对execution 9001连续replay两次，输出A.json/B.json。3. compare两份报告。
- 预期结果：list单位为样本/正文，非人数；report.scope=PARSER_ONLY；added=0、removed=0、changed=0；accuracy=null、labelledBodies=0；相同输入和classes SHA；elapsed可不同。源码未改变时不出现“改善率”。
- 覆盖：O-1/O-2、N-2/N-3、I-1/I-2、IP-3/IP-4。

### A-2: 坏包与无正文
- 前置条件：ReplayTest以 `-Dsample.acceptanceDir=/tmp/discovery-sample-acceptance` 输出 `replay-fixtures`，其中good、no-body、hash-mismatch、unsupported四个目录，全部合成；不修改真实样本。
- 操作步骤：1. 对四个目录分别replay。2. 核对退出码和JSON。3. 将原报告与不同输入fixture报告compare。
- 预期结果：good成功；no-body记录NO_BODY且解析调用0，不发HTTP；hash-mismatch为HASH_MISMATCH、exit2；unsupported为UNSUPPORTED_SCHEMA、exit2；不同输入比较INPUT_MISMATCH、exit2，无准确率。
- 覆盖：O-2、N-1/N-3、I-2/I-3、IP-4。

### A-3: 离线与人工真值
- 前置条件：ReplayTest生成 `replay/cases.json` 和labels fixture；Maven依赖预先可用。测试构造的预测包含正确绑定1条、多余绑定1条，人工真值只有正确那条。
- 操作步骤：1. 运行ReplayTest并查offline/xml-external-entity/read-only三个case。2. 对测试导出的A/B报告追加labels做compare。3. 对照原输入hash清单。
- 预期结果：networkCalls=0、businessWrites=0、inputHashesUnchanged=true；TP=1、FP=1、FN=0、precision=0.5、recall=1.0；报告说明只针对标注集合。该合成数字不能写作线上优化效果。
- 覆盖：O-2、N-1/N-2/N-3、I-1/I-2/I-3、IP-3/IP-4。
