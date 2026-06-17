import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { LoadingSpinner } from '@/components/feedback'
import { selectCurrentRole, useAuthStore } from '@/features/auth/store/auth'
import type { TenantRole } from '@/features/auth/types'

function RouteLoading() {
  return (
    <div className="route-loading">
      <LoadingSpinner />
    </div>
  )
}

/** Gate authenticated routes; mirrors the previous router `beforeEach` session check. */
export function RequireAuth() {
  const location = useLocation()
  const [state, setState] = useState<'checking' | 'ok' | 'deny'>('checking')

  useEffect(() => {
    let active = true
    useAuthStore
      .getState()
      .ensureSession()
      .then((ok) => {
        if (!active) return
        if (ok) {
          setState('ok')
        } else {
          useAuthStore.getState().rememberRedirect(location.pathname + location.search)
          setState('deny')
        }
      })
    return () => {
      active = false
    }
    // Run once on mount; ensureSession is cheap when already hydrated.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (state === 'checking') return <RouteLoading />
  if (state === 'deny') return <Navigate to="/login" replace />
  return <Outlet />
}

/** Per-route role gate; redirects to /forbidden when the tenant role is insufficient. */
export function RequireRole({ roles }: { roles: TenantRole[] }) {
  const role = useAuthStore(selectCurrentRole)
  if (!role) return <RouteLoading />
  if (!roles.includes(role)) return <Navigate to="/forbidden" replace />
  return <Outlet />
}

/** Login route guard: bounce already-authenticated users back into the app. */
export function GuestOnly() {
  const [state, setState] = useState<'checking' | 'guest' | 'authed'>('checking')

  useEffect(() => {
    let active = true
    useAuthStore
      .getState()
      .ensureSession()
      .then((ok) => active && setState(ok ? 'authed' : 'guest'))
    return () => {
      active = false
    }
  }, [])

  if (state === 'checking') return <RouteLoading />
  if (state === 'authed') return <Navigate to={useAuthStore.getState().consumeRedirect()} replace />
  return <Outlet />
}
