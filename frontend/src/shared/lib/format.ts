export const EMPTY_VALUE = '—'

type DateInput = string | number | Date | null | undefined
type NumberInput = number | null | undefined

const integerFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 0,
})

const compactDecimalFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 1,
})

const twoDigitFormatter = new Intl.NumberFormat('zh-CN', {
  minimumIntegerDigits: 2,
  useGrouping: false,
})

function toValidDate(value: DateInput) {
  if (value == null || value === '') {
    return null
  }

  const date = value instanceof Date ? value : new Date(value)
  return Number.isFinite(date.getTime()) ? date : null
}

function toFiniteNumber(value: NumberInput) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function formatDateParts(date: Date) {
  const year = date.getFullYear()
  const month = twoDigitFormatter.format(date.getMonth() + 1)
  const day = twoDigitFormatter.format(date.getDate())
  const hour = twoDigitFormatter.format(date.getHours())
  const minute = twoDigitFormatter.format(date.getMinutes())

  return { year, month, day, hour, minute }
}

export function formatDate(value: DateInput) {
  const date = toValidDate(value)
  if (!date) return EMPTY_VALUE

  const { year, month, day } = formatDateParts(date)
  return `${year}/${month}/${day}`
}

export function formatDateTime(value: DateInput) {
  const date = toValidDate(value)
  if (!date) return EMPTY_VALUE

  const { year, month, day, hour, minute } = formatDateParts(date)
  return `${year}/${month}/${day} ${hour}:${minute}`
}

export function formatShortDateTime(value: DateInput) {
  const date = toValidDate(value)
  if (!date) return EMPTY_VALUE

  const { month, day, hour, minute } = formatDateParts(date)
  return `${month}/${day} ${hour}:${minute}`
}

export function formatNumber(value: NumberInput) {
  const number = toFiniteNumber(value)
  return number == null ? EMPTY_VALUE : integerFormatter.format(number)
}

export function formatTokenCount(value: NumberInput) {
  const number = toFiniteNumber(value)
  return number == null ? EMPTY_VALUE : `${integerFormatter.format(number)} tokens`
}

export function formatDurationMs(value: NumberInput) {
  const milliseconds = toFiniteNumber(value)
  if (milliseconds == null || milliseconds < 0) {
    return EMPTY_VALUE
  }

  if (milliseconds < 1000) {
    return `${Math.round(milliseconds)}ms`
  }

  const totalSeconds = Math.round(milliseconds / 1000)
  if (totalSeconds < 60) {
    return `${compactDecimalFormatter.format(milliseconds / 1000)}s`
  }

  const totalMinutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (totalMinutes < 60) {
    return `${totalMinutes}m ${seconds}s`
  }

  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return `${hours}h ${minutes}m`
}

export function formatFileSize(value: NumberInput) {
  const bytes = toFiniteNumber(value)
  if (bytes == null || bytes < 0) {
    return EMPTY_VALUE
  }

  if (bytes < 1024) {
    return `${Math.round(bytes)} B`
  }

  const units = ['KB', 'MB', 'GB', 'TB']
  let size = bytes / 1024
  let unitIndex = 0

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }

  return `${compactDecimalFormatter.format(size)} ${units[unitIndex]}`
}
