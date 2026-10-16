import React from 'react';
import { Users, AlertCircle } from 'lucide-react';

const CapacityIndicator = ({ current, max, showLabel = true }) => {
    if (!max) return null;

    const percentage = (current / max) * 100;
    const spotsLeft = max - current;
    const isFull = current >= max;
    const isAlmostFull = percentage >= 80 && !isFull;

    const getColor = () => {
        if (isFull) return 'bg-red-500';
        if (isAlmostFull) return 'bg-yellow-500';
        return 'bg-green-500';
    };

    const getTextColor = () => {
        if (isFull) return 'text-red-700';
        if (isAlmostFull) return 'text-yellow-700';
        return 'text-green-700';
    };

    return (
        <div className="space-y-2">
            {showLabel && (
                <div className="flex items-center justify-between text-sm">
                    <span className="flex items-center gap-1 text-gray-600">
                        <Users className="w-4 h-4" />
                        {current} / {max} registered
                    </span>
                    {isAlmostFull && !isFull && (
                        <span className="flex items-center gap-1 text-yellow-600 font-medium">
                            <AlertCircle className="w-4 h-4" />
                            Almost Full!
                        </span>
                    )}
                    {isFull && (
                        <span className="text-red-600 font-medium">Full</span>
                    )}
                    {!isFull && !isAlmostFull && (
                        <span className={getTextColor()}>{spotsLeft} spots left</span>
                    )}
                </div>
            )}

            <div className="w-full bg-gray-200 rounded-full h-2.5 overflow-hidden">
                <div
                    className={`h-full ${getColor()} transition-all duration-300 rounded-full`}
                    style={{ width: `${Math.min(percentage, 100)}%` }}
                />
            </div>
        </div>
    );
};

export default CapacityIndicator;
