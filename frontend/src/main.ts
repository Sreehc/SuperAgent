import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { createRouter } from './app/router'
import App from './app/App.vue'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './features/auth/store/auth'
import './styles.css'

document.title = import.meta.env.VITE_APP_NAME || 'SuperAgent'

const app = createApp(App)
const pinia = createPinia()
const router = createRouter(pinia)

app.use(pinia)
app.use(router)

const authStore = useAuthStore(pinia)
authStore.hydrate()

setUnauthorizedHandler(() => {
  authStore.handleUnauthorized(router.currentRoute.value.fullPath)
})

app.mount('#app')
