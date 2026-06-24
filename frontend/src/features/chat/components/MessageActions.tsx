import { useState } from 'react'
import { Check, Copy, MessageSquareText, ThumbsDown, ThumbsUp, X } from 'lucide-react'
import { PromptDialog } from '@/shared/ui'
import type { DisplayMessage, FeedbackRating } from '../types'

interface MessageActionsProps {
  message: DisplayMessage
  updating?: boolean
  onFeedback: (message: DisplayMessage, rating: FeedbackRating) => void | Promise<void>
  onCorrection: (message: DisplayMessage, correction: string) => void | Promise<void>
  onClearFeedback: (message: DisplayMessage) => void | Promise<void>
}

type CopyState = 'idle' | 'copied' | 'failed'

export function MessageActions({
  message,
  updating = false,
  onFeedback,
  onCorrection,
  onClearFeedback,
}: MessageActionsProps) {
  const [copyState, setCopyState] = useState<CopyState>('idle')
  const [correctionOpen, setCorrectionOpen] = useState(false)
  const activeRating = message.feedback?.rating ?? null
  const canSubmitFeedback = message.role === 'assistant' && message.id > 0

  if (message.role !== 'assistant') {
    return null
  }

  async function handleCopy() {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard API is unavailable')
      }
      await navigator.clipboard.writeText(message.content)
      setCopyState('copied')
    } catch {
      setCopyState('failed')
    }
  }

  return (
    <div className="message-actions" aria-label="消息操作">
      <button type="button" className="message-actions__button" title="复制回答" onClick={handleCopy}>
        <Copy size={14} aria-hidden="true" />
        <span className="sr-only">复制回答</span>
      </button>
      <button
        type="button"
        className="message-actions__button"
        title="标记有帮助"
        aria-pressed={activeRating === 'up'}
        disabled={!canSubmitFeedback || updating}
        onClick={() => onFeedback(message, 'up')}
      >
        <ThumbsUp size={14} aria-hidden="true" />
        <span className="sr-only">标记有帮助</span>
      </button>
      <button
        type="button"
        className="message-actions__button"
        title="标记无帮助"
        aria-pressed={activeRating === 'down'}
        disabled={!canSubmitFeedback || updating}
        onClick={() => onFeedback(message, 'down')}
      >
        <ThumbsDown size={14} aria-hidden="true" />
        <span className="sr-only">标记无帮助</span>
      </button>
      <button
        type="button"
        className="message-actions__button"
        title="提交更正"
        aria-pressed={activeRating === 'correction'}
        disabled={!canSubmitFeedback || updating}
        onClick={() => setCorrectionOpen(true)}
      >
        <MessageSquareText size={14} aria-hidden="true" />
        <span className="sr-only">提交更正</span>
      </button>
      {message.feedback && (
        <button
          type="button"
          className="message-actions__button"
          title="清除反馈"
          disabled={!canSubmitFeedback || updating}
          onClick={() => onClearFeedback(message)}
        >
          <X size={14} aria-hidden="true" />
          <span className="sr-only">清除反馈</span>
        </button>
      )}
      <MessageActionStatus state={copyState} rating={activeRating} />
      <PromptDialog
        open={correctionOpen}
        title="更正回答"
        label="更正建议"
        defaultValue={message.feedback?.correction ?? ''}
        placeholder="输入这条回答应如何修正"
        confirmLabel="提交"
        required
        onConfirm={(value) => onCorrection(message, value)}
        onOpenChange={setCorrectionOpen}
      />
    </div>
  )
}

function MessageActionStatus({ state, rating }: { state: CopyState; rating: FeedbackRating | null }) {
  if (state === 'copied') {
    return (
      <span className="message-actions__status">
        <Check size={13} aria-hidden="true" />
        已复制
      </span>
    )
  }
  if (state === 'failed') {
    return <span className="message-actions__status message-actions__status--error">复制失败</span>
  }
  if (rating === 'up') {
    return <span className="message-actions__status">已标记有帮助</span>
  }
  if (rating === 'down') {
    return <span className="message-actions__status">已标记无帮助</span>
  }
  if (rating === 'correction') {
    return <span className="message-actions__status">已提交更正</span>
  }
  return null
}
