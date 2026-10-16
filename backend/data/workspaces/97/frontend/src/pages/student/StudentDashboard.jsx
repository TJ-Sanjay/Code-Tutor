import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import { Users, Calendar, Bell, Clock, MapPin, CheckCircle, XCircle } from 'lucide-react';

const StudentDashboard = () => {
    const { user } = useAuth();
    const [myClubs, setMyClubs] = useState([]);
    const [pendingRequests, setPendingRequests] = useState([]);
    const [availableClubs, setAvailableClubs] = useState([]);
    const [registeredEvents, setRegisteredEvents] = useState([]);
    const [upcomingEvents, setUpcomingEvents] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchDashboardData();
    }, []);

    const fetchDashboardData = async () => {
        try {
            // Fetch all clubs
            const clubsRes = await api.get('/clubs');
            const allClubs = clubsRes.data;

            // Fetch my membership requests
            const requestsRes = await api.get('/membership/my-requests');
            const myRequests = requestsRes.data;

            // Separate clubs into categories
            const memberClubs = allClubs.filter(club => club.isMember === true);
            console.log('All clubs:', allClubs);
            console.log('Member clubs (isMember===true):', memberClubs);
            console.log('First club isMember value:', allClubs[0]?.isMember, typeof allClubs[0]?.isMember);
            const requestedClubIds = myRequests
                .filter(req => req.status === 'PENDING')
                .map(req => req.clubId);
            const pending = myRequests.filter(req => req.status === 'PENDING');
            const available = allClubs.filter(club =>
                !club.isMember && !requestedClubIds.includes(club.id)
            );

            setMyClubs(memberClubs);
            setPendingRequests(pending);
            setAvailableClubs(available);

            // Fetch all events
            const eventsRes = await api.get('/events');
            const allEvents = eventsRes.data;

            // Separate events into registered and upcoming
            const now = new Date();
            const bufferTime = new Date(now.getTime() - (24 * 60 * 60 * 1000)); // 24 hours ago

            const registered = allEvents.filter(event =>
                event.isRegistered === true
            );
            const upcoming = allEvents.filter(event =>
                event.isRegistered !== true && new Date(event.dateTime) > bufferTime
            ).slice(0, 3);

            setRegisteredEvents(registered);
            setUpcomingEvents(upcoming);
        } catch (error) {
            console.error('Error fetching dashboard data:', error);
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                {/* Welcome Section */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900">
                        Welcome back, <span className="gradient-text">{user?.fullName}!</span>
                    </h1>
                    <p className="text-gray-600 mt-2">Here's your club membership overview</p>
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">My Clubs</p>
                                <p className="text-3xl font-bold mt-2">{myClubs.length}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-green-500 to-green-600 flex items-center justify-center">
                                <CheckCircle className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>
                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Pending Requests</p>
                                <p className="text-3xl font-bold mt-2">{pendingRequests.length}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-yellow-500 to-yellow-600 flex items-center justify-center">
                                <Clock className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>
                    <div className="card p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-gray-600 text-sm font-medium">Available Clubs</p>
                                <p className="text-3xl font-bold mt-2">{availableClubs.length}</p>
                            </div>
                            <div className="w-12 h-12 rounded-lg bg-gradient-to-r from-blue-500 to-blue-600 flex items-center justify-center">
                                <Users className="w-6 h-6 text-white" />
                            </div>
                        </div>
                    </div>
                </div>

                {/* My Clubs */}
                {myClubs.length > 0 && (
                    <div className="card p-6 mb-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">My Clubs</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {myClubs.map((club) => (
                                <Link
                                    key={club.id}
                                    to={`/clubs/${club.id}`}
                                    className="p-4 bg-green-50 border border-green-200 rounded-lg hover:bg-green-100 transition-colors"
                                >
                                    <div className="flex items-start justify-between">
                                        <div>
                                            <h3 className="font-semibold text-gray-900">{club.name}</h3>
                                            <p className="text-sm text-gray-600 mt-1">{club.category}</p>
                                        </div>
                                        <CheckCircle className="w-5 h-5 text-green-600" />
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </div>
                )}

                {/* Pending Requests */}
                {pendingRequests.length > 0 && (
                    <div className="card p-6 mb-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">Pending Requests</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {pendingRequests.map((request) => (
                                <div
                                    key={request.id}
                                    className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg"
                                >
                                    <div className="flex items-start justify-between">
                                        <div>
                                            <h3 className="font-semibold text-gray-900">{request.clubName}</h3>
                                            <p className="text-sm text-gray-600 mt-1">
                                                Requested {new Date(request.requestedAt).toLocaleDateString()}
                                            </p>
                                        </div>
                                        <Clock className="w-5 h-5 text-yellow-600" />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Registered Events */}
                {registeredEvents.length > 0 && (
                    <div className="card p-6 mb-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">Registered Events</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {registeredEvents.map((event) => (
                                <Link
                                    key={event.id}
                                    to={`/events/${event.id}`}
                                    className="p-4 bg-purple-50 border border-purple-200 rounded-lg hover:bg-purple-100 transition-colors"
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <div className="flex-1">
                                            <h3 className="font-semibold text-gray-900">{event.name}</h3>
                                            <p className="text-sm text-gray-600 mt-1">{event.clubName}</p>
                                        </div>
                                        <CheckCircle className="w-5 h-5 text-purple-600" />
                                    </div>
                                    <div className="flex items-center text-xs text-gray-500 mt-2">
                                        <Clock className="w-3 h-3 mr-1" />
                                        {new Date(event.dateTime).toLocaleDateString()}
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </div>
                )}

                {/* Available Clubs */}
                <div className="card p-6 mb-8">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-2xl font-bold text-gray-900">Available Clubs</h2>
                        <Link to="/clubs" className="text-primary-600 hover:text-primary-700 font-medium">
                            View All →
                        </Link>
                    </div>
                    {availableClubs.length === 0 ? (
                        <div className="text-center py-12">
                            <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                            <p className="text-gray-500">You've joined all available clubs!</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {availableClubs.slice(0, 6).map((club) => (
                                <Link
                                    key={club.id}
                                    to={`/clubs/${club.id}`}
                                    className="p-4 bg-gray-50 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors"
                                >
                                    <h3 className="font-semibold text-gray-900">{club.name}</h3>
                                    <p className="text-sm text-gray-600 mt-1">{club.category}</p>
                                    <p className="text-xs text-gray-500 mt-2">{club.memberCount || 0} members</p>
                                </Link>
                            ))}
                        </div>
                    )}
                </div>

                {/* Upcoming Events */}
                {upcomingEvents.length > 0 && (
                    <div className="card p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-gray-900">Upcoming Events</h2>
                            <Link to="/events" className="text-primary-600 hover:text-primary-700 font-medium">
                                View All →
                            </Link>
                        </div>
                        <div className="space-y-4">
                            {upcomingEvents.map((event) => (
                                <Link
                                    key={event.id}
                                    to={`/events/${event.id}`}
                                    className="block p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <h3 className="font-semibold text-gray-900">{event.name}</h3>
                                            <p className="text-sm text-gray-600 mt-1">{event.clubName}</p>
                                            <div className="flex items-center space-x-4 mt-2 text-sm text-gray-500">
                                                <span className="flex items-center">
                                                    <Clock className="w-4 h-4 mr-1" />
                                                    {new Date(event.dateTime).toLocaleDateString()}
                                                </span>
                                                <span className="flex items-center">
                                                    <MapPin className="w-4 h-4 mr-1" />
                                                    {event.venue}
                                                </span>
                                            </div>
                                        </div>
                                        {event.entryFee > 0 && (
                                            <span className="badge badge-primary">₹{event.entryFee}</span>
                                        )}
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default StudentDashboard;
