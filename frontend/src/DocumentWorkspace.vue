<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElCard, ElEmpty, ElForm, ElFormItem, ElInput } from 'element-plus'
import { requestJson } from './api'

const documents = ref([])
const listLoading = ref(false)
const listError = ref('')
const mode = ref('welcome')
const selectedId = ref(null)
const document = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const form = reactive({ title: '', content: '' })
const submitting = ref(false)
const createError = ref('')
const notice = ref('')
const titleError = ref('')
const validTitle = computed(() => form.title.trim().length > 0 && form.title.length <= 255)
let detailRequest = 0
let listRequest = 0

function formatTime(value) {
  return value ? value.replace('T', ' ') : '—'
}

async function loadDocuments() {
  const request = ++listRequest
  listLoading.value = true
  listError.value = ''
  try {
    const result = await requestJson('/api/documents')
    if (request === listRequest) documents.value = result
  } catch (error) {
    if (request === listRequest) listError.value = error.message
  } finally {
    if (request === listRequest) listLoading.value = false
  }
}

async function openDocument(id) {
  const request = ++detailRequest
  mode.value = 'detail'
  selectedId.value = id
  document.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const result = await requestJson(`/api/documents/${id}`)
    // A newer selection (including the create form) owns the detail area.
    if (request === detailRequest) document.value = result
  } catch (error) {
    if (request === detailRequest) detailError.value = error.message
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function newDocument() {
  if (submitting.value) return
  ++detailRequest
  detailLoading.value = false
  selectedId.value = null
  mode.value = 'create'
  notice.value = ''
}

async function createDocument() {
  if (submitting.value) return
  titleError.value = validTitle.value ? '' : '请输入非空标题，最多 255 个字符。'
  if (!validTitle.value) return
  submitting.value = true
  createError.value = ''
  notice.value = ''
  try {
    const created = await requestJson('/api/documents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: form.title, content: form.content }),
    })
    form.title = ''
    form.content = ''
    titleError.value = ''
    notice.value = '文档创建成功。'
    // Include the confirmed creation immediately, even if a concurrent list refresh fails.
    documents.value = [...documents.value.filter(item => item.id !== created.id), created]
    await Promise.all([loadDocuments(), openDocument(created.id)])
  } catch (error) {
    createError.value = `${error.message} 输入已保留；若请求超时，请先刷新列表确认是否已创建。`
  } finally {
    submitting.value = false
  }
}

onMounted(loadDocuments)
</script>

<template>
  <div class="workspace">
    <el-card class="document-list">
      <div class="panel-heading">
        <h2>文档列表</h2>
        <el-button type="primary" :disabled="submitting" @click="newDocument">新建文档</el-button>
      </div>
      <el-button :loading="listLoading" @click="loadDocuments">刷新列表</el-button>
      <p v-if="listLoading" role="status">正在加载文档列表…</p>
      <div v-if="listError" role="alert">
        <el-alert :title="listError" type="error" :closable="false" />
        <el-button :disabled="listLoading" @click="loadDocuments">重试列表</el-button>
      </div>
      <el-empty v-if="!listLoading && !listError && documents.length === 0" description="暂无文档，点击新建文档开始。" />
      <ul v-if="documents.length" class="documents" aria-label="文档列表">
        <li v-for="item in documents" :key="item.id">
          <button
            class="document-link"
            :class="{ selected: selectedId === item.id }"
            :disabled="submitting"
            :aria-pressed="selectedId === item.id"
            @click="openDocument(item.id)"
          >
            <strong>{{ item.title }}</strong>
            <small>创建于 {{ formatTime(item.createdAt) }}</small>
          </button>
        </li>
      </ul>
    </el-card>

    <el-card class="document-main">
      <el-alert v-if="notice" :title="notice" type="success" :closable="false" class="notice" />
      <section v-if="mode === 'create'" aria-label="创建文档">
        <h2>创建文档</h2>
        <el-form label-position="top" @submit.prevent="createDocument">
          <el-form-item label="标题（必填）" :error="titleError">
            <el-input v-model="form.title" aria-label="文档标题" maxlength="255" show-word-limit :disabled="submitting" />
          </el-form-item>
          <el-form-item label="正文（选填）">
            <el-input v-model="form.content" aria-label="文档正文" type="textarea" :rows="12" :disabled="submitting" />
          </el-form-item>
          <el-alert v-if="createError" :title="createError" type="error" :closable="false" class="notice" />
          <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!validTitle || submitting">创建文档</el-button>
        </el-form>
      </section>
      <section v-else-if="mode === 'detail'" aria-label="文档详情" :aria-busy="detailLoading">
        <p v-if="detailLoading" role="status">正在加载文档详情…</p>
        <div v-else-if="detailError" role="alert">
          <el-alert :title="detailError" type="error" :closable="false" />
          <el-button @click="openDocument(selectedId)">重试详情</el-button>
        </div>
        <article v-else-if="document">
          <h2>{{ document.title }}</h2>
          <p class="metadata">编号 {{ document.id }} · 版本 {{ document.version }}</p>
          <p class="metadata">创建 {{ formatTime(document.createdAt) }} · 更新 {{ formatTime(document.updatedAt) }}</p>
          <div class="document-content">{{ document.content || '（正文为空）' }}</div>
        </article>
      </section>
      <el-empty v-else description="从左侧选择文档，或创建一篇新文档。" />
    </el-card>
  </div>
</template>

<style scoped>
.workspace { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 20px; align-items: start; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h2 { font-size: 20px; margin-top: 0; overflow-wrap: anywhere; }
.panel-heading h2 { margin-bottom: 0; }
.documents { list-style: none; padding: 0; margin: 16px 0 0; }
.document-link { width: 100%; padding: 14px 12px; text-align: left; background: white; border: 1px solid #dcdfe6; border-radius: 6px; margin-bottom: 8px; cursor: pointer; font: inherit; color: inherit; }
.document-link:hover, .document-link.selected { background: #ecf5ff; border-color: #409eff; }
.document-link:disabled { cursor: wait; opacity: .7; }
.document-link strong { display: block; overflow-wrap: anywhere; }
small { display: block; color: #606266; margin-top: 8px; }
.metadata { font-size: 13px; }
.document-content { white-space: pre-wrap; overflow-wrap: anywhere; border-top: 1px solid #ebeef5; margin-top: 20px; padding-top: 20px; line-height: 1.8; min-height: 240px; }
.notice { margin-bottom: 20px; }
.document-main { min-height: 440px; }
</style>
