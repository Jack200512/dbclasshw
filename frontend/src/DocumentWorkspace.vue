<script setup>
import { computed, defineAsyncComponent, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElDialog, ElEmpty, ElForm, ElFormItem, ElInput, ElMessageBox, ElTag } from 'element-plus'
import { requestJson } from './api'
import SharingPanel from './SharingPanel.vue'
import HistoryPanel from './HistoryPanel.vue'
import { bodyOf } from './documentContent'
import { createDocumentChannel } from './documentChannel'
const RichTextEditor = defineAsyncComponent(() => import('./RichTextEditor.vue'))

const emptyContent = () => ({ type: 'doc', content: [{ type: 'paragraph' }] })
const documents = ref([])
const keyword = ref('')
const listLoading = ref(false)
const listError = ref('')
const mode = ref('welcome')
const selectedId = ref(null)
const document = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const editing = ref(false)
const form = reactive({ title: '', content: emptyContent() })
const baseline = ref('')
const busy = ref(false)
const notice = ref('')
const actionError = ref('')
const conflict = ref(false)
const editorKey = ref(0)
const realtimeStatus = ref('未连接实时通知')
const remoteNotice = ref('')
let syncRequest = 0
let pendingSync = null
const channel = createDocumentChannel(checkRemote, value => { realtimeStatus.value = value })
const showSharing = ref(false)
const showHistory = ref(false)
const listScope = ref('all')
const roleLabels = { OWNER: '所有者', EDITOR: '编辑者', VIEWER: '查看者' }
const visibleDocuments = computed(() => documents.value.filter(item => listScope.value === 'all' || (listScope.value === 'mine' ? item.currentRole === 'OWNER' : item.currentRole !== 'OWNER')))
const canEdit = computed(() => mode.value === 'create' || ['OWNER', 'EDITOR'].includes(document.value?.currentRole))
const snapshot = () => JSON.stringify({ title: form.title, content: form.content })
const dirty = computed(() => (mode.value === 'create' || editing.value) && baseline.value !== snapshot())
const validTitle = computed(() => form.title.trim().length > 0 && form.title.length <= 255)
let detailRequest = 0
let listRequest = 0

async function checkRemote(event) {
  const id = document.value?.id
  if (!id || event.documentId !== id) return
  if (event.type === 'DOCUMENT_UPDATED' && event.version <= document.value.version) return
  if (event.type === 'ACCESS_LOST') remoteNotice.value = '订阅失效，正在核对权限；本地草稿不会被覆盖。'
  if (busy.value) { pendingSync = event; return }
  const request = ++syncRequest
  try {
    const latest = await requestJson(`/api/documents/${id}`)
    if (request !== syncRequest || document.value?.id !== id) return
    if (busy.value) { pendingSync = event; return }
    if (latest.version < document.value.version) return
    // Update access metadata even when content is protected by a dirty draft.
    document.value = { ...document.value, currentRole: latest.currentRole }
    if (dirty.value) {
      remoteNotice.value = latest.version > document.value.version
        ? `检测到远端更新（版本 ${latest.version}），本地草稿已保留。重新加载前请确认放弃修改。`
        : '已重新核对远端状态，本地未保存草稿已保留。'
      return
    }
    if (latest.version > document.value.version) {
      setDraft(latest)
      document.value = latest
      if (!canEdit.value) editing.value = false
      remoteNotice.value = '文档已自动同步到最新版本。'
      loadDocuments()
    } else document.value = latest
  } catch (error) {
    if (request !== syncRequest || document.value?.id !== id) return
    remoteNotice.value = `${error.message} 本地内容已保留。`
    if (event.type === 'ACCESS_LOST') document.value = { ...document.value, currentRole: null }
  }
}
watch(() => document.value?.id, id => {
  ++syncRequest
  pendingSync = null
  remoteNotice.value = ''
  if (id) channel.start(id)
  else channel.stop()
})
watch(busy, value => { if (!value && pendingSync) { const event = pendingSync; pendingSync = null; checkRemote(event) } })
function checkOnFocus() { if (document.value) checkRemote({ type: 'RECHECK', documentId: document.value.id }) }

function formatTime(value) { return value ? value.replace('T', ' ') : '—' }
function setDraft(row) {
  form.title = row.title
  form.content = bodyOf(row)
  baseline.value = snapshot()
  editorKey.value++
}
async function mayLeave() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前有未保存修改，是否放弃这些修改？', '未保存修改', {
      confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning',
    })
    return true
  } catch { return false }
}
function unload(event) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
function beforeLeave(event) {
  if (dirty.value && !window.confirm('当前有未保存修改，是否放弃并退出登录？')) event.preventDefault()
}
async function loadDocuments() {
  const request = ++listRequest
  listLoading.value = true
  listError.value = ''
  try {
    const result = await requestJson(`/api/documents?keyword=${encodeURIComponent(keyword.value)}`)
    if (request === listRequest) documents.value = result
  } catch (error) { if (request === listRequest) listError.value = error.message }
  finally { if (request === listRequest) listLoading.value = false }
}
async function openDocument(id, skipPrompt = false) {
  if (busy.value || (!skipPrompt && !await mayLeave())) return
  const request = ++detailRequest
  mode.value = 'detail'
  showSharing.value = false
  showHistory.value = false
  editing.value = false
  selectedId.value = id
  document.value = null
  detailError.value = ''
  notice.value = ''
  actionError.value = ''
  conflict.value = false
  detailLoading.value = true
  try {
    const result = await requestJson(`/api/documents/${id}`)
    if (request === detailRequest) { setDraft(result); document.value = result }
  } catch (error) { if (request === detailRequest) detailError.value = error.message }
  finally { if (request === detailRequest) detailLoading.value = false }
}
async function newDocument() {
  if (busy.value || !await mayLeave()) return
  ++detailRequest
  detailLoading.value = false
  selectedId.value = null
  document.value = null
  mode.value = 'create'
  showSharing.value = false
  showHistory.value = false
  editing.value = true
  form.title = ''
  form.content = emptyContent()
  baseline.value = snapshot()
  editorKey.value++
  notice.value = ''
  actionError.value = ''
  conflict.value = false
}
async function saveDocument() {
  if (busy.value || !validTitle.value || !canEdit.value) return
  busy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const creating = mode.value === 'create'
    const saved = await requestJson(creating ? '/api/documents' : `/api/documents/${selectedId.value}`, {
      method: creating ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: form.title, content: form.content, ...(!creating ? { version: document.value.version } : {}) }),
    })
    setDraft(saved)
    document.value = saved
    selectedId.value = saved.id
    mode.value = 'detail'
    editing.value = false
    conflict.value = false
    notice.value = creating ? '文档创建成功。' : '文档保存成功。'
    await loadDocuments()
  } catch (error) {
    conflict.value = error.code === 'DOCUMENT_VERSION_CONFLICT'
    actionError.value = error.message + (conflict.value ? '' : ' 本地输入已保留；超时后请先确认服务器状态再重试。')
  } finally { busy.value = false }
}
async function cancelEdit() {
  if (busy.value || !await mayLeave()) return
  if (document.value) { setDraft(document.value); editing.value = false }
  else { mode.value = 'welcome'; editing.value = false }
}
async function restoreVersion(number) {
  if (!document.value || busy.value || !canEdit.value) return
  const id = document.value.id
  const version = document.value.version
  busy.value = true
  ++syncRequest
  try {
    await ElMessageBox.confirm(
      `恢复 v${number} 将生成新的当前版本并保留全部历史。${dirty.value ? '当前有未保存草稿，成功恢复会放弃这些修改。' : ''}确定恢复？`,
      '恢复历史版本', { confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning' },
    )
    actionError.value = ''
    notice.value = ''
    const saved = await requestJson(`/api/documents/${id}/versions/${number}/restore`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version }),
    })
    if (document.value?.id !== id) return
    setDraft(saved)
    document.value = saved
    editing.value = false
    conflict.value = false
    showHistory.value = false
    remoteNotice.value = ''
    notice.value = `已恢复为新版本 v${saved.version}，历史记录已保留。`
    await loadDocuments()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    conflict.value = error.code === 'DOCUMENT_VERSION_CONFLICT'
    actionError.value = `${error.message} 本地草稿已保留。`
    showHistory.value = false
  } finally { busy.value = false }
}
async function deleteDocument() {
  if (!document.value || busy.value || document.value.currentRole !== 'OWNER') return
  try {
    await ElMessageBox.confirm(dirty.value ? '当前有未保存修改。删除后不可恢复，确定删除这篇文档？' : '删除后不可恢复，确定删除这篇文档？', '删除文档', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch { return }
  busy.value = true
  try {
    await requestJson(`/api/documents/${document.value.id}`, { method: 'DELETE' })
    ++detailRequest
    mode.value = 'welcome'
    editing.value = false
    selectedId.value = null
    document.value = null
    actionError.value = ''
    notice.value = '文档已删除。'
    await loadDocuments()
  } catch (error) { actionError.value = error.message }
  finally { busy.value = false }
}
onMounted(() => { loadDocuments(); window.addEventListener('beforeunload', unload); window.addEventListener('before-workspace-leave', beforeLeave); window.addEventListener('focus', checkOnFocus) })
onBeforeUnmount(() => { ++detailRequest; ++listRequest; ++syncRequest; channel.stop(); window.removeEventListener('beforeunload', unload); window.removeEventListener('before-workspace-leave', beforeLeave); window.removeEventListener('focus', checkOnFocus) })
</script>

<template>
  <div class="workspace">
    <el-card class="document-list">
      <div class="panel-heading"><h2>文档列表</h2><el-button type="primary" :disabled="busy" @click="newDocument">新建文档</el-button></div>
      <div class="scope-actions" role="group" aria-label="文档范围">
        <el-button :type="listScope === 'all' ? 'primary' : 'default'" :aria-pressed="listScope === 'all'" @click="listScope = 'all'">全部文档</el-button>
        <el-button :type="listScope === 'mine' ? 'primary' : 'default'" :aria-pressed="listScope === 'mine'" @click="listScope = 'mine'">我的文档</el-button>
        <el-button :type="listScope === 'shared' ? 'primary' : 'default'" :aria-pressed="listScope === 'shared'" @click="listScope = 'shared'">共享给我的</el-button>
      </div>
      <el-form class="search" @submit.prevent="loadDocuments">
        <el-input v-model="keyword" aria-label="标题关键词" placeholder="按标题搜索" maxlength="255" clearable />
        <el-button native-type="submit" :loading="listLoading">搜索</el-button>
        <el-button :loading="listLoading" @click="loadDocuments">刷新列表</el-button>
      </el-form>
      <p v-if="listLoading" role="status">正在加载文档列表…</p>
      <div v-if="listError" role="alert"><el-alert :title="listError" type="error" :closable="false" /><el-button @click="loadDocuments">重试列表</el-button></div>
      <el-empty v-if="!listLoading && !listError && visibleDocuments.length === 0" :description="keyword ? '没有匹配的文档。' : '暂无文档，点击新建文档开始。'" />
      <ul v-if="visibleDocuments.length" class="documents" aria-label="文档列表">
        <li v-for="item in visibleDocuments" :key="item.id">
          <button class="document-link" :class="{ selected: selectedId === item.id }" :disabled="busy" :aria-pressed="selectedId === item.id" @click="openDocument(item.id)">
            <strong>{{ item.title }}</strong><small>{{ item.currentRole === 'OWNER' ? '我的文档' : '共享给我' }} · {{ roleLabels[item.currentRole] }} {{ item.currentRole }}</small><small>创建于 {{ formatTime(item.createdAt) }}</small>
          </button>
        </li>
      </ul>
    </el-card>
    <el-card class="document-main">
      <el-alert v-if="notice" :title="notice" type="success" :closable="false" class="notice" />
      <p v-if="detailLoading" role="status">正在加载文档详情…</p>
      <div v-else-if="mode === 'detail' && detailError" role="alert"><el-alert :title="detailError" type="error" :closable="false" /><el-button @click="openDocument(selectedId)">重试详情</el-button></div>
      <section v-else-if="mode === 'create' || document" aria-label="文档编辑区">
        <h2>{{ mode === 'create' ? '创建文档' : document.title }}</h2>
        <p v-if="document" class="metadata">编号 {{ document.id }} · 版本 {{ document.version }} · <el-tag size="small" :type="document.currentRole === 'OWNER' ? 'primary' : 'info'">{{ roleLabels[document.currentRole] || '无访问权限' }} {{ document.currentRole }}</el-tag> · 更新 {{ formatTime(document.updatedAt) }}</p>
        <p v-if="document" class="realtime-status"><span class="sync-dot" :class="{ connected: realtimeStatus === '实时连接正常' }"></span>{{ realtimeStatus }} <el-button size="small" text @click="channel.reconnect()">重连实时通知</el-button></p>
        <el-alert v-if="remoteNotice" :title="remoteNotice" type="info" :closable="false" />
        <div class="edit-status" role="status"><el-tag :type="dirty ? 'warning' : 'info'">{{ busy ? '正在处理…' : dirty ? '有未保存修改' : editing ? '编辑中' : '只读查看' }}</el-tag><span>{{ document?.currentRole === 'VIEWER' ? '查看者可阅读正文和历史版本。' : '修改后请手动保存，实时连接状态不代表保存结果。' }}</span></div>
        <el-form label-position="top" @submit.prevent="saveDocument">
          <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" class="notice" />
          <div class="actions document-actions" aria-label="文档操作">
            <el-button v-if="editing" type="primary" native-type="submit" :loading="busy" :disabled="!validTitle || busy || !canEdit">{{ mode === 'create' ? '创建文档' : '保存文档' }}</el-button>
            <el-button v-else-if="canEdit" type="primary" :disabled="busy" @click="editing = true">编辑文档</el-button>
            <el-button v-if="editing" :disabled="busy" @click="cancelEdit">取消编辑</el-button>
            <el-button v-if="document?.currentRole === 'OWNER'" :disabled="busy" @click="showSharing = true; showHistory = false">共享文档</el-button>
            <el-button v-if="document" :disabled="busy" @click="showHistory = true; showSharing = false">历史版本</el-button>
            <el-button v-if="document" :disabled="busy" @click="openDocument(document.id)">{{ conflict ? '重新加载最新版本' : '重新加载' }}</el-button>
            <el-button v-if="document?.currentRole === 'OWNER'" type="danger" plain :disabled="busy" @click="deleteDocument">删除文档</el-button>
          </div>
          <el-form-item v-if="editing" label="标题（必填，最多 255 字符）"><el-input v-model="form.title" aria-label="文档标题" maxlength="255" show-word-limit :disabled="busy || !canEdit" /></el-form-item>
          <RichTextEditor :key="editorKey" v-model="form.content" :readonly="!editing || busy || !canEdit" />
        </el-form>
        <el-dialog v-if="showSharing && document?.currentRole === 'OWNER'" v-model="showSharing" class="sharing-dialog" title="共享管理" width="min(800px, calc(100vw - 48px))" align-center append-to-body :close-on-click-modal="false" destroy-on-close>
          <SharingPanel :key="document.id" :document-id="document.id" />
        </el-dialog>
        <HistoryPanel v-if="showHistory && document" :key="document.id" :document-id="document.id" :current-version="document.version" :current-role="document.currentRole" :busy="busy" @close="showHistory = false" @restore="restoreVersion" />
      </section>
      <el-empty v-else description="从左侧选择文档，或创建一篇新文档。" />
    </el-card>
  </div>
</template>

<style scoped>
.workspace { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 20px; align-items: start; }
.workspace > .el-card { min-width: 0; }
.document-list :deep(.el-card__body) { padding: 16px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h2 { font-size: 20px; margin-top: 0; overflow-wrap: anywhere; }
.panel-heading h2 { margin-bottom: 0; }
.documents { list-style: none; padding: 0; margin: 16px 0 0; max-height: 58vh; overflow-y: auto; }
.document-link { width: 100%; padding: 14px 12px; text-align: left; background: white; border: 1px solid #dcdfe6; border-radius: 6px; margin-bottom: 8px; cursor: pointer; font: inherit; color: inherit; }
.document-link:hover, .document-link.selected { background: #ecf5ff; border-color: #409eff; }
.document-link:focus-visible { outline: 2px solid #409eff; outline-offset: 2px; }
.document-link:disabled { cursor: wait; opacity: .7; }
.document-link strong { display: block; overflow-wrap: anywhere; }
small { display: block; color: #606266; margin-top: 8px; }
.metadata { font-size: 13px; }
.metadata, .realtime-status { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin: 10px 0; }
.realtime-status { font-size: 12px; color: #737b89; }
.sync-dot { width: 7px; height: 7px; border-radius: 50%; background: #a0a6af; flex-shrink: 0; }
.sync-dot.connected { background: #529b2e; }
.edit-status { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 12px 0; }
.edit-status > span { color: #737b89; font-size: 12px; }
.notice { margin-bottom: 20px; }
.document-main { min-height: 440px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
.document-actions { margin: 0 0 16px; position: sticky; top: 8px; z-index: 5; padding: 8px 0; background: white; border-bottom: 1px solid #ebeef5; }
.scope-actions { display: flex; gap: 4px; margin-top: 16px; }
.scope-actions .el-button { font-size: 12px; padding: 8px; flex: 1; }
.search { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.search .el-button { margin-left: 0; }
@media (max-width: 1000px) { .workspace { grid-template-columns: 250px minmax(0, 1fr); gap: 12px; } .scope-actions { flex-wrap: wrap; } }
</style>

<style>
.sharing-dialog.el-dialog { max-height: 90vh; display: flex; flex-direction: column; margin: auto; }
.sharing-dialog .el-dialog__body { min-height: 0; overflow-y: auto; }
</style>
