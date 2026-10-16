import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Users, Mail, Phone, Building, Calendar, Trash2, CheckCircle, Clock } from 'lucide-react';
import api from '../../services/api';

const EventRegistrations = () => {
    const { eventId } = useParams();
    const navigate = useNavigate();
    const [registrations, setRegistrations] = useState([]);
    const [eventName, setEventName] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [removing, setRemoving] = useState(null);

    useEffect(() => {
        fetchRegistrations();
    }, [eventId]);

    const fetchRegistrations = async () => {
        try {
            setLoading(true);
            const response = await api.get(`/events/${eventId}/registrations`);
            setRegistrations(response.data);
            if (response.data.length > 0) {
                setEventName(response.data[0].eventName);
            }
            setError('');
        } catch (err) {
            console.error('Error fetching registrations:', err);
            setError(err.response?.data?.message || 'Failed to load registrations');
        } finally {
            setLoading(false);
        }
    };

    const handleRemoveRegistration = async (registrationId, userName) => {
        if (!window.confirm(`Are you sure you want to remove ${userName} from this event?`)) {
            return;
        }

        try {
            setRemoving(registrationId);
            await api.delete(`/events/registrations/${registrationId}`);
            setRegistrations(registrations.filter(r => r.id !== registrationId));
        } catch (err) {
            console.error('Error removing registration:', err);
            alert(err.response?.data?.message || 'Failed to remove registration');
        } finally {
            setRemoving(null);
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
                    onClick={() => navigate('/clubhead/my-events')}
                    className="flex items-center text-gray-600 hover:text-gray-900 mb-4 transition-colors"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back to My Events
                </button>

                <div className="flex items-center justify-between mb-8">
                    <div>
                        <h1 className="text-3xl font-bold gradient-text mb-2">Event Registrations</h1>
                        <p className="text-gray-600">{eventName || `Event #${eventId}`}</p>
                    </div>
                    <div className="flex items-center space-x-2 bg-white px-4 py-2 rounded-xl shadow-sm">
                        <Users className="w-5 h-5 text-blue-600" />
                        <span className="font-semibold text-gray-900">{registrations.length} Registered</span>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600">
                        {error}
                    </div>
                )}

                {registrations.length === 0 ? (
                    <div className="card p-12 text-center">
                        <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500 text-lg">No registrations yet</p>
                    </div>
                ) : (
                    <div className="card overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead className="bg-gray-50 border-b">
                                    <tr>
                                        <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">Participant</th>
                                        <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">Contact</th>
                                        <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">Department</th>
                                        <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">Payment</th>
                                        <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">Registered</th>
                                        <th className="px-6 py-4 text-center text-sm font-semibold text-gray-600">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {registrations.map((reg) => (
                                        <tr key={reg.id} className="hover:bg-gray-50 transition-colors">
                                            <td className="px-6 py-4">
                                                <div className="flex items-center space-x-3">
                                                    <div className="w-10 h-10 rounded-full bg-gradient-to-r from-blue-500 to-purple-500 flex items-center justify-center text-white font-semibold">
                                                        {reg.userName?.charAt(0) || '?'}
                                                    </div>
                                                    <div>
                                                        <p className="font-medium text-gray-900">{reg.userName}</p>
                                                        {reg.userYear && (
                                                            <p className="text-sm text-gray-500">Year {reg.userYear}</p>
                                                        )}
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="space-y-1">
                                                    <div className="flex items-center text-sm text-gray-600">
                                                        <Mail className="w-4 h-4 mr-2 text-gray-400" />
                                                        {reg.userEmail}
                                                    </div>
                                                    {reg.userPhone && (
                                                        <div className="flex items-center text-sm text-gray-600">
                                                            <Phone className="w-4 h-4 mr-2 text-gray-400" />
                                                            {reg.userPhone}
                                                        </div>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center text-sm text-gray-600">
                                                    <Building className="w-4 h-4 mr-2 text-gray-400" />
                                                    {reg.userDepartment || 'N/A'}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                {reg.paymentRequired ? (
                                                    reg.paymentCompleted ? (
                                                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                                            <CheckCircle className="w-3 h-3 mr-1" />
                                                            Paid
                                                        </span>
                                                    ) : (
                                                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                                                            <Clock className="w-3 h-3 mr-1" />
                                                            Pending
                                                        </span>
                                                    )
                                                ) : (
                                                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600">
                                                        Free
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center text-sm text-gray-600">
                                                    <Calendar className="w-4 h-4 mr-2 text-gray-400" />
                                                    {formatDate(reg.registeredAt)}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <button
                                                    onClick={() => handleRemoveRegistration(reg.id, reg.userName)}
                                                    disabled={removing === reg.id}
                                                    className="inline-flex items-center px-3 py-1.5 text-sm text-red-600 hover:text-red-700 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                                                >
                                                    {removing === reg.id ? (
                                                        <div className="animate-spin rounded-full h-4 w-4 border-2 border-red-600 border-t-transparent"></div>
                                                    ) : (
                                                        <>
                                                            <Trash2 className="w-4 h-4 mr-1" />
                                                            Remove
                                                        </>
                                                    )}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default EventRegistrations;
