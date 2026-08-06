/**
 * 指标类型中文标签映射（采集侧与判定侧统一使用这些 metricType）
 */
export const METRIC_LABELS = {
  CPU: 'CPU 使用率',
  MEM: '内存使用率',
  DISK: '磁盘使用率',
  NET_RX: '网络接收',
  NET_TX: '网络发送',
  TEMP: '温度',
  UPTIME: '运行时长',
  VERSION: '版本号',
  ANDROID_STATUS: '安卓实例状态',
  APP_PROCESS_STATE: '应用进程状态',
  ONLINE: '设备在线',
  OFFLINE: '设备离线',
  ANDROID_ONLINE: '安卓实例在线数',
  ANDROID_OFFLINE: '安卓实例离线数'
}

/**
 * 指标类型转中文标签，未知类型原样返回
 * @param {string} type
 * @returns {string}
 */
export function metricLabel(type) {
  return METRIC_LABELS[type] || type
}
