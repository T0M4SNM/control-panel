import { useState } from 'react'
import type { FinanceSummary } from '../api/finance'
import { formatEuros } from '../format'

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

const HEIGHT = 180
const PADDING_TOP = 12
const PADDING_BOTTOM = 22

/**
 * Beneficio de cada mes como barra desde la línea del cero: azul si gana, rojo si pierde. Los meses
 * futuros (previsión) van atenuados. Al pasar el ratón o tocar una barra se ve el desglose.
 */
export default function ProfitChart({ months }: { months: FinanceSummary['months'] }) {
  const [active, setActive] = useState<number | null>(null)
  const max = Math.max(1, ...months.map((m) => Math.abs(m.profit)))
  const hasNegative = months.some((m) => m.profit < 0)
  const hasPositive = months.some((m) => m.profit > 0)
  // Si todo es positivo, el cero va abajo; si hay de los dos, en medio en proporción
  const negMax = hasNegative ? Math.max(...months.map((m) => Math.max(0, -m.profit))) : 0
  const posMax = hasPositive ? Math.max(...months.map((m) => Math.max(0, m.profit))) : 0
  const span = Math.max(1, posMax + negMax)
  const plotHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM
  const zeroY = PADDING_TOP + (posMax / span) * plotHeight
  const slot = 100 / months.length
  const hovered = active !== null ? months[active] : null

  return (
    <div className="relative">
      <svg viewBox={`0 0 100 ${HEIGHT}`} preserveAspectRatio="none" className="h-48 w-full" role="img" aria-label="Beneficio por mes">
        <line x1="0" x2="100" y1={zeroY} y2={zeroY} stroke="var(--chart-grid)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
        {months.map((m, i) => {
          const height = (Math.abs(m.profit) / span) * plotHeight
          const positive = m.profit >= 0
          const y = positive ? zeroY - height : zeroY
          return (
            <g key={m.month}>
              {/* Zona de toque más grande que la barra */}
              <rect
                x={i * slot}
                y={0}
                width={slot}
                height={HEIGHT}
                fill="transparent"
                onMouseEnter={() => setActive(i)}
                onMouseLeave={() => setActive(null)}
                onClick={() => setActive(active === i ? null : i)}
              />
              <rect
                x={i * slot + slot * 0.2}
                y={y}
                width={slot * 0.6}
                height={Math.max(height, m.profit === 0 ? 0 : 1)}
                rx="0.6"
                fill={positive ? 'var(--chart-positive)' : 'var(--chart-negative)'}
                opacity={m.projected ? 0.4 : active === null || active === i ? 1 : 0.6}
                pointerEvents="none"
              />
            </g>
          )
        })}
      </svg>
      <div className="mt-1 grid text-center text-xs text-slate-500" style={{ gridTemplateColumns: `repeat(${months.length}, 1fr)` }}>
        {months.map((m) => (
          <span key={m.month}>{MONTHS[Number(m.month.slice(5, 7)) - 1]}</span>
        ))}
      </div>
      {hovered && (
        <div
          className="pointer-events-none absolute top-0 z-10 w-52 rounded-lg border border-slate-200 bg-white p-3 text-xs shadow-lg dark:border-slate-700 dark:bg-slate-900"
          style={{ left: `clamp(0px, calc(${(active! + 0.5) * slot}% - 6.5rem), calc(100% - 13rem))` }}
        >
          <p className="mb-1 font-semibold">
            {MONTHS[Number(hovered.month.slice(5, 7)) - 1]} {hovered.month.slice(0, 4)}
            {hovered.projected && ' · previsión'}
          </p>
          <Row label="Ingresos" value={hovered.income} />
          <Row label="Gastos" value={-hovered.expenses} />
          <Row label="Contratos" value={-hovered.contractCosts} />
          <Row label="Beneficio" value={hovered.profit} strong />
          <Row label="Con tu tiempo" value={hovered.realProfit} />
        </div>
      )}
      <p className="sr-only">Escala máxima {formatEuros(max)}.</p>
    </div>
  )
}

function Row({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return (
    <p className={`flex justify-between gap-3 ${strong ? 'font-semibold' : ''}`}>
      <span className="text-slate-500">{label}</span>
      <span className="tabular-nums">{formatEuros(value)}</span>
    </p>
  )
}
