import dayjs from 'dayjs'

const DEFAULT_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/**
 * 将日期格式化为 YYYY-MM-DD HH:mm:ss
 * @param {string|number|Date} value
 * @param {string} [format]
 * @returns {string} 格式化后字符串，非法输入返回 '-'
 */
export function formatDateTime(value, format = DEFAULT_FORMAT) {
  if (!value || value === '-') {
    return '-'
  }
  const d = dayjs(value)
  if (!d.isValid()) {
    return '-'
  }
  return d.format(format)
}

/**
 * 判断值是否可被 dayjs 解析
 * @param {*} value
 * @returns {boolean}
 */
export function isValidDate(value) {
  return dayjs(value).isValid()
}
