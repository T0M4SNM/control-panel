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
