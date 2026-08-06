import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

describe('设备详情手动运维入口', () => {
  it('保留截图、剪贴板、语言、定位和 Shell 操作', () => {
    const source = readFileSync(resolve(__dirname, '../views/DeviceDetailView.vue'), 'utf8')
    ;['submitScreenshot', 'clipboardGet', 'SET_LANGUAGE', 'REFRESH_LOCATION', 'deviceApi.shell'].forEach(name => {
      expect(source).toContain(name)
    })
  })
})
