<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">指标目录</h2>
      <el-radio-group v-model="targetType" @change="load">
        <el-radio-button label="HOST">主机</el-radio-button>
        <el-radio-button label="ANDROID_INSTANCE">安卓实例</el-radio-button>
      </el-radio-group>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="catalogs" stripe>
        <el-table-column prop="code" label="指标编码" min-width="180" />
        <el-table-column prop="name" label="指标名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="数值类型" width="120">
          <template #default="{ row }"><el-tag size="small">{{ valueTypeLabel(row.valueType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="100">
          <template #default="{ row }">{{ row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="阈值支持" width="120">
          <template #default="{ row }"><el-tag :type="row.thresholdEnabled === 1 ? 'success' : 'info'" size="small">{{ row.thresholdEnabled === 1 ? '支持' : '不支持' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="验证状态" width="120">
          <template #default><el-tag type="success" size="small">{{ verificationLabel() }}</el-tag></template>
        </el-table-column>
        <el-table-column label="建议频率" width="120">60 秒</el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { metricCatalogApi } from '@/api'
import { verificationLabel } from '@/utils/metric-catalog'

const targetType = ref('HOST')
const catalogs = ref([])
const loading = ref(false)

const VALUE_TYPE_LABELS = { NUMBER: '数值', STRING: '字符串', ENUM: '枚举' }
const valueTypeLabel = (valueType) => VALUE_TYPE_LABELS[valueType] || valueType

async function load() {
  loading.value = true
  try {
    const response = await metricCatalogApi.list({ targetType: targetType.value })
    catalogs.value = response.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
