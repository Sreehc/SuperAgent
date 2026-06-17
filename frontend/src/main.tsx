import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { router } from './app/router'
import { AppProviders } from './app/providers'
import { setRefreshHandler, setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './features/auth/store/auth'
import { initTheme } from './components/ThemeToggle'
import './tailwind.css'
import 'highlight.js/styles/github-dark.css'
import './styles.css'

document.title = import.meta.env.VITE_APP_NAME || 'SuperAgent'
initTheme()

const auth = useAuthStore.getState()
auth.hydrate()

setUnauthorizedHandler(() => {
  useAuthStore.getState().handleUnauthorized(window.location.pathname + window.location.search)
})
setRefreshHandler(() => useAuthStore.getState().refresh())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
)
