export function bodyOf(row) {
  if (row.contentFormat === 'TIPTAP_JSON') return JSON.parse(row.content)
  if (row.contentFormat !== 'PLAIN_TEXT') throw new Error('无法识别的正文格式。')
  return { type: 'doc', content: (row.content || '').split(/\r?\n/).map(text => ({
    type: 'paragraph', ...(text ? { content: [{ type: 'text', text }] } : {}),
  })) }
}
