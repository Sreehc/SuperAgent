export function BrandLogo({ size = 'medium' }: { size?: 'small' | 'medium' | 'large' }) {
  const px = size === 'small' ? 26 : size === 'large' ? 40 : 32
  return (
    <span className="brand-logo" aria-hidden="true">
      <svg width={px} height={px} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="2" y="2" width="28" height="28" rx="8" fill="var(--accent)" />
        <path
          d="M10 20.5c1.4 1.2 3.3 1.9 5.4 1.9 3.2 0 5.1-1.5 5.1-3.7 0-2-1.4-3-4.3-3.7l-1.7-.4c-1.4-.3-2-.8-2-1.6 0-.9.9-1.5 2.3-1.5 1.5 0 2.7.6 3.6 1.5l1.9-2c-1.3-1.3-3.1-2-5.4-2-3 0-5 1.6-5 3.9 0 2 1.4 3 4 3.6l1.7.4c1.6.4 2.3.8 2.3 1.7 0 1-1 1.6-2.6 1.6-1.7 0-3.1-.7-4.2-1.7L10 20.5Z"
          fill="#fff"
        />
      </svg>
    </span>
  )
}
