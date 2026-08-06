export function toThresholdForm(rule = {}) {
  let thresholdOptions = []
  try {
    const parsed = rule.thresholdOptions ? JSON.parse(rule.thresholdOptions) : []
    thresholdOptions = Array.isArray(parsed) ? parsed : []
  } catch (error) {
    thresholdOptions = []
  }
  return {
    metricCode: rule.metricCode || rule.metricType || '',
    durationSec: rule.durationSec == null ? 60 : rule.durationSec,
    thresholdOptions
  }
}
