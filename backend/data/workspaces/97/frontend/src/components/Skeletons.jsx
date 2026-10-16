import React from 'react';

export const CardSkeleton = () => (
    <div className="card p-6 animate-pulse">
        <div className="flex items-center space-x-4 mb-4">
            <div className="w-12 h-12 bg-gray-200 rounded-lg"></div>
            <div className="flex-1">
                <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
                <div className="h-3 bg-gray-200 rounded w-1/2"></div>
            </div>
        </div>
        <div className="space-y-2">
            <div className="h-3 bg-gray-200 rounded"></div>
            <div className="h-3 bg-gray-200 rounded w-5/6"></div>
        </div>
    </div>
);

export const TableSkeleton = ({ rows = 5 }) => (
    <div className="animate-pulse">
        <div className="h-12 bg-gray-200 rounded mb-4"></div>
        {[...Array(rows)].map((_, i) => (
            <div key={i} className="flex space-x-4 mb-3">
                <div className="h-10 bg-gray-200 rounded flex-1"></div>
                <div className="h-10 bg-gray-200 rounded flex-1"></div>
                <div className="h-10 bg-gray-200 rounded flex-1"></div>
                <div className="h-10 bg-gray-200 rounded w-24"></div>
            </div>
        ))}
    </div>
);

export const ListSkeleton = ({ items = 3 }) => (
    <div className="space-y-4">
        {[...Array(items)].map((_, i) => (
            <div key={i} className="card p-4 animate-pulse">
                <div className="flex items-start space-x-4">
                    <div className="w-16 h-16 bg-gray-200 rounded-lg flex-shrink-0"></div>
                    <div className="flex-1 space-y-2">
                        <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                        <div className="h-3 bg-gray-200 rounded w-1/2"></div>
                        <div className="flex space-x-2 mt-2">
                            <div className="h-6 bg-gray-200 rounded w-16"></div>
                            <div className="h-6 bg-gray-200 rounded w-20"></div>
                        </div>
                    </div>
                </div>
            </div>
        ))}
    </div>
);

export const DashboardSkeleton = () => (
    <div className="space-y-6 animate-pulse">
        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[...Array(4)].map((_, i) => (
                <div key={i} className="card p-6">
                    <div className="flex items-center justify-between mb-4">
                        <div className="w-12 h-12 bg-gray-200 rounded-lg"></div>
                        <div className="h-8 bg-gray-200 rounded w-16"></div>
                    </div>
                    <div className="h-4 bg-gray-200 rounded w-24"></div>
                </div>
            ))}
        </div>

        {/* Content Area */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="card p-6">
                <div className="h-6 bg-gray-200 rounded w-32 mb-4"></div>
                <div className="space-y-3">
                    {[...Array(3)].map((_, i) => (
                        <div key={i} className="h-16 bg-gray-200 rounded"></div>
                    ))}
                </div>
            </div>
            <div className="card p-6">
                <div className="h-6 bg-gray-200 rounded w-32 mb-4"></div>
                <div className="space-y-3">
                    {[...Array(3)].map((_, i) => (
                        <div key={i} className="h-16 bg-gray-200 rounded"></div>
                    ))}
                </div>
            </div>
        </div>
    </div>
);
