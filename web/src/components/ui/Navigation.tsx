'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useSession, signOut } from 'next-auth/react'
import { useHeader } from '@/components/providers/HeaderProvider'

const HomeIcon = () => (
  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
  </svg>
)

const BookIcon = () => (
  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" />
  </svg>
)

const ChatIcon = () => (
  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z" />
  </svg>
)

const ChartIcon = () => (
  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5m.75-9l3-3 2.148 2.148A12.061 12.061 0 0116.5 7.605" />
  </svg>
)

const SparklesIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
  </svg>
)

const ExpandIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
    <line x1="5" y1="19" x2="19" y2="19" strokeLinecap="round" />
  </svg>
)

const CollapseIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M5 15l7-7 7 7" />
    <line x1="5" y1="5" x2="19" y2="5" strokeLinecap="round" />
  </svg>
)

export function Navigation() {
  const pathname = usePathname()
  const { data: session } = useSession()
  const { isHeaderVisible, toggleHeader } = useHeader()

  const navItems = [
    { href: '/', label: 'Dashboard', icon: HomeIcon },
    { href: '/journal', label: 'Journal', icon: BookIcon },
    { href: '/coach', label: 'Coach', icon: ChatIcon },
    { href: '/summaries', label: 'Summaries', icon: ChartIcon }
  ]

  if (!session) {
    return null
  }

  return (
    <>
      {/* Toggle button - iOS-style frosted glass */}
      <button
        onClick={toggleHeader}
        className="fixed top-4 right-4 z-[60] p-2.5 rounded-2xl bg-[var(--glass-bg)] backdrop-blur-[20px] border border-[var(--glass-border)] shadow-[var(--glass-shadow)] transition-all duration-300 text-[var(--muted)] hover:text-[var(--accent-primary)]"
        aria-label={isHeaderVisible ? 'Hide navigation' : 'Show navigation'}
      >
        {isHeaderVisible ? <CollapseIcon /> : <ExpandIcon />}
      </button>

      {/* Navigation bar - iOS-style translucent material */}
      <nav
        className={`fixed top-0 left-0 right-0 bg-[var(--glass-bg)] backdrop-blur-[20px] border-b border-[var(--glass-border)] z-50 shadow-[var(--glass-shadow)] transition-all duration-300 overflow-hidden ${
          isHeaderVisible ? 'max-h-[200px] opacity-100' : 'max-h-0 opacity-0 border-b-0'
        }`}
      >
        <div className="max-w-7xl mx-auto px-5 sm:px-8 lg:px-10">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <Link href="/" className="flex items-center gap-3 group">
                <div className="p-2.5 rounded-2xl bg-[var(--accent-primary)] text-white shadow-lg shadow-[var(--accent-primary)]/20 group-hover:shadow-xl group-hover:shadow-[var(--accent-primary)]/30 group-hover:scale-105 transition-all duration-300">
                  <SparklesIcon />
                </div>
                <span className="text-xl font-semibold text-[var(--foreground)]">
                  Personal Coach
                </span>
              </Link>
              <div className="hidden sm:ml-10 sm:flex sm:space-x-2">
                {navItems.map((item) => {
                  const Icon = item.icon
                  const isActive = pathname === item.href
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={`relative inline-flex items-center gap-2.5 px-5 py-2.5 rounded-xl text-sm font-medium transition-all duration-300 ${
                        isActive
                          ? 'bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]'
                          : 'text-[var(--muted)] hover:bg-[var(--foreground)]/5 hover:text-[var(--foreground)]'
                      }`}
                    >
                      <Icon />
                      {item.label}
                      {isActive && (
                        <span className="absolute -bottom-[15px] left-1/2 -translate-x-1/2 w-8 h-0.5 bg-[var(--accent-primary)] rounded-full" />
                      )}
                    </Link>
                  )
                })}
              </div>
            </div>
            <div className="flex items-center gap-4 pr-14">
              <div className="hidden sm:flex items-center gap-3 px-4 py-2 rounded-full bg-[var(--foreground)]/5 border border-[var(--glass-border)]">
                <div className="w-8 h-8 rounded-full bg-[var(--accent-primary)] flex items-center justify-center text-white font-semibold text-sm">
                  {session.user?.email?.[0]?.toUpperCase() || 'U'}
                </div>
                <span className="text-sm font-medium text-[var(--foreground)] max-w-[120px] truncate">{session.user?.email}</span>
              </div>
              <button
                onClick={() => signOut()}
                className="text-sm font-medium text-[var(--muted)] hover:text-red-500 px-4 py-2 rounded-xl hover:bg-red-500/10 transition-all duration-300"
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
        {/* Mobile nav - iOS-style tab bar */}
        <div className="sm:hidden border-t border-[var(--glass-border)] bg-[var(--glass-bg)]">
          <div className="flex justify-around py-3 px-3">
            {navItems.map((item) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex flex-col items-center gap-1.5 px-5 py-2.5 rounded-2xl text-xs font-medium transition-all duration-300 ${
                    isActive
                      ? 'text-[var(--accent-primary)] bg-[var(--accent-primary)]/10'
                      : 'text-[var(--muted)] hover:text-[var(--foreground)] hover:bg-[var(--foreground)]/5'
                  }`}
                >
                  <Icon />
                  {item.label}
                </Link>
              )
            })}
          </div>
        </div>
      </nav>
    </>
  )
}
