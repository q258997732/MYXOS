import { ipRangeToCidr } from './ip'

export async function submitRangeDiscover(scan, startIp, endIp, portFrom, portTo) {
  const cidrs = ipRangeToCidr(startIp, endIp)
  const submitted = []

  for (const cidr of cidrs) {
    try {
      await scan({ cidr, portFrom, portTo })
      submitted.push(cidr)
    } catch (error) {
      error.submittedCount = submitted.length
      throw error
    }
  }

  return { submittedCount: submitted.length, cidrs: submitted }
}

export function filterIpResults(rows, filters = {}) {
  const ports = Array.isArray(filters.port) ? filters.port : []
  const results = Array.isArray(filters.result) ? filters.result : []
  const ip = filters.ip || ''
  const message = filters.message || ''

  return rows.filter((row = {}) =>
    (!ip || String(row.ip || '').includes(ip)) &&
    (!ports.length || ports.includes(row.port)) &&
    (!results.length || results.includes(row.result)) &&
    (!message || String(row.message || '').includes(message))
  )
}
