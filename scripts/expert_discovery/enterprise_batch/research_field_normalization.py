"""English-only primary research-field normalization for enterprise R&D experts."""

import re
from typing import Tuple


PENDING_LABEL = "待分类"
CJK_RE = re.compile(r"[\u3400-\u9fff]")

# Values in ``matched_technical_area`` are internal Chinese demand labels.  They
# must never be written into the candidate's externally visible research field.
TECHNICAL_AREA_TO_ENGLISH = {
    "工业自动化": "Industrial Automation",
    "热管理与换热": "Thermal Management and Heat Transfer",
    "人工智能与工业软件": "Artificial Intelligence and Industrial Software",
    "金属材料与加工": "Metal Materials and Processing",
    "新能源汽车电驱": "Electric Drivetrains for New Energy Vehicles",
    "芯片设计与EDA": "Integrated Circuit Design and Electronic Design Automation",
    "半导体设备": "Semiconductor Manufacturing Equipment",
    "航空航天": "Aerospace Engineering",
    "汽车零部件": "Automotive Components Engineering",
    "光伏电池与设备": "Photovoltaic Cells and Manufacturing Equipment",
    "碳纤维复合材料": "Carbon Fiber Composites",
    "PCB与电子互连": "Printed Circuit Boards and Electronic Interconnects",
    "电线电缆": "Wire and Cable Engineering",
    "电池与储能": "Battery and Energy Storage",
    "机床与精密加工": "Machine Tools and Precision Manufacturing",
    "生物医药": "Biopharmaceuticals",
    "工业材料研发": "Industrial Materials Research",
    "射频与通信": "Radio Frequency and Communications",
    "高性能纤维纺织": "High-Performance Fibers and Textiles",
    "光通信": "Optical Communications",
    "工业机器人": "Industrial Robotics",
    "涂料与表面处理": "Coatings and Surface Engineering",
    "电力电子": "Power Electronics",
    "精密模具与注塑": "Precision Tooling and Injection Molding",
    "激光加工": "Laser Processing",
    "医疗器械": "Medical Devices",
    "高分子与化工材料": "Polymer and Chemical Materials",
    "半导体封装": "Semiconductor Packaging",
    "半导体材料": "Semiconductor Materials",
    "半导体检测量测": "Semiconductor Test and Metrology",
    "光学与光子": "Optics and Photonics",
    "压缩机与气体设备": "Compressors and Gas Equipment",
    "包装": "Packaging Engineering",
    "分析仪器": "Analytical Instrumentation",
    "功能薄膜与胶黏剂": "Functional Films and Adhesives",
    "环保与水处理": "Environmental Engineering and Water Treatment",
    "密封与轴承": "Sealing and Bearing Technology",
    "农业生物技术": "Agricultural Biotechnology",
}


def _infer_from_title(title: str) -> str:
    """Give unclassified records a broad but English, evidence-based field."""
    value = (title or "").strip()
    lower = value.lower()
    rules = (
        (("machine learning", "artificial intelligence", "computer vision", "image processing", "algorithm", "software"),
         "Artificial Intelligence and Software Engineering"),
        (("semiconductor", "mems", "chip", "eda"), "Semiconductor Engineering"),
        (("battery", "energy storage"), "Battery and Energy Storage"),
        (("cable",), "Wire and Cable Engineering"),
        (("packaging",), "Packaging Engineering"),
        (("optical", "photon", "laser", "image processing"), "Optics and Photonics"),
        (("rf", "radar", "acoustic", "communication"), "Radio Frequency and Communications"),
        (("material", "polymer", "chemical", "chemist", "formulation", "coating"),
         "Materials Science and Chemical Engineering"),
        (("protein", "antibody", "biolog", "pharma", "drug"), "Biopharmaceutical Research"),
        (("thermal", "heat transfer"), "Thermal Management and Heat Transfer"),
        (("mechanical", "molding", "tooling", "manufacturing"), "Mechanical and Manufacturing Engineering"),
        (("electrical", "electronics", "hardware", "power"), "Electrical and Electronic Engineering"),
        (("process",), "Process Engineering"),
        (("failure analysis", "reliability"), "Reliability and Failure Analysis Engineering"),
        (("automation", "control", "robot"), "Industrial Automation"),
        (("environmental", "water"), "Environmental Engineering and Water Treatment"),
        (("system",), "Systems Engineering"),
    )
    for tokens, field in rules:
        if any(token in lower for token in tokens):
            return field
    return "Research and Development Engineering"


def _looks_like_job_title(value: str) -> bool:
    lower = value.lower()
    return any(token in lower for token in (
        "engineer", "scientist", "director", "chief", "principal", "senior", "staff ",
        "lead", "associate director", "technologies", "therapeutics",
    ))


def primary_research_field(technical_areas, title: str = "") -> Tuple[str, str]:
    """Return ``(english_field, source_rule)`` for one expert.

    The first known domain label wins because it is the strongest explicit
    relationship signal.  ``待分类`` is intentionally ignored.
    """
    for area in technical_areas:
        label = (area or "").strip()
        if label in TECHNICAL_AREA_TO_ENGLISH:
            return TECHNICAL_AREA_TO_ENGLISH[label], "mapped_technical_area"
        if label and label != PENDING_LABEL and not CJK_RE.search(label):
            if _looks_like_job_title(label):
                return _infer_from_title(title or label), "title_inference"
            return label, "existing_english_area"
    return _infer_from_title(title), "title_inference"


def assert_english_research_field(value: str) -> None:
    if not value or CJK_RE.search(value) or PENDING_LABEL in value:
        raise ValueError(f"researchFields must be non-empty English text: {value!r}")
