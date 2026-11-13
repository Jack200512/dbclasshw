<script setup>
import { reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput } from 'element-plus'
import { requestJson, refreshCsrf } from './api'

const props = defineProps({ user: { type: Object, default: null } })
const emit = defineEmits(['authenticated', 'updated'])
const mode = ref('login')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({ username: '', email: '', password: '', login: '' })
watch(() => props.user, user => { if (user) form.email = user.email }, { immediate: true })

async function submit() {
  if (busy.value) return
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
  <el-card class="user-panel">
    <h2>{{ user ? '个人资料' : mode === 'login' ? '登录' : '注册账号' }}</h2>
    <p v-if="user">用户 {{ user.id }} · {{ user.username }} · 注册于 {{ user.createdAt?.replace('T', ' ') }}</p>
    <el-form label-position="top" @submit.prevent="submit">
      <template v-if="!user && mode === 'login'">
        <el-form-item label="用户名或邮箱"><el-input v-model="form.login" aria-label="用户名或邮箱" autocomplete="username" maxlength="254" :disabled="busy" /></el-form-item>
      </template>
      <template v-if="!user && mode === 'register'">
        <p>用户名为 3–32 位英文字母、数字或下划线，且以字母开头。密码为 8–64 个字符，UTF-8 编码不超过 72 字节。</p>
        <el-form-item label="用户名"><el-input v-model="form.username" aria-label="用户名" autocomplete="username" maxlength="32" :disabled="busy" /></el-form-item>
      </template>
      <el-form-item v-if="user || mode === 'register'" label="邮箱"><el-input v-model="form.email" aria-label="邮箱" type="email" maxlength="254" autocomplete="email" :disabled="busy" /></el-form-item>
      <el-form-item v-if="!user" label="密码"><el-input v-model="form.password" aria-label="密码" type="password" show-password :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" maxlength="64" :disabled="busy" /></el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
      <div class="actions">
        <el-button type="primary" native-type="submit" :loading="busy">{{ user ? '保存资料' : mode === 'login' ? '登录' : '注册' }}</el-button>
        <el-button v-if="!user" :disabled="busy" @click="switchMode">{{ mode === 'login' ? '去注册' : '返回登录' }}</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.user-panel { margin-top: 20px; }
.actions { margin-top: 16px; }
h2 { font-size: 20px; }
</style>
