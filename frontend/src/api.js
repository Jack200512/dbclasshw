// CSRF stays in memory; authentication stays in an HttpOnly session cookie.
let csrf = null
let csrfLoading = null
export async function refreshCsrf() {
  if (!csrfLoading) {
    csrfLoading = fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) })
      .then(async response => {
        if (!response.ok) throw new Error('无法获取安全令牌，请重试。')
        csrf = await response.json()
        return csrf
      }).finally(() => { csrfLoading = null })
  }
  return csrfLoading
}

export async function requestJson(path, options = {}) {
  try {
    const headers = new Headers(options.headers)
    if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
      const token = csrf || await refreshCsrf()
      headers.set(token.headerName, token.token)
    }
    const response = await fetch(path, {
      cache: 'no-store',
      signal: AbortSignal.timeout(10000),
      ...options,
      credentials: 'same-origin',
      headers,
    })
    if (!response.ok) {
      if (response.status === 401) {
        if (path !== '/api/auth/login') window.dispatchEvent(new Event('session-expired'))
        throw new Error(path === '/api/auth/login' ? '用户名、邮箱或密码错误。' : '会话已失效，请重新登录。')
      }
      if (response.status === 403) {
        csrf = null
        const me = await fetch('/api/users/me', { credentials: 'same-origin', cache: 'no-store' })
        if (me.status === 401) window.dispatchEvent(new Event('session-expired'))
        throw new Error('安全令牌或会话已失效，请重试或重新登录。')
      }
      if (response.status === 409) throw new Error('用户名或邮箱已被使用。')
      if (response.status === 404) throw new Error('文档不存在，请刷新列表后重试。')
      if (response.status === 400) throw new Error('输入无效，请检查必填项、格式与长度。')
      throw new Error(`请求失败（HTTP ${response.status}），请稍后重试。`)
    }
    return await response.json()
  } catch (error) {
    if (error.name === 'TimeoutError') throw new Error('请求超时，请检查连接后重试。')
    if (error instanceof TypeError) throw new Error('无法连接后端，请确认服务已启动后重试。')
    throw error
  }
}
