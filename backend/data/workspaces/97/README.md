# 🎓 College Club Management System

A comprehensive full-stack web application for managing college clubs, events, memberships, and student activities.

## 📋 Overview



The College Club Management System is a modern, scalable platform that digitizes club management in educational institutions. It replaces manual coordination through WhatsApp, posters, and spreadsheets with a secure, user-friendly web platform.

## ✨ Features

### For Students
- 🔍 Browse and search clubs by category
- 📝 Submit membership requests with detailed information
- 📅 View and register for upcoming events
- 💳 Secure payment integration for paid events
- 🔔 Real-time notifications
- 📊 Personal dashboard with activity tracking

### For Club Heads
- 🏢 Manage club information and branding
- ✅ Approve/reject membership requests
- 🎉 Create and manage events
- 👥 View member lists and registrations
- 📈 Access club analytics
- 📢 Post announcements and achievements

### For Administrators
- ✔️ Approve new club creation requests
- 👤 Manage users and assign roles
- 📊 System-wide analytics and reports
- 🔧 Monitor all platform activities
- 🎯 Manage club categories

## 🛠️ Technology Stack

### Backend
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT
- **ORM**: JPA/Hibernate
- **Build Tool**: Maven

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite
- **Styling**: Tailwind CSS
- **Routing**: React Router v6
- **HTTP Client**: Axios
- **Icons**: Lucide React

### Additional Services
- **Payment**: Razorpay (test mode)
- **Email**: SMTP integration
- **Image Storage**: Cloudinary (optional)

## 📁 Project Structure

```
club_management/
├── backend/                 # Spring Boot application
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/college/clubmanagement/
│   │       │       ├── config/          # Configuration classes
│   │       │       ├── controller/      # REST controllers
│   │       │       ├── dto/             # Data Transfer Objects
│   │       │       ├── entity/          # JPA entities
│   │       │       ├── exception/       # Custom exceptions
│   │       │       ├── repository/      # JPA repositories
│   │       │       ├── security/        # Security & JWT
│   │       │       └── service/         # Business logic
│   │       └── resources/
│   │           └── application.properties
│   ├── pom.xml
│   └── README.md
│
└── frontend/                # React application
    ├── src/
    │   ├── components/      # Reusable components
    │   ├── context/         # React Context
    │   ├── pages/           # Page components
    │   ├── services/        # API services
    │   ├── App.jsx
    │   ├── main.jsx
    │   └── index.css
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    └── README.md
```

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Node.js 16 or higher
- MySQL 8.0 or higher
- Maven 3.6+
- npm or yarn

### Backend Setup

1. **Create MySQL Database**
   ```sql
   CREATE DATABASE club_management;
   ```

2. **Configure Database**
   
   Edit `backend/src/main/resources/application.properties`:
   ```properties
   spring.datasource.username=YOUR_USERNAME
   spring.datasource.password=YOUR_PASSWORD
   ```

3. **Run Backend**
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```

   Backend will start on `http://localhost:8080`

### Frontend Setup

1. **Install Dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Run Frontend**
   ```bash
   npm run dev
   ```

   Frontend will start on `http://localhost:5173`

## 📖 API Documentation

### Authentication Endpoints

```
POST /api/auth/register    - Register new user
POST /api/auth/login       - Login user
POST /api/auth/forgot-password - Request password reset
```

### Club Endpoints

```
GET    /api/clubs                  - Get all clubs
GET    /api/clubs/{id}             - Get club by ID
POST   /api/clubs                  - Create club
PUT    /api/clubs/{id}             - Update club
DELETE /api/clubs/{id}             - Delete club
POST   /api/clubs/{id}/join-request - Submit join request
```

### Event Endpoints

```
GET  /api/events                - Get all events
GET  /api/events/{id}           - Get event by ID
POST /api/events                - Create event
PUT  /api/events/{id}           - Update event
POST /api/events/{id}/register  - Register for event
```

### Payment Endpoints

```
POST /api/payments/create  - Create payment order
POST /api/payments/verify  - Verify payment
```

## 👥 User Roles

### STUDENT
- Browse clubs and events
- Submit membership requests
- Register for events
- Make payments

### CLUB_HEAD
- All student permissions
- Manage club details
- Create and manage events
- Approve membership requests

### ADMIN
- All permissions
- Approve new clubs
- Manage users
- Access system analytics

## 🔐 Security

- JWT-based authentication
- Password encryption with BCrypt
- Role-based access control (RBAC)
- CORS configuration
- SQL injection prevention
- XSS protection

## 🎨 UI/UX Features

- Modern, responsive design
- Gradient backgrounds and animations
- Glass morphism effects
- Smooth transitions
- Mobile-first approach
- Dark mode ready
- Accessible components

## 📊 Database Schema

### Core Tables
- `users` - User accounts and profiles
- `clubs` - Club information
- `club_members` - Club membership records
- `membership_requests` - Join requests
- `events` - Event details
- `event_registrations` - Event registrations
- `payments` - Payment records
- `posts` - Club posts and announcements
- `notifications` - User notifications

## 🧪 Testing

### Backend Tests
```bash
cd backend
mvn test
```

### Frontend Tests
```bash
cd frontend
npm run test
```

## 📦 Deployment

### Backend Deployment

1. Build JAR file:
   ```bash
   mvn clean package
   ```

2. Run JAR:
   ```bash
   java -jar target/club-management-1.0.0.jar
   ```

### Frontend Deployment

1. Build production bundle:
   ```bash
   npm run build
   ```

2. Deploy `dist` folder to hosting service (Netlify, Vercel, etc.)

## 🔧 Configuration

### Backend Configuration

Key properties in `application.properties`:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/club_management
spring.datasource.username=root
spring.datasource.password=password

# JWT
jwt.secret=your-secret-key
jwt.expiration=86400000

# Email
spring.mail.host=smtp.gmail.com
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### Frontend Configuration

Update API proxy in `vite.config.js`:

```javascript
server: {
  proxy: {
    '/api': 'http://localhost:8080'
  }
}
```

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Verify MySQL is running
   - Check credentials in application.properties
   - Ensure database exists

2. **CORS Errors**
   - Check CORS configuration in SecurityConfig.java
   - Verify frontend URL in allowed origins

3. **JWT Token Errors**
   - Ensure secret key is configured
   - Check token expiration time
   - Clear browser localStorage

4. **Build Failures**
   - Clean and rebuild: `mvn clean install`
   - Clear npm cache: `npm cache clean --force`

## 📝 Future Enhancements

- [ ] Real-time chat for club members
- [ ] AI-based club recommendations
- [ ] QR code-based event attendance
- [ ] Certificate generation
- [ ] Mobile app (React Native)
- [ ] Multi-college support
- [ ] Advanced analytics dashboard
- [ ] Social media integration

## 🤝 Contributing

This is an educational project. Contributions, issues, and feature requests are welcome!

## 📄 License

This project is created for educational purposes.

## 👨‍💻 Author

Created as a comprehensive full-stack demonstration project.

## 📞 Support

For issues or questions:
- Create an issue in the repository
- Check existing documentation
- Review API endpoints

## 🙏 Acknowledgments

- Spring Boot community
- React community
- Tailwind CSS team
- All open-source contributors

---

**Note**: This project uses test/sandbox modes for payment integration. For production use, proper payment gateway configuration and security audits are required.
