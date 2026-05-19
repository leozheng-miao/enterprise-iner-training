#!/usr/bin/env bash
# 仅打印预期文件清单，实际 PDF 受版权 / robots 限制需要手动下载。
# 用法：bash scripts/download-corpus.sh
set -euo pipefail
TARGET="src/main/resources/corpus"
mkdir -p "$TARGET"

echo "请按 scripts/README.md 指引，手动下载以下类别共 30-50 篇 PDF 到 $TARGET："
cat <<HELP
  - 信通院 AI / 通信 / 数据要素 白皮书 (~10 篇)
  - 工信部 / 发改委 行业规划 (~10 篇)
  - 赛迪研究院 半导体 / 新能源 行业研报 (~10 篇)
  - 龙头公司年度财报 / 招股说明书 (~10 篇)
HELP
ls "$TARGET"/*.pdf 2>/dev/null | wc -l | xargs -I{} echo "当前已有 {} 篇 PDF"
