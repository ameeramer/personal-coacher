'use client'

import { createContext, useContext, useState, ReactNode } from 'react'

interface HeaderContextType {
  isHeaderVisible: boolean
  toggleHeader: () => void
}

const HeaderContext = createContext<HeaderContextType | undefined>(undefined)

export function HeaderProvider({ children }: { children: ReactNode }) {
  const [isHeaderVisible, setIsHeaderVisible] = useState(false)

  const toggleHeader = () => {
    setIsHeaderVisible(prev => !prev)
  }

  return (
    <HeaderContext.Provider value={{ isHeaderVisible, toggleHeader }}>
      {children}
    </HeaderContext.Provider>
  )
}

export function useHeader() {
  const context = useContext(HeaderContext)
  if (context === undefined) {
    throw new Error('useHeader must be used within a HeaderProvider')
  }
  return context
}
