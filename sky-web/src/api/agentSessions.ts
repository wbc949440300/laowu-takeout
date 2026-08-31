import axios from 'axios'
import { UserModule } from '@/store/modules/user'

const agent = axios.create({
  baseURL: process.env.VUE_APP_AGENT_API || 'http://localhost:8000',
  timeout: 15000,
})
agent.interceptors.request.use(config => {
  config.headers = config.headers || {}
  config.headers.token = UserModule.token
  return config
})

export const getAgentSessions = (params: any) => agent.get('/admin/sessions', { params })
export const getAgentSession = (threadId: string) => agent.get(`/admin/sessions/${encodeURIComponent(threadId)}`)
export const updateAgentSession = (threadId: string, data: any) => agent.patch(`/admin/sessions/${encodeURIComponent(threadId)}`, data)
