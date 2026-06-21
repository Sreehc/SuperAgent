import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const backendBaseUrl = 'http://127.0.0.1:18080/api/v1'
const visualDatasetKey = 'sa-visual-release'
const visualKnowledgeBaseName = `截图基线库 ${visualDatasetKey}`
const visualConversationTitle = `截图基线会话 ${visualDatasetKey}`

test.describe('@visual core page baselines', () => {
  test.beforeEach(async ({ page }) => {
    await stabilizePage(page)
  })

  test('login page baseline @visual', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByTestId('login-submit')).toBeVisible()
    await expectPageScreenshot(page, 'login-default')
  })

  test('chat, document, trace, and admin console baselines @visual', async ({ page, request }, testInfo) => {
    const admin = await loginByApi(request, 'admin', 'password123')
    const prepared = await prepareVisualDataset(request, admin)
    const conversationId = await createVisualConversation(request, admin, prepared.knowledgeBaseId)

    await loginByUi(page, 'admin', 'password123')

    await page.goto(`/chat/${conversationId}`)
    if (testInfo.project.name === 'visual-mobile') {
      await expect(page.getByRole('heading', { name: visualConversationTitle, exact: true })).toBeVisible()
      await stabilizeChatVisualState(page, prepared.knowledgeBaseName)
      await expectPageScreenshot(page, 'chat-mobile-home')
    } else {
      await expect(page.getByRole('heading', { name: visualConversationTitle, exact: true })).toBeVisible()
      await stabilizeChatVisualState(page, prepared.knowledgeBaseName)
      await expectPageScreenshot(page, 'chat-session')
    }

    await page.goto('/knowledge')
    await expect(page.locator(`[data-testid="knowledge-row-${prepared.knowledgeBaseId}"]`)).toBeVisible()
    await stabilizeKnowledgeListVisualState(page, prepared.knowledgeBaseId)
    await expectPageScreenshot(page, 'knowledge-list')

    await page.goto(`/knowledge/${prepared.knowledgeBaseId}`)
    await expect(page.locator(`[data-testid="document-row-${prepared.documentId}"]`)).toBeVisible()
    await expectPageScreenshot(page, 'knowledge-detail')

    await page.goto(`/documents/${prepared.documentId}`)
    await expect(page.locator('.parsed-text')).toContainText('退款申请需要在 7 日内提交')
    await expectPageScreenshot(page, 'document-detail')

    await page.goto(`/traces?sessionId=${conversationId}`)
    await page.getByTestId('trace-refresh').click()
    await expect(page.getByTestId('trace-refresh')).not.toHaveClass(/btn-loading/)
    const traceRow = page.locator('[data-testid^="trace-row-"]').first()
    await expect(page.getByRole('heading', { name: 'Trace', exact: true })).toBeVisible()
    await stabilizeTraceListVisualState(page, conversationId)
    await expectPageScreenshot(page, 'trace-list')

    if (testInfo.project.name !== 'visual-mobile' && await traceRow.isVisible()) {
      await traceRow.click()
      await expect(page.getByRole('heading', { name: '阶段时间线', exact: true })).toBeVisible()
      await expectPageScreenshot(page, 'trace-detail')
    }

    await page.goto('/tools')
    await expect(page.getByRole('heading', { name: 'Tools', exact: true })).toBeVisible()
    await expectPageScreenshot(page, 'tools-console')

    await page.goto('/evals')
    await expect(page.getByRole('heading', { name: '评测', exact: true })).toBeVisible()
    await expectPageScreenshot(page, 'evals-console')

    await page.goto('/settings?tab=rag')
    await expect(page.getByRole('tab', { name: 'RAG' })).toHaveAttribute('aria-selected', 'true')
    await expectPageScreenshot(page, 'settings-rag')

    await page.goto('/members')
    await expect(page.getByRole('heading', { name: '成员', exact: true })).toBeVisible()
    await expectPageScreenshot(page, 'members-console')
  })
})

async function stabilizePage(page: Page) {
  await page.addInitScript(() => {
    const style = document.createElement('style')
    style.setAttribute('data-testid', 'visual-stabilizer')
    style.textContent = `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        caret-color: transparent !important;
      }

      .toast-region,
      .typing-cursor {
        display: none !important;
      }
    `
    document.documentElement.appendChild(style)
  })
}

async function stabilizeChatVisualState(page: Page, knowledgeBaseName: string) {
  await page.getByTestId('chat-knowledge-base').evaluate((select, selectedName) => {
    if (!(select instanceof HTMLSelectElement)) return
    Array.from(select.options).forEach((option) => {
      const keepOption = option.value === '' || option.textContent?.trim() === selectedName
      option.hidden = !keepOption
    })
    select.size = Math.min(3, select.options.length)
  }, knowledgeBaseName)
  await page.locator('.conversation-list').evaluate((list) => {
    Array.from(list.children).forEach((child, index) => {
      if (child instanceof HTMLElement && index > 0) {
        child.style.display = 'none'
      }
    })
  }).catch(() => {})
}

async function stabilizeKnowledgeListVisualState(page: Page, knowledgeBaseId: number) {
  await page.locator('tbody').evaluate((body, targetId) => {
    Array.from(body.children).forEach((row) => {
      if (!(row instanceof HTMLElement)) return
      row.style.display = row.dataset.testid === `knowledge-row-${targetId}` ? '' : 'none'
    })
  }, knowledgeBaseId)
}

async function stabilizeTraceListVisualState(page: Page, sessionId: number) {
  await page.locator('tbody').evaluate((body, targetSessionId) => {
    Array.from(body.children).forEach((row) => {
      if (!(row instanceof HTMLElement)) return
      const cells = Array.from(row.querySelectorAll('td'))
      const sessionCellText = cells[1]?.textContent?.trim()
      row.style.display = sessionCellText === String(targetSessionId) ? '' : 'none'
    })
  }, sessionId)
}

async function expectPageScreenshot(page: Page, name: string) {
  await page.locator('body').evaluate(() => document.fonts?.ready)
  await expect(page).toHaveScreenshot(`${name}.png`, {
    animations: 'disabled',
    fullPage: true,
    maxDiffPixelRatio: 0.01,
    maxDiffPixels: page.viewportSize()?.width && page.viewportSize()!.width < 760 ? 800 : 1200,
  })
}

async function loginByUi(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('superagent.accessToken'))).not.toBeNull()
}

async function loginByApi(request: APIRequestContext, username: string, password: string) {
  const response = await request.post(`${backendBaseUrl}/auth/login`, {
    data: {
      username,
      password,
    },
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  return {
    accessToken: json.data.accessToken as string,
    tenantId: json.data.defaultTenant.id as number,
  }
}

async function createVisualConversation(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  knowledgeBaseId: number,
) {
  const existing = await findConversationByTitle(request, admin, visualConversationTitle)
  if (existing) {
    await request.patch(`${backendBaseUrl}/conversations/${existing.id}`, {
      headers: authHeaders(admin),
      data: {
        title: visualConversationTitle,
        knowledgeBaseId,
        memoryStrategy: 'SLIDING_WINDOW',
        status: 'active',
      },
    })
    return existing.id
  }

  const createResponse = await request.post(`${backendBaseUrl}/conversations`, {
    headers: authHeaders(admin),
    data: {
      title: visualConversationTitle,
      knowledgeBaseId,
      memoryStrategy: 'SLIDING_WINDOW',
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createJson = await createResponse.json()
  return createJson.data.id as number
}

async function prepareVisualDataset(request: APIRequestContext, admin: { accessToken: string; tenantId: number }) {
  const existing = await findKnowledgeBaseByName(request, admin, visualKnowledgeBaseName)
  const knowledgeBaseId = existing?.id ?? await createPublishedKnowledgeBase(request, admin, visualKnowledgeBaseName)
  const documentId = await ensureReadyDocument(request, admin, knowledgeBaseId)

  return {
    knowledgeBaseName: visualKnowledgeBaseName,
    knowledgeBaseId,
    documentId,
  }
}

async function findKnowledgeBaseByName(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  name: string,
) {
  const response = await request.get(`${backendBaseUrl}/knowledge-bases?pageSize=100&keyword=${encodeURIComponent(name)}`, {
    headers: authHeaders(admin),
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  return (json.data?.items as Array<{ id: number; name: string }> | undefined)?.find((item) => item.name === name) ?? null
}

async function findConversationByTitle(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  title: string,
) {
  const response = await request.get(`${backendBaseUrl}/conversations?pageSize=100&keyword=${encodeURIComponent(title)}`, {
    headers: authHeaders(admin),
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  return (json.data?.items as Array<{ id: number; title: string }> | undefined)?.find((item) => item.title === title) ?? null
}

async function createPublishedKnowledgeBase(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  name: string,
) {
  const createResponse = await request.post(`${backendBaseUrl}/knowledge-bases`, {
    headers: authHeaders(admin),
    data: {
      name,
      visibility: 'tenant',
    },
  })
  expect(createResponse.ok()).toBeTruthy()
  const createJson = await createResponse.json()
  const knowledgeBaseId = createJson.data.id as number

  const publishResponse = await request.patch(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}`, {
    headers: authHeaders(admin),
    data: {
      status: 'published',
    },
  })
  expect(publishResponse.ok()).toBeTruthy()
  return knowledgeBaseId
}

async function uploadReadyDocument(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  knowledgeBaseId: number,
) {
  const content = await readFile(new URL('./fixtures/refund-guide.txt', import.meta.url))
  const uploadResponse = await request.post(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}/documents`, {
    headers: authHeaders(admin),
    multipart: {
      title: '退款说明',
      file: {
        name: 'refund-guide.txt',
        mimeType: 'text/plain',
        buffer: content,
      },
    },
  })
  expect(uploadResponse.ok()).toBeTruthy()

  const uploadJson = await uploadResponse.json()
  const documentId = uploadJson.data.id as number

  await expect
    .poll(async () => {
      const response = await request.get(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}/documents`, {
        headers: authHeaders(admin),
      })
      const json = await response.json()
      return json.data?.items?.find((item: { id: number; status: string }) => item.id === documentId)?.status ?? 'missing'
    }, { timeout: 15000 })
    .toBe('ready')

  return documentId
}

async function ensureReadyDocument(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  knowledgeBaseId: number,
) {
  const response = await request.get(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}/documents?pageSize=100&keyword=${encodeURIComponent('退款说明')}`, {
    headers: authHeaders(admin),
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  const existing = (json.data?.items as Array<{ id: number; title: string; status: string }> | undefined)?.find((item) => (
    item.title === '退款说明' && item.status === 'ready'
  ))
  if (existing) return existing.id

  return uploadReadyDocument(request, admin, knowledgeBaseId)
}

function authHeaders(admin: { accessToken: string; tenantId: number }) {
  return {
    Authorization: `Bearer ${admin.accessToken}`,
    'X-Tenant-Id': `${admin.tenantId}`,
  }
}
