import { describe, expect, it } from 'vitest'
import { buildInstanceBindingRows, collectionStatusLabel } from './metric-binding'

describe('实例指标绑定展示', () => {
  it('直接绑定应优先于主机继承绑定', () => {
    const rows = buildInstanceBindingRows(
      [{ metricCode: 'CPU_USAGE_PERCENT', enabled: 1, intervalSec: 60 }],
      [{ metricCode: 'CPU_USAGE_PERCENT', enabled: 0, intervalSec: 30 }]
    )

    expect(rows).toEqual([{
      metricCode: 'CPU_USAGE_PERCENT', inherited: { metricCode: 'CPU_USAGE_PERCENT', enabled: 1, intervalSec: 60 },
      direct: { metricCode: 'CPU_USAGE_PERCENT', enabled: 0, intervalSec: 30 },
      effective: { metricCode: 'CPU_USAGE_PERCENT', enabled: 0, intervalSec: 30 }
    }])
  })

  it('非运行实例应显示采集暂停', () => {
    expect(collectionStatusLabel('STOPPED')).toBe('已暂停采集')
    expect(collectionStatusLabel('RUNNING')).toBe('采集中')
  })
})
