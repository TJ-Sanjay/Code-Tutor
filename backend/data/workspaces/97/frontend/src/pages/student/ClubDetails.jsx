import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import { Users, Mail, Phone, Calendar, MapPin, ArrowLeft, Send, LogOut } from 'lucide-react';

const ClubDetails = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [club, setClub] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isMember, setIsMember] = useState(false);
    const [showJoinModal, setShowJoinModal] = useState(false);
    const [joinRequest, setJoinRequest] = useState({
        areaOfInterest: '',
        skills: '',
        reason: '',
        preferredRole: ''
    });

    useEffect(() => {
        fetchClubDetails();
    }, [id]);

    const fetchClubDetails = async () => {
        try {
            const response = await api.get(`/clubs/${id}`);
            setClub(response.data);
            // Check if current user is a member
            if (response.data.isMember !== undefined) {
                setIsMember(response.data.isMember);
            }
        } catch (error) {
            console.error('Error fetching club details:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleJoinRequest = async (e) => {
        e.preventDefault();
        try {
            await api.post(`/clubs/${id}/join-request`, joinRequest);
            alert('Join request submitted successfully!');
            setShowJoinModal(false);
        } catch (error) {
            alert(error.response?.data?.message || 'Failed to submit join request');
        }
    };

    const handleLeaveClub = async () => {
        const confirmed = window.confirm('Are you sure you want to leave this club? You will need to request to join again.');
        if (!confirmed) return;

        try {
            await api.delete(`/clubs/${id}/leave`);
            alert('You have left the club successfully');
            setIsMember(false);
            fetchClubDetails();
        } catch (error) {
            alert(error.response?.data?.message || 'Failed to leave club');
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
            </div>
        );
    }

    if (!club) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <p className="text-gray-500">Club not found</p>
            </div>
        );
    }

    const isClubHead = user?.roles?.includes('CLUB_HEAD') || user?.roles?.includes('ADMIN');

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
            <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
                <button
                    onClick={() => navigate(-1)}
                    className="flex items-center text-gray-600 hover:text-primary-600 mb-6"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back to Clubs
                </button>

                {/* Club Header */}
                <div className="card mb-8 overflow-hidden">
                    <div className="h-64 bg-gradient-to-r from-primary-500 to-secondary-500 flex items-center justify-center">
                        {club.bannerUrl ? (
                            <img src={club.bannerUrl} alt={club.name} className="w-full h-full object-cover" />
                        ) : (
                            <Users className="w-24 h-24 text-white" />
                        )}
                    </div>
                    <div className="p-8">
                        <div className="flex items-start justify-between mb-4">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900 mb-2">{club.name}</h1>
                                <span className="badge badge-primary">{club.category}</span>
                                {isMember && <span className="badge badge-success ml-2">Member</span>}
                            </div>
                            <div className="flex space-x-2">
                                {isMember && !isClubHead && (
                                    <button
                                        onClick={handleLeaveClub}
                                        className="btn-outline text-red-600 border-red-300 hover:bg-red-50 flex items-center space-x-2"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        <span>Leave Club</span>
                                    </button>
                                )}
                                {!isMember && !isClubHead && (
                                    <button
                                        onClick={() => setShowJoinModal(true)}
                                        className="btn-primary"
                                    >
                                        Request to Join
                                    </button>
                                )}
                            </div>
                        </div>
                        <p className="text-gray-600 mb-6">{club.description}</p>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                            <div className="flex items-center text-gray-600">
                                <Users className="w-5 h-5 mr-2" />
                                {club.memberCount || 0} Members
                            </div>
                            {club.contactEmail && (
                                <div className="flex items-center text-gray-600">
                                    <Mail className="w-5 h-5 mr-2" />
                                    {club.contactEmail}
                                </div>
                            )}
                            {club.contactPhone && (
                                <div className="flex items-center text-gray-600">
                                    <Phone className="w-5 h-5 mr-2" />
                                    {club.contactPhone}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* Achievements */}
                {club.achievements && (
                    <div className="card p-8 mb-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">Achievements</h2>
                        <p className="text-gray-600">{club.achievements}</p>
                    </div>
                )}

                {/* Join Request Modal */}
                {showJoinModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
                        <div className="bg-white rounded-xl p-8 max-w-md w-full max-h-[90vh] overflow-y-auto">
                            <h3 className="text-2xl font-bold mb-4">Join {club.name}</h3>
                            <form onSubmit={handleJoinRequest} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Area of Interest
                                    </label>
                                    <textarea
                                        value={joinRequest.areaOfInterest}
                                        onChange={(e) => setJoinRequest({ ...joinRequest, areaOfInterest: e.target.value })}
                                        className="input-field"
                                        rows="2"
                                        placeholder="What interests you about this club?"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Skills
                                    </label>
                                    <textarea
                                        value={joinRequest.skills}
                                        onChange={(e) => setJoinRequest({ ...joinRequest, skills: e.target.value })}
                                        className="input-field"
                                        rows="2"
                                        placeholder="What skills can you bring?"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Why do you want to join? *
                                    </label>
                                    <textarea
                                        value={joinRequest.reason}
                                        onChange={(e) => setJoinRequest({ ...joinRequest, reason: e.target.value })}
                                        className="input-field"
                                        rows="3"
                                        placeholder="Tell us why you want to join..."
                                        required
                                    />
                                </div>
                                <div className="flex space-x-4">
                                    <button type="submit" className="btn-primary flex-1">
                                        <Send className="w-4 h-4 mr-2 inline" />
                                        Submit Request
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setShowJoinModal(false)}
                                        className="btn-outline flex-1"
                                    >
                                        Cancel
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ClubDetails;
