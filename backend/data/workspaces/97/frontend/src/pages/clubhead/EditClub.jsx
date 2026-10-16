import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../services/api';
import { Building, ArrowLeft, Save } from 'lucide-react';

const EditClub = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [formData, setFormData] = useState({
        name: '',
        description: '',
        category: 'TECHNICAL',
        logoUrl: '',
        bannerUrl: '',
        achievements: '',
        contactEmail: '',
        contactPhone: ''
    });

    const categories = [
        { value: 'TECHNICAL', label: 'Technical' },
        { value: 'CULTURAL', label: 'Cultural' },
        { value: 'SPORTS', label: 'Sports' },
        { value: 'LITERARY', label: 'Literary' },
        { value: 'SOCIAL', label: 'Social' },
        { value: 'OTHER', label: 'Other' }
    ];

    useEffect(() => {
        fetchClubDetails();
    }, [id]);

    const fetchClubDetails = async () => {
        try {
            setLoading(true);
            const response = await api.get(`/clubs/${id}`);
            const club = response.data;

            setFormData({
                name: club.name || '',
                description: club.description || '',
                category: club.category || 'TECHNICAL',
                logoUrl: club.logoUrl || '',
                bannerUrl: club.bannerUrl || '',
                achievements: club.achievements || '',
                contactEmail: club.contactEmail || '',
                contactPhone: club.contactPhone || ''
            });
            setError('');
        } catch (err) {
            console.error('Error fetching club:', err);
            setError(err.response?.data?.message || 'Failed to load club details');
        } finally {
            setLoading(false);
        }
    };

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSaving(true);

        try {
            await api.put(`/clubs/${id}`, formData);
            alert('Club updated successfully!');
            navigate('/clubhead/dashboard');
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to update club');
        } finally {
            setSaving(false);
        }
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
            <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
                <button
                    onClick={() => navigate(-1)}
                    className="flex items-center text-gray-600 hover:text-primary-600 mb-6"
                >
                    <ArrowLeft className="w-5 h-5 mr-2" />
                    Back
                </button>

                <div className="card p-8">
                    <div className="flex items-center mb-6">
                        <div className="w-12 h-12 bg-gradient-to-r from-blue-600 to-purple-600 rounded-lg flex items-center justify-center mr-4">
                            <Building className="w-6 h-6 text-white" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Edit Club</h1>
                            <p className="text-gray-600">Update club details</p>
                        </div>
                    </div>

                    {error && (
                        <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600">
                            {error}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="space-y-6">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Club Name *
                            </label>
                            <input
                                type="text"
                                name="name"
                                value={formData.name}
                                onChange={handleChange}
                                className="input-field"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Description *
                            </label>
                            <textarea
                                name="description"
                                value={formData.description}
                                onChange={handleChange}
                                className="input-field"
                                rows="4"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Category *
                            </label>
                            <select
                                name="category"
                                value={formData.category}
                                onChange={handleChange}
                                className="input-field"
                                required
                            >
                                {categories.map((cat) => (
                                    <option key={cat.value} value={cat.value}>
                                        {cat.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Logo URL
                                </label>
                                <input
                                    type="url"
                                    name="logoUrl"
                                    value={formData.logoUrl}
                                    onChange={handleChange}
                                    className="input-field"
                                    placeholder="https://example.com/logo.png"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Banner URL
                                </label>
                                <input
                                    type="url"
                                    name="bannerUrl"
                                    value={formData.bannerUrl}
                                    onChange={handleChange}
                                    className="input-field"
                                    placeholder="https://example.com/banner.png"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Achievements
                            </label>
                            <textarea
                                name="achievements"
                                value={formData.achievements}
                                onChange={handleChange}
                                className="input-field"
                                rows="3"
                                placeholder="List club achievements and accolades"
                            />
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Contact Email
                                </label>
                                <input
                                    type="email"
                                    name="contactEmail"
                                    value={formData.contactEmail}
                                    onChange={handleChange}
                                    className="input-field"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Contact Phone
                                </label>
                                <input
                                    type="tel"
                                    name="contactPhone"
                                    value={formData.contactPhone}
                                    onChange={handleChange}
                                    className="input-field"
                                />
                            </div>
                        </div>

                        <div className="flex space-x-4">
                            <button
                                type="submit"
                                disabled={saving}
                                className="btn-primary flex-1 flex items-center justify-center space-x-2 disabled:opacity-50"
                            >
                                <Save className="w-5 h-5" />
                                <span>{saving ? 'Saving...' : 'Save Changes'}</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => navigate(-1)}
                                className="btn-outline flex-1"
                            >
                                Cancel
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};

export default EditClub;
