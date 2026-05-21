import { apiPost } from './client'
import type { SearchRequest, SearchResponse } from '@/types/rag'

export const ragApi = {
  search(body: SearchRequest) {
    return apiPost<SearchResponse>('/rag/search', body)
  }
}
