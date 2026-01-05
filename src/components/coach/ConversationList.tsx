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
      {/* New conversation button with proper spacing */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-800 flex-shrink-0">
        <button
          onClick={onNewConversation}
          className="w-full py-3 px-4 bg-emerald-600 text-white rounded-xl font-medium hover:bg-emerald-700 active:bg-emerald-800 transition-colors touch-manipulation flex items-center justify-center gap-2"
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
          <div className="text-center text-gray-400 dark:text-gray-500 mt-8 px-4">
            <p className="text-sm">No conversations yet</p>
            <p className="text-xs mt-1">Start a new conversation to begin</p>
          </div>
        ) : (
          <ul className="divide-y divide-gray-100 dark:divide-gray-800">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <button
                  onClick={() => onSelect(conversation.id)}
                  className={`w-full text-left px-4 py-4 hover:bg-gray-50 dark:hover:bg-gray-800/50 active:bg-gray-100 dark:active:bg-gray-800 transition-colors touch-manipulation ${
                    selectedId === conversation.id
                      ? 'bg-emerald-50 dark:bg-emerald-900/30 border-l-4 border-emerald-500'
                      : 'border-l-4 border-transparent'
                  }`}
                >
                  <p className="font-medium text-gray-900 dark:text-white truncate text-[15px]">
                    {conversation.title || 'Untitled'}
                  </p>
                  <p className="text-sm text-gray-400 dark:text-gray-500 mt-0.5">
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
