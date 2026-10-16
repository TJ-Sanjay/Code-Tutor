import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Menu, Bell, User, LogOut, Home, Users, Calendar, Settings } from 'lucide-react';
import NotificationDropdown from './NotificationDropdown';
import notificationService from '../services/notificationService';

const Navbar = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const [showUserMenu, setShowUserMenu] = useState(false);
    const [showNotifications, setShowNotifications] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const notificationRef = useRef(null);
    const userMenuRef = useRef(null);

    useEffect(() => {
        if (user) {
            fetchUnreadCount();
            const interval = setInterval(fetchUnreadCount, 60000);
            return () => clearInterval(interval);
        }
    }, [user]);

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (notificationRef.current && !notificationRef.current.contains(event.target)) {
                setShowNotifications(false);
            }
            if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
                setShowUserMenu(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const fetchUnreadCount = async () => {
        try {
            const count = await notificationService.getUnreadCount();
            setUnreadCount(count);
        } catch (error) {
            console.error('Error fetching unread count:', error);
        }
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const getNavLinks = () => {
        const links = [];

        // Admin gets admin-specific links
        if (user?.roles?.includes('ADMIN')) {
            links.push(
                { to: '/admin/dashboard', icon: Home, label: 'Dashboard' },
                { to: '/admin/club-approval', icon: Users, label: 'Club Approvals' },
                { to: '/admin/users', icon: Settings, label: 'User Management' },
                { to: '/clubs', icon: Users, label: 'Clubs' },
                { to: '/events', icon: Calendar, label: 'Events' }
            );
        }
        // Club Head gets club head links (but not if they're admin)
        else if (user?.roles?.includes('CLUB_HEAD')) {
            links.push(
                { to: '/clubhead/dashboard', icon: Home, label: 'Dashboard' },
                { to: '/clubs', icon: Users, label: 'Clubs' },
                { to: '/events', icon: Calendar, label: 'Events' }
            );
        }
        // Students get student links (but not if they're club head or admin)
        else if (user?.roles?.includes('STUDENT')) {
            links.push(
                { to: '/student/dashboard', icon: Home, label: 'Dashboard' },
                { to: '/clubs', icon: Users, label: 'Clubs' },
                { to: '/events', icon: Calendar, label: 'Events' }
            );
        }

        return links;
    };

    return (
        <nav className="bg-white shadow-lg sticky top-0 z-50">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex justify-between h-16">
                    <div className="flex items-center">
                        <Link to="/" className="flex items-center space-x-2">
                            <div className="w-10 h-10 bg-gradient-to-r from-primary-600 to-secondary-600 rounded-lg flex items-center justify-center">
                                <Users className="w-6 h-6 text-white" />
                            </div>
                            <span className="text-xl font-bold gradient-text">ClubHub</span>
                        </Link>

                        <div className="hidden md:flex ml-10 space-x-4">
                            {getNavLinks().map((link) => (
                                <Link
                                    key={link.to}
                                    to={link.to}
                                    className="flex items-center space-x-1 px-3 py-2 rounded-md text-gray-700 hover:text-primary-600 hover:bg-primary-50 transition-all duration-200"
                                >
                                    <link.icon className="w-4 h-4" />
                                    <span>{link.label}</span>
                                </Link>
                            ))}
                        </div>
                    </div>

                    <div className="flex items-center space-x-4">
                        {user && (
                            <div className="relative" ref={notificationRef}>
                                <button
                                    onClick={() => setShowNotifications(!showNotifications)}
                                    className="relative p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-all duration-200"
                                >
                                    <Bell className="w-5 h-5" />
                                    {unreadCount > 0 && (
                                        <span className="absolute top-1.5 right-1.5 w-4 h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center border-2 border-white">
                                            {unreadCount > 9 ? '9+' : unreadCount}
                                        </span>
                                    )}
                                </button>

                                {showNotifications && (
                                    <NotificationDropdown
                                        onClose={() => {
                                            setShowNotifications(false);
                                            fetchUnreadCount();
                                        }}
                                        onUpdateCount={fetchUnreadCount}
                                    />
                                )}
                            </div>
                        )}

                        <div className="relative" ref={userMenuRef}>
                            <button
                                onClick={() => setShowUserMenu(!showUserMenu)}
                                className="flex items-center space-x-2 p-2 rounded-lg hover:bg-gray-100 transition-all duration-200"
                            >
                                <div className="w-8 h-8 bg-gradient-to-r from-primary-600 to-secondary-600 rounded-full flex items-center justify-center">
                                    <User className="w-5 h-5 text-white" />
                                </div>
                                <span className="hidden md:block text-sm font-medium text-gray-700">
                                    {user?.fullName || user?.username}
                                </span>
                            </button>

                            {showUserMenu && (
                                <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-xl py-1 animate-fade-in">
                                    <div className="px-4 py-2 border-b border-gray-100">
                                        <p className="text-sm font-medium text-gray-900">{user?.fullName}</p>
                                        <p className="text-xs text-gray-500">{user?.email}</p>
                                    </div>
                                    <button
                                        onClick={handleLogout}
                                        className="w-full flex items-center space-x-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-all duration-200"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        <span>Logout</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </nav>
    );
};

export default Navbar;
