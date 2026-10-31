import { useState } from 'react'
import { listUsers, setHourlyRate, type User } from '../api/users'
import { Badge, Button, Card, ErrorText, Input, Loading, PageHeader } from '../components/ui'
import { errorMessage, useLoad } from '../hooks'

/** Usuarios del workspace y su tarifa por hora (solo admin). */
export default function UsersPage() {
  const users = useLoad(listUsers)

  return (
    <>
      <PageHeader title="Usuarios" />
      <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
        La tarifa valora el tiempo de cada uno. Se copia en cada cronómetro al iniciarlo, así que cambiarla no
        reescribe lo ya registrado.
      </p>
      {users.error && <ErrorText>{users.error}</ErrorText>}
      {users.loading && !users.data && <Loading />}
      <Card className="divide-y divide-slate-100 dark:divide-slate-800">
        {users.data?.map((u) => (
          <UserRow key={u.id} user={u} onSaved={users.reload} />
        ))}
      </Card>
    </>
  )
}

function UserRow({ user, onSaved }: { user: User; onSaved: () => void }) {
  const [rate, setRate] = useState(user.hourlyRate?.toString() ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const changed = rate !== (user.hourlyRate?.toString() ?? '')

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await setHourlyRate(user.id, rate === '' ? null : Number(rate))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 px-5 py-3">
      <div className="min-w-0 flex-1">
        <p className="font-medium">
          {user.displayName} {!user.active && <Badge>Desactivado</Badge>}
        </p>
        <p className="truncate text-sm text-slate-500">{user.email}</p>
      </div>
      <Badge tone={user.role === 'ADMIN' ? 'sky' : 'slate'}>{user.role === 'ADMIN' ? 'Admin' : 'Miembro'}</Badge>
      <label className="flex items-center gap-2 text-sm">
        <Input type="number" min={0} step="0.5" value={rate} onChange={(e) => setRate(e.target.value)} className="w-24" placeholder="—" />
        €/h
      </label>
      <Button onClick={save} disabled={!changed || busy}>
        Guardar
      </Button>
      {error && <ErrorText>{error}</ErrorText>}
    </div>
  )
}
