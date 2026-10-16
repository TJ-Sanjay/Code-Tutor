import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Calendar, MapPin, Clock, Users, Edit, ArrowLeft } from 'lucide-react';
import api from '../../services/api';

const MyEvents = () => {
    const navigate = useNavigate();
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchMyEvents();
    }, []);

    const fetchMyEvents = async () => {
        try {
            setLoading(true);
            const response = await api.get('/events/my-events');
            setEvents(response.data);
            setError('');
        } catch (err) {
            console.error('Error fetching events:', err);
            setError(err.response?.data?.message || 'Failed to load events');
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (dateString) => {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
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
                <button
                    onClick={() => navigate('/clubhead/dashboard')}
                    className="flex items-center text-gray-600 hover:text-gray-900 mb-4 transition-colors"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back to Dashboard
                </button>

                <div className="flex items-center justify-between mb-8">
                    <div>
                        <h1 className="text-3xl font-bold gradient-text mb-2">My Events</h1>
                        <p className="text-gray-600">Events from your clubs</p>
                    </div>
                    <Link to="/clubhead/create-event" className="btn-primary">
                        + Create Event
                    </Link>
                </div>

                {error && (
                    <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600">
                        {error}
                    </div>
                )}

                {events.length === 0 ? (
                    <div className="card p-12 text-center">
                        <Calendar className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500 text-lg mb-4">No events created yet</p>
                        <Link to="/clubhead/create-event" className="btn-primary">
                            Create Your First Event
                        </Link>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {events.map((event) => (
                            <div key={event.id} className="card overflow-hidden hover:shadow-lg transition-shadow">
                                <div className="h-32 bg-gradient-to-r from-purple-500 to-pink-500 flex items-center justify-center">
                                    <Calendar className="w-12 h-12 text-white" />
                                </div>
                                <div className="p-6">
                                    <div className="flex items-start justify-between mb-3">
                                        <h3 className="font-bold text-lg text-gray-900 line-clamp-1">{event.name}</h3>
                                        <span className={`px-2 py-1 text-xs rounded-full ${event.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                                                event.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                                                    'bg-red-100 text-red-700'
                                            }`}>
                                            {event.status}
                                        </span>
                                    </div>

                                    <p className="text-gray-500 text-sm mb-3">{event.clubName}</p>

                                    <div className="space-y-2 text-sm text-gray-600 mb-4">
                                        <div className="flex items-center">
                                            <Clock className="w-4 h-4 mr-2 text-gray-400" />
                                            <span>{formatDate(event.dateTime)}</span>
                                        </div>
                                        <div className="flex items-center">
                                            <MapPin className="w-4 h-4 mr-2 text-gray-400" />
                                            <span className="line-clamp-1">{event.venue}</span>
                                        </div>
                                        <div className="flex items-center">
                                            <Users className="w-4 h-4 mr-2 text-gray-400" />
                                            <span>
                                                {event.registrationCount} registered
                                                {event.maxParticipants && ` / ${event.maxParticipants}`}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="flex space-x-2">
                                        <Link
                                            to={`/clubhead/events/${event.id}/registrations`}
                                            className="flex-1 btn-outline flex items-center justify-center space-x-2 text-sm"
                                        >
                                            <Users className="w-4 h-4" />
                                            <span>Registrations</span>
                                        </Link>
                                        <Link
                                            to={`/clubhead/events/${event.id}/edit`}
                                            className="flex-1 btn-primary flex items-center justify-center space-x-2 text-sm"
                                        >
                                            <Edit className="w-4 h-4" />
                                            <span>Edit</span>
                                        </Link>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default MyEvents;
