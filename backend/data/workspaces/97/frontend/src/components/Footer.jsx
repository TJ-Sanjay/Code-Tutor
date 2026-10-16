import React from 'react';
import { Link } from 'react-router-dom';
import { Github, Twitter, Linkedin, Mail } from 'lucide-react';

const Footer = () => {
    return (
        <footer className="bg-gray-900 text-gray-300 mt-auto">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
                    <div className="col-span-1 md:col-span-2">
                        <h3 className="text-white text-lg font-bold mb-4">College Club Management System</h3>
                        <p className="text-sm text-gray-400 mb-4">
                            A comprehensive platform for managing college clubs, events, and student activities.
                            Join clubs, participate in events, and stay connected with your college community.
                        </p>
                        <div className="flex space-x-4">
                            <a href="#" className="hover:text-primary-400 transition-colors">
                                <Github className="w-5 h-5" />
                            </a>
                            <a href="#" className="hover:text-primary-400 transition-colors">
                                <Twitter className="w-5 h-5" />
                            </a>
                            <a href="#" className="hover:text-primary-400 transition-colors">
                                <Linkedin className="w-5 h-5" />
                            </a>
                            <a href="#" className="hover:text-primary-400 transition-colors">
                                <Mail className="w-5 h-5" />
                            </a>
                        </div>
                    </div>

                    <div>
                        <h4 className="text-white font-semibold mb-4">Quick Links</h4>
                        <ul className="space-y-2 text-sm">
                            <li><Link to="/clubs" className="hover:text-primary-400 transition-colors">Browse Clubs</Link></li>
                            <li><Link to="/events" className="hover:text-primary-400 transition-colors">Upcoming Events</Link></li>
                            <li><Link to="/about" className="hover:text-primary-400 transition-colors">About Us</Link></li>
                            <li><Link to="/contact" className="hover:text-primary-400 transition-colors">Contact</Link></li>
                        </ul>
                    </div>

                    <div>
                        <h4 className="text-white font-semibold mb-4">Support</h4>
                        <ul className="space-y-2 text-sm">
                            <li><Link to="/help" className="hover:text-primary-400 transition-colors">Help Center</Link></li>
                            <li><Link to="/faq" className="hover:text-primary-400 transition-colors">FAQ</Link></li>
                            <li><Link to="/privacy" className="hover:text-primary-400 transition-colors">Privacy Policy</Link></li>
                            <li><Link to="/terms" className="hover:text-primary-400 transition-colors">Terms of Service</Link></li>
                        </ul>
                    </div>
                </div>

                <div className="border-t border-gray-800 mt-8 pt-8 text-sm text-center text-gray-400">
                    <p>&copy; {new Date().getFullYear()} College Club Management System. All rights reserved.</p>
                </div>
            </div>
        </footer>
    );
};

export default Footer;
