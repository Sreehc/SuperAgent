import { useId, useMemo, useState, type ChangeEvent } from 'react'

export interface KnowledgeBaseOption {
  id: number
  name: string
}

interface KnowledgeBasePickerProps {
  options: KnowledgeBaseOption[]
  selectedIds: number[]
  disabled?: boolean
  onChange: (ids: number[]) => void
}

function uniqueIds(ids: number[]) {
  return Array.from(new Set(ids.filter((id) => Number.isFinite(id))))
}

export function KnowledgeBasePicker({ options, selectedIds, disabled = false, onChange }: KnowledgeBasePickerProps) {
  const searchId = useId()
  const [query, setQuery] = useState('')
  const normalizedSelectedIds = uniqueIds(selectedIds)
  const selectedSet = new Set(normalizedSelectedIds)
  const normalizedQuery = query.trim().toLowerCase()
  const filteredOptions = useMemo(() => {
    if (!normalizedQuery) return options
    return options.filter((option) => option.name.toLowerCase().includes(normalizedQuery))
  }, [normalizedQuery, options])
  const selectedOptions = normalizedSelectedIds.map((id) => options.find((option) => option.id === id) ?? { id, name: `#${id}` })
  const visibleSelectedOptions = selectedOptions.slice(0, 5)
  const hiddenCount = Math.max(0, selectedOptions.length - visibleSelectedOptions.length)

  function handleSelectChange(event: ChangeEvent<HTMLSelectElement>) {
    const values = Array.from(event.currentTarget.selectedOptions).map((option) => option.value)
    if (values.includes('')) {
      onChange([])
      return
    }
    onChange(uniqueIds(values.map((value) => Number(value))))
  }

  return (
    <div className="knowledge-picker" aria-label="知识库范围">
      <div className="knowledge-picker__header">
        <span>知识库</span>
        <strong>{selectedOptions.length > 0 ? `已选 ${selectedOptions.length} 个知识库` : '不限定'}</strong>
        {selectedOptions.length > 0 && (
          <button
            type="button"
            className="btn-text knowledge-picker__clear"
            disabled={disabled}
            aria-label="清空知识库选择"
            onClick={() => onChange([])}
          >
            清空
          </button>
        )}
      </div>

      <label className="sr-only" htmlFor={searchId}>
        搜索知识库
      </label>
      <input
        id={searchId}
        className="knowledge-picker__search"
        placeholder="搜索知识库"
        value={query}
        disabled={disabled}
        onChange={(event) => setQuery(event.target.value)}
      />

      <select
        multiple
        size={Math.min(6, Math.max(3, filteredOptions.length + 1))}
        className="knowledge-picker__select"
        data-testid="chat-knowledge-base"
        value={selectedOptions.length > 0 ? normalizedSelectedIds.map(String) : ['']}
        disabled={disabled}
        onChange={handleSelectChange}
        aria-label="知识库"
      >
        <option value="">不限定</option>
        {filteredOptions.map((option) => (
          <option key={option.id} value={option.id}>
            {option.name}
          </option>
        ))}
      </select>

      <div className="knowledge-picker__selected" role="status" aria-label="已选知识库">
        {visibleSelectedOptions.length > 0 ? (
          <>
            {visibleSelectedOptions.map((option) => (
              <span key={option.id} className="knowledge-picker__chip">
                {option.name}
              </span>
            ))}
            {hiddenCount > 0 && <span className="knowledge-picker__chip">另 {hiddenCount} 个</span>}
          </>
        ) : (
          <span className="knowledge-picker__empty">未限定知识库</span>
        )}
      </div>

      {selectedSet.size > 1 && (
        <p className="knowledge-picker__notice">当前后端暂不支持多知识库发送，保留 1 个知识库或选择不限定后再发送。</p>
      )}
    </div>
  )
}
