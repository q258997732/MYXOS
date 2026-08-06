import { describe, expect, it } from 'vitest'
import { normalizeTemplateItem } from './metric-template'

describe('指标模板项规范化', () => {
  it('仅为枚举指标保留可选值并校验频率下限', () => {
    const item = normalizeTemplateItem({
      valueType: 'ENUM',
      intervalSec: 10,
      enumOptions: ['RUNNING']
    })

    expect(item).toEqual({
      valueType: 'ENUM',
      intervalSec: 15,
      enumOptions: ['RUNNING']
    })
  })

  it('非枚举指标不应提交枚举选项', () => {
    expect(normalizeTemplateItem({
      valueType: 'NUMBER',
      intervalSec: 60,
      enumOptions: ['x']
    }).enumOptions).toEqual([])
  })
})
