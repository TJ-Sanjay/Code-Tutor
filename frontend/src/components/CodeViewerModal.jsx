import React, { useState, useEffect, useRef } from 'react';
import Editor from '@monaco-editor/react';
import { X, FileCode, Folder, File, ChevronRight, Loader2 } from 'lucide-react';
import api from '../api';
import TutorChat from './TutorChat';

export default function CodeViewerModal({ project, onClose }) {
  const [fileNodes, setFileNodes] = useState([]);
  const [selectedFilePath, setSelectedFilePath] = useState(null);
  const [fileContent, setFileContent] = useState('');
  const [loadingFiles, setLoadingFiles] = useState(true);
  const [loadingContent, setLoadingContent] = useState(false);
  const [theme, setTheme] = useState('vs-dark');
  
  const editorRef = useRef(null);

  // Helper to flatten the FileNode tree from ScanResult into a flat list of FILE nodes
  const flattenTree = (node, fileList = []) => {
    if (!node) return fileList;
    if (node.type === 'FILE') {
      fileList.push(node);
    }
    if (node.children) {
      node.children.forEach(child => flattenTree(child, fileList));
    }
    return fileList;
  };

  useEffect(() => {
    if (!project) return;
    setLoadingFiles(true);
    api.get(`/projects/${project.id}/files`)
      .then(res => {
        const rootNode = res.data.data.root;
        const flatFiles = flattenTree(rootNode);
        setFileNodes(flatFiles);
        if (flatFiles.length > 0) {
          setSelectedFilePath(flatFiles[0].path);
        }
      })
      .catch(err => console.error('Failed to load files:', err))
      .finally(() => setLoadingFiles(false));
  }, [project]);

  useEffect(() => {
    if (!project || !selectedFilePath) return;
    setLoadingContent(true);
    api.get(`/projects/${project.id}/file?path=${encodeURIComponent(selectedFilePath)}`)
      .then(res => {
        setFileContent(res.data);
      })
      .catch(err => {
        console.error('Failed to load file content:', err);
        setFileContent('// Error loading file content.');
      })
      .finally(() => setLoadingContent(false));
  }, [project, selectedFilePath]);

  const handleEditorDidMount = (editor, monaco) => {
    editorRef.current = editor;

    // Module 39: Click-to-Explain Context Menu
    editor.addAction({
      id: 'tutor-explain',
      label: 'Ask CodeTutor: Explain this',
      contextMenuGroupId: 'navigation',
      contextMenuOrder: 1,
      run: function(ed) {
        const selectedText = ed.getModel().getValueInRange(ed.getSelection());
        if (selectedText) {
          window.dispatchEvent(new CustomEvent('tutor-context-action', {
            detail: { action: 'explain', text: selectedText, file: selectedFilePath }
          }));
        }
      }
    });

    // Module 40: Refactor Suggestion Context Menu
    editor.addAction({
      id: 'tutor-refactor',
      label: 'Ask CodeTutor: Refactor this',
      contextMenuGroupId: 'navigation',
      contextMenuOrder: 2,
      run: function(ed) {
        const selectedText = ed.getModel().getValueInRange(ed.getSelection());
        if (selectedText) {
          window.dispatchEvent(new CustomEvent('tutor-context-action', {
            detail: { action: 'refactor', text: selectedText, file: selectedFilePath }
          }));
        }
      }
    });
  };

  const handleHighlightCode = (filePath) => {
    // Module 38: Context Highlighting
    setSelectedFilePath(filePath);
  };

  if (!project) return null;

  const getLanguage = (path) => {
    if (!path) return 'plaintext';
    if (path.endsWith('.java')) return 'java';
    if (path.endsWith('.xml')) return 'xml';
    if (path.endsWith('.json')) return 'json';
    if (path.endsWith('.yml') || path.endsWith('.yaml')) return 'yaml';
    if (path.endsWith('.md')) return 'markdown';
    return 'plaintext';
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '1400px', width: '95vw', height: '90vh', display: 'flex', flexDirection: 'column', padding: 0, overflow: 'hidden' }}>
        
        {/* Header */}
        <div style={{
          padding: '1rem 1.5rem', borderBottom: '1px solid var(--border-color)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f172a'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <FileCode size={22} color="var(--primary)" />
            <div>
              <div style={{ fontWeight: 700, fontSize: '1.05rem', color: 'white' }}>{project.name} Code Viewer & AI Tutor</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Read-only Editor • {selectedFilePath || 'Loading...'}</div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <select 
              value={theme} onChange={e => setTheme(e.target.value)}
              className="form-input" style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
            >
              <option value="vs-dark">VS Dark</option>
              <option value="light">VS Light</option>
            </select>
            <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
              <X size={22} />
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          
          {/* File Explorer */}
          <div style={{
            width: '250px', background: '#0b0f19', borderRight: '1px solid var(--border-color)',
            display: 'flex', flexDirection: 'column'
          }}>
            <div style={{ padding: '1rem', borderBottom: '1px solid var(--border-color)' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                WORKSPACE FILES
              </div>
            </div>
            
            <div style={{ flex: 1, overflowY: 'auto', padding: '0.5rem' }}>
              {loadingFiles ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-muted)', padding: '1rem' }}>
                  <Loader2 size={16} className="spin" /> Loading tree...
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.1rem' }}>
                  {fileNodes.map(file => (
                    <button
                      key={file.path}
                      onClick={() => setSelectedFilePath(file.path)}
                      style={{
                        display: 'flex', alignItems: 'center', gap: '0.5rem',
                        padding: '0.5rem', borderRadius: '4px', border: 'none',
                        background: selectedFilePath === file.path ? 'rgba(99, 102, 241, 0.2)' : 'transparent',
                        color: selectedFilePath === file.path ? '#a5b4fc' : 'var(--text-main)',
                        fontWeight: selectedFilePath === file.path ? 600 : 400,
                        fontSize: '0.8rem', cursor: 'pointer', textAlign: 'left'
                      }}
                      title={file.path}
                    >
                      <File size={14} color={selectedFilePath === file.path ? '#818cf8' : '#6b7280'} />
                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {file.path}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Monaco Editor */}
          <div style={{ flex: 1, height: '100%', position: 'relative' }}>
            {loadingContent && (
              <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.4)', zIndex: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Loader2 size={32} className="spin" color="var(--primary)" />
              </div>
            )}
            <Editor
              height="100%"
              language={getLanguage(selectedFilePath)}
              theme={theme}
              value={fileContent}
              onMount={handleEditorDidMount}
              options={{
                readOnly: true,
                fontSize: 14,
                fontFamily: "'Fira Code', monospace",
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                automaticLayout: true,
                smoothScrolling: true
              }}
            />
          </div>

          {/* AI Tutor Chat Interface */}
          <TutorChat 
            project={project}
            selectedFile={selectedFilePath}
            onHighlightCode={handleHighlightCode}
          />
        </div>
      </div>
    </div>
  );
}
