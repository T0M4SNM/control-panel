import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import { Link } from 'react-router'
import type { InputHTMLAttributes, TextareaHTMLAttributes } from 'react'

// Kit de componentes compartido por todas las pantallas. Tailwind, sin librerías de UI.

const FIELD_CLASS =
  'w-full rounded-lg border border-slate-300 bg-transparent px-3 py-2 outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/30 disabled:opacity-60 dark:border-slate-700'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const BUTTON_VARIANTS: Record<Variant, string> = {
  primary: 'bg-sky-600 text-white hover:bg-sky-700',
  secondary: 'border border-slate-300 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  ghost: 'text-sky-700 hover:bg-sky-50 dark:text-sky-400 dark:hover:bg-sky-950/40',
}

export function Button({
  variant = 'secondary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      {...props}
      className={`rounded-lg px-3 py-1.5 text-sm font-medium disabled:opacity-60 ${BUTTON_VARIANTS[variant]} ${className}`}
    />
  )
}

/** Enlace con aspecto de botón (un botón dentro de un enlace no es HTML válido). */
export function ButtonLink({ to, variant = 'secondary', children }: { to: string; variant?: Variant; children: ReactNode }) {
  return (
    <Link to={to} className={`inline-block rounded-lg px-3 py-1.5 text-sm font-medium ${BUTTON_VARIANTS[variant]}`}>
      {children}
    </Link>
  )
}

/** Etiqueta + control + ayuda o error. */
export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium">{label}</span>
      {children}
      {hint && <span className="block text-xs text-slate-500">{hint}</span>}
    </label>
  )
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${FIELD_CLASS} ${props.className ?? ''}`} />
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea rows={3} {...props} className={`${FIELD_CLASS} ${props.className ?? ''}`} />
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${FIELD_CLASS} bg-white dark:bg-slate-900 ${props.className ?? ''}`} />
}

export function Checkbox({ label, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <input type="checkbox" {...props} className="size-4 accent-sky-600" />
      {label}
    </label>
  )
}

/** Título de pantalla con acciones a la derecha. */
export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children && <div className="flex flex-wrap gap-2">{children}</div>}
    </div>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={`rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900 ${className}`}
    >
      {children}
    </div>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-slate-500 dark:border-slate-700">
      {children}
    </p>
  )
}

export function ErrorText({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="text-sm text-red-600 dark:text-red-400">
      {children}
    </p>
  )
}

export function Loading() {
  return <p className="text-slate-500">Cargando…</p>
}

type Tone = 'slate' | 'sky' | 'green' | 'amber' | 'red'

const BADGE_TONES: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  sky: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  green: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
  amber: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  red: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
}

export function Badge({ tone = 'slate', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-block shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${BADGE_TONES[tone]}`}>
      {children}
    </span>
  )
}

/**
 * Ventana modal con el elemento nativo {@code <dialog>}: el navegador gestiona el foco, Escape y
 * el fondo. Se cierra con onClose.
 */
export function Modal({
  title,
  open,
  onClose,
  children,
  footer,
}: {
  title: string
  open: boolean
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-xl border border-slate-200 bg-white p-0 text-slate-900 shadow-xl backdrop:bg-slate-950/50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100"
    >
      {open && (
        <div className="flex max-h-[calc(100dvh-4rem)] flex-col">
          <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
            <h2 className="font-semibold">{title}</h2>
            <button onClick={onClose} aria-label="Cerrar" className="text-xl leading-none text-slate-500 hover:text-slate-800 dark:hover:text-slate-200">
              ×
            </button>
          </div>
          <div className="space-y-4 overflow-y-auto px-5 py-4">{children}</div>
          {footer && (
            <div className="flex justify-end gap-2 border-t border-slate-200 px-5 py-3 dark:border-slate-800">{footer}</div>
          )}
        </div>
      )}
    </dialog>
  )
}

/** Pregunta de confirmación para acciones que no se pueden deshacer. */
export function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Borrar',
  busy,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  message: ReactNode
  confirmLabel?: string
  busy?: boolean
  error?: string | null
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Modal
      title={title}
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="danger" onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-sm">{message}</p>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
