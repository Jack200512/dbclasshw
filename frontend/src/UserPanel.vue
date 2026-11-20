<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput } from 'element-plus'
import { requestJson, refreshCsrf } from './api'

const props = defineProps({ user: { type: Object, default: null } })
const emit = defineEmits(['authenticated', 'updated'])
const mode = ref('login')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({ username: '', email: '', password: '', login: '' })
const complete = computed(() => props.user ? !!form.email.trim() : mode.value === 'login'
  ? !!(form.login.trim() && form.password) : !!(form.username.trim() && form.email.trim() && form.password))
watch(() => props.user, user => { if (user) form.email = user.email }, { immediate: true })

async function submit() {
  if (busy.value) return
  if (!complete.value) { error.value = '请填写所有必填项。'; return }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (props.user) {
      const result = await requestJson('/api/users/me', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: form.email }) })
      emit('updated', result)
      notice.value = '资料已更新。'
    } else if (mode.value === 'register') {
      await requestJson('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: form.username, email: form.email, password: form.password }) })
      form.login = form.username
      form.password = ''
      mode.value = 'login'
      notice.value = '注册成功，请登录。'
    } else {
      const user = await requestJson('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ login: form.login, password: form.password }) })
      form.password = ''
      await refreshCsrf()
      emit('authenticated', user)
    }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.password = ''
  error.value = ''
  notice.value = ''
}
</script>

<template>
  <el-card class="user-panel" :class="user ? 'profile-panel' : 'auth-panel'">
    <div class="profile-description">
    <h2>{{ user ? '个人资料' : mode === 'login' ? '登录' : '注册账号' }}</h2>
    <p v-if="user">用户 {{ user.id }} · {{ user.username }} · 注册于 {{ user.createdAt?.replace('T', ' ') }}</p>
    <p v-else>登录后管理私人文档，与成员共享协作。</p>
    </div>
    <el-form label-position="top" @submit.prevent="submit">
      <template v-if="!user && mode === 'login'">
        <el-form-item label="用户名或邮箱" required><el-input v-model="form.login" aria-label="用户名或邮箱" placeholder="输入用户名或邮箱" autocomplete="username" maxlength="254" :disabled="busy" /></el-form-item>
      </template>
      <template v-if="!user && mode === 'register'">
        <p>用户名为 3–32 位英文字母、数字或下划线，且以字母开头。密码为 8–64 个字符，UTF-8 编码不超过 72 字节。</p>
        <el-form-item label="用户名" required><el-input v-model="form.username" aria-label="用户名" placeholder="以字母开头，3–32 位" autocomplete="username" maxlength="32" :disabled="busy" /></el-form-item>
      </template>
      <el-form-item v-if="user || mode === 'register'" label="邮箱" required><el-input v-model="form.email" aria-label="邮箱" type="email" placeholder="输入邮箱地址" maxlength="254" autocomplete="email" :disabled="busy" /></el-form-item>
      <el-form-item v-if="!user" label="密码" required><el-input v-model="form.password" aria-label="密码" placeholder="输入密码" type="password" show-password :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" maxlength="64" :disabled="busy" /></el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
      <div class="actions">
        <el-button type="primary" native-type="submit" :loading="busy" :disabled="busy || !complete">{{ user ? '保存资料' : mode === 'login' ? '登录' : '注册' }}</el-button>
        <el-button v-if="!user" :disabled="busy" @click="switchMode">{{ mode === 'login' ? '去注册' : '返回登录' }}</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.user-panel { margin-top: 16px; }
.auth-panel { max-width: 460px; margin: 32px auto; }
.auth-panel :deep(.el-card__body) { padding: 28px; }
.auth-panel .actions { display: flex; gap: 12px; margin-top: 20px; }
.profile-panel :deep(.el-card__body) { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 28px; padding: 14px 20px; }
.profile-description { flex: 1; min-width: 220px; }
.profile-description p { margin: 4px 0 0; font-size: 12px; }
.profile-panel :deep(.el-form) { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.profile-panel :deep(.el-form-item) { margin-bottom: 0; width: 290px; max-width: 100%; }
.profile-panel :deep(.el-form-item__label) { font-size: 12px; margin-bottom: 4px; }
.profile-panel .el-alert { width: 100%; }
h2 { font-size: 18px; margin: 0 0 8px; }
.profile-panel h2 { font-size: 15px; margin-bottom: 0; }
</style>
