<script setup>
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElCard } from 'element-plus'

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
      <p>数据库课程大作业 · M1 前后端通信验证</p>
      <section aria-live="polite" :aria-busy="state === 'loading'">
        <el-alert
          :title="message"
          :type="state === 'loading' ? 'info' : state"
          :closable="false"
          show-icon
        />
        <pre v-if="health">{{ JSON.stringify(health, null, 2) }}</pre>
      </section>
      <el-button type="primary" :loading="state === 'loading'" @click="checkHealth">
        {{ state === 'loading' ? '正在连接' : '重新检查' }}
      </el-button>
    </el-card>
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
  max-width: 760px;
  margin: 64px auto;
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
.el-button {
  margin-top: 20px;
}
</style>
