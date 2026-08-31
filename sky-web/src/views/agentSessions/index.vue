<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <el-select v-model="status" clearable placeholder="会话状态" style="width: 160px" @change="load">
          <el-option label="处理中" value="processing" />
          <el-option label="已解决" value="resolved" />
          <el-option label="已转人工" value="transferred" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table :data="records" stripe>
        <el-table-column prop="thread_id" label="会话 ID" min-width="280" />
        <el-table-column prop="user_id" label="用户 ID" width="100" />
        <el-table-column label="状态" width="120">
          <template slot-scope="scope">{{ statusText(scope.row.conversation_status) }}</template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" width="100" />
        <el-table-column prop="rating" label="评分" width="80" />
        <el-table-column prop="updated_at" label="更新时间" width="160">
          <template slot-scope="scope">{{ formatTime(scope.row.updated_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" @click="setStatus(scope.row, 'resolved')">解决</el-button>
            <el-button type="text" @click="handoff(scope.row)">转人工</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pageList" layout="total, prev, pager, next" :total="total" :page-size="pageSize" @current-change="changePage" />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { getAgentSessions, updateAgentSession } from '@/api/agentSessions'

@Component({ name: 'AgentSessions' })
export default class extends Vue {
  private records: any[] = []
  private total = 0
  private page = 1
  private pageSize = 20
  private status = ''

  created() { this.load() }
  private async load() {
    try {
      const res: any = await getAgentSessions({ page: this.page, page_size: this.pageSize, status: this.status || undefined })
      this.records = res.data.records || []
      this.total = res.data.total || 0
    } catch (e) { this.$message.error('客服会话加载失败') }
  }
  private changePage(page: number) { this.page = page; this.load() }
  private statusText(value: string) { return ({ processing: '处理中', resolved: '已解决', transferred: '已转人工' } as any)[value] || value }
  private formatTime(value: number) { return value ? new Date(value * 1000).toLocaleString() : '-' }
  private async setStatus(row: any, value: string, reason?: string) {
    await updateAgentSession(row.thread_id, { conversation_status: value, handoff_reason: reason })
    this.$message.success('会话状态已更新'); this.load()
  }
  private handoff(row: any) {
    this.$prompt('请输入转人工原因', '转人工', { inputValidator: v => !!v || '请填写原因' }).then((result: any) => this.setStatus(row, 'transferred', result.value)).catch(() => undefined)
  }
}
</script>
