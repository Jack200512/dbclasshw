// One authenticated, document-scoped native WebSocket per mounted workspace.
export function createDocumentChannel(onMessage, onStatus) {
  let socket = null
  let documentId = null
  let generation = 0
  let retryTimer = null
  let heartbeat = null
  let attempts = 0
  const delays = [500, 1000, 2000, 4000, 8000]

  function clearTimers() { clearTimeout(retryTimer); clearInterval(heartbeat) }
  function connect(current) {
    if (current !== generation || !documentId) return
    onStatus(attempts ? '正在重连…' : '正在连接实时通知…')
    const url = new URL('/api/ws/documents', window.location.href)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(url)
    socket = ws
    let lastSeen = Date.now()
    ws.onopen = () => {
      if (current !== generation) { ws.close(); return }
      ws.send(JSON.stringify({ type: 'SUBSCRIBE', documentId }))
      heartbeat = setInterval(() => {
        if (Date.now() - lastSeen > 35000) { ws.close(); return }
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'PING' }))
      }, 15000)
    }
    ws.onmessage = event => {
      if (current !== generation) return
      lastSeen = Date.now()
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'PONG') return
        if (data.documentId !== documentId) return
        if (data.type === 'SUBSCRIBED') { attempts = 0; onStatus('实时连接正常') }
        onMessage(data)
      } catch { ws.close() }
    }
    ws.onerror = () => { /* onclose owns retry handling. HTTP remains available. */ }
    ws.onclose = event => {
      if (current !== generation) return
      clearTimers()
      if (event.code === 1008) {
        onStatus('实时订阅已失效，请重新检查会话或文档权限。')
        onMessage({ type: 'ACCESS_LOST', documentId })
      } else if (attempts < delays.length) {
        const delay = delays[attempts++]
        onStatus(`实时连接断开，${delay / 1000} 秒后重连…`)
        retryTimer = setTimeout(() => connect(current), delay)
      } else onStatus('实时连接失败；HTTP 仍可使用，可手动重连。')
    }
  }
  function stop() {
    generation++
    clearTimers()
    documentId = null
    if (socket) { socket.close(); socket = null }
    onStatus('未连接实时通知')
  }
  function start(id) { stop(); documentId = id; attempts = 0; connect(generation) }
  return { start, stop, reconnect() { if (documentId) start(documentId) } }
}
