# RAG 预置语料

阶段 1 的预置语料库放在 `src/main/resources/corpus/`（被 .gitignore）。
推荐来源（完全公开、合规）：

| 来源 | 说明 | 站点 |
|---|---|---|
| CAICT（信通院） | AI/通信/数据/工业互联网白皮书 | http://www.caict.ac.cn/kxyj/qwfb/bps/ |
| MIIT（工信部） | 产业规划、行业指导意见 | https://www.miit.gov.cn |
| NDRC（发改委） | 战略性新兴产业规划 | https://www.ndrc.gov.cn |
| CCID（赛迪研究院） | 半导体/数字经济/新能源行业研报 | https://www.ccidwise.com |
| 巨潮资讯网 | 龙头公司年度财报 / 招股说明书 | http://www.cninfo.com.cn |

下载指引：手动从上述站点下载 30-50 篇 PDF，存到 `src/main/resources/corpus/`，
文件名建议：`<来源>-<主题>-<年份>.pdf`，例如：
- `caict-embodied-ai-2026.pdf`
- `miit-new-energy-vehicle-2026.pdf`
- `cninfo-ningde-2025-annual.pdf`

然后调用 `POST /api/rag/ingest` 入库（先用 alice / 你的账号 登录拿 JWT）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<你的用户名>","password":"<你的密码>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

curl -s -X POST http://localhost:8080/api/rag/ingest \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"source":"MIXED","pathOrGlob":"*.pdf"}'
```
