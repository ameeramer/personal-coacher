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
    <div className="h-full flex flex-col">
      <div className="p-4 border-b border-gray-200">
        <button
          onClick={onNewConversation}
          className="w-full py-2 px-4 bg-emerald-600 text-white rounded-lg font-medium hover:bg-emerald-700"
        >
          New Conversation
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {conversations.length === 0 ? (
          <p className="text-center text-gray-500 mt-4 px-4">No conversations yet</p>
        ) : (
          <ul className="divide-y divide-gray-200">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <button
                  onClick={() => onSelect(conversation.id)}
                  className={`w-full text-left px-4 py-3 hover:bg-gray-50 ${
                    selectedId === conversation.id ? 'bg-emerald-50' : ''
                  }`}
                >
                  <p className="font-medium text-gray-900 truncate">
                    {conversation.title || 'Untitled'}
                  </p>
                  <p className="text-sm text-gray-500">
                    {new Date(conversation.updatedAt).toLocaleDateString()}
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
