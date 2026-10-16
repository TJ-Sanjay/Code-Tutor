import api from './api';

const notificationService = {
    getNotifications: async () => {
        const response = await api.get('/notifications');
        return response.data;
    },

    getUnreadNotifications: async () => {
        const response = await api.get('/notifications/unread');
        return response.data;
    },

    getUnreadCount: async () => {
        const response = await api.get('/notifications/unread/count');
        return response.data.count;
    },

    markAsRead: async (id) => {
        await api.put(`/notifications/${id}/read`);
    },

    markAllAsRead: async () => {
        await api.put('/notifications/read-all');
    },

    deleteNotification: async (id) => {
        await api.delete(`/notifications/${id}`);
    }
};

export default notificationService;
