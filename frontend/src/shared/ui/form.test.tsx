import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './button'
import { FormField, SaveBar } from './form'

describe('FormField', () => {
  it('associates label, hint, and error text with the control', () => {
    render(
      <FormField
        label="接口地址"
        htmlFor="base-url"
        hint="用于对话模型和向量模型请求"
        error="接口地址不能为空"
        errorTestId="base-url-error"
      >
        <input id="base-url" />
      </FormField>,
    )

    const input = screen.getByLabelText('接口地址')
    const describedBy = input.getAttribute('aria-describedby') ?? ''

    expect(describedBy).toContain('base-url-hint')
    expect(describedBy).toContain('base-url-error')
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByText('用于对话模型和向量模型请求')).not.toBeNull()
    expect(screen.getByTestId('base-url-error').textContent).toBe('接口地址不能为空')
  })
})

describe('SaveBar', () => {
  it('shows dirty state and submits with primary action', () => {
    const onSave = vi.fn()

    render(
      <SaveBar dirty message="模型配置有未保存修改">
        <Button onClick={onSave}>保存配置</Button>
      </SaveBar>,
    )

    expect(screen.getByRole('status').textContent).toContain('模型配置有未保存修改')

    fireEvent.click(screen.getByRole('button', { name: '保存配置' }))

    expect(onSave).toHaveBeenCalledTimes(1)
  })

  it('announces saving, saved, and failed states', () => {
    const { rerender } = render(
      <SaveBar dirty saving message="正在保存模型配置">
        <Button loading>保存配置</Button>
      </SaveBar>,
    )

    expect(screen.getByRole('status').textContent).toContain('正在保存模型配置')

    rerender(
      <SaveBar saved message="模型配置已保存">
        <Button>保存配置</Button>
      </SaveBar>,
    )
    expect(screen.getByRole('status').textContent).toContain('模型配置已保存')

    rerender(
      <SaveBar error="模型配置保存失败">
        <Button>保存配置</Button>
      </SaveBar>,
    )
    expect(screen.getByRole('alert').textContent).toContain('模型配置保存失败')
  })
})
