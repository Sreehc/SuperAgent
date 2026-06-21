import { describe, expect, test } from 'vitest'
import packageJson from '../../package.json' with { type: 'json' }
import playwrightConfig from '../../playwright.config'

describe('visual regression configuration', () => {
  test('keeps visual screenshots isolated from the default e2e command', () => {
    expect(packageJson.scripts.e2e).toBe('playwright test --project=desktop --project=tablet --grep-invert @visual')
    expect(packageJson.scripts['test:visual']).toBe(
      'playwright test --project=visual-desktop --project=visual-tablet --project=visual-mobile --grep @visual',
    )
  })

  test('defines desktop, tablet, and mobile visual projects', () => {
    const projectNames = playwrightConfig.projects?.map((project) => project.name) ?? []

    expect(projectNames).toEqual(
      expect.arrayContaining(['desktop', 'tablet', 'visual-desktop', 'visual-tablet', 'visual-mobile']),
    )
  })
})
