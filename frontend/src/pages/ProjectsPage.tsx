import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth-context'
import { listProjects, type Project, type ProjectRole, type ProjectStatus } from '../api/projects'

const STATUS_LABELS: Record<ProjectStatus, string> = {
  IDEA: 'Idea',
  DEVELOPMENT: 'En desarrollo',
  PRODUCTION: 'En producción',
  PAUSED: 'Pausado',
}

const STATUS_STYLES: Record<ProjectStatus, string> = {
  IDEA: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  DEVELOPMENT: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  PRODUCTION: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
  PAUSED: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
}

const ROLE_LABELS: Record<ProjectRole, string> = {
  OWNER: 'Propietario',
  EDITOR: 'Editor',
  VIEWER: 'Lector',
}

export default function ProjectsPage() {
  const { user, sessionExpired: onSessionExpired } = useAuth()
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listProjects()
      .then(setProjects)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          onSessionExpired()
        } else {
          setError('No se han podido cargar los proyectos.')
        }
      })
  }, [onSessionExpired])

  return (
    <>
      <h1 className="mb-4 text-2xl font-semibold">Proyectos</h1>

      {error && <p className="text-red-600 dark:text-red-400">{error}</p>}
      {!error && projects === null && <p className="text-slate-500">Cargando…</p>}
      {projects?.length === 0 && (
        <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-slate-500 dark:border-slate-700">
          {user.role === 'ADMIN' ? 'Aún no hay proyectos.' : 'Aún no participas en ningún proyecto.'}
        </p>
      )}

      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {projects?.map((project) => (
          <li key={project.id}>
            <ProjectCard project={project} />
          </li>
        ))}
      </ul>
    </>
  )
}

function ProjectCard({ project }: { project: Project }) {
  return (
    <article className="flex h-full flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-start justify-between gap-3">
        <h2 className="font-semibold break-words">{project.name}</h2>
        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_STYLES[project.status]}`}>
          {STATUS_LABELS[project.status]}
        </span>
      </div>

      {project.description && (
        <p className="line-clamp-3 text-sm text-slate-600 dark:text-slate-400">{project.description}</p>
      )}

      <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 pt-2 text-sm">
        {project.publicUrl && <ExternalLink href={project.publicUrl}>Web</ExternalLink>}
        {project.repoUrl && <ExternalLink href={project.repoUrl}>Repositorio</ExternalLink>}
        {project.myRole && (
          <span className="ml-auto text-xs text-slate-500">{ROLE_LABELS[project.myRole]}</span>
        )}
      </div>
    </article>
  )
}

function ExternalLink({ href, children }: { href: string; children: string }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="text-sky-700 hover:underline dark:text-sky-400">
      {children} ↗
    </a>
  )
}
