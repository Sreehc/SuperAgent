import { cn } from '@/lib/cn'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'accent'

const TONE_CLASS: Record<Tone, string> = {
  neutral: 'badge',
  success: 'badge badge--success',
  warning: 'badge badge--warning',
  danger: 'badge badge--danger',
  accent: 'badge badge--accent',
}

export function Badge({
  tone = 'neutral',
  className,
  children,
}: {
  tone?: Tone
  className?: string
  children: React.ReactNode
}) {
  return <span className={cn(TONE_CLASS[tone], className)}>{children}</span>
}
