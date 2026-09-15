#!/usr/bin/env python3
"""Run from any directory. Stage boundaries are explicit local approvals, not prompts."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from adapters import Providers
from core import Job, Stop, job_lock, keys_from, read_json
from excel_input import build_apollo_config, load_requirement
from workflow import (execute, export, import_existing, mark_conflicts, merge_into, plan_proposal,
                      proposal_enrich, proposal_search, proposal_verify, render, selected, validate_config)

ROOT = Path(__file__).resolve().parent


def parser():
    p = argparse.ArgumentParser(description="按需求表和对标企业搜索专家；每个调用阶段单独审批。")
    p.add_argument("--job", required=True, help="任务目录，例如 scripts/expert_discovery/runs/prysmian")
    p.add_argument("--keys", help="本地 properties 文件；也支持环境变量，仅读取所用服务的 Key")
    sub = p.add_subparsers(dest="command", required=True)
    plan = sub.add_parser("plan", help="读取需求并生成固定预算计划，不联网")
    plan.add_argument("--config", help="旧版公开来源模式配置 JSON")
    plan.add_argument("--excel", help="企业人才需求表 .xlsx")
    plan.add_argument("--row", type=int, help="需求所在 Excel 行号")
    plan.add_argument("--sheet", help="工作表名称；默认首个工作表")
    plan.add_argument("--benchmark", help="对标企业名称")
    plan.add_argument("--domain", help="对标企业官网域名，不带协议")
    plan.add_argument("--demo", action="store_true", help="固定使用打包的虚构数据，绝不联网")
    sub.add_parser("review", help="查看名单、依据、邮箱验证建议和全部提案")
    sub.add_parser("export", help="导出本地审核文件；不写 ES")
    discover_plan = sub.add_parser("propose", help="提出下一阶段动作；不调用服务")
    discover_plan.add_argument("action", choices=["discover", "enrich", "apollo-search", "verify"])
    discover_plan.add_argument("--ids", nargs="+")
    discover_plan.add_argument("--emails", nargs="+", default=[])
    discover_plan.add_argument("--reason", help="为什么要补全/验证这些具体对象")
    for action in ("approve", "cancel"):
        command = sub.add_parser(action)
        command.add_argument("--proposal", required=True)
        command.add_argument("--confirm", action="store_true", required=True,
                             help="表示操作者已查看该提案及额度上限")
    for action in ("discover", "enrich", "apollo-search", "verify"):
        command = sub.add_parser(action)
        command.add_argument("--proposal", required=True)
    imp = sub.add_parser("import", help="导入旧公开资料 NDJSON 或 LIVE Apollo checkpoint，不联网")
    imp.add_argument("--file", required=True)
    merge = sub.add_parser("merge", help="人工确认两个记录属于同一人；使旧审批失效")
    merge.add_argument("--ids", nargs=2, required=True)
    merge.add_argument("--reason", required=True)
    merge.add_argument("--confirm", action="store_true", required=True)
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        with job_lock(args.job):
            job = Job(args.job)
            if args.command == "plan":
                fixture = read_json(ROOT / "demo.json") if args.demo else None
                if fixture:
                    config = fixture["config"]
                elif args.excel:
                    if args.row is None:
                        raise Stop("使用 --excel 时必须提供 --row。")
                    config = build_apollo_config(load_requirement(args.excel, args.row, args.sheet),
                                                 args.benchmark, args.domain)
                else:
                    config = read_json(args.config or ROOT / "config.example.json")
                job.create(validate_config(config), fixture)
                proposal = plan_proposal(job)
                print("DEMO：全部为虚构测试数据。" if fixture else "LIVE计划：尚未联网，尚未调用任何API。")
                print(json.dumps(proposal, ensure_ascii=False, indent=2))
            else:
                job.require()
                if args.command == "propose":
                    if args.action != "discover" and not (args.reason and args.reason.strip()):
                        raise Stop("必须填写 --reason，说明为什么使用额度。")
                    if args.action == "discover":
                        proposal = plan_proposal(job)
                    elif args.action == "enrich":
                        proposal = proposal_enrich(job, args.ids, args.reason)
                    elif args.action == "apollo-search":
                        proposal = proposal_search(job, args.reason)
                    else:
                        proposal = proposal_verify(job, args.ids, args.emails, args.reason)
                    print(json.dumps(proposal, ensure_ascii=False, indent=2))
                elif args.command in {"approve", "cancel"}:
                    getattr(job, args.command)(args.proposal)
                    print("已批准；尚未调用API。" if args.command == "approve" else "已取消。")
                elif args.command in {"discover", "enrich", "apollo-search", "verify"}:
                    keyfile = args.keys
                    default_keys = ROOT / "keys.properties"
                    if not keyfile and default_keys.exists():
                        keyfile = default_keys
                    # Fixture tasks never require or read any real secret.
                    keys = {} if job.data["mode"] == "DEMO" else keys_from(keyfile)
                    print(execute(job, args.proposal, args.command, Providers(keys, job.data["fixtures"])))
                    export(job)
                    print("审核文件：" + str(job.root.resolve() / "review.txt"))
                elif args.command == "import":
                    print(f"导入 {import_existing(job, args.file)} 人；未调用API。")
                    export(job)
                elif args.command == "merge":
                    first, second = selected(job, args.ids)
                    merge_into(first, second)
                    first["issues"].append("MANUAL_MERGE:" + args.reason)
                    job.data["candidates"].remove(second)
                    first["issues"] = [i for i in first["issues"] if not i.startswith("MATCH_TO_REVIEW:")]
                    mark_conflicts(job.data["candidates"])
                    job.save()
                    print("已合并并保留来源；旧名单提案需重新生成。")
                elif args.command == "review":
                    print(render(job)[0])
                else:
                    export(job)
                    print("已导出到：" + str(job.root.resolve()))
        return 0
    except Stop as exc:
        print("停止：" + str(exc), file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print("停止：配置、文件或数据格式异常（" + type(exc).__name__ + "）；未自动重试。", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("已中断；检查任务中的 PENDING/UNCERTAIN 请求，勿盲目重试。", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
