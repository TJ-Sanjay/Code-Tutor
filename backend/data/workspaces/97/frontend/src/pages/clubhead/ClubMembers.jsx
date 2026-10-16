import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Users, Trash2, Search, ArrowLeft, Mail, Phone, GraduationCap, Calendar } from 'lucide-react';
import api from '../../services/api';

const ClubMembers = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [club, setClub] = useState(null);
    const [members, setMembers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [memberToRemove, setMemberToRemove] = useState(null);
    const [removing, setRemoving] = useState(false);

    useEffect(() => {
        fetchClubAndMembers();
    }, [id]);

    const fetchClubAndMembers = async () => {
        try {
            setLoading(true);
            const [clubRes, membersRes] = await Promise.all([
                api.get(`/clubs/${id}`),
                api.get(`/clubs/${id}/members`)
            ]);
            setClub(clubRes.data);
            setMembers(membersRes.data);
            setError('');
        } catch (err) {
            console.error('Error fetching data:', err);
            setError(err.response?.data?.message || 'Failed to load club members');
        } finally {
            setLoading(false);
        }
    };

    const handleRemoveMember = async () => {
        if (!memberToRemove) return;

        try {
            setRemoving(true);
            await api.delete(`/clubs/${id}/members/${memberToRemove.id}`);
            setMembers(members.filter(m => m.id !== memberToRemove.id));
            setShowConfirmModal(false);
            setMemberToRemove(null);
        } catch (err) {
            console.error('Error removing member:', err);
            setError(err.response?.data?.message || 'Failed to remove member');
        } finally {
            setRemoving(false);
        }
    };

    const openRemoveModal = (member) => {
        setMemberToRemove(member);
        setShowConfirmModal(true);
    };

    const filteredMembers = members.filter(member =>
        member.fullName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        member.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        member.department?.toLowerCase().includes(searchTerm.toLowerCase())
    );

    const formatDate = (dateString) => {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
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
                {/* Header */}
                <div className="mb-8">
                    <button
                        onClick={() => navigate('/clubhead/dashboard')}
                        className="flex items-center text-gray-600 hover:text-gray-900 mb-4 transition-colors"
                    >
                        <ArrowLeft className="w-5 h-5 mr-2" />
                        Back to Dashboard
                    </button>
                    <div className="flex items-center justify-between">
                        <div>
                            <h1 className="text-3xl font-bold gradient-text mb-2">
                                {club?.name} - Members
                            </h1>
                            <p className="text-gray-600">
                                Manage {members.length} member{members.length !== 1 ? 's' : ''} in your club
                            </p>
                        </div>
                        <div className="w-16 h-16 rounded-xl bg-gradient-to-r from-blue-500 to-purple-600 flex items-center justify-center">
                            <Users className="w-8 h-8 text-white" />
                        </div>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600">
                        {error}
                    </div>
                )}

                {/* Search Bar */}
                <div className="card p-4 mb-6">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
                        <input
                            type="text"
                            placeholder="Search members by name, email, or department..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full pl-10 pr-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                        />
                    </div>
                </div>

                {/* Members Table */}
                <div className="card overflow-hidden">
                    {filteredMembers.length === 0 ? (
                        <div className="p-12 text-center">
                            <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                            <p className="text-gray-500 text-lg">
                                {searchTerm ? 'No members found matching your search' : 'No members in this club yet'}
                            </p>
                        </div>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead className="bg-gray-50 border-b border-gray-200">
                                    <tr>
                                        <th className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Member
                                        </th>
                                        <th className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Contact
                                        </th>
                                        <th className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Department
                                        </th>
                                        <th className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Role
                                        </th>
                                        <th className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Joined
                                        </th>
                                        <th className="px-6 py-4 text-center text-xs font-semibold text-gray-600 uppercase tracking-wider">
                                            Actions
                                        </th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {filteredMembers.map((member) => (
                                        <tr key={member.id} className="hover:bg-gray-50 transition-colors">
                                            <td className="px-6 py-4">
                                                <div className="flex items-center">
                                                    <div className="w-10 h-10 rounded-full bg-gradient-to-r from-blue-500 to-purple-500 flex items-center justify-center text-white font-semibold">
                                                        {member.fullName?.charAt(0) || member.username?.charAt(0) || '?'}
                                                    </div>
                                                    <div className="ml-4">
                                                        <p className="font-semibold text-gray-900">{member.fullName || member.username}</p>
                                                        <p className="text-sm text-gray-500">@{member.username}</p>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="space-y-1">
                                                    <p className="flex items-center text-sm text-gray-600">
                                                        <Mail className="w-4 h-4 mr-2 text-gray-400" />
                                                        {member.email || 'N/A'}
                                                    </p>
                                                    <p className="flex items-center text-sm text-gray-600">
                                                        <Phone className="w-4 h-4 mr-2 text-gray-400" />
                                                        {member.phone || 'N/A'}
                                                    </p>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center">
                                                    <GraduationCap className="w-4 h-4 mr-2 text-gray-400" />
                                                    <span className="text-gray-700">
                                                        {member.department || 'N/A'}
                                                        {member.year && ` (Year ${member.year})`}
                                                    </span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className={`inline-flex px-3 py-1 rounded-full text-xs font-semibold ${member.role === 'MEMBER'
                                                        ? 'bg-blue-100 text-blue-700'
                                                        : member.role === 'COORDINATOR'
                                                            ? 'bg-purple-100 text-purple-700'
                                                            : 'bg-green-100 text-green-700'
                                                    }`}>
                                                    {member.role}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center text-gray-600">
                                                    <Calendar className="w-4 h-4 mr-2 text-gray-400" />
                                                    {formatDate(member.joinedAt)}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <button
                                                    onClick={() => openRemoveModal(member)}
                                                    className="inline-flex items-center px-3 py-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                                                    title="Remove member"
                                                >
                                                    <Trash2 className="w-4 h-4 mr-1" />
                                                    Remove
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>

            {/* Confirmation Modal */}
            {showConfirmModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-2xl p-6 max-w-md w-full mx-4 shadow-2xl">
                        <div className="text-center">
                            <div className="w-16 h-16 rounded-full bg-red-100 flex items-center justify-center mx-auto mb-4">
                                <Trash2 className="w-8 h-8 text-red-600" />
                            </div>
                            <h3 className="text-xl font-bold text-gray-900 mb-2">Remove Member</h3>
                            <p className="text-gray-600 mb-6">
                                Are you sure you want to remove <strong>{memberToRemove?.fullName}</strong> from the club?
                                This action cannot be undone.
                            </p>
                            <div className="flex space-x-3">
                                <button
                                    onClick={() => {
                                        setShowConfirmModal(false);
                                        setMemberToRemove(null);
                                    }}
                                    disabled={removing}
                                    className="flex-1 px-4 py-3 border border-gray-300 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleRemoveMember}
                                    disabled={removing}
                                    className="flex-1 px-4 py-3 bg-red-600 text-white rounded-xl hover:bg-red-700 transition-colors disabled:opacity-50"
                                >
                                    {removing ? 'Removing...' : 'Remove Member'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ClubMembers;
