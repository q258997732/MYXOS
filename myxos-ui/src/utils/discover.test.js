import { describe, expect, it, vi } from 'vitest'
import { filterIpResults, submitRangeDiscover } from './discover'
import { ipRangeToCidr, isValidIPv4 } from './ip'

describe('IPv4 校验', () => {
  it('拒绝包含非数字字符的分段', () => {
    expect(isValidIPv4('1x.2.3.4')).toBe(false)
    expect(isValidIPv4('1.2.3.4x')).toBe(false)
  })

  it('起止 IP 任一非法时不生成 CIDR', () => {
    expect(ipRangeToCidr('1x.2.3.4', '1.2.3.4')).toEqual([])
    expect(ipRangeToCidr('1.2.3.4', '1.2.3.4x')).toEqual([])
  })
})

describe('submitRangeDiscover', () => {
  it('将 192.168.107.1 到 192.168.107.254 按顺序提交所有 CIDR', async () => {
    const scan = vi.fn().mockResolvedValue({ data: { id: 1 } })
    const expectedCidrs = [
      '192.168.107.1/32',
      '192.168.107.2/31',
      '192.168.107.4/30',
      '192.168.107.8/29',
      '192.168.107.16/28',
      '192.168.107.32/27',
      '192.168.107.64/26',
      '192.168.107.128/26',
      '192.168.107.192/27',
      '192.168.107.224/28',
      '192.168.107.240/29',
      '192.168.107.248/30',
      '192.168.107.252/31',
      '192.168.107.254/32'
    ]

    const result = await submitRangeDiscover(scan, '192.168.107.1', '192.168.107.254', 81, 81)

    expect(result).toEqual({ submittedCount: expectedCidrs.length, cidrs: expectedCidrs })
    expect(scan.mock.calls.map(([payload]) => payload)).toEqual(
      expectedCidrs.map(cidr => ({ cidr, portFrom: 81, portTo: 81 }))
    )
  })

  it('范围拆分请求失败时停止后续提交并保留已提交数量', async () => {
    const scan = vi.fn()
      .mockResolvedValueOnce({ data: { id: 1 } })
      .mockRejectedValueOnce(new Error('网络错误'))

    await expect(submitRangeDiscover(scan, '192.168.107.1', '192.168.107.254', 81, 81))
      .rejects.toMatchObject({ message: '网络错误', submittedCount: 1 })
    expect(scan).toHaveBeenCalledTimes(2)
  })
})

describe('filterIpResults', () => {
  it('按 IP、端口、结果和说明同时筛选逐 IP 结果', () => {
    const rows = [
      { ip: '192.168.107.1', port: 81, result: 'ADDED', message: '已添加' },
      { ip: '192.168.107.2', port: 81, result: 'DUPLICATE', message: '设备已存在' }
    ]

    expect(filterIpResults(rows, { ip: '107.2', port: [81], result: ['DUPLICATE'], message: '存在' }))
      .toEqual([rows[1]])
  })

  it('筛选条件或行字段缺失时安全处理', () => {
    const rows = [
      { ip: '192.168.107.1', port: 81, result: 'ADDED', message: '已添加' },
      { port: 82, message: null }
    ]

    expect(filterIpResults(rows, {})).toEqual(rows)
    expect(filterIpResults(rows, { ip: '107.1' })).toEqual([rows[0]])
    expect(filterIpResults(rows, { port: [82] })).toEqual([rows[1]])
  })
})
