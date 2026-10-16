import React, { useState, useEffect } from 'react';
import { ReactFlow, Background, Controls } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { X, Network, Loader2 } from 'lucide-react';
import api from '../api';

const LAYER_Y = {
  'CONTROLLER': 50,
  'SERVICE': 200,
  'REPOSITORY': 350,
  'ENTITY': 500,
  'CONFIGURATION': 50,
  'COMPONENT': 200,
  'OTHER': 650
};

const LAYER_STYLE = {
  'CONTROLLER': { background: '#1e1b4b', color: '#a5b4fc', border: '1px solid #6366f1' },
  'SERVICE': { background: '#31104b', color: '#f472b6', border: '1px solid #ec4899' },
  'REPOSITORY': { background: '#172554', color: '#60a5fa', border: '1px solid #3b82f6' },
  'ENTITY': { background: '#451a03', color: '#fbbf24', border: '1px solid #f59e0b' },
  'CONFIGURATION': { background: '#064e3b', color: '#34d399', border: '1px solid #10b981' },
  'COMPONENT': { background: '#334155', color: '#e2e8f0', border: '1px solid #94a3b8' },
  'OTHER': { background: '#1e293b', color: '#94a3b8', border: '1px solid #475569' }
};

export default function ArchitectureDiagram({ project, onClose }) {
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!project) return;
    
    setLoading(true);
    api.get(`/projects/${project.id}/graph`)
      .then(res => {
        const graph = res.data.data;
        if (!graph || !graph.nodes) {
          setError('No graph data available.');
          return;
        }

        // Layout counters
        const layerXCounts = {};

        const mappedNodes = graph.nodes.map(n => {
          const group = n.group || 'OTHER';
          const y = LAYER_Y[group] || LAYER_Y['OTHER'];
          
          layerXCounts[group] = (layerXCounts[group] || 0) + 1;
          const x = (layerXCounts[group] * 200) - 100;

          const baseStyle = LAYER_STYLE[group] || LAYER_STYLE['OTHER'];
          
          return {
            id: n.id,
            data: { label: `${n.label}\n(${group})` },
            position: { x, y },
            style: { ...baseStyle, borderRadius: '8px', padding: '12px', fontWeight: 'bold', minWidth: '150px', textAlign: 'center' }
          };
        });

        const mappedEdges = (graph.edges || []).map(e => ({
          id: e.id,
          source: e.source,
          target: e.target,
          label: e.label,
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 1.5 }
        }));

        setNodes(mappedNodes);
        setEdges(mappedEdges);
      })
      .catch(err => {
        console.error(err);
        setError('Failed to load architecture graph.');
      })
      .finally(() => setLoading(false));
  }, [project]);

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
        <div style={{ flex: 1, background: '#090d16', position: 'relative' }}>
          {loading && (
            <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10, background: 'rgba(9, 13, 22, 0.7)' }}>
              <Loader2 size={32} className="spin" color="var(--primary)" />
            </div>
          )}
          {error && !loading && (
            <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10 }}>
              <div className="glass-card" style={{ color: 'var(--text-muted)' }}>{error}</div>
            </div>
          )}
          {!loading && !error && (
            <ReactFlow nodes={nodes} edges={edges} fitView>
              <Background color="#334155" gap={16} />
              <Controls />
            </ReactFlow>
          )}
        </div>
      </div>
    </div>
  );
}
