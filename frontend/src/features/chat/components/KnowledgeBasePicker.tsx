import { useEffect, useId, useMemo, useRef, useState } from 'react'

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
  const panelId = useId()
  const rootRef = useRef<HTMLDivElement | null>(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const normalizedSelectedIds = uniqueIds(selectedIds)
  const selectedId = normalizedSelectedIds[0] ?? null
  const selectedOption = options.find((option) => option.id === selectedId) ?? null
  const normalizedQuery = query.trim().toLowerCase()
  const filteredOptions = useMemo(() => {
    if (!normalizedQuery) return options
    return options.filter((option) => option.name.toLowerCase().includes(normalizedQuery))
  }, [normalizedQuery, options])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }

    window.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('keydown', handleEscape)
    return () => {
      window.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('keydown', handleEscape)
    }
  }, [])

  function chooseOption(option: KnowledgeBaseOption | null) {
    if (disabled) return
    onChange(option ? [option.id] : [])
    setOpen(false)
    setQuery(option?.name ?? '')
  }

  return (
    <div ref={rootRef} className="knowledge-picker" aria-label="知识库范围">
      <button
        type="button"
        className="knowledge-picker__trigger"
        disabled={disabled}
        aria-label="选择知识库"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="knowledge-picker__trigger-copy">
          <span className="knowledge-picker__eyebrow">知识库</span>
          <span className="knowledge-picker__summary">{selectedOption ? selectedOption.name : '不限定'}</span>
        </span>
        <span className="knowledge-picker__trigger-meta">
          {selectedOption ? <span className="knowledge-picker__badge">已选</span> : <span className="knowledge-picker__meta">全部</span>}
          <span className="knowledge-picker__caret" aria-hidden="true" />
        </span>
      </button>

      {open && (
        <div id={panelId} className="knowledge-picker__panel" role="dialog" aria-label="知识库选择面板">
          <div className="knowledge-picker__panel-header">
            <div className="knowledge-picker__panel-title">
              <strong>搜索知识库</strong>
              <span>单选优先，不选则表示不限定。</span>
            </div>
            <div className="knowledge-picker__panel-actions">
              {selectedOption && (
                <button
                  type="button"
                  className="btn-text knowledge-picker__clear"
                  disabled={disabled}
                  aria-label="清空知识库选择"
                  onClick={() => chooseOption(null)}
                >
                  清空
                </button>
              )}
              <button type="button" className="knowledge-picker__panel-close" onClick={() => setOpen(false)} aria-label="关闭知识库选择">
                收起
              </button>
            </div>
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
            autoFocus
            onChange={(event) => {
              setQuery(event.target.value)
              setOpen(true)
            }}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown' && filteredOptions.length > 0) {
                event.preventDefault()
                setOpen(true)
              }
            }}
            aria-controls={panelId}
            role="combobox"
            aria-expanded={open}
            aria-autocomplete="list"
            aria-haspopup="listbox"
          />

          <div className="knowledge-picker__options" role="listbox" aria-label="知识库候选">
            <button
              type="button"
              role="option"
              className="knowledge-picker__option knowledge-picker__option--unrestricted"
              aria-selected={selectedOption == null}
              onClick={() => chooseOption(null)}
            >
              <span>不限定</span>
              {selectedOption == null && <span className="knowledge-picker__check">✓</span>}
            </button>
            {filteredOptions.length > 0 ? (
              filteredOptions.map((option) => {
                const selected = selectedOption?.id === option.id
                return (
                  <button
                    key={option.id}
                    type="button"
                    role="option"
                    className="knowledge-picker__option"
                    aria-selected={selected}
                    onClick={() => chooseOption(option)}
                  >
                    <span className="knowledge-picker__option-label">{option.name}</span>
                    {selected && <span className="knowledge-picker__check">✓</span>}
                  </button>
                )
              })
            ) : (
              <div className="knowledge-picker__empty">没有匹配的知识库。</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
