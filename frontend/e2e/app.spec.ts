import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const backendBaseUrl = 'http://127.0.0.1:18080/api/v1'

test.beforeEach(async ({ page }, testInfo) => {
  const viewport = page.viewportSize()
  expect(testInfo.project.name).toMatch(/desktop|tablet/)
  expect(viewport?.width ?? 0).toBeGreaterThanOrEqual(820)
})

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
  const conversationId = await createConversationForE2E(request, admin, 'E2E RAG 对话', knowledgeBaseId)

  await loginByUi(page, 'admin', 'password123')
  await page.goto(`/chat/${conversationId}`)
  await expect(page.getByTestId('chat-knowledge-base')).toHaveValues([`${knowledgeBaseId}`])
  await page.getByTestId('chat-composer').fill('请基于文档总结退款规则，并补充适用条件。为了保证流式输出时间足够，请详细解释每个要点。')
  await page.getByTestId('chat-send').click()
  const stopButton = page.getByTestId('chat-stop')
  await expect(stopButton).toBeVisible()
  await stopButton.click({ force: true }).catch(() => {
    // A short response may complete between the visibility check and click.
  })
  await expect(stopButton).toBeHidden()
  await expect(page.locator('.stream-status--error')).toBeHidden()
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
  const conversationId = await createConversationForE2E(request, admin, 'E2E Trace 对话', knowledgeBaseId)

  await loginByUi(page, 'admin', 'password123')
  await page.goto(`/chat/${conversationId}`)
  await expect(page.getByTestId('chat-knowledge-base')).toHaveValues([`${knowledgeBaseId}`])
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
  await expect(page.getByRole('heading', { name: '阶段时间线', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '阶段详情', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '关联检索', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '模型调用', exact: true })).toBeVisible()
})

test('E2E-005 文档详情展示解析文本和任务日志', async ({ page, request }) => {
  const admin = await loginByApi(request, 'admin', 'password123')
  const prepared = await prepareReadyKnowledgeBaseWithDocument(request, admin, `文档详情验证库 ${Date.now()}`)

  await loginByUi(page, 'admin', 'password123')
  await page.goto(`/knowledge/${prepared.knowledgeBaseId}`)
  const documentRow = page.locator('[data-testid^="document-row-"]').first()
  await expect(documentRow).toContainText('退款说明')
  const rowTestId = await documentRow.getAttribute('data-testid')
  const documentId = Number(rowTestId?.replace('document-row-', ''))
  await documentRow.click()
  await expect(page).toHaveURL(new RegExp(`/documents/${documentId}$`))
  await expect(page.getByText('解析文本')).toBeVisible()
  await expect(page.getByText('任务日志')).toBeVisible()
  await expect(page.locator('.parsed-text')).toContainText('退款申请需要在 7 日内提交')
  await page.getByRole('tab', { name: '任务日志' }).click()
  await expect(page.getByText('parse · success')).toBeVisible()
  await expect(page.getByRole('button', { name: '重处理' })).toBeVisible()
})

test('E2E-006 引用来源侧栏可打开并跳转文档详情', async ({ page, request }) => {
  const admin = await loginByApi(request, 'admin', 'password123')
  const prepared = await prepareReadyKnowledgeBaseWithDocument(request, admin, `引用来源验证库 ${Date.now()}`)
  const conversationId = await createConversationForE2E(request, admin, 'E2E 引用来源对话', prepared.knowledgeBaseId)

  await loginByUi(page, 'admin', 'password123')
  await page.goto(`/chat/${conversationId}`)
  await expect(page.getByTestId('chat-knowledge-base')).toHaveValues([`${prepared.knowledgeBaseId}`])
  await page.getByTestId('chat-composer').fill('退款规则是什么？')
  await page.getByTestId('chat-send').click()
  await expect(page.getByTestId('chat-stop')).toBeVisible()
  await expect(page.getByTestId('chat-stop')).toBeHidden({ timeout: 15000 })

  const referenceChip = page.getByTestId('chat-reference-chip').first()
  await expect(referenceChip).toBeVisible({ timeout: 15000 })
  await referenceChip.click()
  const referencePanel = page.locator('.reference-panel')
  await expect(referencePanel.getByText('引用来源')).toBeVisible()
  await expect(referencePanel.getByRole('heading', { name: '退款说明' })).toBeVisible()
  await referencePanel.getByRole('link', { name: '查看文档' }).click()
  await expect(page).toHaveURL(/\/documents\/\d+$/)
  await expect(page.getByText('解析文本')).toBeVisible()
})

test('E2E-007 设置页支持字段级校验和保存', async ({ page }) => {
  await loginByUi(page, 'admin', 'password123')
  await page.goto('/settings')

  await page.getByRole('tab', { name: 'RAG' }).click()
  await page.getByTestId('settings-rag-vector-top-k').fill('0')
  await page.getByTestId('settings-save-rag').click()
  await page.getByRole('dialog', { name: '保存 RAG 配置' }).getByRole('button', { name: '确认保存并记录审计' }).click()
  await expect(page.getByTestId('settings-error-vector-top-k')).toBeVisible()

  await page.getByTestId('settings-rag-vector-top-k').fill('12')
  await page.getByTestId('settings-save-rag').click()
  await page.getByRole('dialog', { name: '保存 RAG 配置' }).getByRole('button', { name: '确认保存并记录审计' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'RAG 设置已保存。已记录审计。' })).toBeVisible()

  await page.getByRole('tab', { name: '模型' }).click()
  const apiKeyInput = page.getByTestId('settings-model-api-key')
  await expect(apiKeyInput).toHaveValue('')
  await expect(page.getByText('API Key 已设置')).toBeVisible()
})

async function loginByUi(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('superagent.accessToken'))).not.toBeNull()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('superagent.refreshToken'))).toBeNull()
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

async function createConversationForE2E(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  title: string,
  knowledgeBaseId: number,
) {
  const response = await request.post(`${backendBaseUrl}/conversations`, {
    headers: authHeaders(admin),
    data: {
      title,
      knowledgeBaseId,
      memoryStrategy: 'SLIDING_WINDOW',
    },
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  return json.data.id as number
}

async function prepareReadyKnowledgeBase(
  request: APIRequestContext,
  admin: { accessToken: string; tenantId: number },
  name: string,
) {
  const prepared = await prepareReadyKnowledgeBaseWithDocument(request, admin, name)
  return prepared.knowledgeBaseId
}

async function prepareReadyKnowledgeBaseWithDocument(
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

  const uploadJson = await uploadResponse.json()
  const documentId = uploadJson.data.id as number

  await expect
    .poll(async () => {
      const response = await request.get(`${backendBaseUrl}/knowledge-bases/${knowledgeBaseId}/documents`, {
        headers: authHeaders(admin),
      })
      const json = await response.json()
      return json.data?.items?.find((item: { title: string; status: string }) => item.title === '退款说明')?.status ?? 'missing'
    }, { timeout: 15000 })
    .toBe('ready')

  return {
    knowledgeBaseId,
    documentId,
  }
}

function authHeaders(admin: { accessToken: string; tenantId: number }) {
  return {
    Authorization: `Bearer ${admin.accessToken}`,
    'X-Tenant-Id': `${admin.tenantId}`,
  }
}
