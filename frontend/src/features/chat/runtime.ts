import { useExternalStoreRuntime, type AppendMessage, type ThreadMessageLike } from '@assistant-ui/react'
import { useChatStore } from './store/chat'
import type { DisplayMessage } from './types'

/** Map our server-shaped message onto assistant-ui's thread message. */
function convertMessage(message: DisplayMessage): ThreadMessageLike {
  return {
    role: message.role,
    content: message.content,
    id: String(message.id),
  }
}

function extractText(message: AppendMessage): string {
  return message.content.map((part) => (part.type === 'text' ? part.text : '')).join('')
}

/**
 * assistant-ui external-store runtime: our Zustand store stays the source of
 * truth (so the session rail and evidence inspector read the same stream),
 * while assistant-ui drives the thread/composer UX and streaming indicator.
 */
export function useChatRuntime() {
  const messages = useChatStore((state) => state.messages)
  const streaming = useChatStore((state) => state.streaming)

  return useExternalStoreRuntime<DisplayMessage>({
    isRunning: streaming,
    messages,
    convertMessage,
    onNew: async (message) => {
      await useChatStore.getState().sendMessage(extractText(message))
    },
    onCancel: async () => {
      await useChatStore.getState().stopStreaming()
    },
  })
}
