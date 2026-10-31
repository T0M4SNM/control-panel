import { useState, type PointerEvent } from 'react'
import type { VisitPoint } from '../api/visits'

const HEIGHT = 160
const PADDING_TOP = 8

const time = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' })
const day = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' })
const dayTime = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/**
 * Visitantes por intervalo: línea con área suave, una sola serie (el título de la sección la nombra).
 * Al pasar el ratón o tocar, una línea vertical marca el punto y se ven visitantes y páginas vistas.
 */
export default function VisitsChart({ points, stepSeconds }: { points: VisitPoint[]; stepSeconds: number }) {
  const [active, setActive] = useState<number | null>(null)
  const daily = stepSeconds >= 86_400
  const max = niceMax(Math.max(...points.map((p) => p.visitors)))
  const plot = HEIGHT - PADDING_TOP
  const x = (i: number) => (points.length === 1 ? 50 : (i / (points.length - 1)) * 100)
  const y = (v: number) => PADDING_TOP + plot - (v / max) * plot
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(p.visitors)}`).join(' ')
  const area = `${line} L${x(points.length - 1)},${HEIGHT} L${x(0)},${HEIGHT} Z`
  const hovered = active !== null ? points[active] : null
  const label = (p: VisitPoint) => (daily ? day : stepSeconds >= 3_600 && points.length > 30 ? dayTime : time).format(new Date(p.start))
  const ticks = [0, Math.floor((points.length - 1) / 2), points.length - 1]

  function pick(e: PointerEvent<SVGSVGElement>) {
    const box = e.currentTarget.getBoundingClientRect()
    const ratio = Math.min(1, Math.max(0, (e.clientX - box.left) / box.width))
    setActive(Math.round(ratio * (points.length - 1)))
  }

  return (
    <div className="relative">
      <div className="flex">
        <div className="flex w-8 shrink-0 flex-col justify-between pr-1 text-right text-xs text-slate-500 tabular-nums" style={{ height: '10rem' }}>
          <span>{max}</span>
          <span>0</span>
        </div>
        <svg
          viewBox={`0 0 100 ${HEIGHT}`}
          preserveAspectRatio="none"
          className="h-40 w-full touch-none"
          role="img"
          aria-label={`Visitantes por ${daily ? 'día' : 'intervalo'}, máximo ${max}`}
          onPointerMove={pick}
          onPointerDown={pick}
          onPointerLeave={() => setActive(null)}
        >
          {[0, 0.5, 1].map((f) => (
            <line key={f} x1="0" x2="100" y1={y(max * f)} y2={y(max * f)} stroke="var(--chart-grid)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
          ))}
          <path d={area} fill="var(--chart-series)" opacity="0.12" />
          <path d={line} fill="none" stroke="var(--chart-series)" strokeWidth="2" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
          {active !== null && (
            <line x1={x(active)} x2={x(active)} y1={PADDING_TOP} y2={HEIGHT} stroke="currentColor" strokeOpacity="0.35" strokeWidth="1" vectorEffect="non-scaling-stroke" />
          )}
        </svg>
      </div>
      {/* Punto activo fuera del SVG para que no se deforme con preserveAspectRatio="none" */}
      {hovered && (
        <span
          className="pointer-events-none absolute size-2.5 -translate-1/2 rounded-full border-2 border-white bg-[var(--chart-series)] dark:border-slate-900"
          style={{ left: `calc(2rem + (100% - 2rem) * ${x(active!) / 100})`, top: `${(y(hovered.visitors) / HEIGHT) * 10}rem` }}
        />
      )}
      <div className="mt-1 ml-8 flex justify-between text-xs text-slate-500">
        {ticks.map((i) => (
          <span key={i}>{points[i] && label(points[i])}</span>
        ))}
      </div>
      {hovered && (
        <div
          className="pointer-events-none absolute -top-2 z-10 w-40 rounded-lg border border-slate-200 bg-white p-2.5 text-xs shadow-lg dark:border-slate-700 dark:bg-slate-900"
          // Al lado de la línea vertical, no encima, para no tapar el punto
          style={{
            left:
              x(active!) < 60
                ? `calc(2rem + (100% - 2rem) * ${x(active!) / 100} + 0.75rem)`
                : `calc(2rem + (100% - 2rem) * ${x(active!) / 100} - 10.75rem)`,
          }}
        >
          <p className="mb-1 font-semibold">{(daily ? day : dayTime).format(new Date(hovered.start))}</p>
          <p className="flex justify-between">
            <span className="text-slate-500">Visitantes</span>
            <span className="tabular-nums">{hovered.visitors}</span>
          </p>
          <p className="flex justify-between">
            <span className="text-slate-500">Páginas vistas</span>
            <span className="tabular-nums">{hovered.views}</span>
          </p>
        </div>
      )}
    </div>
  )
}

/** Máximo redondo del eje (1, 2, 5, 10, 20, 50...), para que la escala se lea de un vistazo. */
function niceMax(value: number): number {
  if (value <= 1) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  for (const step of [1, 2, 5, 10]) {
    if (value <= step * power) return step * power
  }
  return 10 * power
}
