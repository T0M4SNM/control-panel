import { Link } from 'react-router'
import { listProjects, PROJECT_ROLE_LABELS, PROJECT_STATUS_LABELS, type Project, type ProjectStatus } from '../api/projects'
import { useAuth } from '../auth-context'
import { ButtonLink, EmptyState, ErrorText, Loading, PageHeader } from '../components/ui'
import { useLoad } from '../hooks'

const STATUS_STYLES: Record<ProjectStatus, string> = {
  IDEA: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  DEVELOPMENT: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  PRODUCTION: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
  PAUSED: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
}

export default function ProjectsPage() {
  const { isAdmin } = useAuth()
  const projects = useLoad(listProjects)

  return (
    <>
      <PageHeader title="Proyectos">
        {isAdmin && (
          <ButtonLink to="/anadir?tipo=app" variant="primary">
            Nueva app
          </ButtonLink>
        )}
      </PageHeader>

      {projects.error && <ErrorText>{projects.error}</ErrorText>}
      {projects.loading && !projects.data && <Loading />}
      {projects.data?.length === 0 && (
        <EmptyState>{isAdmin ? 'Aún no hay proyectos.' : 'Aún no participas en ningún proyecto.'}</EmptyState>
      )}

      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {projects.data?.map((project) => (
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
    <article className="flex h-full flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700">
      <div className="flex items-start justify-between gap-3">
        <h2 className="font-semibold break-words">
          <Link to={`/proyectos/${project.id}`} className="hover:underline">
            {project.name}
          </Link>
        </h2>
        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_STYLES[project.status]}`}>
          {PROJECT_STATUS_LABELS[project.status]}
        </span>
      </div>

      {project.description && (
        <p className="line-clamp-3 text-sm text-slate-600 dark:text-slate-400">{project.description}</p>
      )}

      <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 pt-2 text-sm">
        <Link to={`/proyectos/${project.id}`} className="font-medium text-sky-700 hover:underline dark:text-sky-400">
          Ver detalle
        </Link>
        {project.publicUrl && <ExternalLink href={project.publicUrl}>Web</ExternalLink>}
        {project.repoUrl && <ExternalLink href={project.repoUrl}>Repositorio</ExternalLink>}
        {project.myRole && <span className="ml-auto text-xs text-slate-500">{PROJECT_ROLE_LABELS[project.myRole]}</span>}
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
