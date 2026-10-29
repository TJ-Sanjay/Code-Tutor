// One palette used everywhere (diagrams, badges, file tree, chat chips) so a layer always looks the same.
export const LAYERS = {
  FRONTEND: { label: 'Front-end', color: '#e11d74' },
  ENDPOINT: { label: 'Endpoint', color: '#7c3aed' },
  CONTROLLER: { label: 'Controller', color: '#2563eb' },
  SERVICE: { label: 'Service', color: '#0d9488' },
  COMPONENT: { label: 'Component', color: '#65a30d' },
  REPOSITORY: { label: 'Repository', color: '#d97706' },
  ENTITY: { label: 'Entity', color: '#7c3aed' },
  MODEL: { label: 'Model', color: '#a78bfa' },
  TABLE: { label: 'Table', color: '#ea580c' },
  DTO: { label: 'DTO', color: '#7c6cd6' },
  SECURITY: { label: 'Security', color: '#dc2626' },
  CONFIG: { label: 'Config', color: '#64748b' },
  APPLICATION: { label: 'Application', color: '#ca8a04' },
  ADVICE: { label: 'Exception handler', color: '#e11d48' },
  EXCEPTION: { label: 'Exception', color: '#be123c' },
  UTIL: { label: 'Utility', color: '#78716c' },
  TEST: { label: 'Test', color: '#16a34a' },
  OTHER: { label: 'Other', color: '#6b7280' },
  // flow-only kinds
  CLIENT: { label: 'Client', color: '#e11d74' },
  FILTER: { label: 'Security filter', color: '#dc2626' },
  RESPONSE: { label: 'Response', color: '#16a34a' },
};

export const layerColor = (l) => (LAYERS[l] || LAYERS.OTHER).color;
export const layerLabel = (l) => (LAYERS[l] || LAYERS.OTHER).label;

export const METHOD_COLORS = {
  GET: '#16a34a',
  POST: '#2563eb',
  PUT: '#d97706',
  PATCH: '#9333ea',
  DELETE: '#dc2626',
  ANY: '#9aa3b5',
};

export const methodColor = (m) => METHOD_COLORS[m] || METHOD_COLORS.ANY;

export const LANG_COLORS = {
  Java: '#f89820',
  JavaScript: '#f7df1e',
  TypeScript: '#3178c6',
  CSS: '#a78bfa',
  HTML: '#e34c26',
  XML: '#60a5fa',
  YAML: '#cb171e',
  Properties: '#94a3b8',
  SQL: '#38bdf8',
  Python: '#3776ab',
  Kotlin: '#a97bff',
  Shell: '#4ade80',
  Markdown: '#9aa3b5',
  JSON: '#9aa3b5',
};

export const langColor = (l) => LANG_COLORS[l] || '#6b7489';

export function accessInfo(access) {
  if (!access || access === 'PUBLIC') return { label: 'Public', tone: 'good' };
  if (access === 'AUTHENTICATED') return { label: 'Login', tone: 'warn' };
  if (access.startsWith('ROLE:')) return { label: access.slice(5), tone: 'bad' };
  if (access === 'DENIED') return { label: 'Blocked', tone: 'bad' };
  return { label: access.toLowerCase(), tone: 'warn' };
}

/** One-line, plain-language meaning of each layer — used by the overview diagram. */
export const LAYER_HELP = {
  FRONTEND: 'The React user interface',
  CONTROLLER: 'Receive web requests',
  SERVICE: 'Business logic',
  COMPONENT: 'Middleware, tasks and other helpers',
  REPOSITORY: 'Read and write the database',
  ENTITY: 'Data models',
  MODEL: 'Domain data classes',
  TABLE: 'Database tables',
  DTO: 'Data carried in requests',
  SECURITY: 'Login and permissions',
  CONFIG: 'Settings and wiring',
  APPLICATION: 'Starts the application',
  ADVICE: 'Global error handling',
  EXCEPTION: 'Custom errors',
  UTIL: 'Small helper functions',
  OTHER: 'Other classes',
};

export const LAYER_PLURAL = {
  FRONTEND: 'Front-end', CONTROLLER: 'Controllers', SERVICE: 'Services', COMPONENT: 'Components', REPOSITORY: 'Repositories',
  ENTITY: 'Entities', MODEL: 'Models', TABLE: 'Database', DTO: 'DTOs', SECURITY: 'Security', CONFIG: 'Configuration',
  APPLICATION: 'Application', ADVICE: 'Error handlers', EXCEPTION: 'Exceptions', UTIL: 'Utilities', OTHER: 'Other',
};
