export function verificationLabel() {
  return '已验证'
}

export function buildCatalogTree(catalogs = []) {
  return [
    { label: '主机', targetType: 'HOST' },
    { label: '安卓实例', targetType: 'ANDROID_INSTANCE' }
  ].map(parent => ({
    ...parent,
    children: [...new Set(catalogs.filter(item => item.targetType === parent.targetType).map(item => item.category).filter(Boolean))]
      .map(category => ({ label: category, targetType: parent.targetType, category }))
  }))
}
