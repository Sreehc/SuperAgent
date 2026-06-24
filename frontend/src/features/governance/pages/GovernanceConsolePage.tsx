import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createChunkingProfile,
  createKnowledgeDomain,
  listChunkingProfiles,
  listKnowledgeDomains,
  updateChunkingProfile,
  updateKnowledgeDomain,
} from '../../knowledge/api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog, Dialog, DialogContent } from '../../../shared/ui/dialog'
import { FormField } from '../../../shared/ui/form'
import type { ChunkingProfileItem, KnowledgeDomainItem } from '../../knowledge/types'

type DomainFormMode = 'create' | 'edit'
type ProfileFormMode = 'create' | 'edit'

interface DomainFormState {
  mode: DomainFormMode
  domain: KnowledgeDomainItem | null
  code: string
  name: string
  description: string
}

interface ProfileFormState {
  mode: ProfileFormMode
  profile: ChunkingProfileItem | null
  code: string
  name: string
  strategy: string
  isDefault: boolean
  configJson: string
}

const emptyDomainForm: DomainFormState = {
  mode: 'create',
  domain: null,
  code: '',
  name: '',
  description: '',
}

const emptyProfileForm: ProfileFormState = {
  mode: 'create',
  profile: null,
  code: '',
  name: '',
  strategy: '',
  isDefault: false,
  configJson: '{\n  \n}',
}

function stringifyConfig(config: Record<string, unknown>) {
  return JSON.stringify(config ?? {}, null, 2)
}

function parseConfigJson(value: string): Record<string, unknown> | null {
  const trimmed = value.trim()
  if (!trimmed) return {}

  try {
    const parsed = JSON.parse(trimmed)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

export function GovernanceConsolePage() {
  const queryClient = useQueryClient()
  const [domainForm, setDomainForm] = useState<DomainFormState | null>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [domainSuccess, setDomainSuccess] = useState<string | null>(null)
  const [disableTarget, setDisableTarget] = useState<KnowledgeDomainItem | null>(null)
  const [disableError, setDisableError] = useState<string | null>(null)
  const [profileForm, setProfileForm] = useState<ProfileFormState | null>(null)
  const [profileFieldError, setProfileFieldError] = useState<string | null>(null)
  const [profileFormError, setProfileFormError] = useState<string | null>(null)
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null)
  const [disableProfileTarget, setDisableProfileTarget] = useState<ChunkingProfileItem | null>(null)
  const [disableProfileError, setDisableProfileError] = useState<string | null>(null)
  const domainsQuery = useQuery({
    queryKey: ['knowledge-domains'],
    queryFn: () => listKnowledgeDomains(),
  })
  const profilesQuery = useQuery({
    queryKey: ['chunking-profiles'],
    queryFn: () => listChunkingProfiles(),
  })
  const createDomainMutation = useMutation({
    mutationFn: (payload: { code: string; name: string; description?: string }) => createKnowledgeDomain(payload),
    onSuccess: async () => {
      setFormError(null)
      setDomainSuccess('知识域已创建。')
      setDomainForm(null)
      await queryClient.invalidateQueries({ queryKey: ['knowledge-domains'] })
    },
    onError: () => {
      setFormError('知识域创建失败，请检查权限或稍后重试。')
    },
  })
  const updateDomainMutation = useMutation({
    mutationFn: ({ domainId, payload }: { domainId: number; payload: { name?: string; description?: string; status?: string } }) =>
      updateKnowledgeDomain(domainId, payload),
    onSuccess: async (_data, variables) => {
      setFormError(null)
      setDisableError(null)
      setDomainSuccess(variables.payload.status === 'disabled' ? '知识域已禁用。' : '知识域已更新。')
      await queryClient.invalidateQueries({ queryKey: ['knowledge-domains'] })
    },
  })
  const createProfileMutation = useMutation({
    mutationFn: (payload: { code: string; name: string; strategy: string; isDefault?: boolean; config?: Record<string, unknown> }) =>
      createChunkingProfile(payload),
    onSuccess: async () => {
      setProfileFormError(null)
      setProfileSuccess('切块策略已创建。')
      setProfileForm(null)
      await queryClient.invalidateQueries({ queryKey: ['chunking-profiles'] })
    },
    onError: () => {
      setProfileFormError('切块策略创建失败，请检查权限或稍后重试。')
    },
  })
  const updateProfileMutation = useMutation({
    mutationFn: ({
      profileId,
      payload,
    }: {
      profileId: number
      payload: { name?: string; strategy?: string; isDefault?: boolean; status?: string; config?: Record<string, unknown> }
    }) => updateChunkingProfile(profileId, payload),
    onSuccess: async (_data, variables) => {
      setProfileFormError(null)
      setDisableProfileError(null)
      setProfileSuccess(variables.payload.status === 'disabled' ? '切块策略已禁用。' : '切块策略已更新。')
      await queryClient.invalidateQueries({ queryKey: ['chunking-profiles'] })
    },
  })

  const domains = domainsQuery.data?.data ?? []
  const profiles = profilesQuery.data?.data ?? []

  function openCreateDomain() {
    setDomainSuccess(null)
    setFieldError(null)
    setFormError(null)
    setDomainForm(emptyDomainForm)
  }

  function openEditDomain(domain: KnowledgeDomainItem) {
    setDomainSuccess(null)
    setFieldError(null)
    setFormError(null)
    setDomainForm({
      mode: 'edit',
      domain,
      code: domain.code,
      name: domain.name,
      description: domain.description ?? '',
    })
  }

  function closeDomainForm(open: boolean) {
    if (open) return
    setDomainForm(null)
    setFieldError(null)
    setFormError(null)
  }

  function updateDomainForm(field: keyof Pick<DomainFormState, 'code' | 'name' | 'description'>, value: string) {
    setFieldError(null)
    setDomainForm((current) => (current ? { ...current, [field]: value } : current))
  }

  async function submitDomainForm() {
    if (!domainForm) return

    const code = domainForm.code.trim()
    const name = domainForm.name.trim()
    const description = domainForm.description.trim()
    setFieldError(null)
    setFormError(null)
    setDomainSuccess(null)

    if (!code) {
      setFieldError('请填写编码。')
      return
    }
    if (!name) {
      setFieldError('请填写名称。')
      return
    }

    try {
      if (domainForm.mode === 'create') {
        await createDomainMutation.mutateAsync({
          code,
          name,
          ...(description ? { description } : {}),
        })
        return
      }

      if (!domainForm.domain) return
      await updateDomainMutation.mutateAsync({
        domainId: domainForm.domain.id,
        payload: {
          name,
          description,
        },
      })
      setDomainForm(null)
    } catch {
      if (domainForm.mode === 'edit') {
        setFormError('知识域更新失败，请检查权限或稍后重试。')
      }
    }
  }

  async function confirmDisableDomain() {
    if (!disableTarget) return

    try {
      await updateDomainMutation.mutateAsync({
        domainId: disableTarget.id,
        payload: { status: 'disabled' },
      })
      setDisableTarget(null)
    } catch {
      setDisableError('知识域禁用失败，请检查权限或稍后重试。')
      throw new Error('disable domain failed')
    }
  }

  function openCreateProfile() {
    setProfileSuccess(null)
    setProfileFieldError(null)
    setProfileFormError(null)
    setProfileForm(emptyProfileForm)
  }

  function openEditProfile(profile: ChunkingProfileItem) {
    setProfileSuccess(null)
    setProfileFieldError(null)
    setProfileFormError(null)
    setProfileForm({
      mode: 'edit',
      profile,
      code: profile.code,
      name: profile.name,
      strategy: profile.strategy,
      isDefault: profile.isDefault,
      configJson: stringifyConfig(profile.config),
    })
  }

  function closeProfileForm(open: boolean) {
    if (open) return
    setProfileForm(null)
    setProfileFieldError(null)
    setProfileFormError(null)
  }

  function updateProfileForm(field: keyof Pick<ProfileFormState, 'code' | 'name' | 'strategy' | 'configJson'>, value: string) {
    setProfileFieldError(null)
    setProfileForm((current) => (current ? { ...current, [field]: value } : current))
  }

  function updateProfileDefault(isDefault: boolean) {
    setProfileForm((current) => (current ? { ...current, isDefault } : current))
  }

  async function submitProfileForm() {
    if (!profileForm) return

    const code = profileForm.code.trim()
    const name = profileForm.name.trim()
    const strategy = profileForm.strategy.trim()
    setProfileFieldError(null)
    setProfileFormError(null)
    setProfileSuccess(null)

    if (!code) {
      setProfileFieldError('请填写编码。')
      return
    }
    if (!name) {
      setProfileFieldError('请填写名称。')
      return
    }
    if (!strategy) {
      setProfileFieldError('请填写策略。')
      return
    }

    const config = parseConfigJson(profileForm.configJson)
    if (!config) {
      setProfileFieldError('Config JSON 必须是对象。')
      return
    }

    try {
      if (profileForm.mode === 'create') {
        await createProfileMutation.mutateAsync({
          code,
          name,
          strategy,
          isDefault: profileForm.isDefault,
          config,
        })
        return
      }

      if (!profileForm.profile) return
      await updateProfileMutation.mutateAsync({
        profileId: profileForm.profile.id,
        payload: {
          name,
          strategy,
          isDefault: profileForm.isDefault,
          config,
        },
      })
      setProfileForm(null)
    } catch {
      if (profileForm.mode === 'edit') {
        setProfileFormError('切块策略更新失败，请检查权限或稍后重试。')
      }
    }
  }

  async function confirmDisableProfile() {
    if (!disableProfileTarget) return

    try {
      await updateProfileMutation.mutateAsync({
        profileId: disableProfileTarget.id,
        payload: { status: 'disabled' },
      })
      setDisableProfileTarget(null)
    } catch {
      setDisableProfileError('切块策略禁用失败，请检查权限或稍后重试。')
      throw new Error('disable profile failed')
    }
  }

  const domainDialogTitle =
    domainForm?.mode === 'edit' ? `编辑知识域 ${domainForm.domain?.name ?? domainForm.name}` : '新建知识域'
  const profileDialogTitle =
    profileForm?.mode === 'edit' ? `编辑切块策略 ${profileForm.profile?.name ?? profileForm.name}` : '新建切块策略'

  return (
    <ConsolePage
      title="治理"
      description="知识域与切块策略等治理配置，统一约束知识库的解析与检索行为。"
      actions={
        <>
          <Button type="button" variant="secondary" onClick={() => domainsQuery.refetch()}>
            刷新知识域
          </Button>
          <Button type="button" variant="primary" onClick={openCreateDomain}>
            新建知识域
          </Button>
        </>
      }
    >
      {domainSuccess && (
        <p className="success-banner" role="status">
          {domainSuccess}
        </p>
      )}
      {profileSuccess && (
        <p className="success-banner" role="status">
          {profileSuccess}
        </p>
      )}

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">知识域</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>状态</th>
                <th>描述</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {domains.map((domain) => (
                <tr key={domain.id} data-testid={`knowledge-domain-row-${domain.id}`}>
                  <td className="mono">{domain.code}</td>
                  <td>{domain.name}</td>
                  <td>
                    <Badge tone={domain.status === 'active' ? 'success' : 'neutral'}>{domain.status}</Badge>
                  </td>
                  <td>{domain.description || '—'}</td>
                  <td>
                    <div className="action-row">
                      <Button type="button" variant="ghost" size="sm" aria-label={`编辑知识域 ${domain.code}`} onClick={() => openEditDomain(domain)}>
                        编辑
                      </Button>
                      {domain.status === 'active' ? (
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          aria-label={`禁用知识域 ${domain.code}`}
                          onClick={() => {
                            setDomainSuccess(null)
                            setDisableError(null)
                            setDisableTarget(domain)
                          }}
                        >
                          禁用
                        </Button>
                      ) : (
                        <span className="field-label">已禁用</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {domains.length === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">{domainsQuery.isLoading ? '加载中…' : '暂无知识域'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <div className="section-heading-row">
          <p className="section-label">切块策略</p>
          <div className="action-row">
            <Button type="button" variant="secondary" size="sm" onClick={() => profilesQuery.refetch()}>
              刷新切块策略
            </Button>
            <Button type="button" variant="primary" size="sm" onClick={openCreateProfile}>
              新建切块策略
            </Button>
          </div>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>策略</th>
                <th>Config</th>
                <th>默认</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {profiles.map((profile) => (
                <tr key={profile.id} data-testid={`chunking-profile-row-${profile.id}`}>
                  <td className="mono">{profile.code}</td>
                  <td>{profile.name}</td>
                  <td className="mono">{profile.strategy}</td>
                  <td className="mono" style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {JSON.stringify(profile.config)}
                  </td>
                  <td>{profile.isDefault ? <Badge tone="accent">默认</Badge> : '—'}</td>
                  <td>
                    <Badge tone={profile.status === 'active' ? 'success' : 'neutral'}>{profile.status}</Badge>
                  </td>
                  <td>
                    <div className="action-row">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={`编辑切块策略 ${profile.code}`}
                        onClick={() => openEditProfile(profile)}
                      >
                        编辑
                      </Button>
                      {profile.status === 'active' ? (
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          aria-label={`禁用切块策略 ${profile.code}`}
                          onClick={() => {
                            setProfileSuccess(null)
                            setDisableProfileError(null)
                            setDisableProfileTarget(profile)
                          }}
                        >
                          禁用
                        </Button>
                      ) : (
                        <span className="field-label">已禁用</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {profiles.length === 0 && (
                <tr>
                  <td colSpan={7}>
                    <div className="empty-line">{profilesQuery.isLoading ? '加载中…' : '暂无切块策略'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <Dialog open={profileForm != null} onOpenChange={closeProfileForm}>
        <DialogContent title={profileDialogTitle}>
          <div className="dialog-body">
            <p className="dialog-description">切块策略决定文档解析后的分段方式，默认策略会作为上传和重处理时的兜底配置。</p>
            {profileFormError && (
              <p className="error-banner" role="alert">
                {profileFormError}
              </p>
            )}
            <FormField label="编码" htmlFor="governance-profile-code" error={profileFieldError?.includes('编码') ? profileFieldError : undefined}>
              <input
                id="governance-profile-code"
                value={profileForm?.code ?? ''}
                placeholder="support-large"
                disabled={profileForm?.mode === 'edit'}
                onChange={(event) => updateProfileForm('code', event.target.value)}
              />
            </FormField>
            <FormField label="名称" htmlFor="governance-profile-name" error={profileFieldError?.includes('名称') ? profileFieldError : undefined}>
              <input
                id="governance-profile-name"
                value={profileForm?.name ?? ''}
                placeholder="客服长文切块"
                onChange={(event) => updateProfileForm('name', event.target.value)}
              />
            </FormField>
            <FormField label="策略" htmlFor="governance-profile-strategy" error={profileFieldError?.includes('策略') ? profileFieldError : undefined}>
              <input
                id="governance-profile-strategy"
                value={profileForm?.strategy ?? ''}
                placeholder="recursive"
                onChange={(event) => updateProfileForm('strategy', event.target.value)}
              />
            </FormField>
            <FormField
              label="Config JSON"
              htmlFor="governance-profile-config"
              hint="必须是 JSON 对象；留空会提交空对象。"
              error={profileFieldError?.includes('Config JSON') ? profileFieldError : undefined}
            >
              <textarea
                id="governance-profile-config"
                rows={8}
                value={profileForm?.configJson ?? ''}
                onChange={(event) => updateProfileForm('configJson', event.target.value)}
              />
            </FormField>
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={profileForm?.isDefault ?? false}
                onChange={(event) => updateProfileDefault(event.target.checked)}
              />
              <span>设为默认策略</span>
            </label>
            <div className="dialog-footer">
              <Button type="button" variant="ghost" onClick={() => closeProfileForm(false)}>
                取消
              </Button>
              <Button
                type="button"
                variant="primary"
                loading={createProfileMutation.isPending || updateProfileMutation.isPending}
                onClick={submitProfileForm}
              >
                {profileForm?.mode === 'edit' ? '保存切块策略' : '创建切块策略'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={domainForm != null} onOpenChange={closeDomainForm}>
        <DialogContent title={domainDialogTitle}>
          <div className="dialog-body">
            <p className="dialog-description">
              知识域用于约束文档归属和检索范围，编码创建后不建议变更。
            </p>
            {formError && (
              <p className="error-banner" role="alert">
                {formError}
              </p>
            )}
            <FormField label="编码" htmlFor="governance-domain-code" error={fieldError?.includes('编码') ? fieldError : undefined}>
              <input
                id="governance-domain-code"
                value={domainForm?.code ?? ''}
                placeholder="finance"
                disabled={domainForm?.mode === 'edit'}
                onChange={(event) => updateDomainForm('code', event.target.value)}
              />
            </FormField>
            <FormField label="名称" htmlFor="governance-domain-name" error={fieldError?.includes('名称') ? fieldError : undefined}>
              <input
                id="governance-domain-name"
                value={domainForm?.name ?? ''}
                placeholder="财务制度"
                onChange={(event) => updateDomainForm('name', event.target.value)}
              />
            </FormField>
            <FormField label="描述" htmlFor="governance-domain-description" hint="可选，用于说明该知识域覆盖的文档范围。">
              <textarea
                id="governance-domain-description"
                rows={4}
                value={domainForm?.description ?? ''}
                onChange={(event) => updateDomainForm('description', event.target.value)}
              />
            </FormField>
            <div className="dialog-footer">
              <Button type="button" variant="ghost" onClick={() => closeDomainForm(false)}>
                取消
              </Button>
              <Button
                type="button"
                variant="primary"
                loading={createDomainMutation.isPending || updateDomainMutation.isPending}
                onClick={submitDomainForm}
              >
                {domainForm?.mode === 'edit' ? '保存知识域' : '创建知识域'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={disableTarget != null}
        title={`禁用知识域 ${disableTarget?.name ?? ''}`.trim()}
        description="禁用后该知识域不应继续用于新的文档归类或检索配置，历史文档保留原有关联。"
        error={disableError}
        confirmLabel="确认禁用"
        tone="danger"
        onOpenChange={(open) => {
          if (!open) {
            setDisableTarget(null)
            setDisableError(null)
          }
        }}
        onConfirm={confirmDisableDomain}
      />
      <ConfirmDialog
        open={disableProfileTarget != null}
        title={`禁用切块策略 ${disableProfileTarget?.name ?? ''}`.trim()}
        description="禁用后该切块策略不应继续用于新的上传或重处理，历史文档版本保留原有关联。"
        error={disableProfileError}
        confirmLabel="确认禁用"
        tone="danger"
        onOpenChange={(open) => {
          if (!open) {
            setDisableProfileTarget(null)
            setDisableProfileError(null)
          }
        }}
        onConfirm={confirmDisableProfile}
      />
    </ConsolePage>
  )
}

export default GovernanceConsolePage
