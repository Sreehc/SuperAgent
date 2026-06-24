import { useMemo, useState } from 'react'
import type { ComponentProps, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import {
  Badge,
  Button,
  ConsolePage,
  DataTable,
  Dialog,
  DialogContent,
  DialogTrigger,
  FormField,
} from '@/shared/ui'
import { formatDateTime, formatNumber } from '@/shared/lib/format'
import { toast } from '@/utils/toast'
import { createKnowledgeBase, listKnowledgeBases } from '../api'
import type { KnowledgeBaseListItem, KnowledgeBaseStatus } from '../types'

const column = createColumnHelper<KnowledgeBaseListItem>()
type BadgeTone = ComponentProps<typeof Badge>['tone']

const STATUS_LABEL: Record<KnowledgeBaseStatus, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
  deleted: '已删除',
}

const STATUS_TONE: Record<KnowledgeBaseStatus, BadgeTone> = {
  draft: 'neutral',
  published: 'success',
  archived: 'warning',
  deleted: 'danger',
}

interface CreateKnowledgeBaseForm {
  name: string
  description?: string
}

export function KnowledgeListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [nameError, setNameError] = useState('')
  const [formError, setFormError] = useState('')
  const [open, setOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['knowledge-bases'],
    queryFn: () => listKnowledgeBases({ pageSize: 100 }),
  })

  const createMutation = useMutation({
    mutationFn: (payload: CreateKnowledgeBaseForm) => createKnowledgeBase(payload),
    onSuccess: async () => {
      toast.success('知识库已创建')
      resetCreateForm()
      await queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] })
    },
    onError: () => {
      setFormError('创建知识库失败，请稍后重试。')
      toast.error('创建失败，请稍后重试')
    },
  })

  function resetCreateForm() {
    setName('')
    setDescription('')
    setNameError('')
    setFormError('')
    setOpen(false)
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      resetCreateForm()
      return
    }

    setOpen(true)
  }

  function handleCreateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    if (!trimmedName) {
      setNameError('请输入知识库名称。')
      setFormError('')
      return
    }

    setNameError('')
    setFormError('')
    createMutation.mutate({
      name: trimmedName,
      ...(trimmedDescription ? { description: trimmedDescription } : {}),
    })
  }

  const columns = useMemo(
    () => [
      column.accessor('name', { header: '名称' }),
      column.accessor('status', {
        header: '状态',
        cell: (info) => {
          const status = info.getValue()
          return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>
        },
      }),
      column.accessor('documentCount', {
        header: '文档数',
        cell: (info) => <span className="numeric">{formatNumber(info.getValue())}</span>,
      }),
      column.accessor('updatedAt', { header: '更新时间', cell: (info) => formatDateTime(info.getValue()) }),
    ],
    [],
  )

  return (
    <ConsolePage
      title="知识库"
      description="管理租户内的知识库与文档。"
      actions={
        <Dialog open={open} onOpenChange={handleOpenChange}>
          <DialogTrigger asChild>
            <Button variant="primary" data-testid="knowledge-create-open">
              新建知识库
            </Button>
          </DialogTrigger>
          <DialogContent title="新建知识库">
            <form className="dialog-body" onSubmit={handleCreateSubmit}>
              <p className="dialog-description">用于组织文档、检索范围和聊天引用。</p>
              {formError && (
                <p className="error-banner" role="alert">
                  {formError}
                </p>
              )}
              <FormField label="名称" htmlFor="knowledge-create-name" error={nameError} errorTestId="knowledge-create-name-error">
                <input
                  data-testid="knowledge-create-name"
                  value={name}
                  onChange={(event) => {
                    setName(event.target.value)
                    if (nameError && event.target.value.trim()) setNameError('')
                  }}
                />
              </FormField>
              <FormField label="描述" htmlFor="knowledge-create-description" hint="可选，用于说明这个知识库覆盖的业务范围。">
                <textarea
                  id="knowledge-create-description"
                  rows={3}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </FormField>
              <div className="dialog-footer">
                <Button type="button" variant="ghost" onClick={resetCreateForm}>
                  取消
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  data-testid="knowledge-create-submit"
                  loading={createMutation.isPending}
                >
                  创建
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      }
    >
      <DataTable
        columns={columns as never}
        data={data?.data.items ?? []}
        rowTestId={(row) => `knowledge-row-${row.id}`}
        onRowClick={(row) => navigate(`/knowledge/${row.id}`)}
        emptyLabel="暂无知识库"
        loading={isLoading}
        loadingLabel="正在加载知识库"
      />
    </ConsolePage>
  )
}
