import React, { useState, useEffect } from 'react';
import api from '../../services/api';
import { CheckCircle, XCircle, Calendar, MapPin } from 'lucide-react';
import { format } from 'date-fns';

const EventApproval = () => {
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [rejectReason, setRejectReason] = useState({});

    useEffect(() => {
        fetchPendingEvents();
    }, []);

    const fetchPendingEvents = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/events/pending');
            setEvents(response.data);
        } catch (error) {
            console.error('Error fetching events:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (eventId) => {
        try {
            await api.put(`/admin/events/${eventId}/approve`);
            alert('Event approved successfully!');
            fetchPendingEvents();
        } catch (error) {
            alert('Failed to approve event');
        }
    };

    const handleReject = async (eventId) => {
        const reason = rejectReason[eventId] || "Not specified";
        if (window.confirm('Are you sure you want to reject this event?')) {
            try {
                await api.put(`/admin/events/${eventId}/reject`, { reason });
                alert('Event rejected');
                fetchPendingEvents();
            } catch (error) {
                alert('Failed to reject event');
            }
        }
    };

    const handleReasonChange = (eventId, value) => {
        setRejectReason(prev => ({
            ...prev,
            [eventId]: value
        }));
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
                <div className="mb-8">
                    <h1 className="text-3xl font-bold gradient-text mb-2">Event Approvals</h1>
                    <p className="text-gray-600">Review and approve new event submissions from clubs</p>
                </div>

                {events.length === 0 ? (
                    <div className="card p-12 text-center">
                        <Calendar className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500">No pending event approvals</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {events.map((event) => (
                            <div key={event.id} className="card p-6">
                                <div className="flex items-start justify-between mb-4">
                                    <div>
                                        <h3 className="text-xl font-bold text-gray-900">{event.name}</h3>
                                        <span className="badge badge-purple mt-2">{event.clubName}</span>
                                    </div>
                                    <div className="text-right">
                                        <p className="text-lg font-bold text-primary-600">
                                            {event.entryFee > 0 ? `₹${event.entryFee}` : 'Free'}
                                        </p>
                                    </div>
                                </div>

                                <div className="space-y-2 mb-4">
                                    <div className="flex items-center text-gray-600 text-sm">
                                        <Calendar className="w-4 h-4 mr-2" />
                                        {format(new Date(event.dateTime), 'PPP p')}
                                    </div>
                                    <div className="flex items-center text-gray-600 text-sm">
                                        <MapPin className="w-4 h-4 mr-2" />
                                        {event.venue}
                                    </div>
                                </div>

                                <p className="text-gray-600 text-sm mb-4 line-clamp-3">{event.description}</p>

                                <div className="mb-4">
                                    <label className="block text-xs font-medium text-gray-700 mb-1">
                                        Rejection Reason (if applicable)
                                    </label>
                                    <input
                                        type="text"
                                        placeholder="Reason for rejection..."
                                        value={rejectReason[event.id] || ''}
                                        onChange={(e) => handleReasonChange(event.id, e.target.value)}
                                        className="input-field text-sm"
                                    />
                                </div>

                                <div className="flex space-x-2">
                                    <button
                                        onClick={() => handleApprove(event.id)}
                                        className="btn-primary flex-1 flex items-center justify-center space-x-2"
                                    >
                                        <CheckCircle className="w-4 h-4" />
                                        <span>Approve</span>
                                    </button>
                                    <button
                                        onClick={() => handleReject(event.id)}
                                        className="bg-red-600 text-white flex-1 rounded-lg hover:bg-red-700 flex items-center justify-center space-x-2"
                                    >
                                        <XCircle className="w-4 h-4" />
                                        <span>Reject</span>
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default EventApproval;
