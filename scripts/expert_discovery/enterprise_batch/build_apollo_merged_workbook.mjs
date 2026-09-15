import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const [inputPath, outputDir] = process.argv.slice(2);
const data = JSON.parse(await fs.readFile(inputPath, "utf8"));
await fs.mkdir(outputDir, { recursive: true });

const domesticById = Object.fromEntries(data.domestic_enterprises.map(x => [x.enterprise_id, x]));
const apolloDomains = new Set(data.apollo_new_relations.map(x => x.official_domain));
const apolloByDomain = new Map();
for (const r of data.apollo_new_relations) {
  const s = apolloByDomain.get(r.official_domain) ?? { count: 0, maxFocus: 0 };
  s.count += 1;
  s.maxFocus = Math.max(s.maxFocus, Number(r.apollo_focus_engineers || 0));
  apolloByDomain.set(r.official_domain, s);
}
const mergedBenchmarks = data.merged_benchmark_enterprises.map(b => {
  const s = apolloByDomain.get(b.official_domain) ?? { count: 0, maxFocus: 0 };
  const baseSource = b.data_source || "";
  return { ...b, data_source: apolloDomains.has(b.official_domain) && !baseSource.includes("Apollo") ? `${baseSource}；Apollo People Search` : baseSource,
    apollo_relation_count: s.count, apollo_max_focus_engineers: s.maxFocus };
});
const normalizedRelations = data.merged_relations.map(r => ({
  relation_id: r.enterprise_benchmark_rel_id || r.apollo_relation_id,
  enterprise_id: r.enterprise_id,
  enterprise_name: domesticById[r.enterprise_id]?.enterprise_name ?? r.enterprise_name ?? "",
  benchmark_id: r.benchmark_id,
  benchmark_name: r.benchmark_name,
  official_domain: r.official_domain,
  country: r.country,
  source_stage: r.source_stage,
  relation_type: r.benchmark_type || "apollo_people_coverage",
  rank: r.benchmark_priority || r.apollo_rank,
  query_keyword: r.query_keyword || "",
  query_titles: Array.isArray(r.query_titles) ? r.query_titles.join("; ") : "",
  apollo_focus_engineers: r.apollo_focus_engineers,
  apollo_sample_engineers: r.apollo_sample_engineers,
  match_reason: r.match_reason,
  confidence: r.confidence,
  relation_review_status: r.relation_review_status,
})).sort((a,b) => a.enterprise_id.localeCompare(b.enterprise_id) || a.source_stage.localeCompare(b.source_stage) || Number(a.rank)-Number(b.rank));
const newRelations = normalizedRelations.filter(x => x.source_stage.startsWith("Apollo"));
const newByCompany = new Map();
for (const r of newRelations) newByCompany.set(r.enterprise_id, (newByCompany.get(r.enterprise_id) || 0) + 1);
const noNew = data.domestic_enterprises.filter(x => !newByCompany.has(x.enterprise_id)).map(x => ({
  ...x,
  reason: x.core_need?.includes("缺少明确核心需求") ? "缺少明确核心需求，未执行有效检索" : "Apollo首批100条结果中未形成通过相关性复核的新海外企业",
}));

const wb = Workbook.create();
const colors = { dark: "#1F4E78", blue: "#D9EAF7", amber: "#FFF2CC", red: "#FCE8E6", green: "#E2F0D9" };
const font = "Arial";
function col(n) { let s=""; while(n){n--;s=String.fromCharCode(65+n%26)+s;n=Math.floor(n/26);} return s; }
function sheetData(sheet,title,note,headers,rows,widths,tableName,tabColor,rowHeight=34){
  sheet.showGridLines=false; sheet.tabColor=tabColor; const last=col(headers.length);
  sheet.getRange(`A2:${last}2`).merge(); sheet.getRange("A2").values=[[title]]; sheet.getRange("A2").format.font={name:font,size:14,bold:true,color:"#17202A"};
  sheet.getRange(`A3:${last}3`).merge(); sheet.getRange("A3").values=[[note]]; sheet.getRange("A3").format={font:{name:font,size:10,italic:true,color:"#5B6573"},borders:{bottom:{style:"thin",color:"#AAB7C4"}}};
  sheet.getRange(`A4:${last}4`).values=[headers.map(x=>x[1])];
  const values=rows.map(r=>headers.map(x=>r[x[0]]??null));
  if(values.length) sheet.getRange(`A5:${last}${values.length+4}`).values=values;
  sheet.getRange(`A4:${last}4`).format={fill:colors.dark,font:{name:font,size:10,bold:true,color:"#FFFFFF"},horizontalAlignment:"center",verticalAlignment:"center",wrapText:true,rowHeight:30};
  if(values.length){sheet.getRange(`A5:${last}${values.length+4}`).format={font:{name:font,size:10,color:"#1F2937"},verticalAlignment:"center",wrapText:true,rowHeight};sheet.tables.add(`A4:${last}${values.length+4}`,true,tableName).style="TableStyleMedium2";}
  widths.forEach((w,i)=>sheet.getRange(`${col(i+1)}:${col(i+1)}`).format.columnWidth=w);
  sheet.freezePanes.freezeRows(4); sheet.freezePanes.freezeColumns(Math.min(2,headers.length)); return values.length;
}

const overview=wb.worksheets.add("总览");
const domestic=wb.worksheets.add("国内企业名录");
const benchmark=wb.worksheets.add("合并海外企业名录");
const added=wb.worksheets.add("Apollo新增关系");
const merged=wb.worksheets.add("合并关系");
const none=wb.worksheets.add("未新增企业");

const domesticRows=data.domestic_enterprises.map(x=>({...x,apollo_new_count:newByCompany.get(x.enterprise_id)||0,merged_count:8+(newByCompany.get(x.enterprise_id)||0)}));
sheetData(domestic,"国内企业名录","每家保留原8家对标，并追加0至3家Apollo研发人才覆盖候选",
  [["enterprise_id","enterprise_id"],["enterprise_name","企业名称"],["source_names","数据来源"],["industry","行业领域"],["product","核心产品"],["core_need","核心需求"],["technical_area","技术领域"],["apollo_new_count","Apollo新增数"],["merged_count","合并后数量"]],
  domesticRows,[18,32,28,22,30,62,24,16,16],"DomesticApolloMerge","#1F4E78",40);

const benchmarkCount=sheetData(benchmark,"合并海外企业名录","主键：benchmark_id；现有DeepSeek名单与Apollo新发现企业按官网域名去重",
  [["benchmark_id","benchmark_id"],["benchmark_name","海外企业名称"],["official_domain","官网根域名"],["country","国家/地区"],["benchmark_roles","原名单角色"],["relation_count","原关系数"],["apollo_relation_count","Apollo新增关系数"],["apollo_max_focus_engineers","Apollo定向工程师最大数"],["data_source","数据来源"],["verification_status","复核状态"]],
  mergedBenchmarks,[20,32,28,18,30,14,18,20,36,20],"MergedBenchmarks","#4F81BD",34);
benchmark.getRange(`J5:J${benchmarkCount+4}`).dataValidation={rule:{type:"list",values:["AI推荐待人工复核","待人工复核","已确认","排除"]}};
benchmark.getRange(`J5:J${benchmarkCount+4}`).format.fill=colors.amber;

const relationHeaders=[["relation_id","relation_id"],["enterprise_id","enterprise_id"],["enterprise_name","国内企业名称"],["benchmark_id","benchmark_id"],["benchmark_name","海外企业名称"],["official_domain","官网根域名"],["country","国家/地区"],["source_stage","来源阶段"],["relation_type","关系类型"],["rank","阶段内排名"],["query_keyword","研发关键词"],["query_titles","目标职位"],["apollo_focus_engineers","Apollo定向工程师数"],["apollo_sample_engineers","首批样本人数"],["match_reason","匹配理由"],["confidence","置信度"],["relation_review_status","复核状态"]];
const addedCount=sheetData(added,"Apollo新增对标关系","定向工程师数=域名+研发关键词+职位的Apollo total_entries；未获取邮箱",
  relationHeaders,newRelations,[24,18,32,20,32,28,16,34,24,14,20,52,20,16,58,14,18],"ApolloNewRelations","#C65911",38);
added.getRange(`Q5:Q${addedCount+4}`).dataValidation={rule:{type:"list",values:["待复核","已确认","排除"]}}; added.getRange(`Q5:Q${addedCount+4}`).format.fill=colors.amber;
added.getRange(`P5:P${addedCount+4}`).conditionalFormats.add("containsText",{text:"low",format:{fill:colors.red,font:{color:"#9C0006",bold:true}}});

const mergedCount=sheetData(merged,"合并后的国内企业—海外企业关系","原3,920条关系 + Apollo新增关系；联合唯一键：enterprise_id + benchmark_id",
  relationHeaders,normalizedRelations,[24,18,32,20,32,28,16,34,24,14,20,52,20,16,58,14,18],"AllMergedRelations","#7F8C8D",38);
merged.getRange(`Q5:Q${mergedCount+4}`).dataValidation={rule:{type:"list",values:["待复核","已确认","排除"]}}; merged.getRange(`Q5:Q${mergedCount+4}`).format.fill=colors.amber;

sheetData(none,"未新增Apollo候选的国内企业","不等于Apollo没有人才；表示本次首批100条人员样本中没有形成通过复核的新海外企业",
  [["enterprise_id","enterprise_id"],["enterprise_name","企业名称"],["technical_area","技术领域"],["core_need","核心需求"],["reason","未新增原因"]],
  noNew,[18,34,24,66,58],"NoApolloNew","#A5A5A5",42);

overview.showGridLines=false; overview.tabColor=colors.dark;
overview.getRange("A2:J2").merge(); overview.getRange("A2").values=[["海外对标企业名录｜Apollo研发人才覆盖合并版"]]; overview.getRange("A2").format.font={name:font,size:16,bold:true,color:"#17202A"};
overview.getRange("A3:J3").merge(); overview.getRange("A3").values=[["按国内企业研发重点搜索Apollo人员，聚合当前企业，再与原8家名单合并｜2026-09-12"]]; overview.getRange("A3").format={font:{name:font,size:10,italic:true,color:"#5B6573"},borders:{bottom:{style:"thin",color:"#AAB7C4"}}};
overview.getRange("A5:C5").values=[["指标","数量","说明"]];
overview.getRange("A6:C12").values=[["国内企业",data.metadata.domestic_enterprises,"全部企业"],["原对标关系",data.metadata.existing_relations,"每家8家"],["Apollo新增关系",addedCount,"每家最多3家"],["合并关系",mergedCount,"原关系+新增关系"],["合并后海外企业",benchmarkCount,"按官网域名去重"],["获得Apollo新增的国内企业",newByCompany.size,"至少新增1家"],["本轮未新增",noNew.length,"详见“未新增企业”"]];
overview.getRange("A5:C5").format={fill:colors.dark,font:{name:font,bold:true,color:"#FFFFFF"},horizontalAlignment:"center"}; overview.getRange("B6:B12").format.numberFormat="#,##0"; overview.getRange("A5:C12").format.borders={preset:"outside",style:"thin",color:"#AAB7C4"};
overview.getRange("E5:J5").merge(); overview.getRange("E5").values=[["口径"]]; overview.getRange("E5").format={fill:colors.blue,font:{name:font,bold:true},horizontalAlignment:"center"};
overview.getRange("E6:J13").merge(); overview.getRange("E6").values=[[`1. DeepSeek从核心需求提取英文研发关键词和目标职位。\n2. Apollo People Search读取首批最多100名人员，按当前企业聚合。\n3. Apollo公司搜索补齐企业ID和官网域名。\n4. 对候选企业执行“域名+研发关键词+职位”查询，记录total_entries。\n5. DeepSeek复核业务相关性，每家最多保留3家。\n6. 结果是Apollo首批候选池内的优先企业，不代表全库绝对排名。\n7. 本轮邮箱补全=0，邮箱验证=0。`]]; overview.getRange("E6").format={fill:"#F3F6F9",font:{name:font,size:10},wrapText:true,verticalAlignment:"top",borders:{preset:"outside",style:"thin",color:"#D5DCE3"}};
overview.getRange("A15:J15").merge(); overview.getRange("A15").values=[["质量检查"]]; overview.getRange("A15").format={fill:colors.blue,font:{name:font,bold:true}};
overview.getRange("A16:J21").merge(); overview.getRange("A16").values=[[`• Apollo新增关系：${addedCount}条；覆盖${newByCompany.size}/490家国内企业。\n• 新增关系均满足：官网域名存在、Apollo定向工程师数≥1、非中国大陆企业、非高校/研究机构。\n• 合并关系无重复“国内企业+海外企业”组合，所有benchmark_id均能关联主表。\n• high/medium/low为模型业务相关性判断，仍需人工复核。\n• 缺少明确核心需求的企业未强行生成Apollo候选。`]]; overview.getRange("A16").format={fill:"#F3F6F9",font:{name:font,size:10},wrapText:true,verticalAlignment:"top",borders:{preset:"outside",style:"thin",color:"#D5DCE3"}};
overview.getRange("A:A").format.columnWidth=34; overview.getRange("B:B").format.columnWidth=14; overview.getRange("C:C").format.columnWidth=48; overview.getRange("D:D").format.columnWidth=4; overview.getRange("E:J").format.columnWidth=18;

wb.recalculate();
for(const [sheetName,range] of [["总览","A1:J21"],["国内企业名录","A1:I24"],["合并海外企业名录","A1:J24"],["Apollo新增关系","A1:Q24"],["合并关系","A1:Q24"],["未新增企业","A1:E24"]]){
  const png=await wb.render({sheetName,range,format:"png",scale:0.7}); await fs.writeFile(path.join(outputDir,`preview_${sheetName}.png`),new Uint8Array(await png.arrayBuffer()));
}
const errors=await wb.inspect({kind:"match",searchTerm:"#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",options:{useRegex:true,maxResults:200},maxChars:12000}); await fs.writeFile(path.join(outputDir,"formula_errors.txt"),errors.ndjson||String(errors),"utf8");
const inspect=await wb.inspect({kind:"region",sheetId:"总览",range:"A1:J21",maxChars:8000}); await fs.writeFile(path.join(outputDir,"inspect_overview.txt"),inspect.ndjson||String(inspect),"utf8");
const file=await SpreadsheetFile.exportXlsx(wb); const outputPath=path.join(outputDir,"海外对标企业名录_Apollo研发覆盖合并版_20260912.xlsx"); await file.save(outputPath);
console.log(JSON.stringify({outputPath,counts:{domestic:data.domestic_enterprises.length,benchmarks:benchmarkCount,newRelations:addedCount,mergedRelations:mergedCount,noNew:noNew.length}}));
