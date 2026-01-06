'use client'

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { usePathname } from 'next/navigation'

interface HeaderContextType {
  isHeaderVisible: boolean
  toggleHeader: () => void
  closeHeader: () => void
}

const HeaderContext = createContext<HeaderContextType | undefined>(undefined)

export function HeaderProvider({ children }: { children: ReactNode }) {
  const [isHeaderVisible, setIsHeaderVisible] = useState(false)
  const pathname = usePathname()

  // Auto-close header when navigating between pages
  // This is intentional - we want to close the header when URL changes
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsHeaderVisible(false)
  }, [pathname])

  const toggleHeader = useCallback(() => {
    setIsHeaderVisible(prev => !prev)
  }, [])

  const closeHeader = useCallback(() => {
    setIsHeaderVisible(false)
  }, [])

  return (
    <HeaderContext.Provider value={{ isHeaderVisible, toggleHeader, closeHeader }}>
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
