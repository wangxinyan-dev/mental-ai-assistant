// 生产环境走同源（由 nginx 反代 /uploads 到后端）
// 开发环境由 vite.config.js 的 proxy 兜底，仍指向 localhost:1236
export const fileBaseUrl = ''