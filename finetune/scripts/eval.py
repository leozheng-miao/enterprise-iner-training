#!/usr/bin/env python3
"""
离线评估：在 test.jsonl 上对比两个 OpenAI 兼容端点（base vs 微调后）
按 Intent F1 / Industry NER F1 / sub_query Recall / JSON 合法率 / P50/P95 延迟出表。

用法：
    # 1. 起 vLLM 服务（serve_vllm.sh），假设监听 http://localhost:8000/v1
    # 2. 跑评估
    python finetune/scripts/eval.py \
        --test finetune/data/test.jsonl \
        --base-url-a https://dashscope.aliyuncs.com/compatible-mode/v1--model-a qwen-max --api-key-a $DASHSCOPE_API_KEY \
        --base-url-b http://localhost:8000/v1 --model-b qwen2.5-1.5b-querywriter-lora --api-key-b EMPTY
"""

import argparse
import json
import statistics
import time
from collections import Counter
from pathlib import Path

import requests

SYSTEM_PROMPT_KEY = "instruction"


def call(base_url, api_key, model, system_prompt, user_input):
    t0 = time.time()
    resp = requests.post(
        f"{base_url.rstrip('/')}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={
            "model": model,
            "temperature": 0.0,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_input},
            ],
        },
        timeout=30,
    )
    latency_ms = int((time.time() - t0) * 1000)
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"].strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()
    return content, latency_ms


def safe_parse(s):
    try:
        return json.loads(s)
    except Exception:
        return None


def intent_f1(preds, golds):
    """所有 intent 的 macro-F1。"""
    labels = sorted(set(golds))
    f1s = []
    for lbl in labels:
        tp = sum(1 for p, g in zip(preds, golds) if p == lbl and g == lbl)
        fp = sum(1 for p, g in zip(preds, golds) if p == lbl and g != lbl)
        fn = sum(1 for p, g in zip(preds, golds) if p != lbl and g == lbl)
        p = tp / (tp + fp) if tp + fp else 0
        r = tp / (tp + fn) if tp + fn else 0
        f1s.append(2 * p * r / (p + r) if p + r else 0)
    return sum(f1s) / len(f1s) if f1s else 0


def industry_match(preds, golds):
    return sum(1 for p, g in zip(preds, golds) if p and g and p.strip() == g.strip()) / max(len(golds), 1)


def subquery_jaccard(pred_list, gold_list):
    if not pred_list or not gold_list:
        return 0
    pa, ga = set(pred_list), set(gold_list)
    return len(pa & ga) / len(pa | ga)


def evaluate(endpoint_name, base_url, api_key, model, items):
    intents_p, intents_g = [], []
    industries_p, industries_g = [], []
    subq_scores = []
    json_ok, latencies = 0, []
    for it in items:
        try:
            content, ms = call(base_url, api_key, model, it[SYSTEM_PROMPT_KEY], it["input"])
            latencies.append(ms)
            parsed = safe_parse(content)
            if not parsed:
                continue
            json_ok += 1
            gold = json.loads(it["output"])
            intents_p.append(parsed.get("intent", ""))
            intents_g.append(gold.get("intent", ""))
            industries_p.append(parsed.get("industry", ""))
            industries_g.append(gold.get("industry", ""))
            subq_scores.append(subquery_jaccard(parsed.get("sub_queries"), gold.get("sub_queries")))
        except Exception as e:
            print(f"  [{endpoint_name}] error: {e}")
    n = len(items)
    return {
        "endpoint": endpoint_name,
        "model": model,
        "samples": n,
        "json_ok_rate": round(json_ok / n, 4) if n else 0,
        "intent_f1": round(intent_f1(intents_p, intents_g), 4),
        "industry_match": round(industry_match(industries_p, industries_g), 4),
        "subquery_jaccard": round(sum(subq_scores) / max(len(subq_scores), 1), 4),
        "p50_ms": int(statistics.median(latencies)) if latencies else 0,
        "p95_ms": int(statistics.quantiles(latencies, n=20)[18]) if len(latencies) >= 20 else 0,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--test", required=True)
    ap.add_argument("--base-url-a", required=True)
    ap.add_argument("--model-a", required=True)
    ap.add_argument("--api-key-a", required=True)
    ap.add_argument("--base-url-b", required=True)
    ap.add_argument("--model-b", required=True)
    ap.add_argument("--api-key-b", required=True)
    args = ap.parse_args()

    items = [json.loads(l) for l in Path(args.test).read_text(encoding="utf-8").splitlines() if l.strip()]
    print(f"测试集 {len(items)} 条")

    a = evaluate("A (baseline)", args.base_url_a, args.api_key_a, args.model_a, items)
    b = evaluate("B (lora)",     args.base_url_b, args.api_key_b, args.model_b, items)

    print()
    print("| Endpoint | Model | JSON 合法 | Intent F1 | Industry | SubQ Jaccard | P50 ms | P95 ms |")
    print("|---|---|---|---|---|---|---|---|")
    for r in (a, b):
        print(f"| {r['endpoint']} | {r['model']} | {r['json_ok_rate']} | {r['intent_f1']} | "
              f"{r['industry_match']} | {r['subquery_jaccard']} | {r['p50_ms']} | {r['p95_ms']} |")


if __name__ == "__main__":
    main()
