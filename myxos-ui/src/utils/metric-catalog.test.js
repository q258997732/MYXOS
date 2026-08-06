import { describe, expect, it } from 'vitest'
import { verificationLabel } from './metric-catalog'

describe('指标目录展示', () => {
  it('应为受控指标显示已验证状态', () => {
    expect(verificationLabel()).toBe('已验证')
  })
})
