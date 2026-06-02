import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const backendBaseUrl = 'http://127.0.0.1:18080/api/v1'

test('E2E-001 登录和路由守卫', async ({ page }) => {
  await page.goto('/chat')
  await expect(page).toHaveURL(/\/login$/)

  await loginByUi(page, 'member', 'password123')
  await page.goto('/settings')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByText('当前租户角色无权访问这个页面')).toBeVisible()
})

test('E2E-002 发起 RAG 对话并停止生成', async ({ page, request }) => {
  const admin = await loginByApi(request, 'admin', 'password123')
  const knowledgeBaseId = await prepareReadyKnowledgeBase(request, admin, `RAG 对话库 ${Date.now()}`)

  await loginByUi(page, 'admin', 'password123')
  await page.goto('/chat')
  await page.getByTestId('chat-new-conversation').click()
  await page.getByTestId('chat-knowledge-base').selectOption(`${knowledgeBaseId}`)
  await page.getByTestId('chat-composer').fill('请基于文档总结退款规则，并补充适用条件。为了保证流式输出时间足够，请详细解释每个要点。')
  await page.getByTestId('chat-send').click()
  await expect(page.getByTestId('chat-stop')).toBeVisible()
  await page.getByTestId('chat-stop').click()
  await expect(page.getByTestId('chat-stop')).toBeHidden()
  await expect(page).toHaveURL(/\/chat\/\d+$/)
})

test('E2E-003 上传文档', async ({ page, request }) => {
  const admin = await loginByApi(request, 'admin', 'password123')
  const knowledgeBaseId = await createPublishedKnowledgeBase(request, admin, `上传验证库 ${Date.now()}`)

  await loginByUi(page, 'admin', 'password123')
  await page.goto(`/knowledge/${knowledgeBaseId}`)
  await page.getByTestId('document-upload-file').setInputFiles('./e2e/fixtures/refund-guide.txt')
  await page.getByTestId('document-upload-submit').click()

  const documentRow = page.locator('[data-testid^="document-row-"]').first()
  await expect(documentRow).toBeVisible()
  await expect(documentRow).toContainText('refund-guide.txt')
  await expect(documentRow).toContainText('ready')
})

test('E2E-004 查看 Trace', async ({ page, request }) => {
  const admin = await loginByApi(request, 'admin', 'password123')
  const knowledgeBaseId = await prepareReadyKnowledgeBase(request, admin, `Trace 验证库 ${Date.now()}`)

  await loginByUi(page, 'admin', 'password123')
  await page.goto('/chat')
  await page.getByTestId('chat-new-conversation').click()
  await page.getByTestId('chat-knowledge-base').selectOption(`${knowledgeBaseId}`)
  await page.getByTestId('chat-composer').fill('退款规则是什么？')
  await page.getByTestId('chat-send').click()
  await expect(page.getByTestId('chat-stop')).toBeVisible()
  await expect(page.getByTestId('chat-stop')).toBeHidden()

  await page.goto('/traces')
  await page.getByTestId('trace-refresh').click()
  const traceRow = page.locator('[data-testid^="trace-row-"]').first()
  await expect(traceRow).toBeVisible()
  await traceRow.click()
  await expect(page).toHaveURL(/\/traces\/\d+$/)
  await expect(page.getByText('阶段时间线')).toBeVisible()
  await expect(page.getByText('检索结果')).toBeVisible()
  await expect(page.getByText('模型调用')).toBeVisible()
})

async function loginByUi(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await page.waitForLoadState('networkidle')
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

async function prepareReadyKnowledgeBase(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  name: string,
) {
  const knowledgeBaseId = await createPublishedKnowledgeBase(request, admin, name)
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

  await expect
    .poll(async () => {
      const response = await request.get(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}/documents`, {
        headers: authHeaders(admin),
      })
      const json = await response.json()
      return json.data.items[0]?.status ?? 'missing'
    }, { timeout: 15000 })
    .toBe('ready')

  return knowledgeBaseId
}

function authHeaders(admin: { accessToken: string; tenantId: number }) {
  return {
    Authorization: `Bearer ${admin.accessToken}`,
    'X-Tenant-Id': `${admin.tenantId}`,
  }
}
