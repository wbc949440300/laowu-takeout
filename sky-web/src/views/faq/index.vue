<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="table-bar">
        <el-input v-model.trim="keyword" clearable placeholder="搜索问题或答案" prefix-icon="el-icon-search" />
        <el-button type="primary" icon="el-icon-plus" @click="openCreate">新增 FAQ</el-button>
      </div>
      <el-table v-loading="loading" :data="filteredRecords" stripe>
        <el-table-column prop="question" label="问题" min-width="260" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="420" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-edit" @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="text" class="danger" icon="el-icon-delete" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-dialog :title="editingId ? '编辑 FAQ' : '新增 FAQ'" :visible.sync="dialogVisible" width="620px" @closed="resetForm">
        <el-form ref="faqForm" :model="form" :rules="rules" label-width="70px">
          <el-form-item label="问题" prop="question"><el-input v-model.trim="form.question" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="答案" prop="answer"><el-input v-model.trim="form.answer" type="textarea" :rows="8" maxlength="5000" show-word-limit /></el-form-item>
        </el-form>
        <span slot="footer"><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并生效</el-button></span>
      </el-dialog>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { createFaq, deleteFaq, getFaqList, updateFaq } from '@/api/faq'

@Component({ name: 'FaqManagement' })
export default class extends Vue {
  private records: any[] = []
  private keyword = ''
  private loading = false
  private saving = false
  private dialogVisible = false
  private editingId = ''
  private form = { question: '', answer: '' }
  private rules = {
    question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
    answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
  }
  get filteredRecords() {
    const keyword = this.keyword.toLowerCase()
    return keyword ? this.records.filter(item => (item.question + item.answer).toLowerCase().includes(keyword)) : this.records
  }
  created() { this.load() }
  private async load() {
    this.loading = true
    try { const response: any = await getFaqList(); this.records = response.data.records || [] }
    catch (error) { this.$message.error('FAQ 加载失败') }
    finally { this.loading = false }
  }
  private openCreate() { this.editingId = ''; this.form = { question: '', answer: '' }; this.dialogVisible = true }
  private openEdit(row: any) { this.editingId = row.id; this.form = { question: row.question, answer: row.answer }; this.dialogVisible = true }
  private save() {
    ;(this.$refs.faqForm as any).validate(async (valid: boolean) => {
      if (!valid) return
      this.saving = true
      try {
        if (this.editingId) await updateFaq(this.editingId, this.form)
        else await createFaq(this.form)
        this.$message.success('FAQ 已保存并立即生效')
        this.dialogVisible = false
        await this.load()
      } catch (error) { this.$message.error('FAQ 保存失败，请检查是否存在重复问题') }
      finally { this.saving = false }
    })
  }
  private async remove(row: any) {
    try {
      await this.$confirm('确认删除“' + row.question + '”？', '删除 FAQ', { type: 'warning' })
      await deleteFaq(row.id); this.$message.success('FAQ 已删除'); await this.load()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') this.$message.error('FAQ 删除失败')
    }
  }
  private resetForm() {
    this.editingId = ''; this.form = { question: '', answer: '' }
    const form: any = this.$refs.faqForm
    if (form) form.clearValidate()
  }
}
</script>

<style lang="scss" scoped>
.dashboard-container { margin: 30px; }
.container { background: #fff; padding: 30px 28px; border-radius: 4px; }
.table-bar { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.table-bar .el-input { width: 360px; }
.danger { color: #f56c6c; }
</style>
