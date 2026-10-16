import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { BarChart, Users, Calendar, TrendingUp, CheckCircle, UserCog, Building, DollarSign } from 'lucide-react';
import api from '../../services/api';

const AdminDashboard = () => {
    const [analytics, setAnalytics] = useState(null);
    const [loading, setLoading] = useState(true);
    const [recentClubs, setRecentClubs] = useState([]);
    const [recentEvents, setRecentEvents] = useState([]);

    useEffect(() => {
        fetchDashboardData();
    }, []);

    const fetchDashboardData = async () => {
        try {
            setLoading(true);
            const [analyticsRes, clubsRes, eventsRes] = await Promise.all([
                api.get('/admin/analytics'),
                api.get('/admin/reports/clubs'),
                api.get('/admin/reports/events')
            ]);
            setAnalytics(analyticsRes.data);
            setRecentClubs(clubsRes.data.slice(0, 5));
            setRecentEvents(eventsRes.data.slice(0, 5));
        } catch (error) {
            console.error('Error fetching dashboard data:', error);
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold gradient-text mb-2">Admin Dashboard</h1>
                    <p className="text-gray-600">System overview and management</p>
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Total Clubs</p>
                                <p className="text-3xl font-bold mt-2">{analytics?.totalClubs || 0}</p>
                                <p className="text-sm text-green-600 mt-1">
                                    {analytics?.activeClubs || 0} active
                                </p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-blue-500 to-blue-600 flex items-center justify-center">
                                <Building className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Total Users</p>
                                <p className="text-3xl font-bold mt-2">{analytics?.totalUsers || 0}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-green-500 to-green-600 flex items-center justify-center">
                                <Users className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Upcoming Events</p>
                                <p className="text-3xl font-bold mt-2">{analytics?.upcomingEvents || 0}</p>
                                <p className="text-sm text-purple-600 mt-1">
                                    {analytics?.totalEvents || 0} total
                                </p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-purple-500 to-purple-600 flex items-center justify-center">
                                <Calendar className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Pending Approvals</p>
                                <p className="text-3xl font-bold mt-2 text-yellow-600">
                                    {(analytics?.pendingClubs || 0) + (analytics?.pendingEvents || 0)}
                                </p>
                                <p className="text-xs text-gray-500 mt-1">
                                    {analytics?.pendingClubs || 0} Clubs, {analytics?.pendingEvents || 0} Events
                                </p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-yellow-500 to-yellow-600 flex items-center justify-center">
                                <CheckCircle className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>
                </div>

                {/* Quick Actions */}
                <div className="card p-6 mb-8">
                    <h2 className="text-xl font-bold text-gray-900 mb-4">Quick Actions</h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
                        <Link to="/admin/club-approval" className="btn-primary flex items-center justify-center space-x-2">
                            <Building className="w-5 h-5" />
                            <span>Approve Clubs</span>
                        </Link>
                        <Link to="/admin/event-approval" className="btn-primary flex items-center justify-center space-x-2">
                            <CheckCircle className="w-5 h-5" />
                            <span>Approve Events</span>
                        </Link>
                        <Link to="/admin/users" className="btn-primary flex items-center justify-center space-x-2">
                            <UserCog className="w-5 h-5" />
                            <span>Manage Users</span>
                        </Link>
                        <Link to="/admin/all-clubs" className="btn-outline flex items-center justify-center space-x-2">
                            <Building className="w-5 h-5" />
                            <span>All Clubs</span>
                        </Link>
                        <Link to="/admin/all-events" className="btn-outline flex items-center justify-center space-x-2">
                            <Calendar className="w-5 h-5" />
                            <span>All Events</span>
                        </Link>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                    {/* Recent Clubs */}
                    <div className="card p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold text-gray-900">Recent Clubs</h2>
                            <Link to="/admin/all-clubs" className="text-sm text-blue-600 hover:text-blue-700">
                                View All →
                            </Link>
                        </div>
                        <div className="space-y-3">
                            {recentClubs.length === 0 ? (
                                <p className="text-gray-500 text-center py-4">No clubs yet</p>
                            ) : (
                                recentClubs.map((club) => (
                                    <div key={club.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                        <div>
                                            <p className="font-medium text-gray-900">{club.name}</p>
                                            <p className="text-sm text-gray-500">{club.category} • {club.memberCount} members</p>
                                        </div>
                                        <span className={`px-2 py-1 text-xs rounded-full ${club.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                                            club.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                                                'bg-red-100 text-red-700'
                                            }`}>
                                            {club.status}
                                        </span>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    {/* Recent Events */}
                    <div className="card p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold text-gray-900">Recent Events</h2>
                            <Link to="/admin/all-events" className="text-sm text-blue-600 hover:text-blue-700">
                                View All →
                            </Link>
                        </div>
                        <div className="space-y-3">
                            {recentEvents.length === 0 ? (
                                <p className="text-gray-500 text-center py-4">No events yet</p>
                            ) : (
                                recentEvents.map((event) => (
                                    <div key={event.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                        <div>
                                            <p className="font-medium text-gray-900">{event.name}</p>
                                            <p className="text-sm text-gray-500">{event.clubName} • {event.registrationCount} registrations</p>
                                        </div>
                                        <div className="flex flex-col items-end">
                                            <span className={`px-2 py-1 text-xs rounded-full mb-1 ${event.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                                                    event.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                                                        'bg-red-100 text-red-700'
                                                }`}>
                                                {event.status}
                                            </span>
                                            <span className="text-sm text-gray-600">
                                                {new Date(event.dateTime).toLocaleDateString()}
                                            </span>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default AdminDashboard;
