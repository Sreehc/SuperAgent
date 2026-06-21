import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { GovernanceConsolePage } from './GovernanceConsolePage'
import {
  createChunkingProfile,
  createKnowledgeDomain,
  listChunkingProfiles,
  listKnowledgeDomains,
  updateChunkingProfile,
  updateKnowledgeDomain,
} from '../../knowledge/api'
import type { ApiEnvelope } from '@/api/types'
import type { ChunkingProfileItem, KnowledgeDomainItem } from '../../knowledge/types'

vi.mock('../../knowledge/api', () => ({
  listKnowledgeDomains: vi.fn(),
  createKnowledgeDomain: vi.fn(),
  updateKnowledgeDomain: vi.fn(),
  listChunkingProfiles: vi.fn(),
  createChunkingProfile: vi.fn(),
  updateChunkingProfile: vi.fn(),
}))

function envelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    code: 'OK',
    message: 'ok',
    data,
    traceId: 'test-trace',
  }
}

const domains: KnowledgeDomainItem[] = [
  {
    id: 1,
    code: 'hr',
    name: 'HR Policies',
    description: '人事制度与福利政策',
    status: 'active',
    createdAt: '2026-06-20T10:00:00',
    updatedAt: '2026-06-20T11:00:00',
  },
  {
    id: 2,
    code: 'legal',
    name: 'Legal',
    description: null,
    status: 'disabled',
    createdAt: '2026-06-20T10:00:00',
    updatedAt: '2026-06-20T11:00:00',
  },
]

const profiles: ChunkingProfileItem[] = [
  {
    id: 10,
    code: 'default',
    name: '默认切块',
    strategy: 'recursive',
    isDefault: true,
    status: 'active',
    config: {},
    createdAt: '2026-06-20T10:00:00',
    updatedAt: '2026-06-20T11:00:00',
  },
  {
    id: 11,
    code: 'legal-small',
    name: '法务小切块',
    strategy: 'sentence',
    isDefault: false,
    status: 'active',
    config: { chunkSize: 320, overlap: 40 },
    createdAt: '2026-06-20T10:00:00',
    updatedAt: '2026-06-20T11:00:00',
  },
]

function renderGovernance() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <GovernanceConsolePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('GovernanceConsolePage knowledge domains', () => {
  beforeEach(() => {
    vi.mocked(listKnowledgeDomains).mockResolvedValue(envelope(domains))
    vi.mocked(listChunkingProfiles).mockResolvedValue(envelope(profiles))
    vi.mocked(createKnowledgeDomain).mockResolvedValue(envelope(domains[0]))
    vi.mocked(updateKnowledgeDomain).mockResolvedValue({} as never)
    vi.mocked(createChunkingProfile).mockResolvedValue(envelope(profiles[0]))
    vi.mocked(updateChunkingProfile).mockResolvedValue({} as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('lists and refreshes knowledge domains without changing chunking profiles', async () => {
    renderGovernance()

    const hrRow = await screen.findByTestId('knowledge-domain-row-1')
    expect(hrRow.textContent).toContain('hr')
    expect(hrRow.textContent).toContain('HR Policies')
    expect(hrRow.textContent).toContain('active')
    expect(hrRow.textContent).toContain('人事制度与福利政策')
    expect(screen.getByTestId('knowledge-domain-row-2').textContent).toContain('disabled')
    expect(await screen.findByText('默认切块')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '刷新知识域' }))

    await waitFor(() => expect(vi.mocked(listKnowledgeDomains).mock.calls.length).toBeGreaterThan(1))
    expect(vi.mocked(listChunkingProfiles).mock.calls.length).toBe(1)
  })

  it('creates a knowledge domain and refreshes the domain list', async () => {
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '新建知识域' }))
    const dialog = await screen.findByRole('dialog', { name: '新建知识域' })

    fireEvent.change(within(dialog).getByLabelText('编码'), { target: { value: ' finance ' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: ' 财务制度 ' } })
    fireEvent.change(within(dialog).getByLabelText('描述'), { target: { value: ' 报销、预算与付款流程 ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建知识域' }))

    await waitFor(() =>
      expect(vi.mocked(createKnowledgeDomain)).toHaveBeenCalledWith({
        code: 'finance',
        name: '财务制度',
        description: '报销、预算与付款流程',
      }),
    )
    await waitFor(() => expect(vi.mocked(listKnowledgeDomains).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('知识域已创建。')).toBeTruthy()
  })

  it('keeps the create dialog open on validation or create failure', async () => {
    vi.mocked(createKnowledgeDomain).mockRejectedValueOnce(new Error('forbidden'))
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '新建知识域' }))
    const dialog = await screen.findByRole('dialog', { name: '新建知识域' })

    fireEvent.click(within(dialog).getByRole('button', { name: '创建知识域' }))
    expect(await within(dialog).findByText('请填写编码。')).toBeTruthy()
    expect(vi.mocked(createKnowledgeDomain)).not.toHaveBeenCalled()

    fireEvent.change(within(dialog).getByLabelText('编码'), { target: { value: 'ops' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: '运维手册' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建知识域' }))

    expect(await within(dialog).findByText('知识域创建失败，请检查权限或稍后重试。')).toBeTruthy()
    expect((within(dialog).getByLabelText('编码') as HTMLInputElement).value).toBe('ops')
    expect((within(dialog).getByLabelText('名称') as HTMLInputElement).value).toBe('运维手册')
  })

  it('edits a knowledge domain and refreshes the domain list', async () => {
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '编辑知识域 legal' }))
    const dialog = await screen.findByRole('dialog', { name: '编辑知识域 Legal' })

    expect((within(dialog).getByLabelText('编码') as HTMLInputElement).value).toBe('legal')
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: ' 法务制度 ' } })
    fireEvent.change(within(dialog).getByLabelText('描述'), { target: { value: ' 合同与合规流程 ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存知识域' }))

    await waitFor(() =>
      expect(vi.mocked(updateKnowledgeDomain)).toHaveBeenCalledWith(2, {
        name: '法务制度',
        description: '合同与合规流程',
      }),
    )
    await waitFor(() => expect(vi.mocked(listKnowledgeDomains).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('知识域已更新。')).toBeTruthy()
  })

  it('disables a knowledge domain through confirmation and keeps the dialog open on failure', async () => {
    vi.mocked(updateKnowledgeDomain).mockRejectedValueOnce(new Error('network failed'))
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '禁用知识域 hr' }))
    const dialog = await screen.findByRole('dialog', { name: '禁用知识域 HR Policies' })
    expect(vi.mocked(updateKnowledgeDomain)).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '确认禁用' }))

    await waitFor(() => expect(vi.mocked(updateKnowledgeDomain)).toHaveBeenCalledWith(1, { status: 'disabled' }))
    expect(await within(dialog).findByText('知识域禁用失败，请检查权限或稍后重试。')).toBeTruthy()
    expect(screen.getByRole('dialog', { name: '禁用知识域 HR Policies' })).toBeTruthy()
  })
})

describe('GovernanceConsolePage chunking profiles', () => {
  beforeEach(() => {
    vi.mocked(listKnowledgeDomains).mockResolvedValue(envelope(domains))
    vi.mocked(listChunkingProfiles).mockResolvedValue(envelope(profiles))
    vi.mocked(createKnowledgeDomain).mockResolvedValue(envelope(domains[0]))
    vi.mocked(updateKnowledgeDomain).mockResolvedValue({} as never)
    vi.mocked(createChunkingProfile).mockResolvedValue(envelope(profiles[0]))
    vi.mocked(updateChunkingProfile).mockResolvedValue({} as never)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('lists chunking profiles, highlights the default policy, and refreshes only profiles', async () => {
    renderGovernance()

    const defaultRow = await screen.findByTestId('chunking-profile-row-10')
    expect(defaultRow.textContent).toContain('default')
    expect(defaultRow.textContent).toContain('默认切块')
    expect(defaultRow.textContent).toContain('recursive')
    expect(defaultRow.textContent).toContain('默认')
    expect(defaultRow.textContent).toContain('active')

    const customRow = screen.getByTestId('chunking-profile-row-11')
    expect(customRow.textContent).toContain('legal-small')
    expect(customRow.textContent).toContain('sentence')
    expect(customRow.textContent).toContain('chunkSize')

    fireEvent.click(screen.getByRole('button', { name: '刷新切块策略' }))

    await waitFor(() => expect(vi.mocked(listChunkingProfiles).mock.calls.length).toBeGreaterThan(1))
    expect(vi.mocked(listKnowledgeDomains).mock.calls.length).toBe(1)
  })

  it('creates a chunking profile with parsed config and default flag', async () => {
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '新建切块策略' }))
    const dialog = await screen.findByRole('dialog', { name: '新建切块策略' })

    fireEvent.change(within(dialog).getByLabelText('编码'), { target: { value: ' support-large ' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: ' 客服长文切块 ' } })
    fireEvent.change(within(dialog).getByLabelText('策略'), { target: { value: ' recursive ' } })
    fireEvent.change(within(dialog).getByLabelText('Config JSON'), {
      target: { value: '{\n  "chunkSize": 900,\n  "overlap": 120\n}' },
    })
    fireEvent.click(within(dialog).getByLabelText('设为默认策略'))
    fireEvent.click(within(dialog).getByRole('button', { name: '创建切块策略' }))

    await waitFor(() =>
      expect(vi.mocked(createChunkingProfile)).toHaveBeenCalledWith({
        code: 'support-large',
        name: '客服长文切块',
        strategy: 'recursive',
        isDefault: true,
        config: { chunkSize: 900, overlap: 120 },
      }),
    )
    await waitFor(() => expect(vi.mocked(listChunkingProfiles).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('切块策略已创建。')).toBeTruthy()
  })

  it('keeps the create dialog open when required fields or config JSON are invalid', async () => {
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '新建切块策略' }))
    const dialog = await screen.findByRole('dialog', { name: '新建切块策略' })

    fireEvent.click(within(dialog).getByRole('button', { name: '创建切块策略' }))
    expect(await within(dialog).findByText('请填写编码。')).toBeTruthy()
    expect(vi.mocked(createChunkingProfile)).not.toHaveBeenCalled()

    fireEvent.change(within(dialog).getByLabelText('编码'), { target: { value: 'bad-json' } })
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: '错误配置' } })
    fireEvent.change(within(dialog).getByLabelText('策略'), { target: { value: 'recursive' } })
    fireEvent.change(within(dialog).getByLabelText('Config JSON'), { target: { value: '[1, 2, 3]' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '创建切块策略' }))

    expect(await within(dialog).findByText('Config JSON 必须是对象。')).toBeTruthy()
    expect(vi.mocked(createChunkingProfile)).not.toHaveBeenCalled()
  })

  it('edits a chunking profile and updates default policy state', async () => {
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '编辑切块策略 legal-small' }))
    const dialog = await screen.findByRole('dialog', { name: '编辑切块策略 法务小切块' })

    expect((within(dialog).getByLabelText('编码') as HTMLInputElement).value).toBe('legal-small')
    fireEvent.change(within(dialog).getByLabelText('名称'), { target: { value: ' 法务精细切块 ' } })
    fireEvent.change(within(dialog).getByLabelText('策略'), { target: { value: ' markdown ' } })
    fireEvent.change(within(dialog).getByLabelText('Config JSON'), {
      target: { value: '{\n  "chunkSize": 420\n}' },
    })
    fireEvent.click(within(dialog).getByLabelText('设为默认策略'))
    fireEvent.click(within(dialog).getByRole('button', { name: '保存切块策略' }))

    await waitFor(() =>
      expect(vi.mocked(updateChunkingProfile)).toHaveBeenCalledWith(11, {
        name: '法务精细切块',
        strategy: 'markdown',
        isDefault: true,
        config: { chunkSize: 420 },
      }),
    )
    await waitFor(() => expect(vi.mocked(listChunkingProfiles).mock.calls.length).toBeGreaterThan(1))
    expect(await screen.findByText('切块策略已更新。')).toBeTruthy()
  })

  it('disables a chunking profile through confirmation and keeps the dialog open on failure', async () => {
    vi.mocked(updateChunkingProfile).mockRejectedValueOnce(new Error('network failed'))
    renderGovernance()

    fireEvent.click(await screen.findByRole('button', { name: '禁用切块策略 legal-small' }))
    const dialog = await screen.findByRole('dialog', { name: '禁用切块策略 法务小切块' })
    expect(vi.mocked(updateChunkingProfile)).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '确认禁用' }))

    await waitFor(() => expect(vi.mocked(updateChunkingProfile)).toHaveBeenCalledWith(11, { status: 'disabled' }))
    expect(await within(dialog).findByText('切块策略禁用失败，请检查权限或稍后重试。')).toBeTruthy()
    expect(screen.getByRole('dialog', { name: '禁用切块策略 法务小切块' })).toBeTruthy()
  })
})
