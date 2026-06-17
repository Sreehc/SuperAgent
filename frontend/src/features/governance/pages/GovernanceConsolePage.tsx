import { useQuery } from '@tanstack/react-query'
import { listChunkingProfiles, listKnowledgeDomains } from '../../knowledge/api'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'

export function GovernanceConsolePage() {
  const domainsQuery = useQuery({
    queryKey: ['knowledge-domains'],
    queryFn: () => listKnowledgeDomains(),
  })
  const profilesQuery = useQuery({
    queryKey: ['chunking-profiles'],
    queryFn: () => listChunkingProfiles(),
  })

  return (
    <ConsolePage title="治理" description="知识域与切块策略等治理配置，统一约束知识库的解析与检索行为。">
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
              </tr>
            </thead>
            <tbody>
              {(domainsQuery.data?.data ?? []).map((domain) => (
                <tr key={domain.id}>
                  <td className="mono">{domain.code}</td>
                  <td>{domain.name}</td>
                  <td>
                    <Badge tone={domain.status === 'active' ? 'success' : 'neutral'}>{domain.status}</Badge>
                  </td>
                  <td>{domain.description || '—'}</td>
                </tr>
              ))}
              {(domainsQuery.data?.data.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={4}>
                    <div className="empty-line">{domainsQuery.isLoading ? '加载中…' : '暂无知识域'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="surface-box" style={{ display: 'grid', gap: 10 }}>
        <p className="section-label">切块策略</p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>策略</th>
                <th>默认</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {(profilesQuery.data?.data ?? []).map((profile) => (
                <tr key={profile.id}>
                  <td className="mono">{profile.code}</td>
                  <td>{profile.name}</td>
                  <td className="mono">{profile.strategy}</td>
                  <td>{profile.isDefault ? <Badge tone="accent">默认</Badge> : '—'}</td>
                  <td>
                    <Badge tone={profile.status === 'active' ? 'success' : 'neutral'}>{profile.status}</Badge>
                  </td>
                </tr>
              ))}
              {(profilesQuery.data?.data.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-line">{profilesQuery.isLoading ? '加载中…' : '暂无切块策略'}</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </ConsolePage>
  )
}

export default GovernanceConsolePage
