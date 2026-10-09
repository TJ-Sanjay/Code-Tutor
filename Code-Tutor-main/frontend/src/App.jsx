import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import AuthModal from './components/AuthModal';
import MetricsHeader from './components/MetricsHeader';
import ProjectImport from './components/ProjectImport';
import ProjectList from './components/ProjectList';
import CodeViewerModal from './components/CodeViewerModal';
import ArchitectureDiagram from './components/ArchitectureDiagram';
import ModulePipeline from './components/ModulePipeline';

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [user, setUser] = useState(null);
  const [isAuthOpen, setIsAuthOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  
  const [activeCodeProject, setActiveCodeProject] = useState(null);
  const [activeDiagramProject, setActiveDiagramProject] = useState(null);

  // Fetch Current User Profile
  const fetchProfile = async (authToken) => {
    if (!authToken) return;
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      const data = await res.json();
      if (data.success) {
        setUser(data.data);
        fetchProjects(authToken);
      } else {
        handleLogout();
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Fetch Projects List
  const fetchProjects = async (authToken) => {
    try {
      const res = await fetch('/api/projects', {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      const data = await res.json();
      if (data.success) {
        setProjects(data.data);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    if (token) {
      fetchProfile(token);
    } else {
      setIsAuthOpen(true);
    }
  }, [token]);

  const handleLoginSuccess = (authData) => {
    setToken(authData.accessToken);
    setUser(authData.user || { email: authData.email, username: authData.username, fullName: authData.fullName });
    setIsAuthOpen(false);
    fetchProjects(authData.accessToken);
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
    setProjects([]);
    setIsAuthOpen(true);
  };

  const handleDeleteProject = async (id) => {
    if (!confirm('Are you sure you want to delete this project?')) return;
    try {
      const res = await fetch(`/api/projects/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await res.json();
      if (data.success) {
        fetchProjects(token);
      }
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="app-container">
      <Navbar 
        user={user} 
        onOpenAuth={() => setIsAuthOpen(true)} 
        onLogout={handleLogout} 
      />

      <main className="main-content">
        <MetricsHeader 
          projectsCount={projects.length}
          storageUsed={user ? user.storageUsedBytes : 0}
          storageLimit={user ? user.storageLimitBytes : 5368709120}
        />

        {token && (
          <ProjectImport 
            token={token} 
            onProjectAdded={() => fetchProjects(token)} 
          />
        )}

        <div className="section-title">
          <span>📁</span> Active User Projects
        </div>

        <ProjectList 
          projects={projects}
          token={token}
          onDeleteProject={handleDeleteProject}
          onViewCode={(p) => setActiveCodeProject(p)}
          onViewDiagram={(p) => setActiveDiagramProject(p)}
        />

        <ModulePipeline />
      </main>

      <AuthModal 
        isOpen={isAuthOpen} 
        onClose={() => token && setIsAuthOpen(false)}
        onLoginSuccess={handleLoginSuccess}
      />

      {activeCodeProject && (
        <CodeViewerModal 
          project={activeCodeProject}
          onClose={() => setActiveCodeProject(null)}
        />
      )}

      {activeDiagramProject && (
        <ArchitectureDiagram 
          project={activeDiagramProject}
          onClose={() => setActiveDiagramProject(null)}
        />
      )}
    </div>
  );
}
