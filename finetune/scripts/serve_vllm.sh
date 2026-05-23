#!/usr/bin/env bash
#
# 用 vLLM 起 OpenAI 兼容服务，挂载 LoRA adapter。
# Java 侧只需把 app.query-rewriter.base-url 指向 http://localhost:8000/v1 即可热切。
#
# 依赖：pip install vllm  (CUDA 12.x，显存 >= 6 GB)
#
# 用法：
#   bash finetune/scripts/serve_vllm.sh

set -euo pipefail

BASE_MODEL="${BASE_MODEL:-Qwen/Qwen2.5-1.5B-Instruct}"
LORA_DIR="${LORA_DIR:-./finetune/output/qwen2.5-1.5b-querywriter-lora}"
LORA_NAME="${LORA_NAME:-qwen2.5-1.5b-querywriter-lora}"
PORT="${PORT:-8000}"

python -m vllm.entrypoints.openai.api_server \
    --model "$BASE_MODEL" \
    --enable-lora \
    --lora-modules "$LORA_NAME=$LORA_DIR" \
    --max-lora-rank 8 \
    --host 0.0.0.0 \
    --port "$PORT" \
    --dtype float16
