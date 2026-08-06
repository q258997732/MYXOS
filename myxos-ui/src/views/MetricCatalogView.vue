<template>
  <div class="page-container">
    <h2 class="page-title">指标目录</h2>
    <div class="catalog-layout">
      <aside class="catalog-tree"><el-tree :data="tree" node-key="label" default-expand-all highlight-current @node-click="selectNode" /></aside>
      <section class="content-card catalog-content">
        <el-table v-loading="loading" :data="visibleCatalogs" stripe>
          <el-table-column prop="code" label="指标编码" min-width="180" /><el-table-column prop="name" label="指标名称" min-width="150" />
          <el-table-column prop="valueType" label="数值类型" width="110" /><el-table-column prop="unit" label="单位" width="100"><template #default="{ row }">{{ row.unit || '-' }}</template></el-table-column>
          <el-table-column label="阈值" width="90"><template #default="{ row }"><el-tag :type="row.thresholdEnabled === 1 ? 'success' : 'info'" size="small">{{ row.thresholdEnabled === 1 ? '支持' : '不支持' }}</el-tag></template></el-table-column>
          <el-table-column label="默认频率" width="180"><template #default="{ row }"><el-input-number v-model="row.defaultIntervalSec" :min="10" @change="save(row)" /><span> 秒</span></template></el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { metricCatalogApi } from '@/api'
import { buildCatalogTree } from '@/utils/metric-catalog'

const catalogs = ref([]); const loading = ref(false); const selected = ref({ targetType: 'HOST', category: '' })
const tree = computed(() => buildCatalogTree(catalogs.value))
const visibleCatalogs = computed(() => catalogs.value.filter(item => item.targetType === selected.value.targetType && (!selected.value.category || item.category === selected.value.category)))
function selectNode(node) { selected.value = { targetType: node.targetType, category: node.category || '' } }
async function load() { loading.value = true; try { catalogs.value = (await metricCatalogApi.list()).data || [] } finally { loading.value = false } }
async function save(row) { await metricCatalogApi.update(row.id, { defaultIntervalSec: row.defaultIntervalSec, unit: row.unit, thresholdEnabled: row.thresholdEnabled, sort: row.sort }); ElMessage.success('默认频率已更新') }
onMounted(load)
</script>

<style scoped>
.catalog-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 16px; }.catalog-tree { border: 1px solid #ebeef5; padding: 12px; min-height: 420px; }.catalog-content { min-width: 0; } @media (max-width: 768px) { .catalog-layout { grid-template-columns: 1fr; } }
</style>
