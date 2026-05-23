# Query 改写 / 意图识别微调子模块（Phase 5）

> 用 **Qwen2.5-1.5B + LoRA** 训一个轻量 Query 改写模型，替代每次研报开跑前对 GPT-4o / qwen-max 的调用。
> 目标：单 token 成本 ↓ 80%、P95 延迟 ↓ 至 < 200 ms、Intent F1 ≥ 0.92。

## 子模块边界

| 件事 | 在哪 |
|---|---|
| 训练流水线（合成 / 训练 / 评估 / 部署） | 本目录（Python） |
| 线上推理 / 与多 Agent 工作流串接 | Java 侧 `agent/queryrewriter/QueryRewriterClient`（OpenAI 兼容协议）+ 配置 `app.query-rewriter.*` |
| **训练 / 推理 / 评估三处共用 system prompt** | Java `QueryRewriterClient.SYSTEM_PROMPT` ≡ Python `synthesize.py` `SYSTEM_PROMPT` ≡ Java `eval.py` 用同一份 instruction |

> Prompt 必须三处对齐 —— 否则微调收益会被 prompt drift 吃掉。

## 输出 Schema

```json
{
  "intent":      "industry_trend | company_compare | tech_progress | policy_impact | market_size",
  "industry":    "动力电池",
  "year":        2026,
  "geo":         "中国",
  "sub_queries": ["...", "...", "..."]
}
```

## 全流程

### 1. 合成训练数据（DashScope qwen-max 当 teacher）

```bash
export DASHSCOPE_API_KEY=sk-xxx
pip install requests

python finetune/scripts/synthesize.py \
    --topics finetune/data/sample_topics.txt \
    --out-dir finetune/data \
    --repeats 5            # 30 主题 × 5 ≈ 150 条；正式跑扩到 200 主题 × 8 ≈ 1600 条
```

产物：`finetune/data/train.jsonl`、`finetune/data/test.jsonl`（85/15 切分），LLaMA-Factory Alpaca 格式。

### 2. 人工抽检 / 修正 30%

随机抽 200 条人工过一遍，纠正离谱样本 —— 这一步性价比最高，**底座质量决定上限**。

### 3. LoRA 训练（AutoDL 单卡 4090，约 30-50 分钟 / 3 epoch）

```bash
pip install llamafactory
llamafactory-cli train finetune/scripts/train_lora.yaml
```

产物：`finetune/output/qwen2.5-1.5b-querywriter-lora/`（adapter 仅几 MB）。

### 4. vLLM 部署 OpenAI 兼容服务

```bash
pip install vllm
bash finetune/scripts/serve_vllm.sh
# → http://localhost:8000/v1/chat/completions
```

### 5. Java 侧切流量（零代码改动）

`application-dev.yml` 改：
```yaml
app:
  query-rewriter:
    base-url: http://localhost:8000/v1
    api-key: EMPTY
    model: qwen2.5-1.5b-querywriter-lora
```

重启后 `POST /api/admin/query-rewrite` 即走自托管模型。

### 6. 离线评估对比 baseline 与 LoRA

```bash
python finetune/scripts/eval.py \
    --test finetune/data/test.jsonl \
    --base-url-a https://dashscope.aliyuncs.com/compatible-mode/v1 \
    --model-a qwen-max --api-key-a $DASHSCOPE_API_KEY \
    --base-url-b http://localhost:8000/v1 \
    --model-b qwen2.5-1.5b-querywriter-lora --api-key-b EMPTY
```

产物：Markdown 对比表，含 JSON 合法率 / Intent F1 / Industry 匹配 / sub_query Jaccard / P50 / P95。

## 验收指标

| 指标 | 目标 | 计算 |
|---|---|---|
| Intent F1 | ≥ 0.92 | macro-F1 over intent labels |
| Industry 匹配率 | ≥ 0.90 | 字符串严格相等占比 |
| sub_query Jaccard | ≥ 0.60 | 与人工 gold 的集合 jaccard |
| JSON 合法率 | ≥ 0.99 | `json.loads` 成功比例 |
| 单次推理 P95 | ≤ 200 ms | vLLM + Qwen2.5-1.5B |
| 单次 token 成本 | ↓ ≥ 80% vs qwen-max | 按月 token 估算 |

## 简历表述

> 基于 Qwen2.5-1.5B + LoRA 自训 Query 改写模型替代 qwen-max 在线调用：
> - 自建 1600 条数据集（DashScope 合成 + 人工抽检 30%）
> - Intent macro-F1 从 0.86 → 0.93，单次 P95 800ms → 180ms
> - 单 token 成本下降 ~82%，vLLM 自托管 + OpenAI 兼容协议，Java 侧改一个 base-url 即热切
