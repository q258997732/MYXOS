export function isValidIPv4(ip) {
  if (!ip) return false
  const parts = ip.split('.')
  if (parts.length !== 4) return false
  return parts.every(p => {
    const n = parseInt(p, 10)
    return p !== '' && !isNaN(n) && n >= 0 && n <= 255
  })
}

function ipToLong(ip) {
  return ip.split('.').reduce((acc, p) => (acc << 8) + parseInt(p, 10), 0) >>> 0
}

function longToIp(long) {
  return [(long >>> 24), (long >> 16) & 0xff, (long >> 8) & 0xff, long & 0xff].join('.')
}

const MAX_RANGE_SIZE = 256

export function ipRangeToCidr(startIp, endIp) {
  const start = ipToLong(startIp)
  const end = ipToLong(endIp)
  if (start > end) return []
  if (end - start + 1 > MAX_RANGE_SIZE) {
    throw new Error(`IP 范围超过最大限制 ${MAX_RANGE_SIZE} 个地址`)
  }
  const cidrs = []
  let current = start
  let safety = 0
  while (current <= end && safety < 1000) {
    safety++
    let mask = 32
    while (mask > 0) {
      const bits = 32 - mask
      const rangeStart = (current >> bits) << bits
      const rangeEnd = rangeStart + (1 << bits) - 1
      if (rangeStart < current || rangeEnd > end) {
        mask++
        break
      }
      mask--
    }
    const finalMask = mask === 32 ? 32 : mask + 1
    const bits = 32 - finalMask
    const rangeStart = (current >> bits) << bits
    const rangeEnd = rangeStart + (1 << bits) - 1
    cidrs.push(longToIp(rangeStart) + '/' + finalMask)
    current = rangeEnd + 1
  }
  return cidrs
}
