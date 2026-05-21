/** 一个检索命中里的 citation 子结构（与后端 RagSearchResponse.HitData.CitationData 对齐）。 */
export interface CitationData {
  docId: number
  docTitle: string
  source: string
  sectionTitle: string
  pageStart: number
  pageEnd: number
}

/** 单条 hit。 */
export interface HitData {
  chunkId: number
  score: number
  rerankScore: number | null
  content: string
  citation: CitationData
}

/** POST /api/rag/search 的请求体。 */
export interface SearchRequest {
  query: string
  topK?: number       // 1-50，默认 10
  useRerank?: boolean // 默认 true
}

/** POST /api/rag/search 的响应 data 字段。 */
export interface SearchResponse {
  query: string
  tookMs: number
  hits: HitData[]
}

/** 检索配置（前端常量，等 Phase 4 加配置接口后从后端拉）。 */
export const RAG_CONFIG = {
  embeddingModel: 'text-embedding-v3',
  vectorSimilarity: 'cosine',
  bm25Weight: 0.3,
  vectorWeight: 0.7,
  rerankModel: 'gte-rerank-v2'
} as const
