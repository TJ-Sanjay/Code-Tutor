import React from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { Spinner, ToastProvider } from './ui';
import AuthPage from './pages/AuthPage';
import Dashboard from './pages/Dashboard';
import Workspace from './pages/Workspace';

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div style={{ height: '100vh', display: 'grid', placeItems: 'center' }}><Spinner lg /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  // keyed by user id: switching accounts remounts everything, so no data can leak between accounts in the UI
  return <div key={user.id} style={{ minHeight: '100%' }}><Outlet /></div>;
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<AuthPage />} />
            <Route element={<RequireAuth />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/project/:id/*" element={<Workspace />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
