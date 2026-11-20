<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElAlert, ElButton, ElTag } from 'element-plus'
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
  if (!window.dispatchEvent(new Event('before-workspace-leave', { cancelable: true }))) return
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
    <header class="app-header">
      <div class="app-brand">
      <h1>dbclasshw · 多人在线文档编辑系统</h1>
      <p>文档工作台 · 共享、协作与版本记录</p>
      </div>
      <section class="health-status" aria-label="后端连接状态" aria-live="polite" :aria-busy="state === 'loading'">
        <el-tag v-if="state !== 'error'"
          :type="state === 'loading' ? 'info' : state"
        >{{ message }}</el-tag>
        <span v-else class="connection-error" role="alert">{{ message }}</span>
        <span v-if="health" class="health-message">{{ health.status }} · {{ health.message }}</span>
        <el-button size="small" :loading="state === 'loading'" @click="checkHealth">
        {{ state === 'loading' ? '正在连接' : '重新检查' }}
        </el-button>
      </section>
    </header>
    <p v-if="restoring" role="status">正在恢复登录状态…</p>
    <el-alert v-if="sessionMessage" :title="sessionMessage" type="info" :closable="false" />
    <el-button v-if="!restoring && !user" class="session-retry" @click="restoreSession">重新检查会话</el-button>
    <template v-if="!restoring && user">
      <div class="account-bar"><span>当前用户：<strong>{{ user.username }}</strong> · {{ user.email }}</span><el-button :loading="logoutBusy" @click="logout">退出登录</el-button></div>
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
  font-family: system-ui, 'Microsoft YaHei', sans-serif;
  font-size: 14px;
}
* { box-sizing: border-box; }
main {
  max-width: 1560px;
  margin: 20px auto;
  padding: 0 28px;
}
h1 {
  font-size: 21px;
  line-height: 1.5;
  margin: 0;
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
.app-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding: 16px 20px; background: white; border: 1px solid #e4e7ed; border-radius: 8px; }
.app-brand p { margin: 4px 0 0; font-size: 13px; color: #737b89; }
.health-status { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; max-width: 460px; }
.connection-error { color: #b42318; overflow-wrap: anywhere; }
.account-bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 12px 0; }
.account-bar span { min-width: 0; overflow-wrap: anywhere; }
.session-retry { display: block; margin: 16px auto; }
.el-card { --el-card-border-radius: 8px; box-shadow: none; }
.el-alert { margin: 10px 0; overflow-wrap: anywhere; }
.el-button + .el-button { margin-left: 0; }
.workspace { margin-top: 16px; }
.health-message { font-size: 13px; }
@media (max-width: 1000px) { main { padding: 0 16px; } .health-status { justify-content: flex-start; } }
</style>
