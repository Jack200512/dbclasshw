// Native fetch with relative URLs; Vite handles development proxying.
export async function requestJson(path, options = {}) {
  try {
    const response = await fetch(path, {
      cache: 'no-store',
      signal: AbortSignal.timeout(10000),
      ...options,
    })
    if (!response.ok) {
      if (response.status === 404) throw new Error('文档不存在，请刷新列表后重试。')
      if (response.status === 400) throw new Error('请求无效，请检查标题（必填且不超过 255 个字符）。')
      throw new Error(`请求失败（HTTP ${response.status}），请稍后重试。`)
    }
    return await response.json()
  } catch (error) {
    if (error.name === 'TimeoutError') throw new Error('请求超时，请检查连接后重试。')
    if (error instanceof TypeError) throw new Error('无法连接后端，请确认服务已启动后重试。')
    throw error
  }
}
