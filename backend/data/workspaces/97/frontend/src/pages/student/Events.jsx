import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';
import { Calendar, Clock, MapPin, DollarSign, Users } from 'lucide-react';

const Events = () => {
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchEvents();
    }, []);

    const fetchEvents = async () => {
        try {
            const response = await api.get('/events?upcoming=true');
            setEvents(response.data);
        } catch (error) {
            console.error('Error fetching events:', error);
        } finally {
            setLoading(false);
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
                    <h1 className="text-3xl font-bold gradient-text mb-2">Upcoming Events</h1>
                    <p className="text-gray-600">Register for exciting events happening soon</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {events.map((event) => (
                        <Link
                            key={event.id}
                            to={`/events/${event.id}`}
                            className="card hover:scale-105 transition-transform duration-200"
                        >
                            <div className="h-48 bg-gradient-to-r from-purple-500 to-pink-500 flex items-center justify-center">
                                {event.posterUrl ? (
                                    <img src={event.posterUrl} alt={event.name} className="w-full h-full object-cover" />
                                ) : (
                                    <Calendar className="w-16 h-16 text-white" />
                                )}
                            </div>
                            <div className="p-6">
                                <h3 className="text-xl font-bold text-gray-900 mb-2">{event.name}</h3>
                                <p className="text-sm text-gray-600 mb-4">{event.clubName}</p>
                                <div className="space-y-2 text-sm text-gray-600">
                                    <div className="flex items-center">
                                        <Clock className="w-4 h-4 mr-2" />
                                        {new Date(event.dateTime).toLocaleString()}
                                    </div>
                                    <div className="flex items-center">
                                        <MapPin className="w-4 h-4 mr-2" />
                                        {event.venue}
                                    </div>
                                    {event.entryFee > 0 && (
                                        <div className="flex items-center">
                                            <DollarSign className="w-4 h-4 mr-2" />
                                            ₹{event.entryFee}
                                        </div>
                                    )}
                                </div>
                                <div className="mt-4 flex items-center justify-between">
                                    {event.isFull ? (
                                        <span className="badge badge-danger">Full</span>
                                    ) : (
                                        <span className="badge badge-success">Open</span>
                                    )}
                                    <span className="text-primary-600 font-medium">Register →</span>
                                </div>
                            </div>
                        </Link>
                    ))}
                </div>

                {events.length === 0 && (
                    <div className="text-center py-12">
                        <Calendar className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                        <p className="text-gray-500">No upcoming events</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default Events;
