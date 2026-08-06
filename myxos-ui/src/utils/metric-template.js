const MIN_INTERVAL_SEC = 15
const MAX_INTERVAL_SEC = 86400

export function normalizeTemplateItem(item = {}) {
  const interval = Number(item.intervalSec)
  const intervalSec = Number.isFinite(interval)
    ? Math.min(MAX_INTERVAL_SEC, Math.max(MIN_INTERVAL_SEC, Math.trunc(interval)))
    : 60
  const enumOptions = item.valueType === 'ENUM' && Array.isArray(item.enumOptions)
    ? item.enumOptions.filter(option => typeof option === 'string' && option.trim()).map(option => option.trim())
    : []

  return { valueType: item.valueType, intervalSec, enumOptions }
}

export function parseEnumOptions(value) {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const options = JSON.parse(value)
    return Array.isArray(options) ? options.filter(option => typeof option === 'string') : []
  } catch (error) {
    return []
  }
}
