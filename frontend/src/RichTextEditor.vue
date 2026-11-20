<script setup>
import { onMounted, onBeforeUnmount, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { ElButton } from 'element-plus'

const props = defineProps({ modelValue: { type: Object, required: true }, readonly: Boolean })
const emit = defineEmits(['update:modelValue'])
const editor = shallowRef(null)
onMounted(() => {
  editor.value = new Editor({
    extensions: [StarterKit.configure({
      heading: { levels: [1, 2, 3] }, link: false, underline: false, strike: false,
      code: false, codeBlock: false, blockquote: false, horizontalRule: false,
    })],
    content: props.modelValue,
    editable: !props.readonly,
    editorProps: { attributes: { 'aria-label': '文档正文', role: 'textbox', 'aria-multiline': 'true' } },
    onUpdate: ({ editor }) => emit('update:modelValue', editor.getJSON()),
  })
})
watch(() => props.readonly, value => editor.value?.setEditable(!value))
watch(() => props.modelValue, value => {
  if (editor.value && JSON.stringify(editor.value.getJSON()) !== JSON.stringify(value)) {
    editor.value.commands.setContent(value, { emitUpdate: false })
  }
})
onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <div class="rich-editor" :class="{ readonly }">
    <div v-if="editor && !readonly" class="toolbar" aria-label="编辑工具栏" @mousedown.prevent>
      <el-button :aria-pressed="editor.isActive('paragraph')" @click="editor.chain().focus().setParagraph().run()">段落</el-button>
      <el-button :aria-pressed="editor.isActive('bold')" @click="editor.chain().focus().toggleBold().run()">加粗</el-button>
      <el-button :aria-pressed="editor.isActive('italic')" @click="editor.chain().focus().toggleItalic().run()">斜体</el-button>
      <el-button :aria-pressed="editor.isActive('heading')" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()">标题</el-button>
      <el-button :aria-pressed="editor.isActive('bulletList')" @click="editor.chain().focus().toggleBulletList().run()">无序列表</el-button>
      <el-button :aria-pressed="editor.isActive('orderedList')" @click="editor.chain().focus().toggleOrderedList().run()">有序列表</el-button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>

<style scoped>
.rich-editor { border: 1px solid #c8d0dd; border-radius: 6px; width: 100%; background: white; overflow: hidden; }
.rich-editor:not(.readonly):focus-within { border-color: #409eff; box-shadow: 0 0 0 2px #ecf5ff; }
.toolbar { padding: 8px; border-bottom: 1px solid #ebeef5; display: flex; flex-wrap: wrap; gap: 6px; }
.toolbar .el-button { margin-left: 0; }
.toolbar .el-button[aria-pressed="true"] { background: #ecf5ff; color: #337ecc; border-color: #a0cfff; }
:deep(.tiptap) { min-height: 280px; max-height: 55vh; overflow-y: auto; padding: 20px 24px; outline: none; line-height: 1.8; font-size: 15px; overflow-wrap: anywhere; white-space: pre-wrap; }
:deep(.tiptap p) { color: inherit; margin: .6em 0; }
.readonly { background: #fafafa; }
</style>
