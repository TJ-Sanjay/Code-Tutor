import React, { createContext, useContext, useState } from 'react';
import { CheckCircle, XCircle, AlertCircle, X } from 'lucide-react';

const ToastContext = createContext();

export const useToast = () => {
    const context = useContext(ToastContext);
    if (!context) {
        throw new Error('useToast must be used within ToastProvider');
    }
    return context;
};

export const ToastProvider = ({ children }) => {
    const [toasts, setToasts] = useState([]);

    const addToast = (message, type = 'info') => {
        const id = Date.now();
        setToasts(prev => [...prev, { id, message, type }]);
        setTimeout(() => removeToast(id), 5000);
    };

    const removeToast = (id) => {
        setToasts(prev => prev.filter(toast => toast.id !== id));
    };

    const toast = {
        success: (message) => addToast(message, 'success'),
        error: (message) => addToast(message, 'error'),
        info: (message) => addToast(message, 'info'),
        warning: (message) => addToast(message, 'warning')
    };

    return (
        <ToastContext.Provider value={toast}>
            {children}
            <div className="fixed top-4 right-4 z-50 space-y-2">
                {toasts.map(({ id, message, type }) => (
                    <div
                        key={id}
                        className={`flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg max-w-md animate-slide-in ${type === 'success' ? 'bg-green-50 text-green-800 border border-green-200' :
                                type === 'error' ? 'bg-red-50 text-red-800 border border-red-200' :
                                    type === 'warning' ? 'bg-yellow-50 text-yellow-800 border border-yellow-200' :
                                        'bg-blue-50 text-blue-800 border border-blue-200'
                            }`}
                    >
                        {type === 'success' && <CheckCircle className="w-5 h-5 flex-shrink-0" />}
                        {type === 'error' && <XCircle className="w-5 h-5 flex-shrink-0" />}
                        {type === 'warning' && <AlertCircle className="w-5 h-5 flex-shrink-0" />}
                        {type === 'info' && <AlertCircle className="w-5 h-5 flex-shrink-0" />}
                        <p className="flex-1 text-sm font-medium">{message}</p>
                        <button
                            onClick={() => removeToast(id)}
                            className="flex-shrink-0 hover:opacity-70"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
};
