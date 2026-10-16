export function formatBytes(b) {
  if (b == null) return '–';
  if (b < 1024) return `${b} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
  if (b < 1024 * 1024 * 1024) return `${(b / 1024 / 1024).toFixed(1)} MB`;
  return `${(b / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export function formatNumber(n) {
  if (n == null) return '–';
  return new Intl.NumberFormat('en').format(n);
}

export function timeAgo(iso) {
  if (!iso) return '';
  const s = Math.max(1, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} d ago`;
  return new Date(iso).toLocaleDateString();
}

export function fileName(path) {
  return path ? path.substring(path.lastIndexOf('/') + 1) : '';
}

export function initials(name) {
  if (!name) return '?';
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

export function repoNameFromUrl(url) {
  try {
    const m = url.trim().match(/github\.com\/[^/]+\/([^/#?]+)/i);
    return m ? m[1].replace(/\.git$/, '') : '';
  } catch {
    return '';
  }
}
