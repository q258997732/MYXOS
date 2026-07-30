<template>
  <el-tag :type="tagType" size="small">{{ label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: String
})

const tagType = computed(() => {
  switch (props.status) {
    case 'ONLINE': return 'success'
    case 'OFFLINE': return 'danger'
    case 'DISABLED': return 'info'
    default: return 'warning'
  }
})

const label = computed(() => {
  switch (props.status) {
    case 'ONLINE': return '在线'
    case 'OFFLINE': return '离线'
    case 'DISABLED': return '禁用'
    default: return '未知'
  }
})
</script>
