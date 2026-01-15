'use client'

interface Conversation {
  id: string
  title: string | null
  updatedAt: string
  messages?: { content: string }[]
}

interface ConversationListProps {
  conversations: Conversation[]
  selectedId?: string
  onSelect: (id: string) => void
  onNewConversation: () => void
}

export function ConversationList({
  conversations,
  selectedId,
  onSelect,
  onNewConversation
}: ConversationListProps) {
  return (
    <div className="h-full flex flex-col overflow-hidden">
      {/* New conversation button - iOS style */}
      <div className="p-5 border-b border-[var(--glass-border)] flex-shrink-0">
        <button
          onClick={onNewConversation}
          className="w-full py-3.5 px-5 bg-[var(--accent-primary)] text-white rounded-2xl font-medium hover:bg-[var(--accent-secondary)] active:bg-[var(--accent-tertiary)] transition-colors touch-manipulation flex items-center justify-center gap-2 shadow-lg shadow-[var(--accent-primary)]/20"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
          New Conversation
        </button>
      </div>
      {/* Scrollable conversation list */}
      <div className="flex-1 overflow-y-auto overscroll-contain">
        {conversations.length === 0 ? (
          <div className="text-center text-[var(--muted-foreground)] mt-8 px-5">
            <p className="text-sm">No conversations yet</p>
            <p className="text-xs mt-1 text-[var(--muted-foreground)]/70">Start a new conversation to begin</p>
          </div>
        ) : (
          <ul className="divide-y divide-[var(--glass-border)]">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <button
                  onClick={() => onSelect(conversation.id)}
                  className={`w-full text-left px-5 py-5 hover:bg-[var(--foreground)]/5 active:bg-[var(--foreground)]/10 transition-colors touch-manipulation ${
                    selectedId === conversation.id
                      ? 'bg-[var(--accent-primary)]/10 border-l-4 border-[var(--accent-primary)]'
                      : 'border-l-4 border-transparent'
                  }`}
                >
                  <p className="font-medium text-[var(--foreground)] truncate text-[15px]">
                    {conversation.title || 'Untitled'}
                  </p>
                  <p className="text-xs text-[var(--muted-foreground)] mt-1">
                    {new Date(conversation.updatedAt).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit'
                    })}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
