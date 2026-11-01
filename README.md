StudentFlow — Student Management System
StudentFlow is a web-based student management application built with Java, Spring Boot, and a browser-based frontend. It provides an administrative workspace for managing student records and a separate student workspace where authenticated students can view and update their own profiles.

Live application: https://student-management-4frw.onrender.com/
Production note: Public student registration is currently disabled. Student accounts are provisioned through the administrative process.

Features
Administrator workspace
- Secure administrator sign-in using JWT-based authentication.
- Create, view, update, and delete student records.
- Search student records by supported student details.
- Sort and paginate the student directory.
- View dashboard summaries such as total students, courses represented, and average age.
Student workspace
- Secure student sign-in.
- View and update the authenticated student's own profile.
- Profile access is restricted to the owner of that profile.
- Students cannot access the administrator's full student directory or perform administrator-only record-management operations.
Security and reliability
- JWT-based authentication for protected API endpoints.
- Role-based authorization for ADMIN and STUDENT users.
- Password hashing with BCrypt.
- Input validation and centralized error handling.
- Production configuration uses environment variables for database credentials and the JWT secret.
- Public registration is disabled in the production deployment.
Technology stack
Area	Technologies
Backend	Java 21, Spring Boot, Spring Security, Spring Data JPA
Authentication	JWT, BCrypt
Database	MySQL-compatible database; TiDB Cloud for the deployed environment
Frontend	HTML, CSS, JavaScript
Build	Maven
Deployment	Render


Application structure
The application is organized as a Spring Boot project. Frontend resources are served from the Spring Boot application's static resources, while the backend exposes REST endpoints for authentication and student/profile operations.
src/
└── main/
    ├── java/                 # Spring Boot application, controllers, services,
    │                         # entities, repositories and security configuration
    └── resources/
        ├── static/           # HTML, CSS and JavaScript frontend
        └── application*.properties
API overview
The following routes are part of the application:
Method	Endpoint	Purpose	Access
POST	/api/auth/login	Authenticate and obtain an access token	Public endpoint
POST	/api/auth/register	Register a student account	Disabled in production
GET	/api/students/me	Retrieve the signed-in student's own profile	Authenticated student
PUT	/api/students/me	Update the signed-in student's own profile	Authenticated student
GET	/api/students	Retrieve the student directory	Admin only
POST	/api/students	Create a student record	Admin only
PUT	/api/students/{id}	Update a student record	Admin only
DELETE	/api/students/{id}	Delete a student record	Admin only
GET	/api/students/search	Search student records	Admin only


All protected routes require a valid bearer token in the Authorization header:
Authorization: Bearer <access-token>
Run locally
Prerequisites
- JDK 21
- Maven
- A running MySQL-compatible database
- A database created for the application
Configure the environment
Configure the database connection and JWT signing secret using the property names expected by the selected Spring profile. The production example configuration uses these environment variables:
Variable	Purpose
DB_URL	JDBC connection URL
DB_USERNAME	Database username
DB_PASSWORD	Database password
JWT_SECRET	Base64-encoded JWT signing secret
PORT	Optional HTTP port supplied by the hosting platform


Keep real credentials and secrets outside source control. Do not commit .env files, real application.properties credentials, database passwords, JWT secrets, or generated tokens.
Build and run
From the project root, build the application:
mvn clean package
Then run it with Maven:
mvn spring-boot:run
Use the configuration for your local database and active Spring profile. The exact database URL and credentials depend on your local MySQL setup.
Deployment
The production application is deployed on Render, with TiDB Cloud providing the hosted MySQL-compatible database. Production settings are supplied through the Render environment configuration; do not place production credentials in this repository.

Live URL: https://student-management-4frw.onrender.com/

Render's free instance may spin down after inactivity, so the first request after an idle period can take longer while the service starts.
Verification performed
The following checks were performed against the deployed application during project verification:
Check	Observed result
Administrator login and dashboard	Successful
Student login and profile loading	Successful
Student's own profile endpoint	200 OK
Student attempt to access administrator directory	403 Forbidden
Unauthenticated access to protected student endpoints	401 Unauthorized
Administrator create/read/update/delete workflow	Successful
Data persistence after refresh	Verified
Search for an existing and a non-existing student	Verified
Pagination controls	Verified with the available test record
Sorting control	Verified to respond; ordering across multiple records was not separately measured
Public registration in production	Correctly disabled
Temporary production test data cleanup	Verified; original administrator account preserved


These are recorded manual verification results, not a claim of automated test-suite coverage.
Repository
GitHub repository: MADHURIMAKIIT25/student-management
Security note
Never publish passwords, BCrypt hashes, JWT secrets, access tokens, database connection strings containing credentials, or personal student data. Use placeholders in examples and configure secrets in your local environment or hosting provider's environment settings.
