# Child 01 执行报告 — PDF 作者标记与邮箱完整性修复

- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-30-discovery-pdf-contact-integrity`
- Branch：`fast/2026-09-30-discovery-pdf-contact-integrity`
- `child_base_sha`：`8aa82c87848273313bd9239249eb77cff6c05c14`（执行前 HEAD `fb8e1ed`，仅多一条控制方基线证据提交，产品代码与新基线一致）
- Plan identity：`docs/plans/2026-09-30/discovery-pdf-contact-integrity.md` @ `3502bf077f26cce7b2c368abe05c845b46b3caf43e00dc7945ef82be83792ea0`（执行前执行后一致，未修改计划）
- 实现提交：`827b8b0f7df5c51e06db6d06be528d06e6ee2620` — `feat(fast-p): implement 01`（10 个白名单文件，无 `docs/plans/**`、无运行日志）
- 命令环境：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`（全部 Maven 命令）
- 未 push / merge / rebase / squash / amend / reset；未启动应用、未连接 MySQL/ES、未发信、未新增依赖

---

## 1. 阶段 1：固化真实失败与回归输出

### 1.1 原文归档逐字复制（清单 10）

```sh
cp /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-30-discovery-three-case-replay/original-inputs.zip \
   src/test/resources/discovery/ownership-20260930.zip
shasum -a 256 src/test/resources/discovery/ownership-20260930.zip   # 复制前/后各一次
cmp src/test/resources/discovery/ownership-20260930.zip <源 ZIP>     # 逐字节
```

- 复制前源 SHA256：`9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`
- 复制后目标 SHA256：`9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`（`cmp` 返回 0，逐字节一致）
- 归档成员 PDF SHA256（与 brief / 审计 manifest 一致）：chee `0daf96634f750d280bb1330d9b95d87f4ea04e54bee813db5c372092028fdb99`、lyderic `7459f1af390be438463bbd1b97d5cc79a183fd2ffb09310e2448d12736fb8f9b`、lun `7e2468ed9ad9ada830f943a1a5b1bc423704c0a6b51ea672fe250b5aa74805ba`；未重生成、未重排 PDF、未手工补作者证据。

### 1.2 新增测试与复用辅助

| 文件 | 位置 | 内容 |
| --- | --- | --- |
| `SourceAuthorEmailResolverTest.kt` | `:106-113` | `threeCaseArchive` / `threeCaseMembers` / `threeCaseAuthors`（复用既有 `zipMembers`、`fixtureSha256`、`openAlexPaperAuthors`，只按名读成员） |
| 同上 | `:116-165` | `ThreeCaseObservation` + `observeThreeCase`：真实 PDFBox + 真实 `PdfAuthorContactLayout.collect` + 真实 `extractOwnershipContent`（仅替换 HTTP），并读取与生产同范围的 mailto 线索 |
| 同上 | `:172-222` | `markerOwnershipControls()`：6 个 SYNTHETIC 标记/损坏控制的现场观测值 |
| 同上 | `:225-243` | `PdfPositiveControl` + `pdfPositiveControls()`：真实现有正常 PDF（`source-contact-recall.zip` W2999309192）+ 合成一人两邮箱 |
| 同上 | `:697-745` | 新用例 `SYNTHETIC continuous marker groups and damaged signature areas (I-1 I-2 I-4)` |
| `PlainTextEmailExtractorTest.kt` | `:95-99`、`:106-127` | 新用例 + `bareMarkerTextBoundaryCases()`（I-3 权威输入输出表，11 项） |
| `PdfEmailExtractorTest.kt` | `:149-259` | 新用例 `three archived original cases replay ... emit acceptance report`：三篇原文 + 报告落盘 + X-1/X-2 双路断言 + 文本表 + 正向控制 |

报告先落盘、后断言：断言失败也保留本次观测数据。

### 1.3 修复前红（保留证据）

命令（fresh，生产代码未改，仅新增测试）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test
```

exit code `1`；`Tests run: 363, Failures: 7, Errors: 0, Skipped: 0`（363 = 362 + 1 条后续在修复轮删除的自造统计用例）。失败明细：

```
DiscoveryPipelineServiceTest.superseded ownership extraction cache ...:1891 version=20261003 ==> expected: <FAILED> but was: <SUCCEEDED>
ExpertDiscoveryServiceTest.three archived ownership cases ...:1718 chee 全部为无身份线索 ==> expected: <[ysjang@ucla.edu, cheewei.wong@ucla.edu]> but was: <[]>
PdfEmailExtractorTest.three archived original cases ...:228 chee contacts: [Contact(email=ysjang@ucla.edu, authorIndex=5, page=1, ...), Contact(email=cheewei.wong@ucla.edu, authorIndex=5, page=1, ...)] ==> expected: <true> but was: <false>
PlainTextEmailExtractorTest.bare author marker ...:97 bareMarkerSplitLocalPart: ∗ lun yue@msn.com ==> expected: <[]> but was: <[yue@msn.com]>
PlainTextEmailExtractorTest.bare marker filter ...:106 expected: <6> but was: <7>
SourceAuthorEmailResolverTest.SYNTHETIC continuous marker groups ...:706 expected: <2> but was: <1>
DiscoveryIdentityTest.new extraction cache version ...:55 expected: <20261004> but was: <20261003>
```

修复前 `target/discovery-plan-acceptance/pdf-contact-integrity.json` 现场观测（已另存 `target/pre-fix/`，与审计 `prod-results.json` 一致）：

```
chee contacts=ysjang@ucla.edu,cheewei.wong@ucla.edu resolved=ysjang@ucla.edu:Chee Wei Wong | cheewei.wong@ucla.edu:Chee Wei Wong mailto= method=PDF_PARSE http=1 full=true
lyderic contacts=lyderic.bocquet@ens.fr,alessandro.siria@ens.fr resolved=lyderic.bocquet@ens.fr:Lydéric Bocquet | alessandro.siria@ens.fr:Lydéric Bocquet mailto=lyderic.bocquet@ens.fr,alessandro.siria@ens.fr method=PDF_PARSE http=1 full=true
lun contacts=yue@msn.com resolved=yue@msn.com:Lun Yue | mgaarde1@lsu.edu:null null | lun_yue@msn.com:null null mailto=lun_yue@msn.com,mgaarde1@lsu.edu method=PDF_PARSE http=1 full=true
```

`synthetic`（修复前）：`continuousMarkerGroupSharesOneSignature` 只出 1 个 Contact（`*` 未继承 `†` 的署名）；`unmatchedSecondSignatureJoinsTheSharedMarker` 把共享 `*` 判给 Jane Doe。

---

## 2. 阶段 2：标记组与损坏标记局部修复（清单 1）

`src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt`

- `:170-186` `markerOwners` 改为按连续标记组解析：组 = 由数字/逗号/空白（`:195` `markerGroupGap`）+ 受支持符号组成的后缀段，至少一个支持符号；**只为组首**（`:182`）解析前置署名，组内每个符号共享该拥有者集合；出现字母即跨姓名边界成为新组。
- `:198-204` `ownersOfSignature`：解析不出署名（null）或对不上元数据一律登记 `null` 拥有者，不再 `continue`（原实现会让共享标记看似独占）。
- `:192` `damagedSignature = Regex("[?\\uFFFD]")`；`:170-171` 在 `markerOwners` 输入（即 `authorArea`）命中即返回空映射 —— 本页标记→作者归属不可用；不做 `?→*` 猜测替换，不阻断 `endContactBlocks`、独立姓名联系段落与 resolver 的 `textClaims`。
- `:147` `authorHeader` 判据由 `markerOwners(text).isNotEmpty()` 改为等价的 `hasSignatureMarker(text)`（`:208-209`）：保留「至少一个标记前面能解析出署名」的既有标题行语义，与「null 拥有者参与归属计数」解耦，避免标题行选择被新计数规则放大。
- Contact 对外签名、`markerSegments` 分段语义、`uniqueOwner` 不变。

已归档原文的后果（见 §5/§6）：Chee 的 `*` 拥有者集合变为 `{未知, 5}` → 不再独占 → 两个邮箱降为无身份线索；Lydéric 署名区含 `?` → 该页标记归属不可用 → 两个 ens.fr 邮箱降为线索。

## 3. 阶段 3：裸标记的残缺邮箱过滤（清单 1、2）

`src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt`

- `:21-27` 新增 `bareMarkerTruncatedMailbox = Regex("(?m)^[ \\t]*[*∗†‡§][ \\t]+[A-Za-z0-9._%+-]+[ \\t]+\\z")`：行首空白 + 一个支持标记 + 空白 + 一个 local-part 词片段 + 水平空白，且只匹配到输入末尾（`\z`）。横向空白显式 `[ \t]`，不跨段；不改 `emailRegex` 语法、不加长度门槛、不做后缀关系推断。
- `:42-47` 在既有 `splitLocalPartBeforeMailbox` 旁并列过滤，保留 `Email:` 标签规则。

`PdfAuthorContactLayout.kt`

- `:78-84` `markerSegments` 消费处把片段自己的 marker 交给提取器（`emailExtractor.extract("$marker$segment")`），使 `∗ lun yue@msn.com` 在 Contact 分支与整页文本两路都走同一过滤；`Contact.contactText`/`evidenceText` 仍存原始行（`:84`）。

## 4. 阶段 4：缓存兼容与真实消费者回归（清单 3、7、8、9）

- `expert/domain/DiscoveryIdentity.kt:24`：`EXTRACTION_VERSION` `20261003 → 20261004`；`:22` `VERSION` 保持 `20260925`。producer（`ExpertDiscoveryService.extractQueuedItem`）自动带新版本，consumer 与 pipeline 的既有拒绝语义不变（FAILED / 不下载 / 不写专家 / 不重抽取）。
- `DiscoveryIdentityTest.kt:52-62`：精确常量断言更新为 20261004，并固定「直接前版 20261003 与更旧 20260929 的 proof 版本仍被拒」。
- `ExpertDiscoveryServiceTest.kt:1619-1756`：新用例走真实 parser→consumer→writer（`installOwnershipStorage` 的内存捕获 + 真实 `ExpertIndexWriterService`）：
  - 三篇原文按当前版本消费：全部邮箱 `IDENTITY_UNRESOLVED`、0 次邮箱校验、0 份 raw/candidate 落档；
  - 旧版直连消费：20261003/20261002/20260928/null 一律 `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`、0 写入、文档不变；
  - 真实 producer 调用：`extractQueuedItem` 序列化 JSON 带 20261004；
  - 正向控制：真实现有正常 PDF 与合成一人两邮箱经同一消费者，姓名/ORCID/作者 ID/机构整体传递。
- `DiscoveryPipelineServiceTest.kt:1850-1938`：扩展原 superseded-cache 用例，20261003/20261002/null 在真实 pipeline + 内存队列下 `FAILED` + 精确 reason + 下载 0 + 写入 0；当前版本正常控制 `SUCCEEDED`、`indexedExperts=1`、下载 0。

---

## 5. 阶段 5：必需命令结果（fresh）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test
```

exit code **0**（`BUILD SUCCESS`，`Total time: 03:38 min`）；同一次 `mvn test` 生命周期内 `exec-maven-plugin` 绑定执行的前端 JS 用例也全绿（`tests 1233 / pass 1233 / fail 0`）。

| Class | 基线 tests | 最终 tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| ExpertDiscoveryServiceTest | 176 | 177 | 0 | 0 | 0 |
| PlainTextEmailExtractorTest | 6 | 7 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 39 | 40 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 47 | 47 | 0 | 0 | 0 |
| CoreDataSourceTest | 19 | 19 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 24 | 25 | 0 | 0 | 0 |
| JatsXmlEmailParserTest | 40 | 40 | 0 | 0 | 0 |
| DiscoveryIdentityTest | 7 | 7 | 0 | 0 | 0 |
| **Total** | **358** | **362** | **0** | **0** | **0** |

计数来源：`target/surefire-reports/*.txt`（+4 = 4 条新增/扩展用例；`PlainTextEmailExtractorTest` 在修复轮删除了 1 条自造的冗余统计用例，见 §8）。无既有断言被删除、放宽或改写期望值；六篇真实 PDF、HTML 命名联系人、花括号/换行、尾页、超时等既有用例全部保留通过。

## 6. 三份验收报告（本次运行产出，均为现场数据）

### 6.1 `target/discovery-plan-acceptance/pdf-contact-integrity.json`

- `fixture.archiveSha256` = `9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`；另列出每个成员 SHA 与归档 manifest 的 PDF/元数据 SHA 供交叉核对。
- 三篇原文（`designation=REAL_ORIGINAL`，真实 PDFBox/layout/resolver/extractor，仅替换 HTTP）：

| case | contacts | resolved（身份字段） | mailto 线索 | methodUsed | httpRequests | fulltextObtained |
| --- | --- | --- | --- | --- | --- | --- |
| chee | 0 | `ysjang@ucla.edu`、`cheewei.wong@ucla.edu`：givenNames/familyNames/orcidId/openAlexAuthorId/institution*/identityEvidence 全 null，isCorresponding=false | 无 | PDF_PARSE | 1 | true |
| lyderic | 0 | `lyderic.bocquet@ens.fr`、`alessandro.siria@ens.fr`：同上全空 | `lyderic.bocquet@ens.fr`、`alessandro.siria@ens.fr`（仅线索，不获身份） | PDF_PARSE | 1 | true |
| lun | 0 | `mgaarde1@lsu.edu`、`lun_yue@msn.com`：全空；**`yue@msn.com` 不存在** | `lun_yue@msn.com`、`mgaarde1@lsu.edu` | PDF_PARSE | 1 | true |

- `syntheticControls`（6 项，含现场 contacts/resolved）：`continuousMarkerGroupSharesOneSignature`（`1,†,*` 两个符号共享 Jane Doe，两个 Contact 均绑定）、`unmatchedSecondSignatureJoinsTheSharedMarker`（`*` 拥有者 `{Jane Doe, 未知}` → 只 1 个 Contact，`unknown@uni.edu` 仅为线索）、`damagedSignatureAreaCannotProve`（contacts 空、两邮箱身份全空）、`questionMarkOutsideAuthorAreaDoesNotBlock`（正常绑定）、`damagedMarkerKeepsIndependentNameRecord`（标记证明不可用，但 `Jane Doe: jane@uni.edu` 仍凭独立记录绑定且有 SOURCE_SHA256 证据）、`sameContactLineTwoMarkersBindEachOwner`（各自绑定）。
- `positiveControls`：`realNormalPdfSingleOwnedMailbox`（W2999309192 → `davidechicco@davidechicco.it` 绑定 Davide Chicco + A5011556172）、`syntheticOneAuthorTwoMailboxes`（`first@uni.edu`/`second@uni.edu` 均绑定 Jane Doe + Jane University + A123）。
- `textTable`（11 项，输入→期望→实际）：`∗ lun yue@msn.com`/`* yin- qiu001@e.ntu.edu.sg`/`*Email: lixingwang- bupt@gmail.com`/`* a@\n\nuni.edu` → 空；`*lun_yue@msn.com`、`* lun_yue@msn.com`、`* a@uni.edu; b@uni.edu`（2 个）、`* Email: a@uni.edu`、`*Corresponding author: a@uni.edu`、`Contact: a@uni.edu`、`* a@\nuni.edu` → 完整地址。

### 6.2 `target/discovery-plan-acceptance/pdf-contact-consumer.json`

- `producer`：`extractQueuedItem` 现场序列化 `stampedExtractionVersion=20261004`（`currentExtractionVersion=20261004`，`emailsEmpty=false`）。
- `cases`（真实消费者 + create-only 捕获器）：chee/lyderic/lun 各 `identityUnresolved=2`、`indexedExperts=0`、`validationCalls=0`、`capturedDocuments=0`；lun 的 `resolvedEmails` 为 `[mgaarde1@lsu.edu, lun_yue@msn.com]`（`yue@msn.com` 根本不进入消费者）。
- `directConsumerVersionCases`：20261003 / 20261002 / 20260928 / null 均 `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`、`indexedExperts=0`、`documentsUnchanged=true`。
- `positiveControls`：真实 PDF `indexedExperts=1`、2 份文档（RAW+CANDIDATE，Davide Chicco / A5011556172 / 机构 null）、1 次真实邮箱校验；合成一人两邮箱 `indexedExperts=2`、4 份文档、2 次校验，每份文档 `Jane Doe` + `Jane University` + `A123`。
- `validatedAddresses=[davidechicco@davidechicco.it, first@uni.edu, second@uni.edu]`，`capturedDocuments=6`。

### 6.3 `target/discovery-plan-acceptance/pdf-contact-cache.json`

- `extractionVersion=20261004`、`evidenceVersion=20260925`。
- `pipelineCases`：20261003 / 20261002 / null → 队列 `FAILED`、`lastError=IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`、`indexedExperts=0`、`downloadCalls=0`、`rawWrites=0`、`attempts=0`。
- `positiveControl`：当前版本缓存 → `SUCCEEDED`、`indexedExperts=1`、`rawWrites=1`、`downloadCalls=0`。

---

## 7. 计划验收项逐条结论

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| I-1 连续标记组 | 满足 | Chee 原文 `†,*` 共享署名（`unmatched` 控制 + 真实 chee 无 Contact）；`continuousMarkerGroupSharesOneSignature` 两符号共享 Jane Doe；现有「同联系行多标记」「共享标记」「重复姓名」回归通过 |
| I-2 损坏署名区不出独占证明 | 满足（U+FFFD 见 §8 限制） | Lydéric 真实原文 `?` → 2 个 ens.fr 邮箱身份全空；`damagedSignatureAreaCannotProve` 空映射；正文问号不阻断；无 `?→*` 替换；独立姓名记录仍绑定 |
| I-3 裸标记残缺邮箱过滤 | 满足 | Lun `yue@msn.com` 在 contacts 与 resolved 双路缺席；`lun_yue@msn.com` 与 `mgaarde1@lsu.edu` 保留；文本表 11 项全绿；既有 brace/wrapped/Email 标签用例不变 |
| I-4 独立证明与线索语义 | 满足 | 三篇 contacts=0 且 resolved 身份全空（isCorresponding=false）；合法一人多邮箱（真实 HTML/PDF 与合成控制）仍各有证据 |
| I-5 缓存版本分离 | 满足 | producer 20261004；direct consumer 与真实 pipeline 均拒绝 20261003/20261002/20260928/null（精确 reason + 0 下载/0 写入）；`VERSION=20260925` 不变且历史 proof 用例通过；无新字段、无租约/CAS 改动 |
| I-6 证据来自同一原文 | 满足 | 归档 SHA 前后一致；修改源码后重新编译再测试；报告为本次执行产出（修复前报告另存 `target/pre-fix/`） |
| O-1 / A-1 | 满足 | chee/lyderic 两个错绑消失（contacts=0、身份全空）；methodUsed=PDF_PARSE、httpRequests=1、fulltextObtained=true |
| O-2 / A-2 | 满足 | lun `yue@msn.com` 数量 0；`lun_yue@msn.com` 恰 1 条且身份空；`mgaarde1@lsu.edu` 仍在；报告未声称恢复 Lun 身份 |
| O-3 / A-5 | 满足 | 见 6.3；`DiscoveryIdentityTest` 7/7 通过 |
| N-1 / N-2 / A-3 | 满足 | 正向控制（真实 PDF + 合成）姓名/作者 ID/机构整体传递；`pdfLayoutNegativeCases`、`one unique PDF contact marker retains two explicit mailboxes`、`bounded contact fields carry one whole identity` 等回归通过 |
| N-3 / N-4 / A-4 | 满足 | brace 30 地址总量断言、wrapped 换行、`refuses` 双换行、HTML/XML 命名联系人、尾页 `[1,2,62]` 全部通过；`CoreDataSourceTest` 19/19、`JatsXmlEmailParserTest` 40/40 |
| N-5 | 满足 | `VERSION`、专家 ID、消费/去重/写入/晋升逻辑 diff 为 0（生产 diff 仅清单 1-3） |
| X-1 / X-2 | 满足 | 同一用例同时断言 layout contacts 与 resolver/extractor 输出（§6.1） |
| X-3 | 满足 | CORE 内嵌全文、HTML 联系人、完整 mailto、通用文本提取器回归全绿 |
| X-4 / X-5 | 满足 | 真实原文结果序列化进真实消费链并断言捕获文档数（0），非 Mockito 空列表 |
| A-6 | 满足 | `pdf-contact-consumer.json` 列出解析地址、`IDENTITY_UNRESOLVED`、校验调用与实际捕获文档（0） |
| 范围 | 满足 | 生产 diff 仅清单 1-3；无新依赖、DB/ES 字段、外联规则；报告仅在 `target/` |

## 8. 偏差与限制

1. **I-2 的 U+FFFD 分支已实现但无独立 fixture 证据**：`damagedSignature` 同时覆盖 `?` 与 `\uFFFD`，但 WinAnsi/Symbol 标准字体无法编码 U+FFFD，三个归档原文也未出现该字符，因此只有 `?` 有真实/合成证据。这是未覆盖的测试面，不是跳过实现。
2. **`authorHeader` 判据未随归属计数放大**：新计数会为解析不出署名的标记登记 `null`，若标题行判据继续用 `markerOwners(text).isNotEmpty()`，会把「任意标记」当成作者行候选。已改为语义等价的 `hasSignatureMarker(text)`（`PdfAuthorContactLayout.kt:208`），属计划未提及的行为保持措施，不扩大判定范围。
3. **报告为单写入方**：`pdf-contact-integrity.json` 由清单 6 的用例统一写出，其中的 SYNTHETIC 控制与文本表数据来自清单 4/5 中新增的 `internal` 辅助函数（`markerOwnershipControls`、`bareMarkerTextBoundaryCases`），以现场调用取得，避免多个测试类互相覆盖报告。
4. **修复轮删除了一条自造用例**：初版在 `PlainTextEmailExtractorTest` 另加了一条只统计「非空期望条数」的自造断言（与本计划契约无关、且我算错基数）—— 属新增用例自身缺陷，已删除；未删除或改写任何既有断言。
5. **正向控制的计数口径修正**：真实 PDF W2999309192 只有 1 个明确邮箱，因此 `indexedExperts=1`、文档 2 份（RAW+CANDIDATE）；初版把 `indexedExperts` 误写为 2，已按生产语义修正（属新用例自造断言，非产品行为改动）。
6. **过程偏差（只读、内容等价）**：执行过程中有数次针对 `src/**` 的检索/读取使用了相对路径，实际落在仓库主工作区 `weibo-talent-introduction`（会话工作目录）而非本 worktree。已逐文件哈希核对：涉及的生产文件两树字节一致（`cmp` 全部 SAME），测试文件差异仅为本 child 的改动；所有写入/编辑/测试/提交均在本 worktree 内。后续未再使用相对路径读取。
7. 未做：存量数据修复、部署、ES migration、缓存迁移工程、任何白名单外文件改动。

## 9. 交付

- 实现提交：`827b8b0f7df5c51e06db6d06be528d06e6ee2620`（`feat(fast-p): implement 01`），仅含 10 个白名单文件；提交后 `git status --porcelain` 干净（本报告为报告产物，未入库）。
- 报告产物：`target/discovery-plan-acceptance/pdf-contact-integrity.json`、`pdf-contact-consumer.json`、`pdf-contact-cache.json`（另存修复前快照 `target/pre-fix/`）。
