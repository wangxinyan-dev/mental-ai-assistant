import service from '@/utils/request'

export function login(data) {
    return service.post('/user/login', data)
}


export function categoryTree() {
    return service.get('/knowledge/category/tree')
}

export function articlePage(params) {
     return service.get('/knowledge/article/page', { params })
}

export function uploadFile(file, businessInfo) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessId', businessInfo.businessId)
    formData.append('businessField', 'cover')

    return service.post('/file/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}


export function createArticle(data) {
    return service.post('/knowledge/article', data)
}

export function getArticleDetail(id) {
    return service.get(`/knowledge/article/${id}`)
}


export function updateArticle(id, data) {
    return service.put(`/knowledge/article/${id}`, data)
}

export function changeArticleStatus(id, data) {
    return service.put(`/knowledge/article/${id}/status`, data)
}

export function deleteArticle(id) {
    return service.delete(`/knowledge/article/${id}`)
}

export function getConsultationPage(params) {
    return service.get('/psychological-chat/sessions', { params })
}

export function getSessionDetail(sessionId) {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export function getEmotionalPage(params) {
    return service.get('/emotion-diary/admin/page', { params })
}

export function deleteEmotional(id) {
    return service.delete(`/emotion-diary/admin/${id}`)
}

export function getAnalyticsOverview() {
    return service.get(`/data-analytics/overview`)
}

/** 查看 RAG 索引状态 */
export function getRagStatus() {
    return service.get('/rag/status')
}

/** 重建 RAG 索引（扫描已发布文章 → 分块 → 向量化 → 写库，耗时较长） */
export function rebuildRagIndex() {
    return service.post('/rag/rebuild')
}

/** 检索调试：输入 query 返回 Top-K 相关片段（含相似度分数） */
export function ragSearch(query) {
    return service.get('/rag/search', { params: { query } })
}

export function logout() {
    return service.post('/user/logout')
}