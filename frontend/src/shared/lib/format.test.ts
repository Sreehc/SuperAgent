import { describe, expect, it } from 'vitest'
import {
  EMPTY_VALUE,
  formatDate,
  formatDateTime,
  formatDurationMs,
  formatFileSize,
  formatNumber,
  formatShortDateTime,
  formatTokenCount,
} from './format'

describe('shared format helpers', () => {
  it('uses an empty placeholder for missing or invalid values', () => {
    expect(formatDateTime(null)).toBe(EMPTY_VALUE)
    expect(formatDateTime(undefined)).toBe(EMPTY_VALUE)
    expect(formatDateTime('')).toBe(EMPTY_VALUE)
    expect(formatDateTime('not-a-date')).toBe(EMPTY_VALUE)
    expect(formatDurationMs(null)).toBe(EMPTY_VALUE)
    expect(formatDurationMs(Number.NaN)).toBe(EMPTY_VALUE)
    expect(formatFileSize(-1)).toBe(EMPTY_VALUE)
    expect(formatNumber(Number.POSITIVE_INFINITY)).toBe(EMPTY_VALUE)
  })

  it('formats date and time values with consistent zh-CN numeric output', () => {
    const value = new Date(2026, 5, 20, 9, 5)

    expect(formatDate(value)).toBe('2026/06/20')
    expect(formatDateTime(value)).toBe('2026/06/20 09:05')
    expect(formatShortDateTime(value)).toBe('06/20 09:05')
  })

  it('formats millisecond durations without leaking raw numbers', () => {
    expect(formatDurationMs(0)).toBe('0ms')
    expect(formatDurationMs(875)).toBe('875ms')
    expect(formatDurationMs(1250)).toBe('1.3s')
    expect(formatDurationMs(60_000)).toBe('1m 0s')
    expect(formatDurationMs(3_650_000)).toBe('1h 0m')
  })

  it('formats numbers, token counts, and file sizes', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatTokenCount(4096)).toBe('4,096 tokens')
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1024)).toBe('1 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5 MB')
  })
})
