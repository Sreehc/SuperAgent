const SIZES = {
  small: 26,
  medium: 32,
  large: 40,
} as const

export function BrandLogo({ size = 'medium' }: { size?: keyof typeof SIZES }) {
  const px = SIZES[size]

  return (
    <span className="brand-logo" aria-hidden="true">
      <svg
        data-testid="brand-logo"
        width={px}
        height={px}
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="brandLogoPanel" x1="4" y1="3" x2="27" y2="29" gradientUnits="userSpaceOnUse">
            <stop stopColor="var(--accent)" />
            <stop offset="1" stopColor="var(--accent-strong)" />
          </linearGradient>
        </defs>

        <rect x="2" y="2" width="28" height="28" rx="9" fill="url(#brandLogoPanel)" />

        <rect x="12.2" y="12.2" width="7.6" height="7.6" rx="2.8" fill="var(--accent-strong)" data-testid="brand-logo-core" />

        <rect x="8" y="8" width="4.8" height="4.8" rx="1.8" fill="#F1FFF8" data-testid="brand-logo-tile" />
        <rect x="19.2" y="8" width="4.8" height="4.8" rx="1.8" fill="#F4A77D" data-testid="brand-logo-tile" />
        <rect x="8" y="19.2" width="4.8" height="4.8" rx="1.8" fill="#F4A77D" data-testid="brand-logo-tile" />
        <rect x="19.2" y="19.2" width="4.8" height="4.8" rx="1.8" fill="#F1FFF8" data-testid="brand-logo-tile" />

        <path d="M12.9 11.3h6.2" stroke="#DDF8EC" strokeWidth="1.4" strokeLinecap="round" />
        <path d="M11.3 12.9v6.2" stroke="#DDF8EC" strokeWidth="1.4" strokeLinecap="round" />
        <path d="M20.7 12.9v6.2" stroke="#DDF8EC" strokeWidth="1.4" strokeLinecap="round" />
        <path d="M12.9 20.7h6.2" stroke="#DDF8EC" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
    </span>
  )
}
