import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const routerSource = readFileSync(`${process.cwd()}/src/app/router.tsx`, 'utf8')
const viteConfigSource = readFileSync(`${process.cwd()}/vite.config.ts`, 'utf8')

describe('router splitting strategy', () => {
  it('lazy-loads route pages instead of statically importing feature screens', () => {
    expect(routerSource).toContain('lazy(() => import(')
    expect(routerSource).toContain('<Suspense fallback={<RouteLoading />}>')
    expect(routerSource).not.toMatch(/import\s+\{\s*\w+Page\s*\}\s+from\s+'@\/features\/.+\/pages\//)
  })

  it('configures manual vendor chunk splitting for heavy dependency groups', () => {
    expect(viteConfigSource).toContain('manualChunks(id)')
    expect(viteConfigSource).toContain("if (id.includes('/recharts/')) return 'charts-vendor'")
    expect(viteConfigSource).toContain("if (id.includes('/marked/') || id.includes('/highlight.js/') || id.includes('/dompurify/'))")
    expect(viteConfigSource).toContain("if (id.includes('/@radix-ui/') || id.includes('/lucide-react/') || id.includes('/@phosphor-icons/'))")
  })
})
