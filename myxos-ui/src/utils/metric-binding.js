function indexByMetricCode(bindings = []) {
  return bindings.reduce((result, binding) => {
    result[binding.metricCode] = binding
    return result
  }, {})
}

export function buildInstanceBindingRows(inheritedBindings = [], directBindings = []) {
  const inherited = indexByMetricCode(inheritedBindings)
  const direct = indexByMetricCode(directBindings)
  return Array.from(new Set([...Object.keys(inherited), ...Object.keys(direct)])).sort().map(metricCode => ({
    metricCode,
    inherited: inherited[metricCode] || null,
    direct: direct[metricCode] || null,
    effective: direct[metricCode] || inherited[metricCode] || null
  }))
}

export function collectionStatusLabel(status) {
  return status === 'RUNNING' ? '采集中' : '已暂停采集'
}
