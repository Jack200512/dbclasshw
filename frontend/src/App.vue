<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElAlert, ElButton, ElCard } from 'element-plus'
import DocumentWorkspace from './DocumentWorkspace.vue'
import UserPanel from './UserPanel.vue'
import { requestJson, refreshCsrf } from './api'

const user = ref(null)
const restoring = ref(true)
const sessionMessage = ref('')
const logoutBusy = ref(false)
function sessionExpired() {
  if (user.value) sessionMessage.value = '会话已失效，请重新登录。'
  user.value = null
}
function signedIn(value) { user.value = value; sessionMessage.value = '' }
async function restoreSession() {
  restoring.value = true
  sessionMessage.value = ''
  try {
    await refreshCsrf()
    const response = await fetch('/api/users/me', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) })
    if (response.ok) user.value = await response.json()
    else if (response.status !== 401) throw new Error('无法恢复会话，请重试。')
  } catch (e) { sessionMessage.value = e.message }
  finally { restoring.value = false }
}
async function logout() {
  if (logoutBusy.value) return
  logoutBusy.value = true
  try {
    await requestJson('/api/auth/logout', { method: 'POST' })
    user.value = null
    sessionMessage.value = '已退出登录。'
    await refreshCsrf()
  } catch (e) { sessionMessage.value = e.message }
  finally { logoutBusy.value = false }
}
onMounted(() => { window.addEventListener('session-expired', sessionExpired); restoreSession() })
onUnmounted(() => window.removeEventListener('session-expired', sessionExpired))

const state = ref('loading')
const message = ref('正在请求后端健康检查接口…')
const health = ref(null)

async function checkHealth() {
  state.value = 'loading'
  message.value = '正在请求后端健康检查接口…'
  health.value = null

  try {
    const response = await fetch('/api/health', {
      signal: AbortSignal.timeout(5000),
      cache: 'no-store',
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const data = await response.json()
    if (data.status !== 'UP' || typeof data.message !== 'string') {
      throw new Error('健康检查响应格式不符合预期')
    }
    health.value = data
    state.value = 'success'
    message.value = '连接成功'
  } catch (error) {
    state.value = 'error'
    message.value = `连接失败，请确认后端已启动后重试。${error.message}`
  }
}

onMounted(checkHealth)
</script>

<template>
  <main>
    <el-card>
      <h1>dbclasshw · 多人在线文档编辑系统</h1>
      <p>数据库课程大作业 · 文档工作台</p>
      <section aria-live="polite" :aria-busy="state === 'loading'">
        <el-alert
          :title="message"
          :type="state === 'loading' ? 'info' : state"
          :closable="false"
          show-icon
        />
        <p v-if="health" class="health-message">{{ health.status }} · {{ health.message }}</p>
      </section>
      <el-button type="primary" :loading="state === 'loading'" @click="checkHealth">
        {{ state === 'loading' ? '正在连接' : '重新检查' }}
      </el-button>
    </el-card>
    <p v-if="restoring" role="status">正在恢复登录状态…</p>
    <el-alert v-if="sessionMessage" :title="sessionMessage" type="info" :closable="false" />
    <el-button v-if="!restoring && !user" @click="restoreSession">重新检查会话</el-button>
    <template v-if="!restoring && user">
      <p>当前用户：{{ user.username }} · {{ user.email }} <el-button :loading="logoutBusy" @click="logout">退出登录</el-button></p>
      <UserPanel :user="user" @updated="user = $event" />
      <DocumentWorkspace :key="user.id" />
    </template>
    <UserPanel v-else-if="!restoring" @authenticated="signedIn" />
  </main>
</template>

<style>
body {
  margin: 0;
  background: #f5f7fa;
  color: #303133;
  font-family: system-ui, sans-serif;
}
main {
  max-width: 1200px;
  margin: 32px auto;
  padding: 0 20px;
}
h1 {
  font-size: 24px;
}
p {
  color: #606266;
  line-height: 1.6;
}
pre {
  padding: 16px;
  background: #f5f7fa;
  white-space: pre-wrap;
}
.workspace { margin-top: 20px; }
.health-message { font-size: 13px; }
</style>
