import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import { CheckCircle, XCircle, Clock } from 'lucide-react';

const MembershipRequests = () => {
    const { user } = useAuth();
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [userClubs, setUserClubs] = useState([]);
    const [selectedClubId, setSelectedClubId] = useState(null);

    useEffect(() => {
        fetchUserClubs();
    }, []);

    useEffect(() => {
        if (selectedClubId) {
            fetchRequests();
        }
    }, [selectedClubId]);

    const fetchUserClubs = async () => {
        try {
            // Get all clubs where user is a club head
            const response = await api.get('/clubs');
            // Filter clubs where current user is the creator/head
            // For now, we'll get all clubs and let user select
            // In production, you'd have an endpoint like /clubs/my-clubs
            setUserClubs(response.data);
            if (response.data.length > 0) {
                setSelectedClubId(response.data[0].id);
            }
        } catch (error) {
            console.error('Error fetching clubs:', error);
        }
    };

    const fetchRequests = async () => {
        if (!selectedClubId) return;

        setLoading(true);
        try {
            const response = await api.get(`/membership/clubs/${selectedClubId}/requests/pending`);
            setRequests(response.data);
        } catch (error) {
            console.error('Error fetching requests:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (requestId) => {
        try {
            await api.put(`/membership/requests/${requestId}/approve`);
            alert('Request approved!');
            fetchRequests();
        } catch (error) {
            alert(error.response?.data?.message || 'Failed to approve request');
        }
    };

    const handleReject = async (requestId) => {
        const reason = prompt('Enter rejection reason (optional):');
        try {
            await api.put(`/membership/requests/${requestId}/reject`, { reason });
            alert('Request rejected');
            fetchRequests();
        } catch (error) {
            alert(error.response?.data?.message || 'Failed to reject request');
        }
    };

    if (loading && !selectedClubId) {
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
                    <h1 className="text-3xl font-bold gradient-text mb-2">Membership Requests</h1>
                    <p className="text-gray-600">Review and manage club membership applications</p>
                </div>

                {/* Club Selector */}
                {userClubs.length > 0 && (
                    <div className="card p-4 mb-6">
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Select Club
                        </label>
                        <select
                            value={selectedClubId || ''}
                            onChange={(e) => setSelectedClubId(Number(e.target.value))}
                            className="input-field"
                        >
                            {userClubs.map((club) => (
                                <option key={club.id} value={club.id}>
                                    {club.name}
                                </option>
                            ))}
                        </select>
                    </div>
                )}

                {loading ? (
                    <div className="flex justify-center py-12">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
                    </div>
                ) : requests.length === 0 ? (
                    <div className="card p-12 text-center">
                        <Clock className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500">No pending requests for this club</p>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {requests.map((request) => (
                            <div key={request.id} className="card p-6">
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <h3 className="text-lg font-semibold text-gray-900">
                                            {request.studentName}
                                        </h3>
                                        <p className="text-sm text-gray-600 mt-1">{request.studentEmail}</p>

                                        {/* Student Details */}
                                        <div className="mt-3 grid grid-cols-3 gap-4 text-sm">
                                            {request.studentDepartment && (
                                                <div>
                                                    <span className="font-medium text-gray-700">Department: </span>
                                                    <span className="text-gray-600">{request.studentDepartment}</span>
                                                </div>
                                            )}
                                            {request.studentYear && (
                                                <div>
                                                    <span className="font-medium text-gray-700">Year: </span>
                                                    <span className="text-gray-600">{request.studentYear}</span>
                                                </div>
                                            )}
                                            {request.studentPhone && (
                                                <div>
                                                    <span className="font-medium text-gray-700">Phone: </span>
                                                    <span className="text-gray-600">{request.studentPhone}</span>
                                                </div>
                                            )}
                                        </div>

                                        <div className="mt-4 space-y-2">
                                            {request.areaOfInterest && (
                                                <div>
                                                    <span className="text-sm font-medium text-gray-700">Interest: </span>
                                                    <span className="text-sm text-gray-600">{request.areaOfInterest}</span>
                                                </div>
                                            )}
                                            {request.skills && (
                                                <div>
                                                    <span className="text-sm font-medium text-gray-700">Skills: </span>
                                                    <span className="text-sm text-gray-600">{request.skills}</span>
                                                </div>
                                            )}
                                            {request.reason && (
                                                <div>
                                                    <span className="text-sm font-medium text-gray-700">Reason: </span>
                                                    <span className="text-sm text-gray-600">{request.reason}</span>
                                                </div>
                                            )}
                                        </div>

                                        <p className="text-xs text-gray-500 mt-4">
                                            Requested on {new Date(request.requestedAt).toLocaleDateString()}
                                        </p>
                                    </div>

                                    <div className="flex space-x-2 ml-4">
                                        <button
                                            onClick={() => handleApprove(request.id)}
                                            className="btn-primary flex items-center space-x-2 px-4 py-2"
                                        >
                                            <CheckCircle className="w-4 h-4" />
                                            <span>Approve</span>
                                        </button>
                                        <button
                                            onClick={() => handleReject(request.id)}
                                            className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 flex items-center space-x-2"
                                        >
                                            <XCircle className="w-4 h-4" />
                                            <span>Reject</span>
                                        </button>
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

export default MembershipRequests;
