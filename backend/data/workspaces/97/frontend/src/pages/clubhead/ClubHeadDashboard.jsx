import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Users, Calendar, CheckCircle, Plus, FileText, Settings, Edit } from 'lucide-react';
import api from '../../services/api';

const ClubHeadDashboard = () => {
    const [myClubs, setMyClubs] = useState([]);
    const [myEvents, setMyEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [stats, setStats] = useState({
        totalMembers: 0,
        pendingRequests: 0,
        upcomingEvents: 0
    });

    useEffect(() => {
        fetchDashboardData();
    }, []);

    const fetchDashboardData = async () => {
        try {
            setLoading(true);
            const [clubsRes, eventsRes, pendingRes] = await Promise.all([
                api.get('/clubs/my-clubs'),
                api.get('/events/my-events'),
                api.get('/membership/count/pending')
            ]);

            setMyClubs(clubsRes.data);
            setMyEvents(eventsRes.data);

            // Calculate total members from all clubs
            const totalMembers = clubsRes.data.reduce((sum, club) => sum + (club.memberCount || 0), 0);

            // Calculate upcoming events with 24h buffer
            const now = new Date();
            const bufferTime = new Date(now.getTime() - (24 * 60 * 60 * 1000));
            const upcomingCount = eventsRes.data.filter(event =>
                new Date(event.dateTime) > bufferTime && event.status === 'APPROVED'
            ).length;

            setStats({
                totalMembers,
                pendingRequests: pendingRes.data,
                upcomingEvents: upcomingCount
            });
        } catch (err) {
            console.error('Error fetching dashboard data:', err);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold gradient-text mb-2">Club Head Dashboard</h1>
                    <p className="text-gray-600">Manage your clubs and events</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Total Members</p>
                                <p className="text-3xl font-bold mt-2">{stats.totalMembers}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-blue-500 to-blue-600 flex items-center justify-center">
                                <Users className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Pending Requests</p>
                                <p className="text-3xl font-bold mt-2 text-yellow-600">{stats.pendingRequests}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-yellow-500 to-yellow-600 flex items-center justify-center">
                                <CheckCircle className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Upcoming Events</p>
                                <p className="text-3xl font-bold mt-2">{stats.upcomingEvents}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-purple-500 to-purple-600 flex items-center justify-center">
                                <Calendar className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>
                </div>

                <div className="card p-6 mb-8">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">Quick Actions</h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        <Link to="/clubhead/create-club" className="btn-primary flex items-center justify-center space-x-2">
                            <Plus className="w-5 h-5" />
                            <span>Create Club</span>
                        </Link>
                        <Link to="/clubhead/create-event" className="btn-primary flex items-center justify-center space-x-2">
                            <Calendar className="w-5 h-5" />
                            <span>Create Event</span>
                        </Link>
                        <Link to="/clubhead/membership-requests" className="btn-outline flex items-center justify-center space-x-2">
                            <FileText className="w-5 h-5" />
                            <span>View Requests</span>
                        </Link>
                        <Link to="/clubs" className="btn-outline flex items-center justify-center space-x-2">
                            <Users className="w-5 h-5" />
                            <span>Browse Clubs</span>
                        </Link>
                    </div>
                </div>

                {/* My Clubs Section */}
                <div className="card p-6 mb-8">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">My Clubs</h2>
                    {loading ? (
                        <div className="flex justify-center py-8">
                            <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500"></div>
                        </div>
                    ) : myClubs.length === 0 ? (
                        <div className="text-center py-8">
                            <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                            <p className="text-gray-500">You don't manage any clubs yet.</p>
                            <Link to="/clubhead/create-club" className="btn-primary mt-4 inline-flex items-center">
                                <Plus className="w-5 h-5 mr-2" />
                                Create Your First Club
                            </Link>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {myClubs.map((club) => (
                                <div key={club.id} className="border border-gray-200 rounded-xl p-4 hover:shadow-lg transition-shadow">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold text-lg text-gray-900">{club.name}</h3>
                                        <span className={`px-2 py-1 text-xs rounded-full ${club.status === 'ACTIVE'
                                            ? 'bg-green-100 text-green-700'
                                            : 'bg-yellow-100 text-yellow-700'
                                            }`}>
                                            {club.status}
                                        </span>
                                    </div>
                                    <p className="text-gray-600 text-sm mb-3 line-clamp-2">
                                        {club.description || 'No description available'}
                                    </p>
                                    <div className="flex items-center text-gray-500 text-sm mb-4">
                                        <Users className="w-4 h-4 mr-1" />
                                        <span>{club.memberCount || 0} members</span>
                                    </div>
                                    <div className="flex space-x-2">
                                        <Link
                                            to={`/clubhead/clubs/${club.id}/members`}
                                            className="flex-1 btn-primary flex items-center justify-center space-x-1 text-sm py-2"
                                        >
                                            <Settings className="w-4 h-4" />
                                            <span>Members</span>
                                        </Link>
                                        <Link
                                            to={`/clubhead/clubs/${club.id}/edit`}
                                            className="flex-1 btn-outline flex items-center justify-center space-x-1 text-sm py-2"
                                        >
                                            <Edit className="w-4 h-4" />
                                            <span>Edit</span>
                                        </Link>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* My Events Section */}
                <div className="card p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-2xl font-bold text-gray-900">My Events</h2>
                        <Link to="/clubhead/my-events" className="text-primary-600 hover:text-primary-700 font-medium">
                            View All →
                        </Link>
                    </div>
                    {loading ? (
                        <div className="flex justify-center py-8">
                            <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500"></div>
                        </div>
                    ) : myEvents.length === 0 ? (
                        <div className="text-center py-8">
                            <Calendar className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                            <p className="text-gray-500">You haven't created any events yet.</p>
                            <Link to="/clubhead/create-event" className="btn-primary mt-4 inline-flex items-center">
                                <Plus className="w-5 h-5 mr-2" />
                                Create Your First Event
                            </Link>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {myEvents.slice(0, 6).map((event) => (
                                <div key={event.id} className="border border-gray-200 rounded-xl p-4 hover:shadow-lg transition-shadow">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold text-lg text-gray-900">{event.name}</h3>
                                        <span className={`px-2 py-1 text-xs rounded-full ${event.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                                                event.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                                                    'bg-red-100 text-red-700'
                                            }`}>
                                            {event.status}
                                        </span>
                                    </div>
                                    <p className="text-gray-600 text-sm mb-3">
                                        {event.clubName}
                                    </p>
                                    <div className="flex items-center text-gray-500 text-sm mb-4">
                                        <Users className="w-4 h-4 mr-1" />
                                        <span>{event.registrationCount || 0} registrations</span>
                                    </div>
                                    <div className="flex space-x-2">
                                        <Link
                                            to={`/events/${event.id}`}
                                            className="flex-1 btn-primary flex items-center justify-center space-x-1 text-sm py-2"
                                        >
                                            <FileText className="w-4 h-4" />
                                            <span>View Details</span>
                                        </Link>
                                        <Link
                                            to={`/clubhead/events/${event.id}/edit`}
                                            className="flex-1 btn-outline flex items-center justify-center space-x-1 text-sm py-2"
                                        >
                                            <Edit className="w-4 h-4" />
                                            <span>Edit</span>
                                        </Link>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ClubHeadDashboard;
