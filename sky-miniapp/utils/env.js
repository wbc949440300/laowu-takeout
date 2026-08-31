
// 测试环境
// export const baseUrl = 'http://localhost:8080/user'
// 线上环境
export const baseUrl = process.env.VUE_APP_USER_BASE_URL || 'http://localhost:8080/user'

// Agent 服务地址。真机/生产环境需要替换为已备案的 HTTPS 合法域名。
export const agentBaseUrl = process.env.VUE_APP_AGENT_BASE_URL || 'http://127.0.0.1:8000/agent'
