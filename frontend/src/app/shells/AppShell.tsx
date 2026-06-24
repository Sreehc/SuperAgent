import { useState, type ChangeEvent } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Command, MagnifyingGlass, SidebarSimple, SignOut, X } from '@phosphor-icons/react'
import { BrandLogo } from '@/components/BrandLogo'
import { ThemeToggle } from '@/components/ThemeToggle'
import { selectCurrentRole, useAuthStore } from '@/features/auth/store/auth'
import { SelectField } from '@/shared/ui'
import { NAV_ITEMS } from '../nav'

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const role = useAuthStore(selectCurrentRole)
  const tenants = useAuthStore((s) => s.tenants)
  const currentTenantId = useAuthStore((s) => s.currentTenantId)
  const user = useAuthStore((s) => s.user)
  const [navOpen, setNavOpen] = useState(false)
  const [switching, setSwitching] = useState(false)

  const visibleItems = NAV_ITEMS.filter((item) => role && item.roles.includes(role))
  const isActive = (path: string) => location.pathname === path || location.pathname.startsWith(`${path}/`)

  function openCommand() {
    document.dispatchEvent(new CustomEvent('open-command-palette'))
    setNavOpen(false)
  }

  async function onSwitchTenant(event: ChangeEvent<HTMLSelectElement>) {
    const tenantId = Number(event.target.value)
    if (!tenantId || tenantId === currentTenantId) return
    setSwitching(true)
    try {
      await useAuthStore.getState().switchTenant(tenantId)
    } finally {
      setSwitching(false)
    }
  }

  async function logout() {
    await useAuthStore.getState().logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="console-shell">
      {navOpen ? <button className="console-shell__overlay" type="button" aria-label="关闭导航遮罩" onClick={() => setNavOpen(false)} /> : null}

      <aside className={`global-rail${navOpen ? ' global-rail--expanded' : ''}`} aria-label="主导航" data-testid="desktop-nav">
        <div className="global-rail__head">
          <NavLink className="global-rail__mark" to="/chat" aria-label="SuperAgent 对话" onClick={() => setNavOpen(false)}>
            <BrandLogo size="small" />
            <span className="global-rail__brand-label">SuperAgent</span>
          </NavLink>
          {navOpen ? (
            <button className="icon-button global-rail__collapse" type="button" aria-label="收起导航" onClick={() => setNavOpen(false)}>
              <X size={16} aria-hidden="true" />
            </button>
          ) : null}
        </div>

        <nav className="global-rail__nav" aria-label="主导航">
          {visibleItems.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={`global-rail__item${isActive(item.to) ? ' global-rail__item--active' : ''}`}
                aria-label={item.label}
                title={item.label}
                onClick={() => setNavOpen(false)}
              >
                <Icon size={20} weight={isActive(item.to) ? 'fill' : 'regular'} aria-hidden="true" />
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>

        <div className="global-rail__tools">
          <button className="global-rail__item" type="button" aria-label="打开命令面板" title="命令面板 ⌘K" onClick={openCommand}>
            <Command size={20} aria-hidden="true" />
            <span>命令</span>
          </button>
          <ThemeToggle className="global-rail__item global-rail__theme-toggle" />
        </div>
      </aside>

      <section className="console-workspace">
        <header className="utility-bar">
          <div className="utility-bar__left">
            <button
              className="utility-bar__menu icon-button"
              type="button"
              aria-label={navOpen ? '关闭导航' : '打开导航'}
              aria-expanded={navOpen}
              onClick={() => setNavOpen((open) => !open)}
            >
              {navOpen ? <X size={18} aria-hidden="true" /> : <SidebarSimple size={18} aria-hidden="true" />}
            </button>
            <div className="tenant-chip">
              <span>Tenant</span>
              <SelectField value={currentTenantId ?? ''} disabled={switching} onChange={onSwitchTenant}>
                {tenants.map((tenant) => (
                  <option key={tenant.id} value={tenant.id}>
                    {tenant.name} / {tenant.role}
                  </option>
                ))}
              </SelectField>
            </div>
            <span className="role-chip">{role ?? '未加载'}</span>
          </div>

          <button className="command-trigger" type="button" onClick={openCommand}>
            <MagnifyingGlass size={16} aria-hidden="true" />
            <span>搜索页面或操作</span>
            <kbd>⌘K</kbd>
          </button>

          <div className="utility-bar__right">
            <div className="identity-block">
              <strong>{user?.displayName || user?.username || 'User'}</strong>
              <span>{user?.username || 'unknown'}</span>
            </div>
            <button className="btn btn-ghost btn-sm" type="button" onClick={logout}>
              <SignOut size={15} aria-hidden="true" />
              退出
            </button>
          </div>
        </header>

        <main className="console-content">
          <Outlet />
        </main>
      </section>
    </div>
  )
}
