// One palette used everywhere (diagrams, badges, file tree, chat chips) so a layer always looks the same.
export const LAYERS = {
  FRONTEND: { label: 'Front-end', color: '#38bdf8' },
  ENDPOINT: { label: 'Endpoint', color: '#a78bfa' },
  CONTROLLER: { label: 'Controller', color: '#7c8cff' },
  SERVICE: { label: 'Service', color: '#2dd4bf' },
  COMPONENT: { label: 'Component', color: '#4ade80' },
  REPOSITORY: { label: 'Repository', color: '#fbbf24' },
  ENTITY: { label: 'Entity', color: '#f472b6' },
  MODEL: { label: 'Model', color: '#fb7185' },
  TABLE: { label: 'Table', color: '#fb923c' },
  DTO: { label: 'DTO', color: '#c4b5fd' },
  SECURITY: { label: 'Security', color: '#f87171' },
  CONFIG: { label: 'Config', color: '#94a3b8' },
  APPLICATION: { label: 'Application', color: '#facc15' },
  ADVICE: { label: 'Exception handler', color: '#fda4af' },
  EXCEPTION: { label: 'Exception', color: '#fca5a5' },
  UTIL: { label: 'Utility', color: '#a8a29e' },
  TEST: { label: 'Test', color: '#86efac' },
  OTHER: { label: 'Other', color: '#9aa3b5' },
  // flow-only kinds
  CLIENT: { label: 'Client', color: '#38bdf8' },
  FILTER: { label: 'Security filter', color: '#f87171' },
  RESPONSE: { label: 'Response', color: '#4ade80' },
};

export const layerColor = (l) => (LAYERS[l] || LAYERS.OTHER).color;
export const layerLabel = (l) => (LAYERS[l] || LAYERS.OTHER).label;

export const METHOD_COLORS = {
  GET: '#3ecf8e',
  POST: '#7c8cff',
  PUT: '#f5a524',
  PATCH: '#c084fc',
  DELETE: '#f0616d',
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
