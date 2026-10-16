import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Users, Trash2, Shield, UserCog, Search, X } from 'lucide-react';
import api from '../../services/api';

const UserManagement = () => {
    const navigate = useNavigate();
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterRole, setFilterRole] = useState('ALL');
    const [selectedUser, setSelectedUser] = useState(null);
    const [showRoleModal, setShowRoleModal] = useState(false);
    const [updatingRole, setUpdatingRole] = useState(false);

    useEffect(() => {
        fetchUsers();
    }, []);

    const fetchUsers = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/users');
            setUsers(response.data);
        } catch (error) {
            console.error('Error fetching users:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (userId, username) => {
        if (username === 'admin') {
            alert('Cannot delete the admin account!');
            return;
        }
        if (window.confirm(`Are you sure you want to delete user "${username}"?`)) {
            try {
                await api.delete(`/admin/users/${userId}`);
                alert('User deleted successfully');
                fetchUsers();
            } catch (error) {
                alert('Failed to delete user');
            }
        }
    };

    const openRoleModal = (user) => {
        setSelectedUser(user);
        setShowRoleModal(true);
    };

    const handleRoleUpdate = async (newRoles) => {
        if (!selectedUser) return;

        try {
            setUpdatingRole(true);
            await api.put(`/admin/users/${selectedUser.id}/roles`, { roles: newRoles });
            alert('Roles updated successfully!');
            setShowRoleModal(false);
            setSelectedUser(null);
            fetchUsers();
        } catch (error) {
            console.error('Error updating roles:', error);
            alert('Failed to update roles');
        } finally {
            setUpdatingRole(false);
        }
    };

    const toggleRole = (role) => {
        if (!selectedUser) return;
        const currentRoles = [...(selectedUser.roles || [])];
        if (currentRoles.includes(role)) {
            // Don't allow removing STUDENT if it's the only role
            if (role === 'STUDENT' && currentRoles.length === 1) {
                alert('User must have at least STUDENT role');
                return;
            }
            setSelectedUser({
                ...selectedUser,
                roles: currentRoles.filter(r => r !== role)
            });
        } else {
            setSelectedUser({
                ...selectedUser,
                roles: [...currentRoles, role]
            });
        }
    };

    const filteredUsers = users.filter(user => {
        const matchesSearch = user.username?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            user.email?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            user.fullName?.toLowerCase().includes(searchQuery.toLowerCase());

        const matchesRole = filterRole === 'ALL' || user.roles?.includes(filterRole);

        return matchesSearch && matchesRole;
    });

    const getRoleBadgeColor = (role) => {
        switch (role) {
            case 'ADMIN': return 'bg-red-100 text-red-700';
            case 'CLUB_HEAD': return 'bg-purple-100 text-purple-700';
            case 'STUDENT': return 'bg-blue-100 text-blue-700';
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
                        <h1 className="text-3xl font-bold gradient-text mb-2">User Management</h1>
                        <p className="text-gray-600">Manage users and their roles ({users.length} total users)</p>
                    </div>
                </div>

                {/* Search and Filter */}
                <div className="card p-4 mb-6">
                    <div className="flex flex-col md:flex-row gap-4">
                        <div className="flex-1 relative">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
                            <input
                                type="text"
                                placeholder="Search by name, username or email..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="input-field pl-10"
                            />
                        </div>
                        <select
                            value={filterRole}
                            onChange={(e) => setFilterRole(e.target.value)}
                            className="input-field md:w-48"
                        >
                            <option value="ALL">All Roles</option>
                            <option value="ADMIN">Admin</option>
                            <option value="CLUB_HEAD">Club Head</option>
                            <option value="STUDENT">Student</option>
                        </select>
                    </div>
                </div>

                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        User
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Email
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Roles
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Department
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {filteredUsers.map((user) => (
                                    <tr key={user.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="flex items-center">
                                                <div className="w-10 h-10 bg-gradient-to-r from-primary-600 to-secondary-600 rounded-full flex items-center justify-center">
                                                    <span className="text-white font-semibold">
                                                        {(user.firstName?.charAt(0) || user.username?.charAt(0) || '?').toUpperCase()}
                                                    </span>
                                                </div>
                                                <div className="ml-4">
                                                    <div className="text-sm font-medium text-gray-900">
                                                        {user.fullName || `${user.firstName || ''} ${user.lastName || ''}`}
                                                    </div>
                                                    <div className="text-sm text-gray-500">@{user.username}</div>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="text-sm text-gray-900">{user.email}</div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="flex flex-wrap gap-1">
                                                {user.roles?.map((role) => (
                                                    <span key={role} className={`px-2 py-1 text-xs rounded-full font-medium ${getRoleBadgeColor(role)}`}>
                                                        {role.replace('_', ' ')}
                                                    </span>
                                                ))}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                                            {user.department || '-'}
                                            {user.year && ` (Year ${user.year})`}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-center">
                                            <div className="flex items-center justify-center space-x-2">
                                                <button
                                                    onClick={() => openRoleModal(user)}
                                                    className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                                    title="Manage Roles"
                                                >
                                                    <Shield className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(user.id, user.username)}
                                                    disabled={user.username === 'admin'}
                                                    className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                                    title="Delete User"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            {/* Role Management Modal */}
            {showRoleModal && selectedUser && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-2xl p-6 w-full max-w-md mx-4">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-xl font-bold text-gray-900">Manage Roles</h3>
                            <button
                                onClick={() => {
                                    setShowRoleModal(false);
                                    setSelectedUser(null);
                                }}
                                className="p-2 hover:bg-gray-100 rounded-lg"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        <div className="mb-4">
                            <p className="text-gray-600">
                                User: <span className="font-semibold">{selectedUser.fullName || selectedUser.username}</span>
                            </p>
                            <p className="text-sm text-gray-500">@{selectedUser.username}</p>
                        </div>

                        <div className="space-y-3 mb-6">
                            <p className="text-sm font-medium text-gray-700">Select Roles:</p>

                            <label className="flex items-center p-3 border rounded-lg cursor-pointer hover:bg-gray-50">
                                <input
                                    type="checkbox"
                                    checked={selectedUser.roles?.includes('STUDENT')}
                                    onChange={() => toggleRole('STUDENT')}
                                    className="w-4 h-4 text-blue-600 rounded"
                                />
                                <span className="ml-3">
                                    <span className="font-medium text-gray-900">Student</span>
                                    <p className="text-sm text-gray-500">Can join clubs and register for events</p>
                                </span>
                            </label>

                            <label className="flex items-center p-3 border rounded-lg cursor-pointer hover:bg-gray-50">
                                <input
                                    type="checkbox"
                                    checked={selectedUser.roles?.includes('CLUB_HEAD')}
                                    onChange={() => toggleRole('CLUB_HEAD')}
                                    className="w-4 h-4 text-purple-600 rounded"
                                />
                                <span className="ml-3">
                                    <span className="font-medium text-gray-900">Club Head</span>
                                    <p className="text-sm text-gray-500">Can create and manage clubs and events</p>
                                </span>
                            </label>

                        </div>

                        <div className="flex space-x-3">
                            <button
                                onClick={() => {
                                    setShowRoleModal(false);
                                    setSelectedUser(null);
                                }}
                                className="flex-1 btn-outline"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleRoleUpdate(selectedUser.roles)}
                                disabled={updatingRole || !selectedUser.roles?.length}
                                className="flex-1 btn-primary disabled:opacity-50"
                            >
                                {updatingRole ? 'Updating...' : 'Save Changes'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default UserManagement;
