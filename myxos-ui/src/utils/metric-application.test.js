import { describe, expect, it } from 'vitest'
import { buildBatchMetricBindingPayload, groupAndroidInstances } from './metric-application'

describe('指标应用向导', () => {
  it('按设备分组安卓实例并忽略无法获取实例的设备', () => {
    expect(groupAndroidInstances([
      { id: 1, name: '主机 A', androids: [{ name: 'a1' }, { name: 'a2' }] },
      { id: 2, name: '主机 B', androids: [] }
    ])).toEqual([{ deviceId: 1, deviceName: '主机 A', instances: [{ name: 'a1' }, { name: 'a2' }] }])
  })

  it('为每个安卓目标保留独立的应用包名并仅发送已配置指标', () => {
    const payload = buildBatchMetricBindingPayload({
      targetType: 'ANDROID_INSTANCE',
      targets: [{ deviceId: 1, androidName: 'a1' }, { deviceId: 2, androidName: 'b1' }],
      items: [
        { metricCode: 'CPU_USAGE_PERCENT', enabled: 1, intervalSec: 30 },
        { metricCode: 'APP_PROCESS_STATE', enabled: 1, intervalSec: 60 }
      ],
      appPackages: { '1:a1': 'com.example.a', '2:b1': 'com.example.b' }
    })

    expect(payload).toEqual({
      targetType: 'ANDROID_INSTANCE',
      targets: [
        { deviceId: 1, androidName: 'a1', items: [
          { metricCode: 'CPU_USAGE_PERCENT', enabled: 1, intervalSec: 30 },
          { metricCode: 'APP_PROCESS_STATE', enabled: 1, intervalSec: 60, appPackage: 'com.example.a' }
        ] },
        { deviceId: 2, androidName: 'b1', items: [
          { metricCode: 'CPU_USAGE_PERCENT', enabled: 1, intervalSec: 30 },
          { metricCode: 'APP_PROCESS_STATE', enabled: 1, intervalSec: 60, appPackage: 'com.example.b' }
        ] }
      ]
    })
  })

  it('不为未启用的应用进程指标构造包名字段', () => {
    expect(buildBatchMetricBindingPayload({
      targetType: 'HOST', targets: [{ deviceId: 1 }],
      items: [{ metricCode: 'APP_PROCESS_STATE', enabled: 0, intervalSec: 60 }], appPackages: {}
    }).targets[0].items[0]).toEqual({ metricCode: 'APP_PROCESS_STATE', enabled: 0, intervalSec: 60 })
  })
})
