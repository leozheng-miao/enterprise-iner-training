#!/usr/bin/env python3
"""
合成训练数据：从 data/sample_topics.txt 读主题，调 DashScope qwen-max
生成 {intent, industry, year, geo, sub_queries} JSON，写成 LLaMA-Factory
Alpaca 格式 JSONL（data/train.jsonl + data/test.jsonl，85/15 切分）。

system_prompt 必须与 Java 侧 QueryRewriterClient.SYSTEM_PROMPT 完全一致，
否则训练与推理 prompt drift 会吃掉微调收益。

用法：
    export DASHSCOPE_API_KEY=sk-xxx
    pip install requests
    python finetune/scripts/synthesize.py \
        --topics finetune/data/sample_topics.txt \
        --out-dir finetune/data \
        --repeats 5     # 每条主题改写 5 次（高温采样保证多样性）
"""

import argparse
import json
import os
import random
import sys
import time
from pathlib import Path

import requests

SYSTEM_PROMPT = """你是研究查询改写专家。把用户输入的研究主题改写为下面结构的 JSON：
{
  "intent": "industry_trend | company_compare | tech_progress | policy_impact | market_size",
  "industry": "<行业实体，如 动力电池 / 具身智能>",
  "year": <4 位整数；未指明默认当前年份>,
  "geo": "<地域，默认 中国>",
  "sub_queries": ["...", "...", "..."]
}
sub_queries 给 3-5 条便于 hybrid search 召回的子查询字符串。
严格只输出 JSON 本身，不要 markdown 代码块，不要任何解释文字。"""

API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODEL = "qwen-max"


def call_dashscope(topic: str, temperature: float) -> dict:
    api_key = os.environ.get("DASHSCOPE_API_KEY")
    if not api_key:
        sys.exit("ERROR: DASHSCOPE_API_KEY 未设置")
    resp = requests.post(
        API_URL,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={
            "model": MODEL,
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": topic},
            ],
        },
        timeout=30,
    )
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"].strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()
    return json.loads(content)


def validate(obj: dict) -> bool:
    required = {"intent", "industry", "year", "geo", "sub_queries"}
    if not required.issubset(obj.keys()):
        return False
    if not isinstance(obj["sub_queries"], list) or not 3 <= len(obj["sub_queries"]) <= 5:
        return False
    if not isinstance(obj["year"], int) or not 2000 <= obj["year"] <= 2100:
        return False
    return True


def to_alpaca(topic: str, label: dict) -> dict:
    return {
        "instruction": SYSTEM_PROMPT,
        "input": topic,
        "output": json.dumps(label, ensure_ascii=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--topics", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--repeats", type=int, default=5)
    ap.add_argument("--temperature", type=float, default=0.8)
    ap.add_argument("--test-ratio", type=float, default=0.15)
    args = ap.parse_args()

    topics = [t.strip() for t in Path(args.topics).read_text(encoding="utf-8").splitlines() if t.strip()]
    print(f"载入 {len(topics)} 个原始主题，每条改写 {args.repeats} 次")

    records, failed = [], 0
    for i, topic in enumerate(topics, 1):
        for _ in range(args.repeats):
            try:
                label = call_dashscope(topic, args.temperature)
                if not validate(label):
                    failed += 1
                    continue
                records.append(to_alpaca(topic, label))
                time.sleep(0.3)            # 简单限速
            except Exception as e:
                failed += 1
                print(f"  [skip] {topic[:30]}... : {e}", file=sys.stderr)
        print(f"  [{i}/{len(topics)}] 累计 {len(records)} 条 / 失败 {failed}")

    random.seed(42)
    random.shuffle(records)
    cut = int(len(records) * (1 - args.test_ratio))
    train, test = records[:cut], records[cut:]

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, data in [("train.jsonl", train), ("test.jsonl", test)]:
        with (out_dir / name).open("w", encoding="utf-8") as f:
            for r in data:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"OK: train={len(train)} test={len(test)} 失败={failed}")


if __name__ == "__main__":
    main()
