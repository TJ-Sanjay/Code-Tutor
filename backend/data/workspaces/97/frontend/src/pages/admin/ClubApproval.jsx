import React, { useState, useEffect } from 'react';
import api from '../../services/api';
import { CheckCircle, XCircle, Users } from 'lucide-react';

const ClubApproval = () => {
    const [clubs, setClubs] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchPendingClubs();
    }, []);

    const fetchPendingClubs = async () => {
        try {
            const response = await api.get('/admin/clubs/pending');
            setClubs(response.data);
        } catch (error) {
            console.error('Error fetching clubs:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (clubId) => {
        try {
            await api.put(`/admin/clubs/${clubId}/approve`);
            alert('Club approved successfully!');
            fetchPendingClubs();
        } catch (error) {
            alert('Failed to approve club');
        }
    };

    const handleReject = async (clubId) => {
        if (window.confirm('Are you sure you want to reject this club?')) {
            try {
                await api.put(`/admin/clubs/${clubId}/reject`);
                alert('Club rejected');
                fetchPendingClubs();
            } catch (error) {
                alert('Failed to reject club');
            }
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
                <div className="mb-8">
                    <h1 className="text-3xl font-bold gradient-text mb-2">Club Approvals</h1>
                    <p className="text-gray-600">Review and approve new club applications</p>
                </div>

                {clubs.length === 0 ? (
                    <div className="card p-12 text-center">
                        <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500">No pending club approvals</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {clubs.map((club) => (
                            <div key={club.id} className="card p-6">
                                <div className="flex items-start justify-between mb-4">
                                    <div>
                                        <h3 className="text-xl font-bold text-gray-900">{club.name}</h3>
                                        <span className="badge badge-primary mt-2">{club.category}</span>
                                    </div>
                                </div>

                                <p className="text-gray-600 mb-4">{club.description}</p>

                                <div className="space-y-2 text-sm text-gray-600 mb-4">
                                    {club.contactEmail && (
                                        <p><span className="font-medium">Email:</span> {club.contactEmail}</p>
                                    )}
                                    {club.contactPhone && (
                                        <p><span className="font-medium">Phone:</span> {club.contactPhone}</p>
                                    )}
                                    {club.createdBy && (
                                        <p><span className="font-medium">Created by:</span> {club.createdBy.fullName}</p>
                                    )}
                                </div>

                                <div className="flex space-x-2">
                                    <button
                                        onClick={() => handleApprove(club.id)}
                                        className="btn-primary flex-1 flex items-center justify-center space-x-2"
                                    >
                                        <CheckCircle className="w-4 h-4" />
                                        <span>Approve</span>
                                    </button>
                                    <button
                                        onClick={() => handleReject(club.id)}
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

export default ClubApproval;
