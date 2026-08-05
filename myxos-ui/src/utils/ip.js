export function isValidIPv4(ip) {
  if (typeof ip !== 'string' || !ip) return false
  const parts = ip.split('.')
  if (parts.length !== 4) return false
  return parts.every(p => {
    if (!/^\d+$/.test(p)) return false
    const n = Number(p)
    return n >= 0 && n <= 255
  })
}

function ipToLong(ip) {
  return ip.split('.').reduce((acc, p) => acc * 256 + parseInt(p, 10), 0)
}

function longToIp(long) {
  return [
    Math.floor(long / 16777216),
    Math.floor(long / 65536) % 256,
    Math.floor(long / 256) % 256,
    long % 256
  ].join('.')
}

const MAX_RANGE_SIZE = 256

export function ipRangeToCidr(startIp, endIp) {
  if (!isValidIPv4(startIp) || !isValidIPv4(endIp)) return []

  const start = ipToLong(startIp)
  const end = ipToLong(endIp)
  if (start > end) return []
  if (end - start + 1 > MAX_RANGE_SIZE) {
    throw new Error(`IP 范围超过最大限制 ${MAX_RANGE_SIZE} 个地址`)
  }
  const cidrs = []
  let current = start
  while (current <= end) {
    const remaining = end - current + 1
    let blockSize = 1

    while (blockSize * 2 <= remaining && current % (blockSize * 2) === 0) {
      blockSize *= 2
    }

    cidrs.push(longToIp(current) + '/' + (32 - Math.log2(blockSize)))
    current += blockSize
  }
  return cidrs
}
