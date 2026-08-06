function keyOf(item) {
  return [item.targetType, item.metricCode, item.androidName || '', item.appPackage || ''].join(':')
}

export function filterAppliedMetrics(metrics = [], bindings = []) {
  const enabled = new Set(bindings.filter(binding => binding.enabled === 1).map(keyOf))
  return metrics.filter(metric => enabled.has(keyOf(metric)))
}
