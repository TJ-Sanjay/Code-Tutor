# College Club Management System - Backend

Spring Boot REST API for managing college clubs, events, memberships, and payments.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- IDE (IntelliJ IDEA, Eclipse, or VS Code)

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Security**: Spring Security + JWT
- **Database**: MySQL with JPA/Hibernate
- **Build Tool**: Maven
- **Authentication**: JWT (JSON Web Tokens)
- **Validation**: Hibernate Validator

## Setup Instructions

### 1. Database Configuration

Create a MySQL database:

```sql
CREATE DATABASE club_management;
```

Update `src/main/resources/application.properties` with your MySQL credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/club_management?createDatabaseIfNotExist=true
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

### 2. Email Configuration (Optional)

For email notifications, update SMTP settings in `application.properties`:

```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### 3. Build and Run

```bash
# Navigate to backend directory
cd backend

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The server will start on `http://localhost:8080/api`

## API Endpoints

### Authentication

- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `POST /api/auth/forgot-password` - Request password reset

### Clubs

- `GET /api/clubs` - Get all active clubs
- `GET /api/clubs?category=TECHNICAL` - Filter by category
- `GET /api/clubs?search=keyword` - Search clubs
- `GET /api/clubs/{id}` - Get club details
- `POST /api/clubs` - Create new club (CLUB_HEAD, ADMIN)
- `PUT /api/clubs/{id}` - Update club (CLUB_HEAD, ADMIN)
- `DELETE /api/clubs/{id}` - Delete club (ADMIN)
- `POST /api/clubs/{id}/join-request` - Submit membership request (STUDENT)

### Events

- `GET /api/events` - Get all events
- `GET /api/events?upcoming=true` - Get upcoming events
- `GET /api/events?clubId={id}` - Get events by club
- `GET /api/events/{id}` - Get event details
- `POST /api/events` - Create event (CLUB_HEAD, ADMIN)
- `PUT /api/events/{id}` - Update event (CLUB_HEAD, ADMIN)
- `POST /api/events/{id}/register` - Register for event (STUDENT)

### Payments

- `POST /api/payments/create` - Create payment order
- `POST /api/payments/verify` - Verify payment

### Notifications

- `GET /api/notifications` - Get user notifications
- `PUT /api/notifications/{id}/read` - Mark as read

### Admin

- `GET /api/admin/clubs/pending` - Get pending club approvals
- `PUT /api/admin/clubs/{id}/approve` - Approve club
- `GET /api/admin/analytics` - Get system analytics

## User Roles

- **STUDENT**: Browse clubs, join clubs, register for events
- **CLUB_HEAD**: Manage clubs, create events, approve membership requests
- **ADMIN**: Full system access, approve clubs, manage users

## JWT Authentication

All protected endpoints require a JWT token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

## Project Structure

```
src/main/java/com/college/clubmanagement/
├── config/              # Configuration classes
├── controller/          # REST controllers
├── dto/                 # Data Transfer Objects
├── entity/              # JPA entities
├── exception/           # Custom exceptions
├── repository/          # JPA repositories
├── security/            # Security & JWT
└── service/             # Business logic
```

## Database Schema

The application uses JPA with Hibernate to auto-generate the database schema. Key tables:

- `users` - User accounts
- `clubs` - Club information
- `club_members` - Club membership
- `membership_requests` - Join requests
- `events` - Event details
- `event_registrations` - Event registrations
- `payments` - Payment records
- `posts` - Club posts
- `notifications` - User notifications

## Development

### Running Tests

```bash
mvn test
```

### Building for Production

```bash
mvn clean package
java -jar target/club-management-1.0.0.jar
```

## Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Ensure MySQL is running
   - Verify credentials in application.properties
   - Check if database exists

2. **Port Already in Use**
   - Change server port in application.properties:
     ```properties
     server.port=8081
     ```

3. **JWT Token Errors**
   - Ensure JWT secret is properly configured
   - Check token expiration time

## License

This project is created for educational purposes.

## Support

For issues or questions, please create an issue in the repository.
