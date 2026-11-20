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
        throw new Error('权限不足或安全令牌已失效；权限可能已被降级，请重新加载文档或重试。')
      }
      if (response.status === 409) {
        const body = await response.json()
        const error = new Error(body.code === 'DOCUMENT_VERSION_CONFLICT'
          ? '文档已被其他操作更新。当前内容已保留，请重新加载最新版本后再编辑。'
          : body.code === 'SHARING_CONFLICT' ? '该用户已获得此文档授权，请修改现有角色。' : '用户名或邮箱已被使用。')
        error.code = body.code
        throw error
      }
      if (response.status === 404) throw new Error('文档、授权记录不存在或访问权限已被撤销，请刷新列表后重试。')
      if (response.status === 400) throw new Error(path.includes('/permissions')
        ? '共享请求无效：请确认目标用户已注册、不是所有者，且角色为 EDITOR 或 VIEWER。'
        : '输入无效，请检查必填项、格式与长度。')
      throw new Error(`请求失败（HTTP ${response.status}），请稍后重试。`)
    }
    return response.status === 204 ? null : await response.json()
  } catch (error) {
    if (error.name === 'TimeoutError') throw new Error('请求超时，请检查连接后重试。')
    if (error instanceof TypeError) throw new Error('无法连接后端，请确认服务已启动后重试。')
    throw error
  }
}
