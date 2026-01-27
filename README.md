# Microservices Docker Compose Setup

This repository contains a microservices architecture for Functional Dependency Discovery and Data Quality Assessment, orchestrated using Docker Compose.

## Architecture Overview

The system consists of the following services:

- **svc-discovery-service** (Port 8761) - Eureka service discovery server
- **sqlserver** (Port 1433) - Microsoft SQL Server 2022 database
- **sqlserver-init** - One-time database initialization service
- **svc-fd-discovery** (Port 8081) - Functional Dependency discovery service
- **svc-succinctness** (Port 8082) - Data succinctness assessment service
- **svc-coverage** (Port 8083) - Data coverage analysis service
- **svc-genuineness** (Port 8084) - Data genuineness validation service
- **svc-entropy** (Port 8085) - Data entropy calculation service
- **client** (Port 5173) - Frontend client application (Vite/React)

## Prerequisites

- Docker Desktop or Docker Engine (version 20.10 or higher)
- Docker Compose (version 2.0 or higher)
- At least 4GB of available RAM
- Ports 8761, 1433, 8081-8085, and 5173 available on your host machine

## Environment Configuration

### 1. Create Your `.env` File

A `.env.example` file is provided in the repository as a template. To set up your local environment:

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

A `.env.example` file is also provided in the `client-app` directory. To set up the frontend:

1. **Copy the template:**
   ```bash
   cp client-app/.env.example client-app/.env
   ```

2. **Edit the file** with your local configuration:
   ```bash
   VITE_API_BASE_URL=http://localhost:8761
   ```

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

3. **View logs:**
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

### Using Custom Environment File

All services use the `.env` file by default. If you need multiple environment configurations (development, staging, production), you can:

1. **Create separate `.env` files** for each environment:
   ```bash
   cp .env.example .env.development
   cp .env.example .env.staging
   cp .env.example .env.production
   ```

2. **Use the desired `.env` file** by renaming or copying it:
   ```bash
   # For production
   cp .env.production .env
   docker-compose up
   
   # For staging
   cp .env.staging .env
   docker-compose up
   ```

3. **Or use docker-compose with --env-file flag:**
   ```bash
   docker-compose --env-file .env.production up
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

### Database Connection Issues

1. **Verify SQL Server is healthy:**
   ```bash
   docker-compose logs sqlserver
   ```

2. **Test database connection:**
   ```bash
   docker exec -it sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourPassword" -C
   ```

### Service Won't Start

1. **Check logs:**
   ```bash
   docker-compose logs <service-name>
   ```

2. **Restart specific service:**
   ```bash
   docker-compose restart <service-name>
   ```

3. **Remove and recreate:**
   ```bash
   docker-compose rm -f <service-name>
   docker-compose up -d <service-name>
   ```

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

