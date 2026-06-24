import { expect, test, type Page } from '@playwright/test'

test.beforeEach(async ({ page }, testInfo) => {
  const viewport = page.viewportSize()
  expect(testInfo.project.name).toMatch(/desktop|tablet/)
  expect(viewport?.width ?? 0).toBeGreaterThanOrEqual(820)
})

test('A11Y-001 设置确认框关闭后焦点回到保存按钮', async ({ page }) => {
  await loginByUi(page, 'admin', 'password123')
  await page.goto('/settings?tab=rag')

  const vectorTopKInput = page.getByTestId('settings-rag-vector-top-k')
  const currentTopK = Number(await vectorTopKInput.inputValue()) || 1
  await vectorTopKInput.fill(String(currentTopK === 11 ? 12 : 11))
  const saveButton = page.getByTestId('settings-save-rag')
  await expect(saveButton).toBeEnabled()
  await saveButton.focus()
  await expect(saveButton).toBeFocused()
  await saveButton.press('Enter')

  const dialog = page.getByRole('dialog', { name: '保存 RAG 配置' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '取消' }).press('Enter')
  await expect(dialog).toBeHidden()
  await expect(saveButton).toBeFocused()
})

async function loginByUi(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('superagent.accessToken'))).not.toBeNull()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('superagent.refreshToken'))).toBeNull()
}
