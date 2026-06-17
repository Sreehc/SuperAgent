import { useQuery } from '@tanstack/react-query'
import { listToolCapabilities } from '../api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'

function riskTone(risk: string): 'danger' | 'warning' | 'neutral' {
  if (risk === 'high') return 'danger'
  if (risk === 'medium') return 'warning'
  return 'neutral'
}

export function ToolsConsolePage() {
  const { data, isLoading } = useQuery({
    queryKey: ['tool-capabilities'],
    queryFn: () => listToolCapabilities(),
  })

  const tools = data?.data.tools ?? []

  return (
    <ConsolePage title="Tools" description="agent 可调用工具的能力清单、风险等级与可用状态。">
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>工具</th>
              <th>类型</th>
              <th>风险</th>
              <th>状态</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            {tools.map((tool) => (
              <tr key={tool.toolId}>
                <td>
                  <strong>{tool.name}</strong>
                  <div className="mono" style={{ color: 'var(--text-muted)' }}>
                    {tool.toolId}
                  </div>
                </td>
                <td className="mono">{tool.kind}</td>
                <td>
                  <Badge tone={riskTone(tool.riskLevel)}>{tool.riskLevel}</Badge>
                </td>
                <td>
                  <Badge tone={tool.executable ? 'success' : 'neutral'}>
                    {tool.executable ? '可用' : tool.enabled ? '需配置' : '已禁用'}
                  </Badge>
                </td>
                <td>{tool.description || tool.reason || '—'}</td>
              </tr>
            ))}
            {tools.length === 0 && (
              <tr>
                <td colSpan={5}>
                  <div className="empty-line">{isLoading ? '加载中…' : '暂无可用工具'}</div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </ConsolePage>
  )
}

export default ToolsConsolePage
