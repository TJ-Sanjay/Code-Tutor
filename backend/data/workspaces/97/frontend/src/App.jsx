import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import ProtectedRoute from './components/ProtectedRoute';

// Auth Pages
import Login from './pages/auth/Login';
import Signup from './pages/auth/Signup';
import ForgotPassword from './pages/auth/ForgotPassword';
import ResetPassword from './pages/auth/ResetPassword';

// Student Pages
import StudentDashboard from './pages/student/StudentDashboard';
import BrowseClubs from './pages/student/BrowseClubs';
import ClubDetails from './pages/student/ClubDetails';
import Events from './pages/student/Events';
import EventDetails from './pages/student/EventDetails';

// Club Head Pages
import ClubHeadDashboard from './pages/clubhead/ClubHeadDashboard';
import CreateClub from './pages/clubhead/CreateClub';
import CreateEvent from './pages/clubhead/CreateEvent';
import MembershipRequests from './pages/clubhead/MembershipRequests';
import ClubMembers from './pages/clubhead/ClubMembers';
import MyEvents from './pages/clubhead/MyEvents';
import EditEvent from './pages/clubhead/EditEvent';
import EditClub from './pages/clubhead/EditClub';
import EventRegistrations from './pages/clubhead/EventRegistrations';

// Admin Pages
import AdminDashboard from './pages/admin/AdminDashboard';
import ClubApproval from './pages/admin/ClubApproval';
import UserManagement from './pages/admin/UserManagement';
import AllClubs from './pages/admin/AllClubs';
import AllEvents from './pages/admin/AllEvents';
import EventApproval from './pages/admin/EventApproval';

// Shared Components
import Navbar from './components/Navbar';
import Footer from './components/Footer';

function App() {
    const { isAuthenticated, user } = useAuth();

    const getDefaultRoute = () => {
        if (!isAuthenticated) return '/login';
        if (user?.roles?.includes('ADMIN')) return '/admin/dashboard';
        if (user?.roles?.includes('CLUB_HEAD')) return '/clubhead/dashboard';
        return '/student/dashboard';
    };

    return (
        <ToastProvider>
            <div className="min-h-screen flex flex-col">
                {isAuthenticated && <Navbar />}

                <main className="flex-grow">
                    <Routes>
                        {/* Public Routes */}
                        <Route path="/login" element={!isAuthenticated ? <Login /> : <Navigate to={getDefaultRoute()} />} />
                        <Route path="/signup" element={!isAuthenticated ? <Signup /> : <Navigate to={getDefaultRoute()} />} />
                        <Route path="/forgot-password" element={<ForgotPassword />} />
                        <Route path="/reset-password" element={<ResetPassword />} />

                        {/* Student Routes */}
                        <Route path="/student/dashboard" element={
                            <ProtectedRoute roles={['STUDENT']}>
                                <StudentDashboard />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubs" element={
                            <ProtectedRoute>
                                <BrowseClubs />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubs/:id" element={
                            <ProtectedRoute>
                                <ClubDetails />
                            </ProtectedRoute>
                        } />
                        <Route path="/events" element={
                            <ProtectedRoute>
                                <Events />
                            </ProtectedRoute>
                        } />
                        <Route path="/events/:id" element={
                            <ProtectedRoute>
                                <EventDetails />
                            </ProtectedRoute>
                        } />

                        {/* Club Head Routes */}
                        <Route path="/clubhead/dashboard" element={
                            <ProtectedRoute roles={['CLUB_HEAD']}>
                                <ClubHeadDashboard />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/create-club" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <CreateClub />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/create-event" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <CreateEvent />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/membership-requests" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <MembershipRequests />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/clubs/:id/members" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <ClubMembers />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/my-events" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <MyEvents />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/events/:id/edit" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <EditEvent />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/events/:eventId/registrations" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <EventRegistrations />
                            </ProtectedRoute>
                        } />
                        <Route path="/clubhead/clubs/:id/edit" element={
                            <ProtectedRoute roles={['CLUB_HEAD', 'ADMIN']}>
                                <EditClub />
                            </ProtectedRoute>
                        } />

                        {/* Admin Routes */}
                        <Route path="/admin/dashboard" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <AdminDashboard />
                            </ProtectedRoute>
                        } />
                        <Route path="/admin/club-approval" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <ClubApproval />
                            </ProtectedRoute>
                        } />
                        <Route path="/admin/event-approval" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <EventApproval />
                            </ProtectedRoute>
                        } />
                        <Route path="/admin/users" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <UserManagement />
                            </ProtectedRoute>
                        } />
                        <Route path="/admin/all-clubs" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <AllClubs />
                            </ProtectedRoute>
                        } />
                        <Route path="/admin/all-events" element={
                            <ProtectedRoute roles={['ADMIN']}>
                                <AllEvents />
                            </ProtectedRoute>
                        } />

                        {/* Default Route */}
                        <Route path="/" element={<Navigate to={getDefaultRoute()} replace />} />
                    </Routes>
                </main>

                {isAuthenticated && <Footer />}
            </div>
        </ToastProvider >
    );
}

export default App;
