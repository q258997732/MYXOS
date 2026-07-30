<template>
  <div>
    <h2>{{ isEdit ? '编辑规则' : '新增规则' }}</h2>
    <el-form :model="form" label-width="120px">
      <el-form-item label="规则名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="指标类型">
        <el-select v-model="form.metricType">
          <el-option v-for="t in metricTypes" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="比较操作">
        <el-select v-model="form.compareOp">
          <el-option label=">" value="GT" />
          <el-option label=">=" value="GTE" />
          <el-option label="<" value="LT" />
          <el-option label="<=" value="LTE" />
          <el-option label="=" value="EQ" />
          <el-option label="!=" value="NE" />
        </el-select>
      </el-form-item>
      <el-form-item label="阈值">
        <el-input-number v-model="form.thresholdValue" :precision="4" />
      </el-form-item>
      <el-form-item label="触发模式">
        <el-select v-model="form.triggerMode">
          <el-option label="持续时间" value="DURATION" />
          <el-option label="连续次数" value="CONSECUTIVE" />
        </el-select>
      </el-form-item>
      <el-form-item label="持续时间(秒)" v-if="form.triggerMode === 'DURATION'">
        <el-input-number v-model="form.durationSec" />
      </el-form-item>
      <el-form-item label="连续次数" v-if="form.triggerMode === 'CONSECUTIVE'">
        <el-input-number v-model="form.consecutiveCount" />
      </el-form-item>
      <el-form-item label="作用范围">
        <el-select v-model="form.scopeType">
          <el-option label="全部" value="ALL" />
          <el-option label="分组" value="GROUP" />
          <el-option label="设备" value="DEVICE" />
        </el-select>
      </el-form-item>
      <el-form-item label="作用对象 ID">
        <el-input-number v-model="form.scopeId" />
      </el-form-item>

      <el-card title="动作配置">
        <div v-for="(action, index) in form.actions" :key="index" class="action-row">
          <el-select v-model="action.actionType" placeholder="动作类型">
            <el-option label="记录日志" value="LOG" />
            <el-option label="执行操作" value="OPERATION" />
          </el-select>
          <el-input v-model="action.logLevel" placeholder="日志级别" v-if="action.actionType === 'LOG'" />
          <el-input v-model="action.operationCode" placeholder="操作类型" v-if="action.actionType === 'OPERATION'" />
          <el-input v-model="action.operationParams" placeholder="操作参数(JSON)" />
          <el-input-number v-model="action.sort" placeholder="排序" />
          <el-button type="danger" @click="form.actions.splice(index, 1)">删除</el-button>
        </div>
        <el-button @click="addAction">添加动作</el-button>
      </el-card>

      <el-form-item>
        <el-button type="primary" @click="save">保存</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { thresholdApi } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const metricTypes = ['CPU', 'MEM', 'DISK', 'NET_RX', 'NET_TX', 'TEMP', 'CUSTOM']

const form = reactive({
  name: '',
  metricType: 'CPU',
  compareOp: 'GT',
  thresholdValue: 0,
  triggerMode: 'DURATION',
  durationSec: 0,
  consecutiveCount: 2,
  scopeType: 'ALL',
  scopeId: null,
  actions: []
})

function addAction() {
  form.actions.push({ actionType: 'LOG', logLevel: 'INFO', operationCode: '', operationParams: '{}', sort: 0 })
}

async function save() {
  if (isEdit.value) {
    await thresholdApi.update(route.params.id, form)
  } else {
    await thresholdApi.create(form)
  }
  ElMessage.success('保存成功')
  router.push('/thresholds')
}

async function loadDetail() {
  const res = await thresholdApi.detail(route.params.id)
  Object.assign(form, res.data)
}

onMounted(() => {
  if (isEdit.value) loadDetail()
  if (form.actions.length === 0) addAction()
})
</script>

<style scoped>
.action-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
