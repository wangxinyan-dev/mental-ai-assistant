import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const service = axios.create({
  baseURL: '/api', // 请求的前缀
  timeout: 5000, // 请求的超时时间
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    // 对响应数据做点什么
    const { data, config } = response
    // 处理业务状态码
    if (data.code === '200') {
      return data.data
    } else {
      if (data.code === '-1') {
        if (!config.url?.includes('/login')) {
          ElMessage.error(data.msg || '登录过期，请重新登录')
          // 清除登录信息
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          window.location.href = '/auth/login'
        } else {
          ElMessage.error(data.msg || '登录过期，请重新登录')
          return Promise.reject(data)
        }
      } else {
        // 其他业务错误：先弹提示，再 reject 给调用方
        ElMessage.error(data.msg || '操作失败')
        return Promise.reject(data)
      }
    }
  },
  (error) => {
    // HTTP 错误（4xx/5xx）
    const msg = error?.response?.data?.msg || error?.message || '网络请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service