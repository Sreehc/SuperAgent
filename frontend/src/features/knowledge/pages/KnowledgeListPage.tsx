import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge, Button, ConsolePage, DataTable, Dialog, DialogContent, DialogTrigger } from '@/shared/ui'
import { LoadingSpinner } from '@/components/feedback'
import { toast } from '@/utils/toast'
import { createKnowledgeBase, listKnowledgeBases } from '../api'
import type { KnowledgeBaseListItem } from '../types'

const column = createColumnHelper<KnowledgeBaseListItem>()

export function KnowledgeListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [open, setOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['knowledge-bases'],
    queryFn: () => listKnowledgeBases({ pageSize: 100 }),
  })

  const createMutation = useMutation({
    mutationFn: () => createKnowledgeBase({ name: name.trim() }),
    onSuccess: async () => {
      toast.success('知识库已创建')
      setName('')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] })
    },
    onError: () => toast.error('创建失败，请稍后重试'),
  })

  const columns = useMemo(
    () => [
      column.accessor('name', { header: '名称' }),
      column.accessor('status', { header: '状态', cell: (info) => <Badge tone={info.getValue() === 'published' ? 'success' : 'neutral'}>{info.getValue()}</Badge> }),
      column.accessor('documentCount', { header: '文档数', cell: (info) => <span className="numeric">{info.getValue()}</span> }),
      column.accessor('updatedAt', { header: '更新时间' }),
    ],
    [],
  )

  return (
    <ConsolePage
      title="知识库"
      description="管理租户内的知识库与文档。"
      actions={
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button variant="primary" data-testid="knowledge-create-open">新建知识库</Button>
          </DialogTrigger>
          <DialogContent title="新建知识库">
            <label className="field">
              <span>名称</span>
              <input data-testid="knowledge-create-name" value={name} onChange={(event) => setName(event.target.value)} />
            </label>
            <div className="action-row">
              <Button
                variant="primary"
                data-testid="knowledge-create-submit"
                disabled={!name.trim()}
                loading={createMutation.isPending}
                onClick={() => createMutation.mutate()}
              >
                创建
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      }
    >
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <DataTable
          columns={columns as never}
          data={data?.data.items ?? []}
          rowTestId={(row) => `knowledge-row-${row.id}`}
          onRowClick={(row) => navigate(`/knowledge/${row.id}`)}
          emptyLabel="还没有知识库"
        />
      )}
    </ConsolePage>
  )
}
