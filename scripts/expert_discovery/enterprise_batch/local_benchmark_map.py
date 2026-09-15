#!/usr/bin/env python3
"""Map core needs to public overseas benchmarks without sending source data outside."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


RULES = [
    ("半导体检测量测", r"扫描电镜|晶圆检测|半导体.*量测|工业CT|X射线", "KLA", "kla.com", ["Process Engineer", "Application Engineer", "Metrology Engineer", "R&D Engineer"]),
    ("半导体设备", r"半导体设备|晶圆.*设备|封装设备|光刻|刻蚀|薄膜沉积", "Applied Materials", "appliedmaterials.com", ["Process Engineer", "Mechanical Engineer", "R&D Engineer", "Engineering Manager"]),
    ("芯片设计与EDA", r"FPGA|EDA|SoC|芯片设计|集成电路设计|射频前端芯片", "Synopsys", "synopsys.com", ["Principal Engineer", "R&D Engineer", "Application Engineer", "Engineering Director"]),
    ("半导体封装", r"芯片封测|半导体封装|先进封装|CoWoS|RDL|玻璃封装基板|封装基板", "ASE Technology", "aseglobal.com", ["Packaging Engineer", "Process Engineer", "R&D Manager", "Technical Director"]),
    ("半导体材料", r"湿电子化学品|光刻胶|电子化学品|半导体材料|溅射靶材|晶圆材料", "Merck KGaA", "merckgroup.com", ["Materials Scientist", "Process Development Engineer", "R&D Manager", "Principal Scientist"]),
    ("PCB与电子互连", r"印刷电路板|\bPCB\b|覆铜板|线路板|高速连接器|电子连接器|互连", "Amphenol", "amphenol.com", ["Connector Design Engineer", "Signal Integrity Engineer", "Manufacturing Engineer", "R&D Manager"]),
    ("光通信", r"光模块|光通信|光纤传输|光器件|硅光", "Lumentum", "lumentum.com", ["Optical Engineer", "Photonics Engineer", "Process Engineer", "R&D Director"]),
    ("电线电缆", r"电缆|线缆|光纤光缆|导线", "Prysmian Group", "prysmian.com", ["Cable Design Engineer", "Materials Engineer", "Process Engineer", "R&D Manager"]),
    ("工业自动化", r"自动化设备|非标自动化|智能生产线|工业机器人|机器视觉|智能检测|装配线", "Siemens", "siemens.com", ["Automation Engineer", "Controls Engineer", "Machine Vision Engineer", "Engineering Manager"]),
    ("工业机器人", r"机器人关节|人形机器人|协作机器人|机器人本体|机器人控制", "ABB Robotics", "abb.com", ["Robotics Engineer", "Controls Engineer", "Mechanical Design Engineer", "R&D Manager"]),
    ("精密测量", r"精密测量|三坐标|尺寸测量|机器视觉检测|光学检测", "Hexagon Manufacturing Intelligence", "hexagon.com", ["Metrology Engineer", "Optical Engineer", "Application Engineer", "R&D Manager"]),
    ("分析仪器", r"质谱|色谱|光谱仪|实验室仪器|分析仪器", "Thermo Fisher Scientific", "thermofisher.com", ["Mass Spectrometry Scientist", "Instrument Engineer", "R&D Scientist", "Engineering Manager"]),
    ("机床与精密加工", r"数控机床|加工中心|磨床|五轴|精密加工|磨削|铣削|车削", "DMG MORI", "dmgmori.com", ["Machine Tool Engineer", "Manufacturing Engineer", "Mechanical Design Engineer", "R&D Manager"]),
    ("激光加工", r"激光切割|激光焊接|超快激光|飞秒|皮秒|激光加工", "TRUMPF", "trumpf.com", ["Laser Applications Engineer", "Optical Engineer", "Process Engineer", "R&D Manager"]),
    ("压缩机与气体设备", r"压缩机|真空泵|真空设备|气体压缩", "Atlas Copco", "atlascopco.com", ["Compressor Engineer", "Mechanical Engineer", "R&D Engineer", "Engineering Manager"]),
    ("热管理与换热", r"换热|散热|热管理|冷却系统|热泵|空调|制冷", "Daikin", "daikin.com", ["Thermal Engineer", "HVAC Engineer", "Heat Exchanger Engineer", "R&D Manager"]),
    ("电力电子", r"光伏逆变器|储能变流器|电能质量|电力电子|变频器", "SMA Solar Technology", "sma.de", ["Power Electronics Engineer", "Control Systems Engineer", "R&D Engineer", "Engineering Manager"]),
    ("光伏电池与设备", r"光伏|太阳能电池|钙钛矿|异质结|组件网版", "Meyer Burger", "meyerburger.com", ["Solar Cell Process Engineer", "Equipment Engineer", "Materials Scientist", "R&D Manager"]),
    ("电池与储能", r"锂电|电池材料|电芯|储能系统|电池托盘|电池管理", "Panasonic Energy", "panasonic.com", ["Battery Engineer", "Cell Process Engineer", "Materials Scientist", "R&D Manager"]),
    ("新能源汽车电驱", r"电驱动|新能源汽车|电机控制器|混合动力|纯电系统|线控底盘", "Bosch", "bosch.com", ["Electric Drive Engineer", "Power Electronics Engineer", "Vehicle Controls Engineer", "Engineering Manager"]),
    ("汽车零部件", r"汽车零部件|汽车座椅|汽车车灯|点火线圈|汽车内饰|汽车冲压|汽车底盘", "Magna International", "magna.com", ["Product Development Engineer", "Manufacturing Engineer", "Automotive Design Engineer", "Engineering Manager"]),
    ("航空航天", r"航天|航空|卫星|无人机|太空|热真空", "Honeywell Aerospace", "honeywell.com", ["Aerospace Engineer", "Systems Engineer", "Test Engineer", "Engineering Manager"]),
    ("碳纤维复合材料", r"碳纤维|复合材料|预浸料|SMC|LFT-D|陶瓷基复合", "Hexcel", "hexcel.com", ["Composite Materials Engineer", "Process Engineer", "Research Scientist", "R&D Manager"]),
    ("高性能纤维纺织", r"纺织|织造|纱线|化纤|丝绸|面料|无纺布|涡流纺", "Toray Industries", "toray.com", ["Textile Engineer", "Polymer Scientist", "Process Engineer", "R&D Manager"]),
    ("功能薄膜与胶黏剂", r"功能性薄膜|光学膜|保护膜|胶带|胶黏剂|粘合剂|涂覆", "3M", "3m.com", ["Polymer Scientist", "Adhesive Scientist", "Process Engineer", "R&D Manager"]),
    ("涂料与表面处理", r"涂料|涂层|表面处理|喷涂|电镀|喷丸|喷砂", "AkzoNobel", "akzonobel.com", ["Coatings Scientist", "Surface Treatment Engineer", "Process Engineer", "R&D Manager"]),
    ("高分子与化工材料", r"高分子|改性塑料|工程塑料|聚氨酯|橡胶|硅胶|树脂|化工新材料", "BASF", "basf.com", ["Polymer Scientist", "Materials Scientist", "Process Engineer", "R&D Manager"]),
    ("金属材料与加工", r"铝合金|金属材料|合金材料|压铸|锻造|冲压|钣金|冷弯成型|焊接", "Constellium", "constellium.com", ["Metallurgical Engineer", "Process Engineer", "Manufacturing Engineer", "R&D Manager"]),
    ("精密模具与注塑", r"精密模具|模具设计|注塑|模塑|冲压模|级进模", "Nypro", "jabil.com", ["Mold Design Engineer", "Injection Molding Engineer", "Tooling Engineer", "Engineering Manager"]),
    ("密封与轴承", r"密封件|油封|O型环|轴承", "SKF", "skf.com", ["Sealing Engineer", "Tribology Engineer", "Product Development Engineer", "R&D Manager"]),
    ("包装", r"无菌包装|包装材料|纸质容器|印刷包装|复合包装", "Tetra Pak", "tetrapak.com", ["Packaging Engineer", "Materials Engineer", "Process Engineer", "R&D Manager"]),
    ("光学与光子", r"光学元件|光子|透镜|全息|液晶光取向|光电", "ZEISS", "zeiss.com", ["Optical Engineer", "Photonics Engineer", "Process Engineer", "R&D Manager"]),
    ("射频与通信", r"天线|射频|通信网络|5G|无线通信|卫星通信", "Ericsson", "ericsson.com", ["RF Engineer", "Antenna Engineer", "Systems Engineer", "R&D Manager"]),
    ("人工智能与工业软件", r"大语言模型|知识图谱|人工智能|工业软件|数字孪生|GIS|空间计算", "IBM", "ibm.com", ["Research Scientist", "Machine Learning Engineer", "Software Architect", "R&D Manager"]),
    ("生物医药", r"ADC药物|抗体偶联|新药|制药|原料药|抗生素|药物载体|临床研究", "Roche", "roche.com", ["Principal Scientist", "Process Development Scientist", "Formulation Scientist", "R&D Director"]),
    ("医疗器械", r"医疗器械|医学影像|核医学|体外诊断|诊断设备", "Siemens Healthineers", "siemens-healthineers.com", ["Medical Device Engineer", "Systems Engineer", "R&D Engineer", "Engineering Manager"]),
    ("农业生物技术", r"生物育种|种子|农业生物|植物育种", "Corteva Agriscience", "corteva.com", ["Plant Scientist", "Breeding Scientist", "Research Scientist", "R&D Manager"]),
    ("环保与水处理", r"水处理|污水|环境治理|湿地修复|废气|VOCs|污染物|资源再生", "Veolia", "veolia.com", ["Environmental Engineer", "Water Process Engineer", "Research Scientist", "R&D Manager"]),
    ("能源装备", r"变压器|电气设备|电网|电力设备|高压设备", "Hitachi Energy", "hitachienergy.com", ["Electrical Engineer", "Transformer Design Engineer", "Thermal Engineer", "R&D Manager"]),
    ("工业材料研发", r"新材料|功能材料|材料改性|材料合成|纳米材料|陶瓷材料", "Dow", "dow.com", ["Materials Scientist", "Research Scientist", "Process Development Engineer", "R&D Manager"]),
]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--companies", required=True)
    p.add_argument("--output", required=True)
    args = p.parse_args()
    companies = json.loads(Path(args.companies).read_text(encoding="utf-8"))["企业"]
    mappings = []
    counts = {}
    for c in companies:
        text = c["企业名称"] + "；" + c["核心需求"]
        hit = None
        for rule in RULES:
            if re.search(rule[1], text, re.I):
                hit = rule
                break
        if hit:
            area, _, company, domain, titles = hit
            counts[area] = counts.get(area, 0) + 1
            mappings.append({
                "company_id": c["企业编号"],
                "need_summary": c["核心需求"][:120],
                "technical_area": area,
                "benchmark_company": company,
                "benchmark_domain": domain,
                "benchmark_reason": f"核心需求命中{area}，以该领域成熟海外企业作为首轮人才检索池",
                "titles": titles,
                "mapping_method": "本地关键词规则",
            })
        else:
            mappings.append({
                "company_id": c["企业编号"], "need_summary": c["核心需求"][:120],
                "technical_area": "待分类", "benchmark_company": "", "benchmark_domain": "",
                "benchmark_reason": "现有规则无法可靠匹配，待人工或后续模型复核", "titles": [],
                "mapping_method": "本地关键词规则",
            })
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(mappings, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"companies": len(companies), "mapped": sum(bool(x["benchmark_domain"]) for x in mappings),
                      "unmapped": sum(not x["benchmark_domain"] for x in mappings),
                      "areas": counts}, ensure_ascii=False))


if __name__ == "__main__":
    main()
