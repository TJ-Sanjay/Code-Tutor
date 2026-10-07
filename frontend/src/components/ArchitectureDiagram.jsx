import React from 'react';
import { ReactFlow, Background, Controls } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { X, Network } from 'lucide-react';

const initialNodes = [
  {
    id: '1',
    type: 'input',
    data: { label: '🌐 AuthController (REST API)' },
    position: { x: 250, y: 30 },
    style: { background: '#1e1b4b', color: '#a5b4fc', border: '1px solid #6366f1', borderRadius: '8px', padding: '12px', fontWeight: 'bold' }
  },
  {
    id: '2',
    data: { label: '⚙️ AuthService (Business Logic)' },
    position: { x: 250, y: 150 },
    style: { background: '#31104b', color: '#f472b6', border: '1px solid #ec4899', borderRadius: '8px', padding: '12px', fontWeight: 'bold' }
  },
  {
    id: '3',
    data: { label: '🛡️ JwtUtil & Security' },
    position: { x: 50, y: 150 },
    style: { background: '#064e3b', color: '#34d399', border: '1px solid #10b981', borderRadius: '8px', padding: '12px', fontWeight: 'bold' }
  },
  {
    id: '4',
    data: { label: '📦 UserRepository (JPA Data Layer)' },
    position: { x: 250, y: 270 },
    style: { background: '#172554', color: '#60a5fa', border: '1px solid #3b82f6', borderRadius: '8px', padding: '12px', fontWeight: 'bold' }
  },
  {
    id: '5',
    type: 'output',
    data: { label: '🗄️ PostgreSQL Database (Users Table)' },
    position: { x: 250, y: 390 },
    style: { background: '#451a03', color: '#fbbf24', border: '1px solid #f59e0b', borderRadius: '8px', padding: '12px', fontWeight: 'bold' }
  }
];

const initialEdges = [
  { id: 'e1-2', source: '1', target: '2', label: 'DEPENDS_ON', animated: true, style: { stroke: '#6366f1' } },
  { id: 'e2-3', source: '2', target: '3', label: 'USES_JWT', animated: true, style: { stroke: '#10b981' } },
  { id: 'e2-4', source: '2', target: '4', label: 'DEPENDS_ON', animated: true, style: { stroke: '#ec4899' } },
  { id: 'e4-5', source: '4', target: '5', label: 'PERSISTS_TO', animated: true, style: { stroke: '#f59e0b' } }
];

export default function ArchitectureDiagram({ project, onClose }) {
  if (!project) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '950px', width: '90vw', height: '80vh', display: 'flex', flexDirection: 'column', padding: 0, overflow: 'hidden' }}>
        
        {/* Header */}
        <div style={{
          padding: '1rem 1.5rem', borderBottom: '1px solid var(--border-color)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f172a'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <Network size={22} color="var(--accent)" />
            <div>
              <div style={{ fontWeight: 700, fontSize: '1.05rem', color: 'white' }}>{project.name} Architecture Graph</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Interactive React Flow Dependency & ER Graph</div>
            </div>
          </div>

          <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
            <X size={22} />
          </button>
        </div>

        {/* React Flow Viewport */}
        <div style={{ flex: 1, background: '#090d16' }}>
          <ReactFlow nodes={initialNodes} edges={initialEdges} fitView>
            <Background color="#334155" gap={16} />
            <Controls />
          </ReactFlow>
        </div>

      </div>
    </div>
  );
}
