<script setup>
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElSelect, ElOption, ElMessageBox } from 'element-plus'
import { requestJson } from './api'

const props = defineProps({ documentId: { type: Number, required: true } })
const members = ref([])
const login = ref('')
const role = ref('VIEWER')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const endpoint = () => `/api/documents/${props.documentId}/permissions`
async function load() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { members.value = await requestJson(endpoint()) }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function change(method, userId = null, body = null) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await requestJson(endpoint() + (userId === null ? '' : `/${userId}`), {
      method, ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
    })
    members.value = result || await requestJson(endpoint())
    if (method === 'POST') login.value = ''
    notice.value = '共享权限已更新。'
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function revoke(member) {
  try {
    await ElMessageBox.confirm(`撤销 ${member.username} 的访问权限？`, '撤销共享', { confirmButtonText: '确定撤销', cancelButtonText: '取消' })
    await change('DELETE', member.userId)
  } catch { /* Confirmation canceled. */ }
}
onMounted(load)
</script>

<template>
  <section class="sharing" aria-label="共享管理">
    <h3>成员与权限</h3>
    <p>编辑者可查看和保存；查看者只能阅读。所有者保持不变。</p>
    <div class="share-actions">
      <el-input v-model="login" aria-label="共享用户名或邮箱" placeholder="注册用户的用户名或邮箱" maxlength="254" :disabled="busy" />
      <el-select v-model="role" aria-label="新增共享角色" :disabled="busy"><el-option label="查看者 VIEWER" value="VIEWER" /><el-option label="编辑者 EDITOR" value="EDITOR" /></el-select>
      <el-button type="primary" :disabled="busy || !login.trim()" @click="change('POST', null, { login, role })">添加共享</el-button>
      <el-button :loading="busy" @click="load">刷新成员</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <p v-if="busy" role="status">正在处理共享权限…</p>
    <p v-else-if="!members.length && !error">尚未共享给其他用户。</p>
    <ul class="members">
      <li v-for="member in members" :key="member.userId" :data-user-id="member.userId">
        <span><strong>{{ member.username }}</strong><small>{{ member.email }}</small></span>
        <el-select :model-value="member.role" :aria-label="`${member.username} 的角色`" :disabled="busy" @change="value => change('PUT', member.userId, { role: value })">
          <el-option label="查看者 VIEWER" value="VIEWER" /><el-option label="编辑者 EDITOR" value="EDITOR" />
        </el-select>
        <el-button type="danger" plain :disabled="busy" @click="revoke(member)">撤销共享</el-button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.sharing h3 { margin-top: 0; font-size: 15px; }
.share-actions, .members li { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin: 12px 0; }
.share-actions .el-input { width: 250px; flex: 1; min-width: 180px; }
.el-select { width: 180px; }
.members { list-style: none; padding: 0; }
.members li { padding: 12px 0; border-bottom: 1px solid #ebeef5; }
.members span { overflow-wrap: anywhere; flex: 1; min-width: 180px; }
.members small { display: block; color: #737b89; margin-top: 4px; }
</style>
