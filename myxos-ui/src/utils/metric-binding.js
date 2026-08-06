function indexByMetricCode(bindings = []) {
  return bindings.reduce((result, binding) => {
    result[`${binding.metricCode}:${binding.appPackage || ''}`] = binding
    return result
  }, {})
}

export function buildInstanceBindingRows(inheritedBindings = [], directBindings = []) {
  const inherited = indexByMetricCode(inheritedBindings)
  const direct = indexByMetricCode(directBindings)
  return Array.from(new Set([...Object.keys(inherited), ...Object.keys(direct)])).sort().map(key => {
    const binding = direct[key] || inherited[key]
    return {
      metricCode: binding.metricCode,
      ...(binding.appPackage ? { appPackage: binding.appPackage } : {}),
      inherited: inherited[key] || null,
      direct: direct[key] || null,
      effective: direct[key] || inherited[key] || null
    }
  })
}

export function collectionStatusLabel(status) {
  return status === 'RUNNING' ? '采集中' : '已暂停采集'
}
