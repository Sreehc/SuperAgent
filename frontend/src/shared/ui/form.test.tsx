import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './button'
import { FileTrigger, FormField, SaveBar, SelectField } from './form'

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

describe('SelectField', () => {
  it('wraps the native select in a custom chrome shell while preserving selection behavior', () => {
    render(
      <label>
        执行模式
        <SelectField defaultValue="RAG_QA">
          <option value="AUTO">自动</option>
          <option value="RAG_QA">RAG 问答</option>
        </SelectField>
      </label>,
    )

    const selectButton = screen.getByRole('combobox')
    expect(selectButton.parentElement?.className).toContain('select-control')
    expect(selectButton.textContent).toContain('RAG 问答')

    fireEvent.click(selectButton)
    fireEvent.click(screen.getByRole('option', { name: '自动' }))

    expect(selectButton.textContent).toContain('自动')
  })
})

describe('FileTrigger', () => {
  it('shows a custom upload shell while keeping the native file input accessible', () => {
    render(
      <label>
        文件
        <FileTrigger data-testid="doc-upload" buttonLabel="选择文档" placeholder="未选择文件" summary="refund-guide.txt + 1 个文件" multiple />
      </label>,
    )

    const input = screen.getByTestId('doc-upload') as HTMLInputElement

    expect(input.type).toBe('file')
    expect(input.multiple).toBe(true)
    expect(input.parentElement?.className).toContain('file-trigger')
    expect(screen.getByText('选择文档')).toBeTruthy()
    expect(screen.getByText('refund-guide.txt + 1 个文件')).toBeTruthy()
  })
})
