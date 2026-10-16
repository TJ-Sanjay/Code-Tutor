import React, { useState, useEffect } from 'react';
import { Bell, Check, Trash2, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import notificationService from '../services/notificationService';
import { formatDistanceToNow } from 'date-fns';

const NotificationDropdown = ({ onClose, onUpdateCount }) => {
    const [notifications, setNotifications] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchNotifications();
    }, []);

    const fetchNotifications = async () => {
        try {
            // Fetch unread notifications specifically to match user requirement "not show again"
            const data = await notificationService.getUnreadNotifications();
            console.log('Fetched unread notifications:', data);
            setNotifications(data);
        } catch (error) {
            console.error('Error fetching notifications:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleMarkAsRead = async (id, e) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }
        try {
            await notificationService.markAsRead(id);
            // Remove from list immediately after marking as read
            setNotifications(notifications.filter(n => n.id !== id));
            // Update the navbar count
            if (onUpdateCount) onUpdateCount();
        } catch (error) {
            console.error('Error marking as read:', error);
        }
    };

    const handleMarkAllAsRead = async () => {
        try {
            await notificationService.markAllAsRead();
            // Clear all as they are now read
            setNotifications([]);
            // Update the navbar count
            if (onUpdateCount) onUpdateCount();
        } catch (error) {
            console.error('Error marking all as read:', error);
        }
    };

    return (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-xl shadow-2xl border border-gray-100 overflow-hidden z-50 animate-fade-in">
            <div className="p-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
                <h3 className="font-bold text-gray-900 flex items-center space-x-2">
                    <Bell className="w-4 h-4 text-primary-600" />
                    <span>Notifications</span>
                </h3>
                {notifications.some(n => !n.read) && (
                    <button
                        onClick={handleMarkAllAsRead}
                        className="text-xs font-semibold text-primary-600 hover:text-primary-700 hover:underline"
                    >
                        Mark all as read
                    </button>
                )}
            </div>

            <div className="max-h-[70vh] overflow-y-auto">
                {loading ? (
                    <div className="p-8 text-center">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600 mx-auto"></div>
                    </div>
                ) : notifications.length === 0 ? (
                    <div className="p-12 text-center">
                        <Bell className="w-12 h-12 text-gray-200 mx-auto mb-3" />
                        <p className="text-gray-500 font-medium">No notifications yet</p>
                        <p className="text-gray-400 text-xs mt-1">We'll notify you when something happens!</p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-50">
                        {notifications.map((notification) => (
                            <Link
                                key={notification.id}
                                to={notification.link || '#'}
                                onClick={() => {
                                    if (!notification.read) {
                                        handleMarkAsRead(notification.id);
                                    }
                                    onClose();
                                }}
                                className={`block p-4 hover:bg-gray-50 transition-colors relative ${!notification.read ? 'bg-primary-50/30' : ''}`}
                            >
                                <div className="flex justify-between items-start mb-1">
                                    <h4 className={`text-sm font-semibold truncate pr-6 ${!notification.read ? 'text-gray-900' : 'text-gray-600'}`}>
                                        {notification.title}
                                    </h4>
                                    {!notification.read && (
                                        <button
                                            onClick={(e) => handleMarkAsRead(notification.id, e)}
                                            className="p-1 text-gray-400 hover:text-primary-600 rounded-full hover:bg-white transition-all shadow-sm"
                                            title="Mark as read"
                                        >
                                            <Check className="w-3 h-3" />
                                        </button>
                                    )}
                                </div>
                                <p className="text-xs text-gray-600 line-clamp-2 mb-2 leading-relaxed">
                                    {notification.message}
                                </p>
                                <div className="flex items-center justify-between text-[10px] text-gray-400">
                                    <span>{formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}</span>
                                    {notification.link && (
                                        <span className="flex items-center text-primary-500 font-medium">
                                            View <ExternalLink className="w-2 h-2 ml-1" />
                                        </span>
                                    )}
                                </div>
                                {!notification.read && (
                                    <span className="absolute top-4 right-4 w-2 h-2 bg-primary-500 rounded-full"></span>
                                )}
                            </Link>
                        ))}
                    </div>
                )}
            </div>

            {notifications.length > 0 && (
                <div className="p-3 bg-gray-50 border-t border-gray-100 text-center">
                    <button
                        onClick={onClose}
                        className="text-xs text-gray-500 hover:text-gray-700 font-medium"
                    >
                        Close
                    </button>
                </div>
            )}
        </div>
    );
};

export default NotificationDropdown;
