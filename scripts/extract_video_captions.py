#!/usr/bin/env python3
"""Extract visible auto-captions from meeting recordings at six-second intervals."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import re
import subprocess
import tempfile
from pathlib import Path


def duration_seconds(path: Path) -> float:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    )
    return float(result.stdout.strip())


def clean(text: str) -> str:
    text = text.replace("\n", " ")
    text = re.sub(r"[—_~`|]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip(" .,:;-'\"")
    if len(re.findall(r"[A-Za-z]", text)) < 5 or len(re.findall(r"[A-Za-z]{2,}", text)) < 2:
        return ""
    return text


def ocr_one(image: Path) -> str:
    result = subprocess.run(["tesseract", str(image), "stdout", "--psm", "6"], capture_output=True, text=True)
    return clean(result.stdout)


def process_video(path: Path, output_dir: Path, seconds: int) -> dict:
    duration = duration_seconds(path)
    with tempfile.TemporaryDirectory(prefix="caption-frames-") as temp:
        temp_dir = Path(temp)
        vf = f"fps=1/{seconds},crop=iw:120:0:ih-120,scale=2560:-2,format=gray,eq=contrast=2.4:brightness=-0.05"
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(path), "-vf", vf, str(temp_dir / "%06d.png")], check=True)
        frames = sorted(temp_dir.glob("*.png"))
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            ocr = list(pool.map(ocr_one, frames))

    records, previous = [], ""
    for index, text in enumerate(ocr):
        if not text or text == previous:
            continue
        previous = text
        records.append({"time_seconds": index * seconds, "caption": text})
    output = {"video": path.name, "duration_seconds": round(duration, 1), "sample_interval_seconds": seconds, "caption_records": records}
    out_file = output_dir / f"{path.stem}.json"
    out_file.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"video": path.name, "duration_seconds": duration, "captions": len(records), "output": str(out_file)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--seconds", type=int, default=6)
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    videos = sorted(args.input_dir.glob("*.mp4"))
    # ffmpeg/tesseract perform the CPU work in child processes.  A thread pool
    # avoids the sandbox's prohibited POSIX semaphore allocation for Python's
    # ProcessPoolExecutor.
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(process_video, video, args.output_dir, args.seconds) for video in videos]
        results = []
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            print(f"DONE {result['video']} captions={result['captions']}", flush=True)
    (args.output_dir / "index.json").write_text(json.dumps(sorted(results, key=lambda item: item["video"]), ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
