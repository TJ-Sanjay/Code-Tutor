import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Lock, MessageSquareCode, ShieldAlert } from 'lucide-react';
import { get } from '../api';
import { monaco, languageOf, themeFor } from '../lib/monaco';
import { useWorkspace } from '../pages/wsContext';
import { Spinner } from '../ui';
import { useThemeName } from './diagram/shared';
import ExplainCard from './ExplainCard';

/**
 * Read-only code viewer with IDE-style semantic colours, click-to-explain popups, highlighted line ranges
 * and an "ask AI about this selection" button.
 */
export default function CodeViewer({ path, start, end, onFileLoaded }) {
  const { projectId, ask, follow } = useWorkspace();
  const theme = useThemeName();
  const wrapRef = useRef(null);
  const editorRef = useRef(null);
  const decoRef = useRef(null);
  const hlRef = useRef(null);
  const [file, setFile] = useState(null);
  const [tokens, setTokens] = useState(null);
  const [loading, setLoading] = useState(true);
  const [ready, setReady] = useState(false);
  const [popup, setPopup] = useState(null);       // {x, y, data}
  const [selBtn, setSelBtn] = useState(null);     // {x, y, text, range}

  /* ───── load file + tokens ───── */
  useEffect(() => {
    let cancelled = false;
    setLoading(true); setFile(null); setTokens(null); setPopup(null); setSelBtn(null);
    get(`/projects/${projectId}/file`, { path }).then((f) => {
      if (cancelled) return;
      setFile(f);
      onFileLoaded?.(f);
      setLoading(false);
      if (path.endsWith('.java')) get(`/projects/${projectId}/semantic`, { path }).then((t) => !cancelled && setTokens(t)).catch(() => {});
    }).catch(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [projectId, path]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ───── per-line index of the tokens (for click lookup) ───── */
  const byLine = useMemo(() => {
    if (!tokens) return null;
    const m = new Map();
    tokens.tokens.forEach((t) => { if (!m.has(t[0])) m.set(t[0], []); m.get(t[0]).push(t); });
    return m;
  }, [tokens]);

  /* ───── colour the code semantically ───── */
  useEffect(() => {
    const ed = editorRef.current;
    if (!ed || !tokens || !ready || !file) return;
    const model = ed.getModel();
    if (!model) return;
    const decos = tokens.tokens.map(([line, col, len, kind, ref]) => ({
      range: new monaco.Range(line, col, line, col + len),
      options: { inlineClassName: `tok-${tokens.kinds[kind]}${ref >= 0 ? ' tok-clickable' : ''}` },
    }));
    decoRef.current?.clear();
    decoRef.current = ed.createDecorationsCollection(decos);
  }, [tokens, ready, file]);

  /* ───── highlight + scroll to the requested lines ───── */
  useEffect(() => {
    const ed = editorRef.current;
    if (!ed || !ready || !file) return;
    hlRef.current?.clear();
    const s = Number(start);
    if (!s) return;
    const e = Math.max(s, Number(end) || s);
    hlRef.current = ed.createDecorationsCollection([{
      range: new monaco.Range(s, 1, e, 1),
      options: { isWholeLine: true, className: 'code-highlight-line', linesDecorationsClassName: 'code-highlight-gutter' },
    }]);
    const t = setTimeout(() => ed.revealLinesInCenterIfOutsideViewport(s, e, 0), 60);
    return () => clearTimeout(t);
  }, [start, end, ready, file]);

  useEffect(() => { monaco.editor.setTheme(themeFor(theme)); }, [theme]);

  /* ───── click → explain ───── */
  const tokenAt = useCallback((pos) => {
    const list = byLine?.get(pos.lineNumber);
    if (!list) return null;
    return list.find((t) => pos.column >= t[1] && pos.column <= t[1] + t[2]) || null;
  }, [byLine]);

  const explain = useCallback(async (pos, word, x, y) => {
    try {
      const tok = tokenAt(pos);
      let params;
      if (tok && tok[4] >= 0) params = { ref: tokens.refs[tok[4]] };
      else params = { file: path, line: pos.lineNumber, word };
      const data = await get(`/projects/${projectId}/explain`, params);
      setPopup({ x, y, data });
    } catch {
      setPopup(null);
    }
  }, [projectId, tokens, tokenAt, path]);

  const onMount = (ed) => {
    editorRef.current = ed;
    setReady(true);
    ed.onMouseDown((e) => {
      const rect = wrapRef.current?.getBoundingClientRect();
      if (!rect) return;
      setSelBtn(null);
      if (!e.event.leftButton || !e.target.position) { setPopup(null); return; }
      const model = ed.getModel();
      const sel = ed.getSelection();
      if (sel && !sel.isEmpty()) return;
      const pos = e.target.position;
      const wordInfo = model.getWordAtPosition(pos);
      if (!wordInfo || e.target.type !== monaco.editor.MouseTargetType.CONTENT_TEXT || !path.endsWith('.java')) { setPopup(null); return; }
      explain(pos, wordInfo.word, e.event.browserEvent.clientX - rect.left, e.event.browserEvent.clientY - rect.top);
    });
    ed.onDidChangeCursorSelection((e) => {
      const sel = e.selection;
      if (sel.isEmpty()) { setSelBtn(null); return; }
      const text = ed.getModel().getValueInRange(sel);
      if (text.trim().length < 4) { setSelBtn(null); return; }
      const p = ed.getScrolledVisiblePosition(sel.getEndPosition());
      const dom = ed.getDomNode()?.getBoundingClientRect();
      const wrap = wrapRef.current?.getBoundingClientRect();
      if (!p || !dom || !wrap) return;
      setSelBtn({ x: dom.left - wrap.left + p.left + 12, y: dom.top - wrap.top + p.top + p.height + 6, text, startLine: sel.startLineNumber, endLine: sel.endLineNumber });
    });
    ed.onDidScrollChange(() => { setPopup(null); });
  };

  const askAbout = () => {
    if (!selBtn) return;
    ask(`Explain this code from ${path.substring(path.lastIndexOf('/') + 1)} (lines ${selBtn.startLine}-${selBtn.endLine})`, { file: path, startLine: selBtn.startLine, endLine: selBtn.endLine, text: selBtn.text.slice(0, 2000) });
    setSelBtn(null);
  };

  if (loading) return <div className="ws-center"><Spinner lg /></div>;
  if (!file) return <div className="ws-center"><div className="empty"><h3>Could not open this file</h3></div></div>;
  if (file.blockedReason) {
    return <div className="ws-center"><div className="empty"><div className="ico"><ShieldAlert size={26} /></div><h3>Not shown</h3><p>{file.blockedReason}</p></div></div>;
  }
  if (file.binary) return <div className="ws-center"><div className="empty"><h3>Binary file</h3><p>{file.size} bytes — it cannot be shown as text.</p></div></div>;

  // keep the popup inside the editor area
  const popupStyle = popup && wrapRef.current ? (() => {
    const w = wrapRef.current.clientWidth;
    const h = wrapRef.current.clientHeight;
    const maxH = Math.min(400, h - 16);
    const left = Math.min(Math.max(8, popup.x + 14), Math.max(8, w - 372));
    // keep the whole card (even at its maximum height) inside the editor area
    const top = Math.max(8, Math.min(popup.y + 18, h - maxH - 8));
    return { left, top, maxHeight: maxH };
  })() : null;

  return (
    <div className="code-wrap" ref={wrapRef} onMouseDown={() => { /* clicks inside popup are stopped */ }}>
      <Editor
        height="100%"
        path={`project-${projectId}/${path}`}
        language={languageOf(path)}
        value={file.content}
        theme={themeFor(theme)}
        onMount={onMount}
        loading={<Spinner lg />}
        options={{
          readOnly: true, domReadOnly: true, fontSize: 13.5, fontFamily: "'JetBrains Mono', ui-monospace, Consolas, monospace", fontLigatures: false,
          minimap: { enabled: true, scale: 1, renderCharacters: false }, scrollBeyondLastLine: false, smoothScrolling: true, automaticLayout: true,
          renderLineHighlight: 'line', lineNumbersMinChars: 4, padding: { top: 10, bottom: 24 }, folding: true, bracketPairColorization: { enabled: true },
          guides: { indentation: true, bracketPairs: false }, occurrencesHighlight: 'off', selectionHighlight: false, contextmenu: true,
          scrollbar: { verticalScrollbarSize: 12, horizontalScrollbarSize: 12 }, renderWhitespace: 'none', cursorStyle: 'line-thin', cursorBlinking: 'solid',
        }}
      />
      <div className="readonly-pill"><Lock size={11} /> read-only</div>
      {file.truncated && <div className="trunc-pill">showing the first part of a large file</div>}
      {popup && popupStyle && (
        <ExplainCard className="floating" style={popupStyle} data={popup.data} onClose={() => setPopup(null)}
          onLink={(l) => { setPopup(null); follow(l); }} />
      )}
      {selBtn && (
        <button className="ask-sel" style={{ left: selBtn.x, top: selBtn.y }} onMouseDown={(e) => e.preventDefault()} onClick={askAbout}>
          <MessageSquareCode size={14} /> Ask AI about this
        </button>
      )}
    </div>
  );
}
