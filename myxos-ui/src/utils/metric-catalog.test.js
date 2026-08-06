import { describe, expect, it } from 'vitest'
import { buildCatalogTree, verificationLabel } from './metric-catalog'

describe('指标目录展示', () => {
  it('应为受控指标显示已验证状态', () => {
    expect(verificationLabel()).toBe('已验证')
  })
})

it('按目标类型和二级分类构造目录树', () => {
  expect(buildCatalogTree([{ targetType: 'HOST', category: '性能' }])).toEqual([
    { label: '主机', targetType: 'HOST', children: [{ label: '性能', targetType: 'HOST', category: '性能' }] },
    { label: '安卓实例', targetType: 'ANDROID_INSTANCE', children: [] }
  ])
})
