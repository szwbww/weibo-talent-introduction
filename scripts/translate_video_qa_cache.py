#!/usr/bin/env python3
"""Translate unique DOCX paragraphs with Bing's public web translator and cache results."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import re
import threading
import time
from pathlib import Path

import requests
from docx import Document
from lxml import etree

from build_bilingual_video_qa_doc import iter_all_paragraphs, should_translate


SEPARATOR = '\n[[[SPLIT_9347]]]\n'
MANUAL = {
    'Screenshots Expert QA Summary': 'Screenshots 视频专家 QA 汇总',
    '134 meeting recordings | 2025-11-05 to 2026-07-31 | Searchable review index': '134 段会议录像｜2025-11-05 至 2026-07-31｜可检索复核索引',
    'Reading notes': '阅读说明',
    'Portfolio overview': '全量概览',
    'Video directory': '视频目录',
    'Per-video Q&A index': '逐视频问答索引',
    'Low-signal recordings': '低信息录像',
    'Searchable review index': '可检索复核索引',
    'Auto-classified topic': '自动归类主题',
    'Videos involved': '涉及视频数',
    'Date': '日期',
    'Time': '时间',
    'Length': '时长',
    'QA / signal': '问答 / 信号质量',
    'Topic cues': '主题线索',
}


def join_batch(batch: list[str]) -> str:
    return SEPARATOR.join(batch)


def pack_batches(items: list[str], max_chars: int = 900) -> list[list[str]]:
    batches: list[list[str]] = []
    current: list[str] = []
    current_len = 0
    for item in items:
        extra = len(item) + (len(SEPARATOR) if current else 0)
        if current and current_len + extra > max_chars:
            batches.append(current)
            current = []
            current_len = 0
            extra = len(item)
        current.append(item)
        current_len += extra
    if current:
        batches.append(current)
    return batches


class BingTranslator:
    def __init__(self) -> None:
        self.session = requests.Session()
        self.url = ''
        self.key = ''
        self.token = ''
        self._initialize()

    def _initialize(self) -> None:
        host_url = 'https://www.bing.com/Translator'
        html = self.session.get(host_url, timeout=60).text
        key, token, _ = json.loads(re.findall(r'var params_AbusePreventionHelper = (.*?);', html)[0])
        ig = re.findall(r'IG:"(.*?)"', html)[0]
        iid = etree.HTML(html).xpath('//*[@id="tta_outGDCont"]/@data-iid')[0]
        self.url = f'https://www.bing.com/ttranslatev3?isVertical=1&&IG={ig}&IID={iid}'
        self.key = str(key)
        self.token = token

    def translate(self, text: str) -> str:
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                response = self.session.post(
                    self.url,
                    data={'text': text, 'fromLang': 'en', 'to': 'zh-Hans', 'key': self.key, 'token': self.token},
                    timeout=60,
                )
                response.raise_for_status()
                return response.json()[0]['translations'][0]['text'].strip()
            except Exception as error:  # network/service retries are intentional here
                last_error = error
                time.sleep(2 ** attempt)
                self._initialize()
        raise RuntimeError(f'translation failed after retries: {last_error}')


def translate_batch(client: BingTranslator, batch: list[str]) -> dict[str, str]:
    combined = client.translate(join_batch(batch))
    parts = combined.split('[[[SPLIT_9347]]]')
    if len(parts) == len(batch):
        return {source: translated.strip() for source, translated in zip(batch, parts)}
    return {source: client.translate(source) for source in batch}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_docx', type=Path)
    parser.add_argument('cache_json', type=Path)
    parser.add_argument('--workers', type=int, default=6)
    args = parser.parse_args()

    doc = Document(args.source_docx)
    texts = []
    seen = set()
    for paragraph in iter_all_paragraphs(doc):
        text = paragraph.text.strip()
        if should_translate(text) and text not in seen:
            seen.add(text)
            texts.append(text)

    if args.cache_json.exists():
        cache = json.loads(args.cache_json.read_text(encoding='utf-8'))
    else:
        cache = {}
    cache.update(MANUAL)
    pending = [text for text in texts if text not in cache]
    batches = pack_batches(pending)
    print(f'unique={len(texts)} cached={len(texts) - len(pending)} batches={len(batches)}', flush=True)
    if not batches:
        return

    thread_state = threading.local()

    def translate_in_worker(batch: list[str]) -> dict[str, str]:
        if not hasattr(thread_state, 'client'):
            thread_state.client = BingTranslator()
        return translate_batch(thread_state.client, batch)

    completed = 0
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(translate_in_worker, batch): batch for batch in batches}
        for future in as_completed(futures):
            cache.update(future.result())
            completed += 1
            args.cache_json.parent.mkdir(parents=True, exist_ok=True)
            temp = args.cache_json.with_suffix('.tmp')
            temp.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding='utf-8')
            temp.replace(args.cache_json)
            if completed % 10 == 0 or completed == len(batches):
                print(f'translated {completed}/{len(batches)} batches', flush=True)


if __name__ == '__main__':
    main()
