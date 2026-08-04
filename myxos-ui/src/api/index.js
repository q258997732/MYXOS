import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  me: () => request.get('/auth/me')
}

export const deviceApi = {
  list: (params) => request.get('/devices', { params }),
  create: (data) => request.post('/devices', data),
  detail: (id) => request.get(`/devices/${id}`),
  update: (id, data) => request.put(`/devices/${id}`, data),
  delete: (id) => request.delete(`/devices/${id}`),
  collect: (id) => request.post(`/devices/${id}/collect`),
  ops: (id, data) => request.post(`/devices/${id}/ops`, data),
  screenshot: (id, params) => request.get(`/devices/${id}/screenshot`, { params }),
  androids: (id) => request.get(`/devices/${id}/androids`),
  latestMetrics: (id) => request.get(`/devices/${id}/metrics/latest`),
  metricHistory: (id, params) => request.get(`/devices/${id}/metrics/history`, { params }),
  metrics: (id, params) => request.get(`/devices/${id}/metrics`, { params }),
  alarms: (id, params) => request.get(`/devices/${id}/alarms`, { params }),
  logs: (id, params) => request.get(`/devices/${id}/logs`, { params }),
  tasks: (id, params) => request.get(`/devices/${id}/tasks`, { params })
}

export const deviceGroupApi = {
  list: () => request.get('/device-groups')
}

export const thresholdApi = {
  list: (params) => request.get('/thresholds', { params }),
  create: (data) => request.post('/thresholds', data),
  detail: (id) => request.get(`/thresholds/${id}`),
  update: (id, data) => request.put(`/thresholds/${id}`, data),
  delete: (id) => request.delete(`/thresholds/${id}`),
  toggle: (id, enabled) => request.post(`/thresholds/${id}/toggle`, { enabled })
}

export const alarmApi = {
  list: (params) => request.get('/alarms', { params }),
  resolve: (id) => request.post(`/alarms/${id}/resolve`)
}

export const logApi = {
  list: (params) => request.get('/logs', { params })
}

export const opTaskApi = {
  list: (params) => request.get('/op-tasks', { params }),
  detail: (id) => request.get(`/op-tasks/${id}`),
  retry: (id) => request.post(`/op-tasks/${id}/retry`)
}

export const discoverApi = {
  scan: (data) => request.post('/discover/scan', data),
  tasks: (params) => request.get('/discover/tasks', { params }),
  taskDetail: (id) => request.get(`/discover/tasks/${id}`),
  deleteTask: (id) => request.delete(`/discover/tasks/${id}`),
  clearTasks: () => request.delete('/discover/tasks')
}

export const sysConfigApi = {
  list: () => request.get('/sys-config'),
  update: (key, value) => request.put(`/sys-config/${key}`, { value })
}

export const userApi = {
  list: (params) => request.get('/users', { params }),
  create: (data) => request.post('/users', data),
  update: (id, data) => request.put(`/users/${id}`, data),
  resetPassword: (id, password) => request.post(`/users/${id}/reset-password`, { password }),
  toggleStatus: (id) => request.post(`/users/${id}/toggle-status`),
  changePassword: (data) => request.post('/users/me/password', data)
}
