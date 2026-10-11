import axios from 'axios';

// Create an Axios instance with base URL for the Spring Boot backend
const api = axios.create({
  baseURL: 'http://localhost:8080/api', // Matches the backend server port and default context path
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add a request interceptor to attach JWT tokens if implemented later
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export default api;
