// Monaco is bundled locally (no CDN) and trimmed to what a read-only code viewer needs.
import * as monaco from 'monaco-editor/editor/editor.api.js';
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker';
import { loader } from '@monaco-editor/react';

import 'monaco-editor/editor/contrib/clipboard/browser/clipboard.js';
import 'monaco-editor/editor/contrib/bracketMatching/browser/bracketMatching.js';
import 'monaco-editor/editor/contrib/folding/browser/folding.js';
import 'monaco-editor/editor/contrib/contextmenu/browser/contextmenu.js';
import 'monaco-editor/editor/contrib/wordHighlighter/browser/wordHighlighter.js';
import 'monaco-editor/editor/contrib/linesOperations/browser/linesOperations.js';
import 'monaco-editor/editor/contrib/smartSelect/browser/smartSelect.js';
import 'monaco-editor/features/find/register.js';

import 'monaco-editor/languages/definitions/java/register.js';
import 'monaco-editor/languages/definitions/javascript/register.js';
import 'monaco-editor/languages/definitions/typescript/register.js';
import 'monaco-editor/languages/definitions/xml/register.js';
import 'monaco-editor/languages/definitions/yaml/register.js';
import 'monaco-editor/languages/definitions/markdown/register.js';
import 'monaco-editor/languages/definitions/sql/register.js';
import 'monaco-editor/languages/definitions/html/register.js';
import 'monaco-editor/languages/definitions/css/register.js';
import 'monaco-editor/languages/definitions/scss/register.js';
import 'monaco-editor/languages/definitions/ini/register.js';
import 'monaco-editor/languages/definitions/shell/register.js';
import 'monaco-editor/languages/definitions/dockerfile/register.js';
import 'monaco-editor/languages/definitions/python/register.js';
import 'monaco-editor/languages/definitions/kotlin/register.js';
import 'monaco-editor/languages/definitions/go/register.js';

self.MonacoEnvironment = { getWorker: () => new EditorWorker() };

monaco.editor.defineTheme('ct-dark', {
  base: 'vs-dark',
  inherit: true,
  rules: [
    { token: 'comment', foreground: '6A9955', fontStyle: 'italic' },
    { token: 'keyword', foreground: 'C586C0' },
    { token: 'keyword.control', foreground: 'C586C0' },
    { token: 'string', foreground: 'CE9178' },
    { token: 'string.escape', foreground: 'D7BA7D' },
    { token: 'number', foreground: 'B5CEA8' },
    { token: 'annotation', foreground: 'D7BA7D' },
    { token: 'type.identifier', foreground: '4EC9B0' },
    { token: 'tag', foreground: '7C8CFF' },
    { token: 'attribute.name', foreground: '9CDCFE' },
    { token: 'attribute.value', foreground: 'CE9178' },
    { token: 'delimiter', foreground: '8B93A7' },
    { token: 'operator', foreground: '9AA3B5' },
  ],
  colors: {
    'editor.background': '#0d1016',
    'editor.foreground': '#d4d8e2',
    'editorLineNumber.foreground': '#4b5368',
    'editorLineNumber.activeForeground': '#9aa3b5',
    'editor.lineHighlightBackground': '#141824',
    'editor.selectionBackground': '#33407a88',
    'editorGutter.background': '#0d1016',
    'editorIndentGuide.background1': '#1b2030',
    'editorIndentGuide.activeBackground1': '#2f3650',
    'editorWidget.background': '#151922',
    'scrollbarSlider.background': '#2f365066',
  },
});

monaco.editor.defineTheme('ct-light', {
  base: 'vs',
  inherit: true,
  rules: [
    { token: 'comment', foreground: '5d8a4a', fontStyle: 'italic' },
    { token: 'keyword', foreground: '8c3fb5' },
    { token: 'string', foreground: 'b4532a' },
    { token: 'number', foreground: '2f7d4f' },
    { token: 'annotation', foreground: '9a6a00' },
    { token: 'type.identifier', foreground: '0b7f6d' },
    { token: 'tag', foreground: '3c49d6' },
    { token: 'attribute.name', foreground: '1b6fa8' },
    { token: 'attribute.value', foreground: 'b4532a' },
  ],
  colors: {
    'editor.background': '#ffffff',
    'editorLineNumber.foreground': '#a2abc0',
    'editor.lineHighlightBackground': '#f3f5fb',
    'editorGutter.background': '#ffffff',
  },
});

loader.config({ monaco });

export { monaco };
export const themeFor = (name) => (name === 'light' ? 'ct-light' : 'ct-dark');

const BY_EXT = {
  java: 'java', js: 'javascript', jsx: 'javascript', mjs: 'javascript', ts: 'typescript', tsx: 'typescript',
  json: 'javascript', xml: 'xml', yml: 'yaml', yaml: 'yaml', md: 'markdown', sql: 'sql', html: 'html', htm: 'html',
  css: 'css', scss: 'scss', properties: 'ini', ini: 'ini', sh: 'shell', py: 'python', kt: 'kotlin', kts: 'kotlin',
  gradle: 'java', go: 'go', toml: 'ini', conf: 'ini',
};

export function languageOf(path) {
  const name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
  if (name === 'dockerfile') return 'dockerfile';
  const ext = name.includes('.') ? name.substring(name.lastIndexOf('.') + 1) : '';
  return BY_EXT[ext] || 'plaintext';
}
