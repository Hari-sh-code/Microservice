# Docker Compose Configuration Analysis & Migration Guide

**Date:** May 29, 2026  
**Project:** Microservice (Spring Boot + Docker Compose)  
**Status:** Analysis & Migration from Docker Swarm to Docker Compose Complete

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Original Problem](#original-problem)
3. [Root Cause Analysis](#root-cause-analysis)
4. [Architecture Overview](#architecture-overview)
5. [Configuration Changes](#configuration-changes)
6. [File-by-File Documentation](#file-by-file-documentation)
7. [Dependency Chain](#dependency-chain)
8. [Network Architecture](#network-architecture)
9. [Service Communication](#service-communication)
10. [Cleanup & Fresh Start](#cleanup--fresh-start)
11. [Verification & Testing](#verification--testing)

---

## Executive Summary

### Problem
```
Error response from daemon:
failed to set up container networking:
Could not attach to network microservice_swarm-network:
rpc error: code = PermissionDenied
desc = network microservice_swarm-network not manually attachable
```

### Root Cause
The project was previously deployed using **Docker Swarm** (`docker stack deploy`), which created an **overlay network** named `microservice_swarm-network`. When attempting to use **Docker Compose**, the system tried to attach to this same network, but:
- Overlay networks are not attachable by default in Compose
- The network was marked with Docker Swarm metadata preventing manual attachment
- A conflict existed between Swarm and Compose configurations

### Solution
Migrate the `docker-compose.yml` from Swarm overlay network to Docker Compose bridge network, with proper service dependency management and health checks.

---

## Original Problem

### Error Details
```
docker compose up --build
```

**Error Message:**
```
Error response from daemon:
failed to set up container networking:
Could not attach to network microservice_swarm-network:
rpc error: code = PermissionDenied
desc = network microservice_swarm-network not manually attachable
```

### Why This Happens
1. **Docker Swarm Overlay Network**: When you run `docker stack deploy`, Docker creates overlay networks (designed for swarm cluster communication)
2. **Network Metadata**: The network has labels like `com.docker.stack.namespace=microservice` marking it as Swarm-managed
3. **Compose Incompatibility**: Docker Compose uses bridge networks (single-host) while Swarm uses overlay networks (multi-host)
4. **Conflict**: Compose sees a network with the same name but cannot attach services to a non-attachable overlay network

---

## Root Cause Analysis

### Issues Identified

| # | Issue | Component | Severity | Category |
|---|-------|-----------|----------|----------|
| 1 | Network name `swarm-network` conflicts with Docker Swarm overlay network | docker-compose.yml | 🔴 CRITICAL | Configuration |
| 2 | Network uses `driver: bridge` but name suggests Swarm usage | docker-compose.yml | 🔴 CRITICAL | Naming Convention |
| 3 | MySQL has no health check | docker-compose.yml | 🟡 MEDIUM | Reliability |
| 4 | Services start without waiting for database readiness | docker-compose.yml | 🟡 MEDIUM | Sequencing |
| 5 | Backend might connect to MySQL before it's ready | docker-compose.yml | 🟡 MEDIUM | Sequencing |
| 6 | No explicit container names | docker-compose.yml | 🟢 LOW | Debugging |
| 7 | No persistence for MySQL data | docker-compose.yml | 🟡 MEDIUM | Data |
| 8 | Environment variables not injectable at runtime | docker-compose.yml | 🟢 LOW | Best Practice |
| 9 | Image tags not specified (using implicit `latest`) | docker-compose.yml | 🟢 LOW | Consistency |
| 10 | Eureka dependency order incorrect | docker-compose.yml | 🟡 MEDIUM | Sequencing |

### Why Each Issue Matters

**Issue #1-2: Network Naming**
- The name `swarm-network` is descriptive of Docker Swarm, not Docker Compose
- When Docker Swarm stack was previously deployed, it created an overlay network with this name
- Residual overlay network still exists on the system
- Docker Compose tries to use a network with the same name, causing attachment failure
- **Solution**: Rename to `compose-network` to avoid conflicts

**Issue #3-5: MySQL Health & Sequencing**
- Docker Compose's `depends_on` only checks if a container exists, not if it's ready
- MySQL might be starting but not accepting connections
- Backend or other services might try to connect before MySQL is initialized
- **Solution**: Add health check and use `condition: service_healthy`

**Issue #6: Container Names**
- Auto-generated names like `microservice-mysql-1` are hard to reference
- Makes docker logs and debugging more difficult
- **Solution**: Add explicit `container_name` fields

**Issue #7: MySQL Persistence**
- Without a volume, data is lost when container stops
- Database schema and data are recreated on each startup
- **Solution**: Add named volume `mysql-data`

**Issue #8: Environment Variables**
- Configuration is hardcoded in application.properties
- Cannot override without modifying code
- Best practice: externalize configuration
- **Solution**: Add `environment` section in docker-compose.yml

**Issue #9: Image Tags**
- Implicit `latest` tag can cause confusion
- Rebuild caches might persist
- **Solution**: Explicit `:latest` tag

**Issue #10: Eureka Sequencing**
- Eureka server should be fully ready before other services register
- API Gateway and Backend both need Eureka
- **Solution**: Use proper `depends_on` with explicit service names

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Docker Compose Network                 │
│                   (compose-network)                      │
│                    Bridge Driver                         │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────┐  ┌────────────────┐                 │
│  │  API Gateway   │  │  Eureka Server │                 │
│  │  (Port 8080)   │  │  (Port 8761)   │                 │
│  │  Container:    │  │  Container:    │                 │
│  │  api-gateway   │  │  eureka-server │                 │
│  │  app           │  │  -app          │                 │
│  └────────────────┘  └────────────────┘                 │
│         │                     │                          │
│         │                     │                          │
│         └─────────┬───────────┘                          │
│                   │ Service Registration                 │
│         ┌─────────▼──────────┐                          │
│         │ Backend Service    │                          │
│         │ (Port 8081)        │                          │
│         │ Container:         │                          │
│         │ backend-service    │                          │
│         │ -app               │                          │
│         └──────────┬─────────┘                          │
│                    │ JDBC Connection                    │
│         ┌──────────▼──────────┐                         │
│         │   MySQL Database    │                         │
│         │ (Port 3307 → 3306)  │                         │
│         │ Container:          │                         │
│         │ mysql-db            │                         │
│         └────────────────────┘                          │
│                                                           │
└─────────────────────────────────────────────────────────┘

VOLUMES: mysql-data (persistent)
```

---

## Configuration Changes

### BEFORE (Original docker-compose.yml)

```yaml
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: mypassword
      MYSQL_DATABASE: testdb
    ports:
      - "3307:3306"
    networks:
      - swarm-network

  eureka-server:
    image: microservice-eureka-server
    build:
      context: ./Eureka-Server
    ports:
      - "8761:8761"
    networks:
      - swarm-network

  api-gateway:
    image: microservice-api-gateway
    build:
      context: ./API-Gateway
    ports:
      - "8080:8080"
    depends_on:
      - eureka-server
    networks:
      - swarm-network

  backend:
    image: microservice-backend
    build:
      context: ./Backend-Service
    depends_on:
      - mysql
      - eureka-server
    networks:
      - swarm-network

networks:
  swarm-network:
    driver: bridge
```

### Issues with BEFORE Version
✗ Network named `swarm-network` conflicts with Docker Swarm overlay network  
✗ No `container_name` fields for debugging  
✗ No health checks for MySQL  
✗ No `condition: service_healthy` for proper sequencing  
✗ No persistence volume for MySQL  
✗ No environment variables for configuration  
✗ Image tags not specified (implicit `latest`)  
✗ No memory limits for Java applications  

---

### AFTER (Corrected docker-compose.yml)

```yaml
services:

  mysql:
    image: mysql:8
    container_name: mysql-db
    environment:
      MYSQL_ROOT_PASSWORD: mypassword
      MYSQL_DATABASE: testdb
    ports:
      - "3307:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      timeout: 20s
      retries: 10
    networks:
      - compose-network
    volumes:
      - mysql-data:/var/lib/mysql

  eureka-server:
    image: microservice-eureka-server:latest
    container_name: eureka-server-app
    build:
      context: ./Eureka-Server
    ports:
      - "8761:8761"
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m
    networks:
      - compose-network
    depends_on:
      - mysql

  api-gateway:
    image: microservice-api-gateway:latest
    container_name: api-gateway-app
    build:
      context: ./API-Gateway
    ports:
      - "8080:8080"
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
    depends_on:
      eureka-server:
        condition: service_started
    networks:
      - compose-network

  backend:
    image: microservice-backend:latest
    container_name: backend-service-app
    build:
      context: ./Backend-Service
    ports:
      - "8081:8081"
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=mypassword
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
    depends_on:
      mysql:
        condition: service_healthy
      eureka-server:
        condition: service_started
    networks:
      - compose-network

networks:
  compose-network:
    driver: bridge

volumes:
  mysql-data:
```

### Changes Made (Summary)

✅ Network renamed: `swarm-network` → `compose-network`  
✅ Added `container_name` to all services  
✅ Added health check to MySQL  
✅ Added `condition: service_healthy` for MySQL dependency  
✅ Added persistence volume: `mysql-data:/var/lib/mysql`  
✅ Added environment variables for configuration  
✅ Added explicit `:latest` image tags  
✅ Added Java memory limits: `-Xmx512m -Xms256m`  

---

## File-by-File Documentation

### File: docker-compose.yml

#### Location
```
/home/harish/IdeaProjects/Microservice/docker-compose.yml
```

#### Purpose
Defines the complete Docker Compose configuration for:
- Service definitions (MySQL, Eureka, API Gateway, Backend)
- Container orchestration
- Network configuration
- Volume management
- Service dependencies
- Environment configuration
- Health monitoring

#### Section 1: MySQL Service

```yaml
mysql:
  image: mysql:8                    # MySQL 8 official image
  container_name: mysql-db          # Explicit container name for debugging
  environment:
    MYSQL_ROOT_PASSWORD: mypassword # Root password (change in production!)
    MYSQL_DATABASE: testdb          # Create database on startup
  ports:
    - "3307:3306"                   # Map 3307 (host) → 3306 (container)
                                    # Use 3307 to avoid conflicts with local MySQL
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    timeout: 20s                    # Max time for health check
    retries: 10                     # Retry up to 10 times
    # Default interval: 30s between retries
    # Service considered healthy when test succeeds
  networks:
    - compose-network               # Connect to bridge network
  volumes:
    - mysql-data:/var/lib/mysql     # Persist data across restarts
```

**Why These Settings?**

| Setting | Value | Reason |
|---------|-------|--------|
| `image: mysql:8` | Official MySQL 8 | Stable, well-maintained, supports modern SQL |
| `container_name: mysql-db` | Fixed name | Easy to reference in logs and commands |
| `MYSQL_ROOT_PASSWORD` | mypassword | Default credentials (change for production) |
| `MYSQL_DATABASE: testdb` | testdb | Database created automatically on startup |
| `ports: 3307:3306` | Non-default port | Avoid conflicts with local MySQL installations |
| `healthcheck` | mysqladmin ping | Waits for MySQL to be accepting connections |
| `timeout: 20s` | 20 seconds | Reasonable timeout for ping command |
| `retries: 10` | 10 attempts | Total wait time ~300s (30s × 10) before failing |
| `volumes: mysql-data` | Named volume | Data persists across container lifecycle |

#### Section 2: Eureka Server Service

```yaml
eureka-server:
  image: microservice-eureka-server:latest
  container_name: eureka-server-app
  build:
    context: ./Eureka-Server        # Dockerfile location
  ports:
    - "8761:8761"                   # Standard Eureka port
  environment:
    - JAVA_OPTS=-Xmx512m -Xms256m   # JVM memory limits
                                    # Min: 256MB, Max: 512MB
  networks:
    - compose-network
  depends_on:
    - mysql                         # MySQL should exist first
                                    # (though Eureka doesn't use it)
```

**Purpose of Eureka Server:**
- Service discovery registry
- API Gateway and Backend register with Eureka
- Clients query Eureka to find service instances
- Port 8761: Eureka UI and REST API

**Memory Settings:**
```
-Xms256m = Initial heap size (256 MB)
-Xmx512m = Maximum heap size (512 MB)
```
This prevents Java from allocating excessive memory in containers.

#### Section 3: API Gateway Service

```yaml
api-gateway:
  image: microservice-api-gateway:latest
  container_name: api-gateway-app
  build:
    context: ./API-Gateway
  ports:
    - "8080:8080"                   # Gateway port (client-facing)
  environment:
    - JAVA_OPTS=-Xmx512m -Xms256m   # JVM memory limits
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
      # Spring Cloud Gateway uses Eureka for service discovery
      # Points to Eureka service by container name (internal DNS)
  depends_on:
    eureka-server:
      condition: service_started    # Wait until Eureka container exists
                                    # (not until it's fully ready)
  networks:
    - compose-network
```

**Purpose:**
- Entry point for all client requests
- Routes requests to backend services
- Uses Spring Cloud Gateway
- Discovers Backend Service via Eureka

**Network Configuration:**
```
External:  http://localhost:8080/api/**
Internal:  http://api-gateway:8080/api/**
           (container name used by other services)
```

**Eureka URL:**
- Container name: `eureka-server`
- Internal DNS resolves `eureka-server` to the container's IP
- Service URL: `http://eureka-server:8761/eureka`

#### Section 4: Backend Service

```yaml
backend:
  image: microservice-backend:latest
  container_name: backend-service-app
  build:
    context: ./Backend-Service
  ports:
    - "8081:8081"                   # Backend service port
  environment:
    - JAVA_OPTS=-Xmx512m -Xms256m
    - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true
      # JDBC connection string
      # mysql:3306 = internal container DNS + MySQL port
      # testdb = database name
      # useSSL=false = MySQL doesn't use SSL in Docker
      # allowPublicKeyRetrieval=true = Allow authentication methods
    - SPRING_DATASOURCE_USERNAME=root
    - SPRING_DATASOURCE_PASSWORD=mypassword
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
  depends_on:
    mysql:
      condition: service_healthy    # Wait until MySQL health check passes
                                    # (database is actually ready)
    eureka-server:
      condition: service_started    # Wait until Eureka container exists
  networks:
    - compose-network
```

**Purpose:**
- Business logic service
- Provides REST API endpoints
- Connects to MySQL database
- Registers with Eureka for discovery

**Database Connection Details:**
| Component | Value | Purpose |
|-----------|-------|---------|
| Host | mysql | Container hostname (internal DNS) |
| Port | 3306 | MySQL port (internal, not the 3307 mapping) |
| Database | testdb | Created by MySQL service |
| Username | root | Root user (use dedicated user in production) |
| Password | mypassword | Matches `MYSQL_ROOT_PASSWORD` |

**Critical Setting:**
```
condition: service_healthy
```
This ensures Backend waits for MySQL to be accepting connections, not just started.

#### Section 5: Networks Definition

```yaml
networks:
  compose-network:
    driver: bridge                  # Bridge driver (single-host)
                                    # Suitable for Docker Compose
                                    # Not for Docker Swarm (which uses overlay)
```

**Network Drivers Explained:**

| Driver | Use Case | Scope | Attachable |
|--------|----------|-------|-----------|
| bridge | Docker Compose (single host) | Single host | ✅ Yes |
| overlay | Docker Swarm (multi-host) | Swarm cluster | ❌ No (by default) |
| host | Direct host network | Single host | N/A |

**Why Bridge for Compose:**
- Docker Compose runs on a single Docker host
- Bridge network connects containers on the same host
- Provides internal DNS for container names
- No need for cluster communication

**Why NOT Overlay:**
- Overlay networks are for Docker Swarm
- They don't work with `docker compose up`
- Overlay networks have metadata preventing manual attachment
- (This was the original problem!)

#### Section 6: Volumes Definition

```yaml
volumes:
  mysql-data:                       # Named volume for MySQL
                                    # Location: /var/lib/docker/volumes/mysql-data/_data
```

**Purpose:**
- Persist database data across container restarts
- Managed by Docker (automatic backup location)
- Can be backed up easily
- Survives `docker compose down` (unless using `docker compose down -v`)

**Alternative: Bind Mount**
```yaml
volumes:
  - ./mysql-data:/var/lib/mysql     # Local directory (not recommended)
```
We use named volume instead because:
- Better performance
- Managed by Docker
- Easier to backup
- Works on any OS (including Windows)

---

### File: Application Properties (No Changes)

#### API-Gateway/src/main/resources/application.properties

```properties
spring.application.name=API-Gateway
server.port=8080

eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka

spring.cloud.gateway.routes[0].id=backend-service
spring.cloud.gateway.routes[0].uri=lb://BACKEND
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/**
```

**Analysis:**
✅ **Service Name:** `API-Gateway` - For Spring Cloud identification  
✅ **Port:** `8080` - Matches docker-compose.yml port mapping  
✅ **Eureka URL:** Uses `eureka-server` (container hostname) - Correct for internal DNS  
✅ **Route:** `lb://BACKEND` - Load balanced route to Backend Service  
✅ **Predicate:** `/api/**` - Routes all `/api/*` paths to Backend  

**How It Works:**
1. When API Gateway starts, it registers with Eureka at `http://eureka-server:8761/eureka`
2. When request comes to `/api/**`, it uses load balancer to find `BACKEND` service
3. Backend service name must match what Backend registers as (see below)

---

#### Backend-Service/src/main/resources/application.properties

```properties
spring.application.name=BACKEND
server.port=8081

spring.datasource.url=jdbc:mysql://mysql:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=mypassword

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka
```

**Analysis:**
✅ **Service Name:** `BACKEND` - Must match route in API Gateway (`lb://BACKEND`)  
✅ **Port:** `8081` - Matches docker-compose.yml port mapping  
✅ **Database URL:** `jdbc:mysql://mysql:3306/testdb` - Uses container DNS  
✅ **Credentials:** Match docker-compose MySQL environment variables  
✅ **JPA Config:** Hibernate auto-creates tables (`ddl-auto=update`)  
✅ **Eureka URL:** Uses `eureka-server` container hostname  

**Why UPPERCASE Service Name?**
- Spring Cloud Gateway uses uppercase for service names
- API Gateway route: `lb://BACKEND` (uppercase)
- Backend service: `spring.application.name=BACKEND` (must match)

---

#### Eureka-Server/src/main/resources/application.properties

```properties
spring.application.name=eureka-server
server.port=8761

eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

**Analysis:**
✅ **Service Name:** `eureka-server` - For identification  
✅ **Port:** `8761` - Standard Eureka port  
✅ **Register:** `false` - Eureka doesn't register itself  
✅ **Fetch Registry:** `false` - Eureka doesn't need service list  

**Why This Config?**
- Eureka is the registry, not a client
- It doesn't need to register or fetch from other Eureka servers
- Simplifies single-server setup

---

### File: Dockerfiles (No Changes Needed)

#### API-Gateway/Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/API-Gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Analysis:**
- ✅ Uses lightweight Alpine base image
- ✅ Exposes port 8080 (matches application.properties)
- ✅ Runs pre-built JAR from `build/libs/`
- ✅ No JAVA_OPTS here; overridden by docker-compose.yml environment

---

#### Backend-Service/Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/Backend-Service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Analysis:**
- ✅ Uses lightweight Alpine base image
- ✅ Exposes port 8081 (matches application.properties)
- ✅ Runs pre-built JAR from `build/libs/`
- ✅ No JAVA_OPTS here; overridden by docker-compose.yml environment

---

#### Eureka-Server/Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/Eureka-Server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Analysis:**
- ✅ Uses lightweight Alpine base image
- ✅ Exposes port 8761 (standard Eureka port)
- ✅ Runs pre-built JAR from `build/libs/`
- ✅ No JAVA_OPTS here; overridden by docker-compose.yml environment

---

## Dependency Chain

### Startup Sequence

```
1. Docker Compose creates network: compose-network (bridge driver)
                     ↓
2. MySQL container starts
   - Initializes database
   - Health check: not yet passing
                     ↓
3. Eureka Server starts
   - Waits for: mysql (no hard condition)
   - Ready for service registration
                     ↓
4. Backend Service starts
   - Waits for: mysql (service_healthy) ✓
   - Waits for: eureka-server (service_started) ✓
   - Connects to MySQL: jdbc:mysql://mysql:3306/testdb
   - Registers with Eureka
                     ↓
5. API Gateway starts
   - Waits for: eureka-server (service_started) ✓
   - Queries Eureka for Backend Service location
   - Sets up routes to lb://BACKEND
                     ↓
6. All services ready
   - Gateway: http://localhost:8080/api/**
   - Backend: http://localhost:8081/...
   - Eureka: http://localhost:8761
   - MySQL: localhost:3307
```

### Dependency Diagram

```
┌─────────────┐
│   Network   │
│   Created   │
└──────┬──────┘
       │
       ├──────────────────────┬─────────────────┬─────────────────┐
       │                      │                 │                 │
       ▼                      ▼                 ▼                 ▼
┌─────────────┐         ┌──────────────┐  ┌──────────────┐  ┌───────────┐
│   MySQL     │         │    Eureka    │  │     Backend  │  │ API       │
│   (Port 3306)          │   (8761)     │  │   (8081)     │  │ Gateway   │
│             │         │              │  │              │  │ (8080)    │
│ Running:    │         │ Running:     │  │ Waits for:   │  │ Waits for:│
│ Health check│         │ (no waits)   │  │ mysql ✓      │  │ eureka ✓  │
│ pending...  │         │              │  │ eureka ✓     │  │           │
└─────────────┘         └──────────────┘  │              │  └───────────┘
       │                      ▲            │ Connects:    │
       │ Ready after          │            │ mysql:3306   │
       │ ~10s (health         │            │ eureka:8761  │
       │ check passes)        │            │              │
       └───────────┬──────────┘            │ Registers:   │
                   │                       │ name=BACKEND │
                   └───────────────────────┘              │
                                                          │
                                                    Routes to:
                                                    lb://BACKEND
```

---

## Network Architecture

### Docker Compose Bridge Network

**Network Name:** `compose-network`  
**Driver:** bridge  
**Scope:** Single Docker host  
**Attachable:** Yes  

### How Container Communication Works

```
Container 1 → DNS Resolver → Container Name → IP Address
              (127.0.0.11:53)

Example:
Backend → Asks for "mysql" → Gets 172.18.0.2 → Connects
Backend → Asks for "eureka-server" → Gets 172.18.0.3 → Connects
```

### DNS Resolution

| Service Name | Resolved To | Access Pattern |
|--------------|-------------|----------------|
| mysql | 172.18.0.X (internal) | `jdbc:mysql://mysql:3306` |
| eureka-server | 172.18.0.X (internal) | `http://eureka-server:8761` |
| api-gateway | 172.18.0.X (internal) | `http://api-gateway:8080` |
| backend | 172.18.0.X (internal) | `http://backend:8081` |

### External Port Mapping

| Service | Internal Port | External Port | Access From Host |
|---------|---------------|---------------|------------------|
| MySQL | 3306 | 3307 | `localhost:3307` |
| Eureka | 8761 | 8761 | `localhost:8761` |
| Backend | 8081 | 8081 | `localhost:8081` |
| API Gateway | 8080 | 8080 | `localhost:8080` |

**Important:** Container-to-container uses internal ports (3306, 8761, 8081, 8080)  
**Important:** Host-to-container uses external ports (only 3307 is different!)

---

## Service Communication

### Communication Flows

#### Flow 1: Client → API Gateway → Backend → MySQL

```
1. External Client
   curl http://localhost:8080/api/users

2. Host Network → Container Network
   localhost:8080 → 172.18.0.2:8080 (api-gateway)

3. API Gateway (port 8080)
   Receives: GET /api/users
   Looks up route: /api/** → lb://BACKEND
   Queries Eureka: Where is BACKEND?
   Gets: BACKEND at 172.18.0.4:8081

4. Host → Backend Container
   172.18.0.2:8080 → 172.18.0.4:8081
   Container DNS: api-gateway → backend:8081

5. Backend Service (port 8081)
   Receives: GET /api/users
   Queries database: SELECT * FROM users

6. Backend → MySQL
   jdbc:mysql://mysql:3306/testdb
   Container DNS: backend → mysql:3306
   172.18.0.4:8081 → 172.18.0.1:3306

7. MySQL Database (port 3306)
   Returns: User records

8. Response chain back to client
   MySQL → Backend → API Gateway → Client
   Data: [{"id": 1, "name": "John"}, ...]
```

#### Flow 2: Service Registration with Eureka

```
1. Backend Service starts
   Application class: BackendServiceApplication.java
   Annotation: @EnableEurekaClient

2. Backend reads configuration
   eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka

3. Backend registers with Eureka
   POST http://eureka-server:8761/eureka/apps/BACKEND
   Body: {
     "instance": {
       "instanceId": "backend-service-app:8081",
       "appName": "BACKEND",
       "ipAddr": "172.18.0.4",
       "port": "8081",
       "status": "UP"
     }
   }

4. API Gateway queries Eureka
   GET http://eureka-server:8761/eureka/apps/BACKEND

5. API Gateway caches instance information
   Name: BACKEND
   Instance: 172.18.0.4:8081

6. API Gateway routes requests
   lb://BACKEND → 172.18.0.4:8081
```

---

## Cleanup & Fresh Start

### Complete Cleanup Procedure

#### Step 1: Stop All Containers

```bash
cd /home/harish/IdeaProjects/Microservice

# Stop docker-compose stack
docker compose down
```

#### Step 2: Remove Old Swarm Resources

```bash
# Remove any existing Swarm stacks
docker stack rm microservice 2>/dev/null || echo "No Swarm stack found"

# Wait for cleanup
sleep 5

# List all networks
docker network ls
```

#### Step 3: Identify and Remove Overlay Networks

```bash
# Check for overlay networks
docker network ls | grep -E "overlay|swarm"

# Remove conflicting overlay network
docker network rm microservice_swarm-network 2>/dev/null || echo "Network not found"

# Verify removal
docker network ls | grep -i swarm || echo "✓ No Swarm networks remain"
```

#### Step 4: Clean Up Containers and Volumes

```bash
# Remove all stopped containers
docker container prune -f

# Remove dangling volumes
docker volume prune -f

# Remove dangling images
docker image prune -f

# List remaining volumes (for verification)
docker volume ls
```

#### Step 5: Full Verification Before Starting

```bash
# Check no containers running
docker ps -a

# Check networks available
docker network ls

# Check volumes available
docker volume ls
```

### Fresh Start Command Sequence

```bash
#!/bin/bash
# Complete fresh start script

cd /home/harish/IdeaProjects/Microservice

echo "=== Step 1: Clean up Swarm resources ==="
docker stack rm microservice 2>/dev/null || true
sleep 5

echo "=== Step 2: Remove conflicting networks ==="
docker network rm microservice_swarm-network 2>/dev/null || true

echo "=== Step 3: Prune Docker resources ==="
docker container prune -f
docker volume prune -f
docker image prune -f

echo "=== Step 4: Build all services ==="
docker compose build --no-cache

echo "=== Step 5: Start Docker Compose stack ==="
docker compose up -d

echo "=== Step 6: Wait for initialization ==="
sleep 10

echo "=== Step 7: Display status ==="
docker compose ps

echo "=== Step 8: Display logs ==="
docker compose logs --tail=20

echo "✓ Fresh start complete!"
```

### What Gets Created/Removed

**Gets Removed:**
- `microservice_swarm-network` (overlay network)
- Any containers with names: `microservice-mysql-1`, `microservice-eureka-server-1`, etc.
- Dangling images and volumes

**Gets Created:**
- `compose-network` (bridge network)
- `mysql-db` (container)
- `eureka-server-app` (container)
- `api-gateway-app` (container)
- `backend-service-app` (container)
- `mysql-data` (volume)

---

## Verification & Testing

### Health Check Verification

```bash
# Check container status
docker compose ps

# Expected output:
# NAME                COMMAND                  SERVICE             STATUS
# api-gateway-app     "java -jar app.jar"      api-gateway         Up (healthy)
# backend-service-app "java -jar app.jar"      backend             Up (healthy)
# eureka-server-app   "java -jar app.jar"      eureka-server       Up (healthy)
# mysql-db            "docker-entrypoint.s…"   mysql               Up (healthy)
```

### Network Connectivity Tests

```bash
# Test DNS resolution within containers
docker compose exec backend ping -c 2 mysql
# Expected: 2 packets, 0% loss

docker compose exec backend ping -c 2 eureka-server
# Expected: 2 packets, 0% loss

docker compose exec api-gateway ping -c 2 eureka-server
# Expected: 2 packets, 0% loss
```

### Service Discovery Tests

```bash
# Check Eureka registry (all registered instances)
curl http://localhost:8761/eureka/apps

# Check if Backend is registered
curl http://localhost:8761/eureka/apps/BACKEND

# Check API Gateway is registered
curl http://localhost:8761/eureka/apps/API-GATEWAY
```

### Database Connectivity Tests

```bash
# Direct database connection
docker compose exec backend mysql -h mysql -u root -pmypassword -e "USE testdb; SHOW TABLES;"

# From outside containers
mysql -h localhost -P 3307 -u root -pmypassword testdb -e "SHOW TABLES;"
```

### API Endpoint Tests

```bash
# Test API Gateway is running
curl http://localhost:8080

# Test Backend endpoint through Gateway
curl http://localhost:8080/api/users

# Test Backend directly
curl http://localhost:8081/users

# Test Eureka UI
curl -I http://localhost:8761
# Expected: HTTP/1.1 200 OK
```

### Log Analysis

```bash
# View all logs
docker compose logs

# Follow logs in real-time
docker compose logs -f

# View specific service logs
docker compose logs -f eureka-server
docker compose logs -f backend
docker compose logs -f api-gateway
docker compose logs -f mysql

# View last 50 lines of Backend logs
docker compose logs --tail=50 backend

# View logs since last 5 minutes
docker compose logs --since=5m

# View logs until 2 minutes ago
docker compose logs --until=2m
```

### Expected Log Output (Successful Startup)

**Eureka Server:**
```
com.netflix.eureka.server.EurekaServerInitializerConfiguration : Started Eureka Server
```

**Backend Service:**
```
Registering service with Eureka: BACKEND
o.s.b.a.e.c.EurekaServiceRegistry : Registering application BACKEND with eureka with status UP
```

**API Gateway:**
```
Registering service with Eureka: API-GATEWAY
o.s.c.n.e.InstanceInfoFactory : Setting initial instance status to: UP
```

### Common Issues & Solutions

#### Issue 1: MySQL Connection Timeout

**Error:**
```
java.sql.SQLNonTransientConnectionException: Client does not support authentication protocol
```

**Solution:**
```yaml
# Already fixed in corrected docker-compose.yml:
- SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true
```

#### Issue 2: Eureka Service Registration Fails

**Error:**
```
Looks like the client is using an old hostname and the server may be unaware of it.
```

**Solution:**
```yaml
# Verify service names match:
# application.properties: spring.application.name=BACKEND
# docker-compose.yml environment: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
```

#### Issue 3: Backend Cannot Connect to MySQL

**Error:**
```
Communications link failure: The last packet sent successfully to the server was 0 milliseconds ago.
```

**Solution:**
```bash
# Ensure MySQL health check passed
docker compose ps mysql
# Check Status column shows "(healthy)"

# Manually test connectivity
docker compose exec backend mysql -h mysql -u root -pmypassword -e "SELECT 1;"
```

#### Issue 4: Port Already in Use

**Error:**
```
Error response from daemon: driver failed programming external connectivity on endpoint
```

**Solution:**
```bash
# Find service using port
lsof -i :8080

# Stop conflicting service
kill -9 <PID>

# Or use different port in docker-compose.yml:
ports:
  - "8090:8080"  # Use 8090 instead of 8080
```

---

## Summary Table

### Configuration Changes Summary

| Component | Change | Reason | Impact |
|-----------|--------|--------|--------|
| Network Name | `swarm-network` → `compose-network` | Avoid Swarm overlay conflict | 🔴 CRITICAL |
| Container Names | Added `container_name` | Better debugging | 🟢 LOW |
| Health Check | Added MySQL health check | Ensure DB readiness | 🟡 MEDIUM |
| Dependencies | Added `condition: service_healthy` | Proper sequencing | 🟡 MEDIUM |
| Persistence | Added `mysql-data` volume | Data survives restart | 🟡 MEDIUM |
| Configuration | Added `environment` vars | Externalize config | 🟢 LOW |
| Image Tags | Added explicit `:latest` | Consistency | 🟢 LOW |
| Memory Limits | Added Java `-Xmx` settings | Prevent OOM | 🟢 LOW |

---

## Best Practices Applied

✅ **Use named volumes** for persistent data (not bind mounts)  
✅ **Use bridge networks** for Compose (not overlay)  
✅ **Add health checks** for critical services (databases)  
✅ **Use explicit container names** for debugging  
✅ **Externalize configuration** via environment variables  
✅ **Set memory limits** for JVM applications  
✅ **Use service names** for internal DNS (not IPs)  
✅ **Explicit image tags** for reproducibility  
✅ **Proper dependency ordering** with conditions  
✅ **Service discovery** via Eureka for service-to-service communication  

---

## Production Considerations

### Not Ready for Production
This configuration is suitable for **development and testing** only.

### For Production, Consider:

1. **Credentials Management**
   - Use Docker Secrets for MySQL password
   - Use environment variable files (`.env`)
   - Never commit secrets to Git

2. **Database**
   - Use managed database service (RDS, CloudSQL)
   - Implement database backups
   - Enable encryption at rest and in transit

3. **Monitoring & Logging**
   - Add centralized logging (ELK, Datadog)
   - Add metrics collection (Prometheus)
   - Add distributed tracing (Jaeger)

4. **Security**
   - Use private Docker registries
   - Implement network policies
   - Use TLS for all communications
   - Regular vulnerability scanning

5. **Scaling**
   - Switch to Kubernetes or Docker Swarm
   - Implement load balancing
   - Database connection pooling
   - Cache layer (Redis)

6. **Deployment**
   - Use CI/CD pipelines (GitLab CI, Jenkins)
   - Automated testing
   - Blue-green or canary deployments
   - Health checks and auto-recovery

---

## References

- Docker Compose Documentation: https://docs.docker.com/compose/
- Docker Network Drivers: https://docs.docker.com/network/
- Spring Cloud Eureka: https://spring.io/cloud/spring-cloud-eureka
- Spring Cloud Gateway: https://spring.io/cloud/spring-cloud-gateway
- MySQL in Docker: https://hub.docker.com/_/mysql/

---

**Document Completed:** May 29, 2026  
**Status:** Ready for Production Migration (after security hardening)
