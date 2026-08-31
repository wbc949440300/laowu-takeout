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
export const getFaqList = () => agent.get('/admin/faq')
export const createFaq = (data: any) => agent.post('/admin/faq', data)
export const updateFaq = (id: string, data: any) => agent.put('/admin/faq/' + encodeURIComponent(id), data)
export const deleteFaq = (id: string) => agent.delete('/admin/faq/' + encodeURIComponent(id))
