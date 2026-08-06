import { describe, expect, it } from 'vitest'
import { filterAppliedMetrics } from './device-metrics'

describe('设备详情指标过滤', () => {
  it('仅保留同设备、同实例且已启用绑定的最新指标', () => {
    const metrics = [
      { metricCode: 'CPU', targetType: 'HOST' },
      { metricCode: 'MEM', targetType: 'HOST' },
      { metricCode: 'ANDROID_STATUS', targetType: 'ANDROID_INSTANCE', androidName: 'a1' },
      { metricCode: 'APP_PROCESS_STATE', targetType: 'ANDROID_INSTANCE', androidName: 'a1', appPackage: 'com.demo.a' },
      { metricCode: 'APP_PROCESS_STATE', targetType: 'ANDROID_INSTANCE', androidName: 'a1', appPackage: 'com.demo.b' }
    ]
    const bindings = [
      { metricCode: 'CPU', targetType: 'HOST', enabled: 1 },
      { metricCode: 'MEM', targetType: 'HOST', enabled: 0 },
      { metricCode: 'ANDROID_STATUS', targetType: 'ANDROID_INSTANCE', androidName: 'a1', enabled: 1 },
      { metricCode: 'APP_PROCESS_STATE', targetType: 'ANDROID_INSTANCE', androidName: 'a1', appPackage: 'com.demo.a', enabled: 1 }
    ]
    expect(filterAppliedMetrics(metrics, bindings)).toEqual([metrics[0], metrics[2], metrics[3]])
  })
})
