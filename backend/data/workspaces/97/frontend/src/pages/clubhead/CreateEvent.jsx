import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { Calendar, ArrowLeft } from 'lucide-react';

const CreateEvent = () => {
    const navigate = useNavigate();
    const toast = useToast();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [myClubs, setMyClubs] = useState([]);
    const [loadingClubs, setLoadingClubs] = useState(true);
    const [formData, setFormData] = useState({
        name: '',
        description: '',
        clubId: '',
        dateTime: '',
        venue: '',
        entryFee: 0,
        maxParticipants: '',
        registrationDeadline: '',
        contactEmail: '',
        contactPhone: '',
        rules: ''
    });

    useEffect(() => {
        fetchMyClubs();
    }, []);

    const fetchMyClubs = async () => {
        try {
            setLoadingClubs(true);
            const response = await api.get('/clubs/my-clubs');
            setMyClubs(response.data);
            if (response.data.length > 0) {
                setFormData(prev => ({ ...prev, clubId: response.data[0].id }));
            }
        } catch (err) {
            console.error('Error fetching clubs:', err);
            setError('Failed to load your clubs');
        } finally {
            setLoadingClubs(false);
        }
    };

    const handleChange = (e) => {
        const value = e.target.type === 'number' ? parseFloat(e.target.value) : e.target.value;
        setFormData({ ...formData, [e.target.name]: value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        // Validate dates
        const eventDate = new Date(formData.dateTime);
        const regDeadline = new Date(formData.registrationDeadline);
        const now = new Date();

        if (eventDate <= now) {
            setError('Event date must be in the future');
            return;
        }

        if (regDeadline >= eventDate) {
            setError('Registration deadline must be before the event date');
            return;
        }

        if (regDeadline <= now) {
            setError('Registration deadline must be in the future');
            return;
        }

        if (formData.maxParticipants && formData.maxParticipants < 1) {
            setError('Maximum participants must be at least 1');
            return;
        }

        setLoading(true);

        try {
            await api.post('/events', formData);
            toast.success('Event created successfully! It will be visible after admin approval.');
            navigate('/clubhead/dashboard');
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to create event');
        } finally {
            setLoading(false);
        }
    };

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
                        <div className="w-12 h-12 bg-gradient-to-r from-purple-600 to-pink-600 rounded-lg flex items-center justify-center mr-4">
                            <Calendar className="w-6 h-6 text-white" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Create New Event</h1>
                            <p className="text-gray-600">Organize an event for your club</p>
                        </div>
                    </div>

                    {error && (
                        <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600">
                            {error}
                        </div>
                    )}

                    {loadingClubs ? (
                        <div className="flex justify-center py-8">
                            <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500"></div>
                        </div>
                    ) : myClubs.length === 0 ? (
                        <div className="text-center py-8">
                            <p className="text-gray-500 mb-4">You don't manage any clubs yet.</p>
                            <button
                                onClick={() => navigate('/clubhead/create-club')}
                                className="btn-primary"
                            >
                                Create a Club First
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-6">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Event Name *
                                </label>
                                <input
                                    type="text"
                                    name="name"
                                    value={formData.name}
                                    onChange={handleChange}
                                    className="input-field"
                                    placeholder="Enter event name"
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
                                    placeholder="Describe the event"
                                    required
                                />
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Select Club *
                                    </label>
                                    <select
                                        name="clubId"
                                        value={formData.clubId}
                                        onChange={handleChange}
                                        className="input-field"
                                        required
                                    >
                                        {myClubs.map((club) => (
                                            <option key={club.id} value={club.id}>
                                                {club.name}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Venue *
                                    </label>
                                    <input
                                        type="text"
                                        name="venue"
                                        value={formData.venue}
                                        onChange={handleChange}
                                        className="input-field"
                                        placeholder="Event location"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Event Date & Time *
                                    </label>
                                    <input
                                        type="datetime-local"
                                        name="dateTime"
                                        value={formData.dateTime}
                                        onChange={handleChange}
                                        className="input-field"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Registration Deadline *
                                    </label>
                                    <input
                                        type="datetime-local"
                                        name="registrationDeadline"
                                        value={formData.registrationDeadline}
                                        onChange={handleChange}
                                        className="input-field"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Entry Fee (₹)
                                    </label>
                                    <input
                                        type="number"
                                        name="entryFee"
                                        value={formData.entryFee}
                                        onChange={handleChange}
                                        className="input-field"
                                        placeholder="0"
                                        min="0"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Max Participants
                                    </label>
                                    <input
                                        type="number"
                                        name="maxParticipants"
                                        value={formData.maxParticipants}
                                        onChange={handleChange}
                                        className="input-field"
                                        placeholder="Leave empty for unlimited"
                                        min="1"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Contact Email *
                                    </label>
                                    <input
                                        type="email"
                                        name="contactEmail"
                                        value={formData.contactEmail}
                                        onChange={handleChange}
                                        className="input-field"
                                        placeholder="event@college.com"
                                        required
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
                                        placeholder="+1234567890"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Rules & Guidelines
                                </label>
                                <textarea
                                    name="rules"
                                    value={formData.rules}
                                    onChange={handleChange}
                                    className="input-field"
                                    rows="3"
                                    placeholder="List any rules or guidelines for participants"
                                />
                            </div>

                            <div className="flex space-x-4">
                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="btn-primary flex-1 disabled:opacity-50"
                                >
                                    {loading ? 'Creating...' : 'Create Event'}
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
                    )}
                </div>
            </div>
        </div>
    );
};

export default CreateEvent;
