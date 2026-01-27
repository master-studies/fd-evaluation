# Microservices Docker Compose Setup

This repository contains a microservices architecture for Functional Dependency Discovery and Data Quality Assessment, orchestrated using Docker Compose.

## Project Structure

### client-app/
The frontend application built with React, TypeScript, and Vite. Provides a web interface for service discovery visualization and interaction with the microservices.

### scripts/
Database initialization and configuration scripts:
- **init-db.sql** - SQL Server schema initialization script that creates the CleaningFd database and tables.
Automatically executed by the sqlserver-init container on first startup.

### services/
The backend microservices written in Java with Spring Boot:
- **svc-discovery-service** - Eureka service registry for microservice discovery and registration
- **svc-fd-discovery** - Analyzes datasets to discover functional dependencies between attributes
- **svc-succinctness** - Evaluates data quality by measuring redundancy and compactness
- **svc-coverage** - Assesses completeness and coverage of functional dependencies in datasets
- **svc-genuineness** - Validates data authenticity and integrity against functional dependency rules
- **svc-entropy** - Calculates information entropy to measure data uncertainty and randomness based on plaque test

Each service is independently deployable with its own Dockerfile and Maven configuration.

## Prerequisites

- Docker Desktop or Docker Engine (version 20.10 or higher)
- Docker Compose (version 2.0 or higher)
- At least 4GB of available RAM
- Ports 8761, 1433, 8081-8085, and 5173 available on your host machine

## Environment Configuration

### 1. Create Your `.env` File

A `.env.example` file is provided in the repository as a template. This configuration file is shared between all services and contains configurable parameters for both backend microservices (database connections, Eureka discovery, CORS) and frontend services (API endpoints). To set up your local environment:

1. **Copy the template:**
   ```bash
   cp .env.example .env
   ```

2. **Edit the `.env` file** with your local configuration values:
   ```bash
   # Spring Profile Configuration
   SPRING_PROFILES_ACTIVE=dev
   
   # SQL Server Configuration - Set your own strong password
   # This is for the SQL Server sa (system administrator) account
   MSSQL_SA_PASSWORD=YourStrongPassword123!
   
   # Database Connection URL
   DB_URL=jdbc:sqlserver://sqlserver:1433;databaseName=CleaningFd;encrypt=false;trustServerCertificate=true
   
   # Database Configuration for Application Services
   # Use the dedicated application user created by init-db.sql (recommended)
   DB_USERNAME=cleaningfd_app
   DB_PASSWORD=AppUser123!
   
   # CORS Configuration
   CORS_ALLOWED_ORIGINS=http://localhost:5173
   
   # Eureka Service Discovery URL
   EUREKA_URL=http://svc-discovery-service:8761/eureka/
   ```

3. **Key variables explained:**
   - **`SPRING_PROFILES_ACTIVE`**: Spring Boot profile (dev, staging, prod) - controls application configuration
   - **`MSSQL_SA_PASSWORD`**: Required by the SQL Server Docker container to initialize the `sa` account
   - **`DB_URL`**: JDBC connection string for the database
   - **`DB_USERNAME`/`DB_PASSWORD`**: Application service database credentials (use dedicated user for security, not `sa`)
   - **`CORS_ALLOWED_ORIGINS`**: Allowed domains for cross-origin requests (frontend URL)
   - **`EUREKA_URL`**: Service discovery registry URL for microservices registration


### 2. Client Environment File

A `.env.sample` file is provided in the `client-app` directory. To set up the frontend:

1. **Copy the template:**
   ```bash
   cp client-app/.env.sample client-app/.env
   ```

2. **Edit the file** with your local configuration if needed:
   ```bash
   # API Configuration
   VITE_API_BASE_URL=http://localhost:8080
   VITE_API_TIMEOUT=30000
   
   # Eureka Discovery Configuration
   VITE_EUREKA_URL=
   VITE_EUREKA_APP_NAME=client-app
   
   # Application Settings
   VITE_APP_ENV=development
   VITE_LOG_LEVEL=debug
   ```

**Note:** The root `.env` file is used by backend services and Docker Compose, while `client-app/.env` is specifically loaded by the client container.

### 3. Database Initialization Script

The `sqlserver-init` service automatically runs the database initialization script (`scripts/init-db.sql`) when SQL Server starts for the first time.

**How it works:**
- The initialization script is already included in the repository at `scripts/init-db.sql`
- The `sqlserver-init` service waits for SQL Server to be healthy
- It runs the script once automatically with the `restart: "no"` policy
- The script creates the database schema, tables, and any seed data needed
- Normally you shouldn't be needed to change anything in schema nor the script, as the table and columns are required for the backend services as they are described.

**Note:** The init script runs only once when the containers are first started. If you modify `init-db.sql` and need to re-run it:
```bash
docker-compose down -v
docker-compose up
```

## Quick Start

### Basic Usage

1. **Start all services:**
   ```bash
   docker-compose up
   ```

2. **Start services in detached mode (background):**
   ```bash
   docker-compose up -d
   ```

3. **Access the application:**
   
   Once all containers are up and running successfully, the application is accessible at:
   - **Frontend:** http://localhost:5173 (or your configured client port)
   - **Eureka Dashboard:** http://localhost:8761

4. **View logs:**
   ```bash
   docker-compose logs -f
   ```

4. **Stop all services:**
   ```bash
   docker-compose down
   ```

5. **Stop services and remove volumes:**
   ```bash
   docker-compose down -v
   ```

## Service-Specific Commands

### Start Only Database Services
```bash
docker-compose up sqlserver sqlserver-init
```

### Start Backend Services Only
```bash
docker-compose up svc-discovery-service svc-fd-discovery svc-succinctness svc-coverage svc-genuineness svc-entropy
```

### Start Frontend Only
```bash
docker-compose up client
```

### Rebuild Specific Service
```bash
docker-compose build svc-fd-discovery
docker-compose up -d svc-fd-discovery
```

### Rebuild All Services
```bash
docker-compose build --no-cache
docker-compose up -d
```

## Health Checks

All services include health checks. To verify service status:

```bash
docker-compose ps
```

Individual service health endpoints:
- Discovery Service: http://localhost:8761/actuator/health
- FD Discovery: http://localhost:8081/actuator/health
- Succinctness: http://localhost:8082/actuator/health
- Coverage: http://localhost:8083/actuator/health
- Genuineness: http://localhost:8084/actuator/health
- Entropy: http://localhost:8085/actuator/health
- Eureka Dashboard: http://localhost:8761

## Troubleshooting

### Port Already in Use

Check what's using the port:
```bash
# Windows
netstat -ano | findstr :8081

# Stop the service or change the port in docker-compose.yml
```

### Clean Start

To completely reset the environment:
```bash
docker-compose down -v
docker system prune -a
docker-compose up --build
```


## Network Configuration

All services are connected via the `microservices-network` bridge network, allowing inter-service communication using container names as hostnames.

