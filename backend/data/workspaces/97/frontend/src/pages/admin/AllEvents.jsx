import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Calendar, Users, Search, MapPin, DollarSign, Settings, Power, PowerOff, CheckCircle, XCircle } from 'lucide-react';
import api from '../../services/api';

const AllEvents = () => {
    const navigate = useNavigate();
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState('ALL');

    useEffect(() => {
        fetchEvents();
    }, []);

    const fetchEvents = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/reports/events');
            setEvents(response.data);
        } catch (error) {
            console.error('Error fetching events:', error);
        } finally {
            setLoading(false);
        }
    };

    const filteredEvents = events.filter(event => {
        const matchesSearch = event.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            event.clubName?.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesStatus = filterStatus === 'ALL' || event.status === filterStatus;
        return matchesSearch && matchesStatus;
    });

    const handleApprove = async (eventId) => {
        try {
            await api.put(`/admin/events/${eventId}/approve`);
            alert('Event approved successfully!');
            fetchEvents();
        } catch (error) {
            alert('Failed to approve event');
        }
    };

    const handleReject = async (eventId) => {
        const reason = window.prompt('Reason for rejection:');
        if (reason !== null) {
            try {
                await api.put(`/admin/events/${eventId}/reject`, { reason });
                alert('Event rejected');
                fetchEvents();
            } catch (error) {
                alert('Failed to reject event');
            }
        }
    };

    const handleDeactivate = async (eventId) => {
        if (window.confirm('Are you sure you want to deactivate this event?')) {
            try {
                await api.put(`/admin/events/${eventId}/deactivate`);
                alert('Event deactivated');
                fetchEvents();
            } catch (error) {
                alert('Failed to deactivate event');
            }
        }
    };

    const handleActivate = async (eventId) => {
        try {
            await api.put(`/admin/events/${eventId}/activate`);
            alert('Event activated');
            fetchEvents();
        } catch (error) {
            alert('Failed to activate event');
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

    const isPastEvent = (dateString) => {
        return new Date(dateString) < new Date();
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
                <button
                    onClick={() => navigate('/admin/dashboard')}
                    className="flex items-center text-gray-600 hover:text-gray-900 mb-4 transition-colors"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back to Dashboard
                </button>

                <div className="flex items-center justify-between mb-8">
                    <div>
                        <h1 className="text-3xl font-bold gradient-text mb-2">All Events</h1>
                        <p className="text-gray-600">View all events in the system ({events.length} total)</p>
                    </div>
                </div>

                {/* Search and Filter */}
                <div className="card p-4 mb-6">
                    <div className="flex flex-col md:flex-row gap-4">
                        <div className="flex-1 relative">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
                            <input
                                type="text"
                                placeholder="Search events or clubs..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="input-field pl-10"
                            />
                        </div>
                        <select
                            value={filterStatus}
                            onChange={(e) => setFilterStatus(e.target.value)}
                            className="input-field md:w-48"
                        >
                            <option value="ALL">All Status</option>
                            <option value="APPROVED">Approved</option>
                            <option value="PENDING">Pending</option>
                            <option value="INACTIVE">Inactive</option>
                            <option value="REJECTED">Rejected</option>
                        </select>
                    </div>
                </div>

                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Event</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Club</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date & Time</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Registrations</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Revenue</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {filteredEvents.map((event) => (
                                    <tr key={event.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center">
                                                <div className="w-10 h-10 bg-gradient-to-r from-purple-500 to-pink-500 rounded-lg flex items-center justify-center">
                                                    <Calendar className="w-5 h-5 text-white" />
                                                </div>
                                                <div className="ml-4">
                                                    <p className="font-medium text-gray-900">{event.name}</p>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600">{event.clubName}</td>
                                        <td className="px-6 py-4 text-sm text-gray-600">
                                            {formatDate(event.dateTime)}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center text-sm text-gray-600">
                                                <Users className="w-4 h-4 mr-1" />
                                                {event.registrationCount}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center text-sm text-green-600 font-medium">
                                                <DollarSign className="w-4 h-4" />
                                                {event.revenue?.toFixed(2) || '0.00'}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`px-2 py-1 text-xs rounded-full ${event.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
                                                event.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                                                    'bg-red-100 text-red-700'
                                                }`}>
                                                {event.status}
                                            </span>
                                            {isPastEvent(event.dateTime) && (
                                                <span className="ml-2 px-2 py-1 text-xs rounded-full bg-gray-100 text-gray-600">
                                                    Past
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center justify-center space-x-2">
                                                {event.status === 'PENDING' && (
                                                    <>
                                                        <button
                                                            onClick={() => handleApprove(event.id)}
                                                            className="p-2 text-green-600 hover:bg-green-50 rounded-lg"
                                                            title="Approve"
                                                        >
                                                            <CheckCircle className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleReject(event.id)}
                                                            className="p-2 text-red-600 hover:bg-red-50 rounded-lg"
                                                            title="Reject"
                                                        >
                                                            <XCircle className="w-4 h-4" />
                                                        </button>
                                                    </>
                                                )}
                                                {event.status === 'APPROVED' && (
                                                    <button
                                                        onClick={() => handleDeactivate(event.id)}
                                                        className="p-2 text-orange-600 hover:bg-orange-50 rounded-lg"
                                                        title="Deactivate"
                                                    >
                                                        <PowerOff className="w-4 h-4" />
                                                    </button>
                                                )}
                                                {(event.status === 'INACTIVE' || event.status === 'REJECTED') && (
                                                    <button
                                                        onClick={() => handleActivate(event.id)}
                                                        className="p-2 text-green-600 hover:bg-green-50 rounded-lg"
                                                        title="Activate"
                                                    >
                                                        <Power className="w-4 h-4" />
                                                    </button>
                                                )}
                                                <Link
                                                    to={`/clubhead/events/${event.id}/edit`}
                                                    className="p-2 text-purple-600 hover:bg-purple-50 rounded-lg"
                                                    title="Edit Event"
                                                >
                                                    <Settings className="w-4 h-4" />
                                                </Link>
                                                <Link
                                                    to={`/events/${event.id}`}
                                                    className="text-blue-600 hover:text-blue-700 text-sm font-medium"
                                                >
                                                    View
                                                </Link>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default AllEvents;
