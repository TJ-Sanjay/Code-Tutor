import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Building, Users, Calendar, Search, Trash2, CheckCircle, XCircle, Settings, Power, PowerOff } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import ConfirmModal from '../../components/ConfirmModal';

const AllClubs = () => {
    const navigate = useNavigate();
    const toast = useToast();
    const [clubs, setClubs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [confirmModal, setConfirmModal] = useState({ isOpen: false, action: null, clubId: null, title: '', message: '' });

    useEffect(() => {
        fetchClubs();
    }, []);

    const fetchClubs = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/reports/clubs');
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
            toast.success('Club approved successfully!');
            fetchClubs();
        } catch (error) {
            toast.error('Failed to approve club');
        }
    };

    const handleReject = (clubId) => {
        setConfirmModal({
            isOpen: true,
            action: 'reject',
            clubId,
            title: 'Reject Club',
            message: 'Are you sure you want to reject this club? This action cannot be undone.'
        });
    };

    const executeReject = async (clubId) => {
        try {
            await api.put(`/admin/clubs/${clubId}/reject`);
            toast.success('Club rejected');
            fetchClubs();
        } catch (error) {
            toast.error('Failed to reject club');
        }
    };

    const handleDeactivate = (clubId) => {
        setConfirmModal({
            isOpen: true,
            action: 'deactivate',
            clubId,
            title: 'Deactivate Club',
            message: 'Are you sure you want to deactivate this club? It will be hidden from students.'
        });
    };

    const executeDeactivate = async (clubId) => {
        try {
            await api.put(`/admin/clubs/${clubId}/deactivate`);
            toast.success('Club deactivated');
            fetchClubs();
        } catch (error) {
            toast.error('Failed to deactivate club');
        }
    };

    const handleActivate = async (clubId) => {
        try {
            await api.put(`/admin/clubs/${clubId}/activate`);
            toast.success('Club activated successfully!');
            fetchClubs();
        } catch (error) {
            toast.error('Failed to activate club');
        }
    };

    const handleConfirm = () => {
        const { action, clubId } = confirmModal;
        if (action === 'reject') executeReject(clubId);
        else if (action === 'deactivate') executeDeactivate(clubId);
    };

    const filteredClubs = clubs.filter(club => {
        const matchesSearch = club.name?.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesStatus = filterStatus === 'ALL' || club.status === filterStatus;
        return matchesSearch && matchesStatus;
    });

    const getStatusBadge = (status) => {
        switch (status) {
            case 'ACTIVE': return 'bg-green-100 text-green-700';
            case 'PENDING': return 'bg-yellow-100 text-yellow-700';
            case 'REJECTED': return 'bg-red-100 text-red-700';
            case 'INACTIVE': return 'bg-gray-100 text-gray-700';
            default: return 'bg-gray-100 text-gray-700';
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
                <button
                    onClick={() => navigate('/admin/dashboard')}
                    className="flex items-center text-gray-600 hover:text-gray-900 mb-4 transition-colors"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back to Dashboard
                </button>

                <div className="flex items-center justify-between mb-8">
                    <div>
                        <h1 className="text-3xl font-bold gradient-text mb-2">All Clubs</h1>
                        <p className="text-gray-600">Manage all clubs in the system ({clubs.length} total)</p>
                    </div>
                </div>

                {/* Search and Filter */}
                <div className="card p-4 mb-6">
                    <div className="flex flex-col md:flex-row gap-4">
                        <div className="flex-1 relative">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
                            <input
                                type="text"
                                placeholder="Search clubs..."
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
                            <option value="ACTIVE">Active</option>
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
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Club</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Members</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Events</th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {filteredClubs.map((club) => (
                                    <tr key={club.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center">
                                                <div className="w-10 h-10 bg-gradient-to-r from-blue-500 to-purple-500 rounded-lg flex items-center justify-center">
                                                    <Building className="w-5 h-5 text-white" />
                                                </div>
                                                <div className="ml-4">
                                                    <p className="font-medium text-gray-900">{club.name}</p>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600">{club.category}</td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center text-sm text-gray-600">
                                                <Users className="w-4 h-4 mr-1" />
                                                {club.memberCount}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center text-sm text-gray-600">
                                                <Calendar className="w-4 h-4 mr-1" />
                                                {club.eventCount}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`px-2 py-1 text-xs rounded-full font-medium ${getStatusBadge(club.status)}`}>
                                                {club.status}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center justify-center space-x-2">
                                                {club.status === 'PENDING' && (
                                                    <>
                                                        <button
                                                            onClick={() => handleApprove(club.id)}
                                                            className="p-2 text-green-600 hover:bg-green-50 rounded-lg"
                                                            title="Approve"
                                                        >
                                                            <CheckCircle className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleReject(club.id)}
                                                            className="p-2 text-red-600 hover:bg-red-50 rounded-lg"
                                                            title="Reject"
                                                        >
                                                            <XCircle className="w-4 h-4" />
                                                        </button>
                                                    </>
                                                )}
                                                {club.status === 'ACTIVE' && (
                                                    <button
                                                        onClick={() => handleDeactivate(club.id)}
                                                        className="p-2 text-orange-600 hover:bg-orange-50 rounded-lg"
                                                        title="Deactivate"
                                                    >
                                                        <PowerOff className="w-4 h-4" />
                                                    </button>
                                                )}
                                                {(club.status === 'INACTIVE' || club.status === 'REJECTED') && (
                                                    <button
                                                        onClick={() => handleActivate(club.id)}
                                                        className="p-2 text-green-600 hover:bg-green-50 rounded-lg"
                                                        title="Activate"
                                                    >
                                                        <Power className="w-4 h-4" />
                                                    </button>
                                                )}
                                                <Link
                                                    to={`/clubhead/clubs/${club.id}/members`}
                                                    className="p-2 text-purple-600 hover:bg-purple-50 rounded-lg"
                                                    title="Manage Members"
                                                >
                                                    <Settings className="w-4 h-4" />
                                                </Link>
                                                <Link
                                                    to={`/clubs/${club.id}`}
                                                    className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg"
                                                    title="View Details"
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

            <ConfirmModal
                isOpen={confirmModal.isOpen}
                onClose={() => setConfirmModal({ ...confirmModal, isOpen: false })}
                onConfirm={handleConfirm}
                title={confirmModal.title}
                message={confirmModal.message}
                variant="danger"
            />
        </div>
    );
};

export default AllClubs;
