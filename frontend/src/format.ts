const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })

const date = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })

/** 1234.5 → «1234,50 €» */
export function formatEuros(amount: number): string {
  return euros.format(amount)
}

/** «2026-11-20» o un instante ISO → «20 nov 2026» */
export function formatDate(iso: string): string {
  // Una fecha sin hora se interpreta como medianoche local, no UTC, para no cambiar de día
  return date.format(iso.length === 10 ? new Date(`${iso}T00:00:00`) : new Date(iso))
}

const dateTime = new Intl.DateTimeFormat('es-ES', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
})

/** Instante ISO → «31 oct, 12:04» */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

/** Días que faltan desde hoy hasta una fecha «2026-11-20» (negativo si ya pasó). */
export function daysUntil(isoDate: string): number {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((new Date(`${isoDate}T00:00:00`).getTime() - today.getTime()) / 86_400_000)
}

/** Días hasta una fecha, en palabras: «hoy», «mañana», «en 5 días», «hace 3 días». */
export function formatDaysLeft(days: number): string {
  if (days === 0) return 'hoy'
  if (days === 1) return 'mañana'
  if (days === -1) return 'ayer'
  return days > 0 ? `en ${days} días` : `hace ${-days} días`
}

/** Segundos → «1 h 05 min» o «12 min» */
export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(minutes / 60)
  return hours > 0 ? `${hours} h ${String(minutes % 60).padStart(2, '0')} min` : `${minutes} min`
}

const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto' })

/** Instante ISO → «hace 5 minutos», «hace 2 horas», «ayer» */
export function formatTimeAgo(iso: string): string {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60_000)
  if (Math.abs(minutes) < 60) {
    return relative.format(minutes, 'minute')
  }
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) {
    return relative.format(hours, 'hour')
  }
  return relative.format(Math.round(hours / 24), 'day')
}
