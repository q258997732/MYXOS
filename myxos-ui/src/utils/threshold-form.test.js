import { describe, expect, it } from 'vitest'
import { toThresholdForm } from './threshold-form'

describe('阈值规则表单转换', () => {
  it('编辑持续时长规则时应保留 durationSec', () => {
    expect(toThresholdForm({ metricCode: 'CPU_USAGE_PERCENT', triggerMode: 'DURATION', durationSec: 180 }).durationSec).toBe(180)
  })

  it('枚举选项格式损坏时应回退为空数组', () => {
    expect(toThresholdForm({ thresholdOptions: 'invalid-json' }).thresholdOptions).toEqual([])
  })
})
