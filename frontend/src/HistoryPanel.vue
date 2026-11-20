<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElEmpty } from 'element-plus'
import { requestJson } from './api'
import { bodyOf } from './documentContent'
const RichTextEditor = defineAsyncComponent(() => import('./RichTextEditor.vue'))
const props = defineProps({ documentId: Number, currentVersion: Number, currentRole: String, busy: Boolean })
const emit = defineEmits(['close', 'restore'])
const rows = ref([])
const loading = ref(false)
const error = ref('')
const preview = ref(null)
const previewContent = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const selected = ref(null)
let listRequest = 0
let detailRequest = 0
const canRestore = computed(() => ['OWNER', 'EDITOR'].includes(props.currentRole))
const labels = { CREATED: '创建', UPDATED: '保存', RESTORED: '恢复', BASELINE: '迁移基线' }
async function load() {
  const request = ++listRequest
  const id = props.documentId
  loading.value = true
  error.value = ''
  try {
    const result = await requestJson(`/api/documents/${id}/versions`)
    if (request !== listRequest || id !== props.documentId) return
    rows.value = result
  } catch (e) { if (request === listRequest) error.value = e.message }
  finally { if (request === listRequest) loading.value = false }
}
async function select(number) {
  const request = ++detailRequest
  const id = props.documentId
  selected.value = number
  preview.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    const result = await requestJson(`/api/documents/${id}/versions/${number}`)
    if (request !== detailRequest || id !== props.documentId) return
    previewContent.value = bodyOf(result)
    preview.value = result
  } catch (e) { if (request === detailRequest) previewError.value = e.message }
  finally { if (request === detailRequest) previewLoading.value = false }
}
watch(() => props.currentVersion, load, { immediate: true })
onBeforeUnmount(() => { ++listRequest; ++detailRequest })
</script>

<template>
  <el-dialog :model-value="true" title="历史版本" width="850px" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <p>历史预览只读，不改变当前文档或未保存草稿。当前版本 {{ currentVersion }}。</p>
    <el-button :loading="loading" :disabled="busy" @click="load">刷新历史</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="loading" role="status">正在加载历史版本…</p>
    <el-empty v-if="!loading && !error && !rows.length" description="暂无历史版本。" />
    <ul class="history-list">
      <li v-for="row in rows" :key="row.id">
        <el-button :aria-label="`预览版本 ${row.versionNumber}`" :disabled="busy" @click="select(row.versionNumber)">v{{ row.versionNumber }} · {{ labels[row.changeType] || row.changeType }}</el-button>
        <span>{{ row.changeType === 'BASELINE' ? '基线归属' : '修改者' }}：{{ row.editorName }} · {{ row.createdAt?.replace('T', ' ') }}</span>
        <small v-if="row.sourceVersion">来自 v{{ row.sourceVersion }}</small>
      </li>
    </ul>
    <p v-if="previewLoading" role="status">正在加载历史预览…</p>
    <div v-if="previewError"><el-alert :title="previewError" type="error" :closable="false" /><el-button @click="select(selected)">重试历史预览</el-button></div>
    <section v-if="preview" class="history-preview" aria-label="历史只读预览">
      <h3>历史预览 v{{ preview.versionNumber }}：{{ preview.title }}</h3>
      <p v-if="preview.changeType === 'BASELINE'">这是上线时的当前状态基线；归属用户不代表实际最后修改者，更早历史不可追溯。</p>
      <RichTextEditor :key="preview.id" :model-value="previewContent" readonly />
      <el-button v-if="canRestore" type="primary" :disabled="busy" :loading="busy" @click="emit('restore', preview.versionNumber)">恢复此版本</el-button>
      <p v-else>当前权限只允许查看历史。</p>
    </section>
  </el-dialog>
</template>

<style scoped>
.history-list { list-style: none; padding: 0; max-height: 220px; overflow-y: auto; }
.history-list li { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 8px 0; border-bottom: 1px solid #ebeef5; }
.history-preview { margin-top: 20px; }
.history-preview h3 { overflow-wrap: anywhere; }
.history-preview .el-button { margin-top: 16px; }
</style>
