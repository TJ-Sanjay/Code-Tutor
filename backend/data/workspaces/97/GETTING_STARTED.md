# Getting Started with College Club Management System

## Quick Setup Guide

### Step 1: Database Setup

1. Install MySQL 8.0 or higher
2. Start MySQL service
3. Create database:
   ```sql
   CREATE DATABASE club_management;
   ```

### Step 2: Backend Setup

1. Navigate to backend directory:
   ```bash
   cd backend
   ```

2. Update database credentials in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.username=YOUR_MYSQL_USERNAME
   spring.datasource.password=YOUR_MYSQL_PASSWORD
   ```

3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. Verify backend is running:
   - Open browser: `http://localhost:8080/api`
   - You should see a 401 or 404 (this is normal without authentication)

### Step 3: Frontend Setup

1. Open a new terminal and navigate to frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start development server:
   ```bash
   npm run dev
   ```

4. Open browser: `http://localhost:5173`

### Step 4: Create First User

1. Click "Sign up" on the login page
2. Fill in the registration form
3. Submit to create your first student account

### Step 5: Test the Application

1. **As a Student**:
   - Browse clubs
   - View events
   - Submit join requests

2. **Create Admin User** (via database):
   ```sql
   INSERT INTO user_roles (user_id, role) VALUES (1, 'ADMIN');
   ```

3. **Create Club Head** (via admin or database):
   ```sql
   INSERT INTO user_roles (user_id, role) VALUES (2, 'CLUB_HEAD');
   ```

## Default Ports

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`
- MySQL: `localhost:3306`

## Troubleshooting

### Backend won't start
- Check if MySQL is running
- Verify database credentials
- Ensure port 8080 is not in use

### Frontend won't start
- Run `npm install` again
- Clear node_modules: `rm -rf node_modules && npm install`
- Check if port 5173 is available

### Can't login
- Check if backend is running
- Clear browser localStorage
- Verify user exists in database

## Next Steps

1. Read the full README.md for detailed documentation
2. Explore the API endpoints
3. Customize the application for your needs
4. Add sample data for testing

## Need Help?

- Check backend logs in terminal
- Check browser console for frontend errors
- Review application.properties configuration
- Ensure all services are running

Happy coding! 🚀
