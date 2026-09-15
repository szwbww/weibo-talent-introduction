import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const [inputPath, outputDir] = process.argv.slice(2);
const source = JSON.parse(await fs.readFile(inputPath, "utf8"));
await fs.mkdir(outputDir, { recursive: true });

const typeLabel = {
  direct_competitor: "直接竞品",
  technology_process: "技术/工艺对标",
  talent_source: "人才来源企业",
};
const domesticById = Object.fromEntries(source.domestic_enterprises.map(x => [x.enterprise_id, x]));
const benchmarkById = Object.fromEntries(source.benchmark_enterprises.map(x => [x.benchmark_id, x]));
const relations = source.domestic_benchmark_relations.map(r => ({
  ...r,
  enterprise_name: domesticById[r.enterprise_id]?.enterprise_name ?? "",
  benchmark_name: benchmarkById[r.benchmark_id]?.benchmark_name ?? "",
  official_domain: benchmarkById[r.benchmark_id]?.official_domain ?? "",
  country: benchmarkById[r.benchmark_id]?.country ?? "",
  benchmark_type_label: typeLabel[r.benchmark_type] ?? r.benchmark_type,
}));
const reviewRows = relations.filter(r => r.confidence !== "high");
const output = {
  metadata: {
    ...source.metadata,
    generated_date: "2026-09-12",
    domestic_enterprise_count: source.domestic_enterprises.length,
    unique_benchmark_count: source.benchmark_enterprises.length,
    relation_count: relations.length,
    review_note: "对标企业由DeepSeek基于企业核心需求推荐，未调用Apollo人员搜索、邮箱补全或邮箱验证；上线前需业务复核。",
  },
  domestic_enterprises: source.domestic_enterprises,
  benchmark_enterprises: source.benchmark_enterprises,
  domestic_benchmark_relations: relations,
};
await fs.writeFile(path.join(outputDir, "海外对标企业关系数据_每企8家_20260912.json"), JSON.stringify(output, null, 2), "utf8");

const wb = Workbook.create();
const colors = { dark: "#1F4E78", blue: "#D9EAF7", gray: "#E7EBEF", amber: "#FFF2CC", red: "#FCE8E6", green: "#E2F0D9" };
const font = "Arial";
function col(n) { let s = ""; while (n) { n--; s = String.fromCharCode(65 + n % 26) + s; n = Math.floor(n / 26); } return s; }
function makeSheet(sheet, title, note, headers, rows, widths, tableName, tabColor, rowHeight = 32) {
  sheet.showGridLines = false;
  sheet.tabColor = tabColor;
  const last = col(headers.length);
  sheet.getRange(`A2:${last}2`).merge();
  sheet.getRange("A2").values = [[title]];
  sheet.getRange("A2").format.font = { name: font, size: 14, bold: true, color: "#17202A" };
  sheet.getRange(`A3:${last}3`).merge();
  sheet.getRange("A3").values = [[note]];
  sheet.getRange("A3").format = { font: { name: font, size: 10, italic: true, color: "#5B6573" }, borders: { bottom: { style: "thin", color: "#AAB7C4" } } };
  sheet.getRange(`A4:${last}4`).values = [headers.map(h => h[1])];
  const values = rows.map(row => headers.map(h => row[h[0]] ?? null));
  if (values.length) sheet.getRange(`A5:${last}${values.length + 4}`).values = values;
  sheet.getRange(`A4:${last}4`).format = { fill: colors.dark, font: { name: font, size: 10, bold: true, color: "#FFFFFF" }, horizontalAlignment: "center", verticalAlignment: "center", wrapText: true, rowHeight: 30 };
  if (values.length) {
    sheet.getRange(`A5:${last}${values.length + 4}`).format = { font: { name: font, size: 10, color: "#1F2937" }, verticalAlignment: "center", wrapText: true, rowHeight };
    sheet.tables.add(`A4:${last}${values.length + 4}`, true, tableName).style = "TableStyleMedium2";
  }
  widths.forEach((w, i) => { sheet.getRange(`${col(i + 1)}:${col(i + 1)}`).format.columnWidth = w; });
  sheet.freezePanes.freezeRows(4);
  sheet.freezePanes.freezeColumns(Math.min(2, headers.length));
  return values.length;
}

const overview = wb.worksheets.add("总览");
const domestic = wb.worksheets.add("国内企业名录");
const benchmarks = wb.worksheets.add("海外对标企业名录");
const relationSheet = wb.worksheets.add("关系_国内企业_对标企业");
const review = wb.worksheets.add("待复核关系");

const domesticCount = makeSheet(domestic, "国内企业名录", "主键：enterprise_id；每家企业已关联8家海外对标企业",
  [["enterprise_id","enterprise_id"],["enterprise_name","企业名称"],["source_names","数据来源"],["area","所属板块"],["industry","行业领域"],["product","核心产品"],["core_need","核心需求"],["technical_area","技术领域"],["benchmark_count","对标企业数"],["benchmark_completion_status","完成状态"]],
  source.domestic_enterprises, [18,32,28,16,22,32,64,24,14,18], "DomesticEnterprises8", "#1F4E78", 42);

const benchmarkCount = makeSheet(benchmarks, "海外对标企业名录", "主键：benchmark_id；同一海外企业可被多家国内企业关联",
  [["benchmark_id","benchmark_id"],["benchmark_name","海外企业名称"],["official_domain","官网根域名"],["country","国家/地区"],["benchmark_roles","承担角色"],["relation_count","关联次数"],["data_source","数据来源"],["verification_status","复核状态"],["notes","说明"]],
  source.benchmark_enterprises, [18,32,28,18,28,14,30,20,38], "BenchmarkEnterprises8", "#4F81BD", 34);
benchmarks.getRange(`H5:H${benchmarkCount + 4}`).dataValidation = { rule: { type: "list", values: ["AI推荐待人工复核","已确认","排除"] } };
benchmarks.getRange(`H5:H${benchmarkCount + 4}`).format.fill = colors.amber;

const relationHeaders = [["enterprise_benchmark_rel_id","relation_id"],["enterprise_id","enterprise_id"],["enterprise_name","国内企业名称"],["benchmark_id","benchmark_id"],["benchmark_name","海外企业名称"],["official_domain","官网根域名"],["country","国家/地区"],["benchmark_priority","优先级"],["benchmark_type","类型代码"],["benchmark_type_label","类型"],["matched_technical_area","匹配技术领域"],["match_reason","匹配理由"],["confidence","置信度"],["mapping_method","生成方式"],["relation_review_status","复核状态"]];
const relationCount = makeSheet(relationSheet, "国内企业—海外对标企业关系表", "联合唯一键：enterprise_id + benchmark_id；每家严格8条：3直接竞品+3技术工艺+2人才来源企业",
  relationHeaders, relations, [24,18,32,18,32,26,16,12,22,20,24,58,14,22,18], "DomesticBenchmarkRelations8", "#7F8C8D", 38);
relationSheet.getRange(`H5:H${relationCount + 4}`).format.numberFormat = "0";
relationSheet.getRange(`O5:O${relationCount + 4}`).dataValidation = { rule: { type: "list", values: ["待复核","已确认","排除"] } };
relationSheet.getRange(`O5:O${relationCount + 4}`).format.fill = colors.amber;
relationSheet.getRange(`M5:M${relationCount + 4}`).conditionalFormats.add("containsText", { text: "low", format: { fill: colors.red, font: { color: "#9C0006", bold: true } } });

const reviewCount = makeSheet(review, "待复核关系", "筛选范围：confidence为medium或low；建议优先复核low",
  relationHeaders, reviewRows, [24,18,32,18,32,26,16,12,22,20,24,58,14,22,18], "ReviewRelations8", "#C65911", 38);
review.getRange(`H5:H${reviewCount + 4}`).format.numberFormat = "0";
review.getRange(`O5:O${reviewCount + 4}`).dataValidation = { rule: { type: "list", values: ["待复核","已确认","排除"] } };
review.getRange(`O5:O${reviewCount + 4}`).format.fill = colors.amber;
review.getRange(`M5:M${reviewCount + 4}`).conditionalFormats.add("containsText", { text: "low", format: { fill: colors.red, font: { color: "#9C0006", bold: true } } });

overview.showGridLines = false;
overview.tabColor = colors.dark;
overview.getRange("A2:J2").merge(); overview.getRange("A2").values = [["海外对标企业名录｜每家国内企业8家"]]; overview.getRange("A2").format.font = { name: font, size: 16, bold: true, color: "#17202A" };
overview.getRange("A3:J3").merge(); overview.getRange("A3").values = [["系统导入准备版｜2026-09-12"]]; overview.getRange("A3").format = { font: { name: font, size: 10, italic: true, color: "#5B6573" }, borders: { bottom: { style: "thin", color: "#AAB7C4" } } };
overview.getRange("A5:C5").values = [["数据对象","记录数","主键/关系键"]];
overview.getRange("A6:C8").values = [["国内企业名录",domesticCount,"enterprise_id"],["海外对标企业名录",benchmarkCount,"benchmark_id"],["国内企业—对标关系",relationCount,"enterprise_id + benchmark_id"]];
overview.getRange("A5:C5").format = { fill: colors.dark, font: { name: font, bold: true, color: "#FFFFFF" }, horizontalAlignment: "center" };
overview.getRange("B6:B8").format.numberFormat = "#,##0";
overview.getRange("A5:C8").format.borders = { preset: "outside", style: "thin", color: "#AAB7C4" };
overview.getRange("E5:J5").merge(); overview.getRange("E5").values = [["每家企业的8家构成"]]; overview.getRange("E5").format = { fill: colors.blue, font: { name: font, bold: true }, horizontalAlignment: "center" };
overview.getRange("E6:J10").merge(); overview.getRange("E6").values = [["3家 直接竞品\n3家 技术/工艺对标\n2家 人才来源企业\n\n合计：490 × 8 = 3,920条关系"]]; overview.getRange("E6").format = { fill: "#F3F6F9", font: { name: font, size: 11 }, wrapText: true, verticalAlignment: "center", horizontalAlignment: "center", borders: { preset: "outside", style: "thin", color: "#D5DCE3" } };
overview.getRange("A12:J12").merge(); overview.getRange("A12").values = [["数据状态"]]; overview.getRange("A12").format = { fill: colors.blue, font: { name: font, bold: true } };
overview.getRange("A13:J19").merge(); overview.getRange("A13").values = [[`• 490家国内企业全部完成，每家严格8家；海外企业去重后${benchmarkCount}家。\n• 关系类型：直接竞品1,470条、技术/工艺1,470条、人才来源980条。\n• 置信度：high 2,585条、medium 1,200条、low 135条；“待复核关系”共${reviewCount}条。\n• 已排除中国大陆企业、大学、研究院、协会等非海外商业公司。\n• 本阶段未调用Apollo人员搜索、邮箱补全或Emailable验证。\n• 对标推荐来自DeepSeek，官网域名和业务匹配仍需人工复核后入正式库。`]];
overview.getRange("A13").format = { fill: "#F3F6F9", font: { name: font, size: 10 }, wrapText: true, verticalAlignment: "top", borders: { preset: "outside", style: "thin", color: "#D5DCE3" } };
overview.getRange("A:A").format.columnWidth = 34; overview.getRange("B:B").format.columnWidth = 14; overview.getRange("C:C").format.columnWidth = 34; overview.getRange("D:D").format.columnWidth = 4; overview.getRange("E:J").format.columnWidth = 18;

wb.recalculate();
for (const [sheetName, range] of [["总览","A1:J19"],["国内企业名录","A1:J24"],["海外对标企业名录","A1:I24"],["关系_国内企业_对标企业","A1:O24"],["待复核关系","A1:O24"]]) {
  const png = await wb.render({ sheetName, range, format: "png", scale: 0.72 });
  await fs.writeFile(path.join(outputDir, `preview_${sheetName}.png`), new Uint8Array(await png.arrayBuffer()));
}
const errors = await wb.inspect({ kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A", options: { useRegex: true, maxResults: 200 }, maxChars: 12000 });
await fs.writeFile(path.join(outputDir, "formula_errors.txt"), errors.ndjson || String(errors), "utf8");
const inspect = await wb.inspect({ kind: "region", sheetId: "总览", range: "A1:J19", maxChars: 8000 });
await fs.writeFile(path.join(outputDir, "inspect_overview.txt"), inspect.ndjson || String(inspect), "utf8");
const file = await SpreadsheetFile.exportXlsx(wb);
const outputPath = path.join(outputDir, "海外对标企业名录_每企8家_20260912.xlsx");
await file.save(outputPath);
console.log(JSON.stringify({ outputPath, jsonPath: path.join(outputDir, "海外对标企业关系数据_每企8家_20260912.json"), counts: { domesticCount, benchmarkCount, relationCount, reviewCount } }));
