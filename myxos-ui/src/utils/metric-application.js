export function groupAndroidInstances(devices = []) {
  return devices
    .filter(device => Array.isArray(device.androids) && device.androids.length > 0)
    .map(device => ({
      deviceId: device.id,
      deviceName: device.name || device.ip,
      instances: device.androids
    }))
}

export function buildBatchMetricBindingPayload({ targetType, targets, items, appPackages = {} }) {
  return {
    targetType,
    targets: targets.map(target => ({
      deviceId: target.deviceId,
      ...(targetType === 'ANDROID_INSTANCE' ? { androidName: target.androidName } : {}),
      items: items.map(item => {
        const result = {
          metricCode: item.metricCode,
          enabled: item.enabled ? 1 : 0,
          intervalSec: item.intervalSec
        }
        if (item.metricCode === 'APP_PROCESS_STATE' && item.enabled) {
          const appPackage = (appPackages[`${target.deviceId}:${target.androidName}`] || '').trim()
          if (appPackage) result.appPackage = appPackage
        }
        return result
      })
    }))
  }
}
