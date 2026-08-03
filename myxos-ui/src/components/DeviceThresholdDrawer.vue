<template>
  <el-drawer
    v-model="visible"
    :title="`阈值设置 - ${device?.name || device?.ip}`"
    size="520px"
    destroy-on-close
  >
    <div class="drawer-content">
      <el-alert
        title="阈值规则统一在“阈值规则”页面管理"
        description="点击下方按钮可为该设备快速创建一条阈值规则，或在阈值规则列表中统一维护。"
        type="info"
        :closable="false"
        show-icon
        class="drawer-tip"
      />

      <el-button type="primary" :icon="Plus" @click="goCreate">为当前设备新增规则</el-button>

      <el-divider content-position="left">已生效规则</el-divider>

      <div v-loading="loading">
        <el-empty v-if="!rules.length" description="暂无作用于该设备的规则" />
        <el-card
          v-for="item in rules"
          :key="item.id"
          class="rule-card"
          shadow="never"
        >
          <template #header>
            <div class="rule-header">
              <span class="rule-name">{{ item.name }}</span>
              <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? '启用' : '禁用' }}</el-tag>
            </div>
          </template>
          <div class="rule-body">
            <div class="rule-item">
              <span class="rule-label">指标类型：</span>
              <span>{{ item.metricType }}</span>
            </div>
            <div class="rule-item">
              <span class="rule-label">触发条件：</span>
              <span>{{ item.compareOp }} {{ item.thresholdValue }}</span>
            </div>
            <div class="rule-item">
              <span class="rule-label">作用范围：</span>
              <span>{{ scopeText(item) }}</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { thresholdApi } from '@/api'

const props = defineProps({
  modelValue: Boolean,
  device: Object
})
const emit = defineEmits(['update:modelValue'])
const router = useRouter()

const visible = ref(false)
const loading = ref(false)
const rules = reactive([])

watch(() => props.modelValue, val => {
  visible.value = val
  if (val) loadRules()
})
watch(visible, val => {
  emit('update:modelValue', val)
})

const loadRules = async () => {
  if (!props.device?.id) return
  loading.value = true
  try {
    const res = await thresholdApi.list({ page: 1, size: 1000 })
    const all = res.data.records || []
    const deviceId = props.device.id
    const filtered = all.filter(r =>
      r.scopeType === 'ALL' ||
      (r.scopeType === 'DEVICE' && r.scopeId === deviceId) ||
      (r.scopeType === 'GROUP' && r.scopeId === props.device.groupId)
    )
    rules.splice(0, rules.length, ...filtered)
  } finally {
    loading.value = false
  }
}

const scopeText = (item) => {
  if (item.scopeType === 'ALL') return '全部设备'
  if (item.scopeType === 'GROUP') return `分组 ID: ${item.scopeId}`
  if (item.scopeType === 'DEVICE') return `设备 ID: ${item.scopeId}`
  return item.scopeType
}

const goCreate = () => {
  visible.value = false
  router.push(`/thresholds/edit?deviceId=${props.device.id}`)
}
</script>

<style scoped>
.drawer-content {
  padding: 0 4px;
}
.drawer-tip {
  margin-bottom: var(--spacing-md);
}
.rule-card {
  margin-bottom: var(--spacing-md);
  border-radius: 8px;
}
.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rule-name {
  font-weight: 600;
}
.rule-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}
.rule-item {
  font-size: 13px;
  color: var(--text-secondary);
}
.rule-label {
  color: var(--text-muted);
}
</style>
