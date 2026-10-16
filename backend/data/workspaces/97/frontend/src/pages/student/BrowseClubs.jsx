import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';
import { useDebounce } from '../../hooks/useDebounce';
import { ListSkeleton } from '../../components/Skeletons';
import { Search, Filter, Users, TrendingUp, ArrowUpDown } from 'lucide-react';

const BrowseClubs = () => {
    const [clubs, setClubs] = useState([]);
    const [filteredClubs, setFilteredClubs] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('');
    const [sortBy, setSortBy] = useState('name');
    const [loading, setLoading] = useState(true);

    const debouncedSearch = useDebounce(searchTerm, 300);

    const categories = ['TECHNICAL', 'CULTURAL', 'SPORTS', 'SOCIAL', 'ARTS', 'LITERATURE'];

    useEffect(() => {
        fetchClubs();
    }, []);

    useEffect(() => {
        filterClubs();
    }, [debouncedSearch, selectedCategory, sortBy, clubs]);

    const fetchClubs = async () => {
        try {
            const response = await api.get('/clubs');
            setClubs(response.data);
            setFilteredClubs(response.data);
        } catch (error) {
            console.error('Error fetching clubs:', error);
        } finally {
            setLoading(false);
        }
    };

    const filterClubs = () => {
        let filtered = clubs;

        if (debouncedSearch) {
            filtered = filtered.filter(club =>
                club.name.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
                club.description?.toLowerCase().includes(debouncedSearch.toLowerCase())
            );
        }

        if (selectedCategory) {
            filtered = filtered.filter(club => club.category === selectedCategory);
        }

        // Sort
        filtered = [...filtered].sort((a, b) => {
            switch (sortBy) {
                case 'name':
                    return a.name.localeCompare(b.name);
                case 'members':
                    return (b.memberCount || 0) - (a.memberCount || 0);
                case 'newest':
                    return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
                default:
                    return 0;
            }
        });

        setFilteredClubs(filtered);
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="mb-8">
                        <h1 className="text-3xl font-bold gradient-text mb-2">Browse Clubs</h1>
                        <p className="text-gray-600">Discover and join clubs that match your interests</p>
                    </div>
                    <ListSkeleton items={6} />
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold gradient-text mb-2">Browse Clubs</h1>
                    <p className="text-gray-600">Discover and join clubs that match your interests</p>
                </div>

                {/* Search and Filter */}
                <div className="card p-6 mb-8">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="relative md:col-span-2">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
                            <input
                                type="text"
                                placeholder="Search clubs..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="input-field pl-10"
                            />
                        </div>
                        <select
                            value={selectedCategory}
                            onChange={(e) => setSelectedCategory(e.target.value)}
                            className="input-field"
                        >
                            <option value="">All Categories</option>
                            {categories.map(cat => (
                                <option key={cat} value={cat}>{cat}</option>
                            ))}
                        </select>
                    </div>
                    <div className="mt-4 flex items-center gap-2">
                        <ArrowUpDown className="w-4 h-4 text-gray-500" />
                        <span className="text-sm text-gray-600">Sort by:</span>
                        <div className="flex gap-2">
                            <button
                                onClick={() => setSortBy('name')}
                                className={`px-3 py-1 rounded-lg text-sm font-medium transition-colors ${sortBy === 'name' ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                            >
                                Name
                            </button>
                            <button
                                onClick={() => setSortBy('members')}
                                className={`px-3 py-1 rounded-lg text-sm font-medium transition-colors ${sortBy === 'members' ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                            >
                                Popular
                            </button>
                            <button
                                onClick={() => setSortBy('newest')}
                                className={`px-3 py-1 rounded-lg text-sm font-medium transition-colors ${sortBy === 'newest' ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                            >
                                Newest
                            </button>
                        </div>
                    </div>
                </div>

                {/* Clubs Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {filteredClubs.map((club) => (
                        <Link
                            key={club.id}
                            to={`/clubs/${club.id}`}
                            className="card hover:scale-105 transition-transform duration-200"
                        >
                            <div className="h-48 bg-gradient-to-r from-primary-500 to-secondary-500 flex items-center justify-center">
                                {club.logoUrl ? (
                                    <img src={club.logoUrl} alt={club.name} className="w-full h-full object-cover" />
                                ) : (
                                    <Users className="w-16 h-16 text-white" />
                                )}
                            </div>
                            <div className="p-6">
                                <div className="flex items-center justify-between mb-2">
                                    <h3 className="text-xl font-bold text-gray-900">{club.name}</h3>
                                    <span className="badge badge-primary">{club.category}</span>
                                </div>
                                <p className="text-gray-600 text-sm mb-4 line-clamp-2">
                                    {club.description || 'No description available'}
                                </p>
                                <div className="flex items-center justify-between text-sm">
                                    <span className="flex items-center text-gray-500">
                                        <Users className="w-4 h-4 mr-1" />
                                        {club.memberCount || 0} members
                                    </span>
                                    <span className="text-primary-600 font-medium">View Details →</span>
                                </div>
                            </div>
                        </Link>
                    ))}
                </div>

                {filteredClubs.length === 0 && (
                    <div className="text-center py-12">
                        <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500">No clubs found</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default BrowseClubs;
