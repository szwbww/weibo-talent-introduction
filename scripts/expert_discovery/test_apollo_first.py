"""Apollo-first tests. Every test is offline and uses a generated workbook."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from openpyxl import Workbook

from adapters import Providers
from cli import main
from core import Job


HEADERS = ["序号", "企业名称", "企业研究方向", "当前亟需解决的难点", "岗位名称", "岗位职责",
           "专业领域", "工作经验", "工作履历", "年龄", "薪资待遇\n（年薪）", "备注"]
VALUES = [1, "沪士电子股份有限公司", "玻璃封装基板", "/", "研发副总",
          "主导开发过程与工艺瓶颈突破", "硅中介层、CoWoS、玻璃基板、RDL线宽、翘曲控制、热应力匹配",
          "/", "应届往届皆可", "不限", "面议", "不限国籍"]


def feature_api(test):
    try:
        from excel_input import build_apollo_config, load_requirement
        from workflow import plan_proposal
    except (ImportError, AttributeError) as exc:
        test.fail("Apollo-first feature missing: " + str(exc))
    return build_apollo_config, load_requirement, plan_proposal


class ApolloFirstTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.xlsx = Path(self.directory.name) / "needs.xlsx"
        wb = Workbook()
        ws = wb.active
        ws.title = "Sheet1"
        ws.append(["企业高端人才需求表"])
        ws.append(["序号", "企业信息", None, None, "高端人才需求"])
        ws.append(HEADERS)
        ws.append(VALUES)
        wb.save(self.xlsx)

    def test_reads_requirement_by_header_and_row(self):
        _, load_requirement, _ = feature_api(self)
        row = load_requirement(self.xlsx, 4)
        self.assertEqual(row["企业名称"], "沪士电子股份有限公司")
        self.assertEqual(row["专业领域"], VALUES[6])
        self.assertEqual(row["source"]["sheet"], "Sheet1")
        self.assertEqual(row["source"]["row"], 4)

    def test_excel_plan_spends_nothing_and_targets_people_search(self):
        build_config, load_requirement, plan_proposal = feature_api(self)
        config = build_config(load_requirement(self.xlsx, 4), "Prysmian Group", "prysmiangroup.com")
        job = Job(Path(self.directory.name) / "job")
        job.create(config)
        proposal = plan_proposal(job)
        self.assertEqual(config["strategy"], "APOLLO_PEOPLE")
        self.assertEqual(proposal["limits"], {
            "deepseek.profile": 1, "apollo.search": 1, "deepseek.rank": 1})
        self.assertEqual(job.data["requests"], {})
        self.assertNotIn("apollo.organization", proposal["limits"])

    def test_discovery_searches_people_only_and_never_returns_email(self):
        build_config, load_requirement, plan_proposal = feature_api(self)
        config = build_config(load_requirement(self.xlsx, 4), "Prysmian Group", "prysmiangroup.com")
        fixtures = {"responses": {
            "deepseek.profile": [{"result": {"profile": {
                "topics": ["glass substrate", "CoWoS", "warpage control"],
                "titles": ["R&D Director", "Process Development Manager"],
                "seniorities": ["director", "manager"], "countries": []},
                "usage": {"total_tokens": 100}}}],
            "apollo.search": [{"result": {"people": [
                {"id": "p1", "name": "Alice Example", "title": "R&D Director", "country": "Italy",
                 "organization": {"name": "Prysmian Group", "primary_domain": "prysmiangroup.com"},
                 "email": "must-not-be-used@example.org"},
                {"id": "p2", "name": "Bob Example", "title": "Process Development Manager", "country": "France",
                 "organization": {"name": "Prysmian Group", "primary_domain": "prysmiangroup.com"}}
            ]}}],
            "deepseek.rank": [{"result": {"rankings": [
                {"id": "p2", "score": 91, "matched_requirements": ["工艺开发"],
                 "reason": "职位接近工艺研发需求", "limitations": ["缺少项目证据"]},
                {"id": "p1", "score": 75, "matched_requirements": ["研发管理"],
                 "reason": "职位接近研发管理需求", "limitations": ["技术方向未验证"]}
            ], "usage": {"total_tokens": 100}}}]
        }}
        job = Job(Path(self.directory.name) / "job")
        job.create(config, fixtures)
        proposal = plan_proposal(job)
        job.approve(proposal["id"])
        from workflow import execute
        with patch("urllib.request.OpenerDirector.open", side_effect=AssertionError("NETWORK_FORBIDDEN")):
            execute(job, proposal["id"], "discover", Providers({}, fixtures))
        self.assertEqual([r["kind"] for r in job.data["requests"].values()],
                         ["deepseek.profile", "apollo.search", "deepseek.rank"])
        self.assertEqual([c["apollo_id"] for c in job.data["candidates"]], ["p2", "p1"])
        self.assertTrue(all(not c["emails"] for c in job.data["candidates"]))
        self.assertTrue(all("email" not in c["provider_profiles"][0]["profile"]
                            for c in job.data["candidates"]))
        search_log = next(r for r in job.data["requests"].values() if r["kind"] == "apollo.search")
        self.assertNotIn("email", search_log["response"]["people"][0])
        self.assertEqual(job.data["candidates"][0]["ranking"]["score"], 91)

    def test_cli_builds_plan_from_excel_without_api_calls(self):
        feature_api(self)
        target = Path(self.directory.name) / "cli-job"
        with patch("builtins.print"):
            rc = main(["--job", str(target), "plan", "--excel", str(self.xlsx), "--row", "4",
                       "--benchmark", "Prysmian Group", "--domain", "prysmiangroup.com"])
        self.assertEqual(rc, 0)
        job = Job(target)
        self.assertEqual(job.data["config"]["requirement"]["岗位名称"], "研发副总")
        self.assertEqual(job.data["requests"], {})

    def test_live_apollo_discovery_requires_both_keys(self):
        build_config, load_requirement, plan_proposal = feature_api(self)
        job = Job(Path(self.directory.name) / "job")
        job.create(build_config(load_requirement(self.xlsx, 4), "Prysmian Group", "prysmiangroup.com"))
        proposal = plan_proposal(job)
        job.approve(proposal["id"])
        from core import Stop
        from workflow import execute
        with patch.object(Providers, "call") as call, self.assertRaises(Stop):
            execute(job, proposal["id"], "discover", Providers({"DEEPSEEK_API_KEY": "fake"}))
        call.assert_not_called()

    def test_people_from_another_current_employer_are_excluded(self):
        build_config, load_requirement, plan_proposal = feature_api(self)
        config = build_config(load_requirement(self.xlsx, 4), "Prysmian Group", "prysmiangroup.com")
        fixtures = {"responses": {
            "deepseek.profile": [{"result": {"profile": {"topics": ["glass substrate"],
                "titles": ["R&D Director"], "seniorities": ["director"], "countries": []}}}],
            "apollo.search": [{"result": {"people": [
                {"id": "former", "name": "Former Employee", "title": "R&D Director",
                 "organization": {"name": "Different Company", "primary_domain": "different.example"}}
            ]}}],
        }}
        job = Job(Path(self.directory.name) / "job")
        job.create(config, fixtures)
        proposal = plan_proposal(job)
        job.approve(proposal["id"])
        from workflow import execute
        execute(job, proposal["id"], "discover", Providers({}, fixtures))
        self.assertEqual(job.data["candidates"], [])
        self.assertEqual([r["kind"] for r in job.data["requests"].values()],
                         ["deepseek.profile", "apollo.search"])

    def test_apollo_brand_suffix_and_obfuscated_name_are_supported(self):
        from workflow import apollo_candidate, matches_current_benchmark
        config = {"company": "Prysmian Group", "domain": "prysmian.com",
                  "aliases": ["Prysmian Group"]}
        person = {"id": "p1", "first_name": "Paulina", "last_name_obfuscated": "Ca***a",
                  "title": "R&D Manager", "organization": {"name": "Prysmian"}}
        self.assertTrue(matches_current_benchmark(person, config))
        candidate = apollo_candidate(person, "test", full=False)
        self.assertEqual(candidate["name"], "Paulina Ca***a")
        self.assertEqual(candidate["provider_profiles"][0]["profile"]["last_name_obfuscated"], "Ca***a")

    def test_review_shows_rank_reason_and_limitations(self):
        from workflow import render
        build_config, load_requirement, _ = feature_api(self)
        job = Job(Path(self.directory.name) / "job")
        job.create(build_config(load_requirement(self.xlsx, 4), "Prysmian Group", "prysmiangroup.com"))
        job.data["candidates"] = [{"id": "P-1", "name": "Alice", "apollo_id": "a1",
            "linkedin_url": "", "country": "Italy", "title": "R&D Director", "evidence": [],
            "emails": [], "issues": [], "provider_profiles": [], "source_origins": [],
            "merged_ids": [], "ranking": {"score": 88, "reason": "职位匹配",
                "matched_requirements": ["研发管理"], "limitations": ["缺少项目证据"]}}]
        text, rows = render(job)
        self.assertIn("匹配分：88", text)
        self.assertIn("职位匹配", text)
        self.assertIn("缺少项目证据", text)
        self.assertEqual(rows[0]["fit_score"], 88)


if __name__ == "__main__":
    unittest.main()
