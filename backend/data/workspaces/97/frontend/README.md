# College Club Management System - Frontend

Modern React application for managing college clubs, events, and student activities.

## Prerequisites

- Node.js 16+ and npm
- Backend API running on `http://localhost:8080`

## Technology Stack

- **Framework**: React 18
- **Build Tool**: Vite
- **Styling**: Tailwind CSS
- **Routing**: React Router DOM v6
- **HTTP Client**: Axios
- **Icons**: Lucide React
- **Form Handling**: React Hook Form + Yup

## Setup Instructions

### 1. Install Dependencies

```bash
cd frontend
npm install
```

### 2. Environment Configuration

The frontend is configured to proxy API requests to `http://localhost:8080`. If your backend runs on a different port, update `vite.config.js`:

```javascript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:YOUR_PORT',
      changeOrigin: true,
    }
  }
}
```

### 3. Run Development Server

```bash
npm run dev
```

The application will start on `http://localhost:5173`

### 4. Build for Production

```bash
npm run build
```

The production build will be in the `dist` folder.

## Project Structure

```
src/
├── components/          # Reusable components
│   ├── Navbar.jsx
│   ├── Footer.jsx
│   └── ProtectedRoute.jsx
├── context/            # React Context providers
│   └── AuthContext.jsx
├── pages/              # Page components
│   ├── auth/           # Authentication pages
│   │   ├── Login.jsx
│   │   └── Signup.jsx
│   ├── student/        # Student pages
│   │   ├── StudentDashboard.jsx
│   │   ├── BrowseClubs.jsx
│   │   ├── ClubDetails.jsx
│   │   ├── Events.jsx
│   │   └── EventDetails.jsx
│   ├── clubhead/       # Club head pages
│   │   └── ClubHeadDashboard.jsx
│   └── admin/          # Admin pages
│       └── AdminDashboard.jsx
├── services/           # API services
│   └── api.js
├── App.jsx             # Main app component
├── main.jsx            # Entry point
└── index.css           # Global styles
```

## Features

### Authentication
- User login and registration
- JWT token-based authentication
- Role-based access control
- Automatic token refresh

### Student Features
- Browse and search clubs
- Filter clubs by category
- View club details
- Submit membership requests
- View upcoming events
- Register for events
- Dashboard with stats

### Club Head Features
- Manage club information
- Create and manage events
- Approve/reject membership requests
- View registrations
- Dashboard with analytics

### Admin Features
- Approve new clubs
- Manage users and roles
- System-wide analytics
- Monitor all activities

## Styling

The application uses Tailwind CSS with custom utility classes:

- `.btn-primary` - Primary button style
- `.btn-secondary` - Secondary button style
- `.btn-outline` - Outlined button
- `.card` - Card container with shadow
- `.card-glass` - Glass morphism card
- `.input-field` - Styled input field
- `.badge` - Badge component
- `.gradient-text` - Gradient text effect

## API Integration

All API calls are made through the Axios instance in `src/services/api.js`. The instance automatically:

- Adds JWT token to requests
- Handles 401 unauthorized errors
- Redirects to login on authentication failure

Example usage:

```javascript
import api from '../services/api';

// GET request
const response = await api.get('/clubs');

// POST request
const response = await api.post('/clubs', clubData);
```

## Routing

The application uses React Router v6 with protected routes:

- Public routes: `/login`, `/signup`
- Student routes: `/student/dashboard`, `/clubs`, `/events`
- Club Head routes: `/clubhead/dashboard`
- Admin routes: `/admin/dashboard`

## State Management

- **AuthContext**: Manages user authentication state
- Local state with useState for component-level state
- API calls with useEffect for data fetching

## Responsive Design

The application is fully responsive with breakpoints:

- Mobile: < 768px
- Tablet: 768px - 1024px
- Desktop: > 1024px

## Performance Optimizations

- Lazy loading of routes
- Image optimization
- Code splitting
- Memoization of expensive computations

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Development Tips

### Adding New Pages

1. Create component in appropriate `pages/` subdirectory
2. Add route in `App.jsx`
3. Add navigation link in `Navbar.jsx`

### Adding New API Endpoints

1. Create service function in `services/api.js`
2. Use in component with useEffect or event handlers

### Styling Guidelines

- Use Tailwind utility classes
- Follow existing color scheme (primary, secondary)
- Use custom utility classes from `index.css`
- Maintain consistent spacing and sizing

## Troubleshooting

### API Connection Issues

- Ensure backend is running on port 8080
- Check CORS configuration in backend
- Verify proxy settings in `vite.config.js`

### Build Errors

- Clear node_modules and reinstall: `rm -rf node_modules && npm install`
- Clear Vite cache: `rm -rf node_modules/.vite`

### Authentication Issues

- Clear localStorage: `localStorage.clear()`
- Check JWT token expiration
- Verify backend authentication endpoints

## Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build

## License

This project is created for educational purposes.
