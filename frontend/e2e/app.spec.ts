import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const backendBaseUrl = 'http://127.0.0.1:18080/api/v1'

test('member is redirected to login and blocked from settings', async ({ page }) => {
  await page.goto('/settings')
  await expect(page).toHaveURL(/\/login$/)

  await loginByUi(page, 'member', 'password123')
  await page.goto('/settings')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByText('当前租户角色无权访问这个页面')).toBeVisible()
})

test('admin can open settings, upload a document, start a knowledge conversation, and inspect trace', async ({ page, request }) => {
  const apiSession = await loginByApi(request, 'admin', 'password123')

  await loginByUi(page, 'admin', 'password123')
  await page.goto('/settings')
  await expect(page.getByText('运行时设置')).toBeVisible()

  await page.goto('/knowledge')
  const knowledgeBaseName = `E2E 知识库 ${Date.now()}`
  await page.getByTestId('knowledge-create-name').fill(knowledgeBaseName)
  await page.getByTestId('knowledge-create-submit').click()
  await expect(page).toHaveURL(/\/knowledge\/\d+$/)

  const knowledgeBaseId = Number(page.url().split('/').pop())
  expect(knowledgeBaseId).toBeGreaterThan(0)

  await page.getByTestId('document-upload-file').setInputFiles('./e2e/fixtures/refund-guide.txt')
  await page.getByTestId('document-upload-submit').click()
  await expect(page.getByText('refund-guide.txt')).toBeVisible()

  const conversationResponse = await request.post(`${backendBaseUrl}/conversations`, {
    headers: {
      Authorization: `Bearer ${apiSession.accessToken}`,
      'X-Tenant-Id': `${apiSession.tenantId}`,
    },
    data: {
      title: 'E2E 知识对话',
      memoryStrategy: 'SLIDING_WINDOW',
      knowledgeBaseId,
    },
  })
  expect(conversationResponse.ok()).toBeTruthy()
  const conversationJson = await conversationResponse.json()
  const sessionId = conversationJson.data.id as number

  await page.goto(`/chat/${sessionId}`)
  await page.getByTestId('chat-composer').fill('请总结退款规则')
  await page.getByTestId('chat-send').click()
  await expect(page.getByTestId('chat-stop')).toBeVisible()
  await page.getByTestId('chat-stop').click()

  await page.goto('/traces')
  await page.getByTestId('trace-refresh').click()
  const traceRow = page.locator('[data-testid^="trace-row-"]').first()
  await expect(traceRow).toBeVisible()
  await traceRow.click()
  await expect(page).toHaveURL(/\/traces\/\d+$/)
  await expect(page.getByText(/Trace #/)).toBeVisible()
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
