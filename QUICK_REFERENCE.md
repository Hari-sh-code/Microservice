# Docker Compose Quick Reference Guide

**Project:** Microservice (Spring Boot)  
**Last Updated:** May 29, 2026

---

## Quick Start

### First Time Setup

```bash
cd /home/harish/IdeaProjects/Microservice

# Clean everything
docker compose down -v
docker network rm microservice_swarm-network 2>/dev/null || true
docker container prune -f
docker volume prune -f

# Build and start
docker compose build --no-cache
docker compose up -d

# Wait and verify
sleep 10
docker compose ps
```

### Daily Start/Stop

```bash
# Start services
docker compose up -d

# Stop services (keep data)
docker compose down

# Stop services and remove data
docker compose down -v

# Restart services
docker compose restart
```

---

## Container Management

### View Status

```bash
# Show running containers
docker compose ps

# Show all containers (including stopped)
docker compose ps -a

# Show container details
docker compose ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}"
```

### Logs

```bash
# View all logs
docker compose logs

# Follow logs in real-time
docker compose logs -f

# View specific service
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f eureka-server
docker compose logs -f api-gateway

# View last 50 lines
docker compose logs --tail=50 backend

# View logs from last 5 minutes
docker compose logs --since=5m
```

### Execute Commands

```bash
# Run command in container
docker compose exec backend bash

# Run MySQL command
docker compose exec mysql mysql -u root -pmypassword -e "SHOW DATABASES;"

# Run Java diagnostic
docker compose exec backend jps -l

# Ping between containers
docker compose exec backend ping mysql
```

---

## Database Management

### MySQL Operations

```bash
# Connect to MySQL from host
mysql -h localhost -P 3307 -u root -pmypassword testdb

# Connect from container
docker compose exec mysql mysql -h localhost -u root -pmypassword testdb

# Show tables
docker compose exec mysql mysql -u root -pmypassword testdb -e "SHOW TABLES;"

# Backup database
docker compose exec mysql mysqldump -u root -pmypassword testdb > backup.sql

# Restore database
cat backup.sql | docker compose exec -T mysql mysql -u root -pmypassword testdb

# View mysql logs
docker compose logs mysql
```

### Data Persistence

```bash
# View MySQL volume
docker volume ls | grep mysql-data

# Inspect volume details
docker volume inspect mysql-data

# Backup volume
docker run --rm -v mysql-data:/volume -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz -C /volume .

# Restore volume
docker run --rm -v mysql-data:/volume -v $(pwd):/backup alpine tar xzf /backup/mysql-backup.tar.gz -C /volume

# Remove volume (deletes all data!)
docker volume rm mysql-data
```

---

## Service Health & Testing

### Verify Services Are Ready

```bash
# Check all services running
docker compose ps

# All status should show "Up (healthy)" or "Up (running)"

# Check Eureka is responding
curl -s http://localhost:8761/eureka/apps | head -20

# Check Backend is responsive
curl http://localhost:8081/actuator/health

# Check API Gateway responding
curl http://localhost:8080/
```

### Service Discovery

```bash
# List all registered instances
curl http://localhost:8761/eureka/apps

# Check specific service
curl http://localhost:8761/eureka/apps/BACKEND

# Check API Gateway registration
curl http://localhost:8761/eureka/apps/API-GATEWAY
```

### Network Testing

```bash
# Test DNS resolution
docker compose exec backend nslookup mysql

# Test connectivity to MySQL
docker compose exec backend nc -zv mysql 3306

# Test connectivity to Eureka
docker compose exec backend nc -zv eureka-server 8761

# Ping tests
docker compose exec backend ping -c 2 mysql
docker compose exec backend ping -c 2 eureka-server
```

### API Testing

```bash
# Gateway health
curl http://localhost:8080

# Get users through gateway
curl http://localhost:8080/api/users

# Get users directly from backend
curl http://localhost:8081/users

# Eureka UI
open http://localhost:8761/eureka/web/
# or
curl -I http://localhost:8761
```

---

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker compose logs backend

# Check if port is already in use
lsof -i :8081

# Check resource issues
docker stats

# Rebuild and retry
docker compose down
docker compose build --no-cache
docker compose up
```

### MySQL Connection Issues

```bash
# Check MySQL is healthy
docker compose ps mysql
# Status should show "(healthy)"

# Test connection from backend
docker compose exec backend mysql -h mysql -u root -pmypassword -e "SELECT 1;"

# Check MySQL logs
docker compose logs mysql

# Verify credentials match
# Check docker-compose.yml SPRING_DATASOURCE_* variables
```

### Service Discovery Not Working

```bash
# Check Eureka is running
curl http://localhost:8761/eureka/apps

# Check service is registered
curl http://localhost:8761/eureka/apps/BACKEND

# Check logs for registration errors
docker compose logs backend | grep -i eureka

# Verify service names match
# docker-compose.yml: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
# application.properties: spring.application.name
```

### Swarm Conflict Issues

```bash
# Check for leftover Swarm resources
docker network ls | grep swarm

# Check Swarm mode is disabled
docker info | grep "Swarm"

# Remove conflicting network
docker network rm microservice_swarm-network

# Verify network removed
docker network ls | grep -i swarm || echo "No Swarm networks"
```

### Port Conflicts

```bash
# Find what's using port
lsof -i :8080
netstat -tlnp | grep 8080

# Stop the service
kill -9 <PID>

# Or change port in docker-compose.yml
# Change ports: - "8090:8080"
```

---

## Environment Variables

### Current Configuration

| Variable | Service | Value | Purpose |
|----------|---------|-------|---------|
| `JAVA_OPTS` | eureka, backend, gateway | `-Xmx512m -Xms256m` | JVM memory limits |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | backend, gateway | `http://eureka-server:8761/eureka` | Service discovery |
| `SPRING_DATASOURCE_URL` | backend | `jdbc:mysql://mysql:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true` | Database connection |
| `SPRING_DATASOURCE_USERNAME` | backend | `root` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | backend | `mypassword` | Database password |
| `MYSQL_ROOT_PASSWORD` | mysql | `mypassword` | MySQL root password |
| `MYSQL_DATABASE` | mysql | `testdb` | Database name |

### Override at Runtime

```bash
# Using .env file
cat > .env << EOF
MYSQL_ROOT_PASSWORD=newpassword
SPRING_DATASOURCE_PASSWORD=newpassword
EOF

docker compose up

# Using environment variables
MYSQL_ROOT_PASSWORD=newpassword docker compose up

# Using --env-file
docker compose --env-file .env.production up
```

---

## Backup & Recovery

### Backup Entire System

```bash
#!/bin/bash
BACKUP_DIR="backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

# Backup MySQL data
docker volume inspect mysql-data | grep Mountpoint | awk '{print $2}' | xargs -I {} cp -r {} $BACKUP_DIR/mysql-data

# Backup docker-compose configuration
cp docker-compose.yml $BACKUP_DIR/

# Backup application properties
cp -r */src/main/resources/application.properties $BACKUP_DIR/

# Create tar archive
tar -czf "$BACKUP_DIR.tar.gz" $BACKUP_DIR/
rm -rf $BACKUP_DIR

echo "Backup created: $BACKUP_DIR.tar.gz"
```

### Restore from Backup

```bash
# Extract backup
BACKUP_FILE="backups/20260529_120000.tar.gz"
tar -xzf $BACKUP_FILE

# Stop services
docker compose down

# Restore MySQL volume
docker volume rm mysql-data
docker volume create mysql-data

# Copy data back
docker run --rm -v mysql-data:/volume -v $(pwd)/backups/20260529_120000/mysql-data:/backup alpine cp -r /backup/* /volume/

# Start services
docker compose up -d

# Verify
docker compose ps
```

---

## Performance Monitoring

### Real-time Stats

```bash
# Show resource usage
docker stats

# Show specific container
docker stats backend

# Show formatted output
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

### Check Memory Usage

```bash
# Current usage
docker compose exec backend free -h

# Java heap usage
docker compose exec backend jcmd 1 VM.memory_summary
```

### View Resource Limits

```bash
# From docker-compose.yml
docker compose config | grep -A 5 "environment:"

# JVM options set
docker compose exec backend echo $JAVA_OPTS
```

---

## Development Workflow

### Modify Code and Rebuild

```bash
# Backend Service
cd Backend-Service
./gradlew clean bootJar
cd ..
docker compose build backend
docker compose up backend

# API Gateway
cd API-Gateway
./gradlew clean bootJar
cd ..
docker compose build api-gateway
docker compose up api-gateway

# Eureka Server
cd Eureka-Server
./gradlew clean bootJar
cd ..
docker compose build eureka-server
docker compose up eureka-server
```

### Hot-reload During Development (Optional)

```bash
# Build once
docker compose build

# Keep running
docker compose up

# In another terminal, rebuild and restart service
docker compose build backend && docker compose up backend

# Services will automatically reconnect
```

### Debug a Service

```bash
# Run container interactively
docker compose run --rm backend bash

# Or shell into running container
docker compose exec backend bash

# Check environment
docker compose exec backend env | grep JAVA_OPTS

# Check network connectivity
docker compose exec backend curl eureka-server:8761

# View logs live
docker compose logs -f backend
```

---

## Production Checklist

- [ ] Change all default passwords in docker-compose.yml
- [ ] Use environment files for secrets (`.env`)
- [ ] Add resource limits (memory, CPU)
- [ ] Enable read-only root filesystem where possible
- [ ] Remove debug endpoints from properties files
- [ ] Add proper logging configuration
- [ ] Implement monitoring and alerting
- [ ] Set up regular backups
- [ ] Test disaster recovery procedure
- [ ] Use Docker registry authentication
- [ ] Implement health probes properly
- [ ] Use secrets management (Docker Secrets, HashiCorp Vault)
- [ ] Enable container image scanning
- [ ] Set up automated builds (CI/CD)

---

## Common Commands Cheat Sheet

```bash
# View configuration
docker compose config

# Validate configuration
docker compose config --quiet && echo "Valid"

# Build services
docker compose build
docker compose build --no-cache
docker compose build backend

# Start services
docker compose up
docker compose up -d
docker compose up backend

# Stop services
docker compose stop
docker compose stop backend

# Restart services
docker compose restart
docker compose restart backend

# Remove containers
docker compose rm
docker compose rm backend

# View logs
docker compose logs
docker compose logs -f
docker compose logs backend
docker compose logs --tail=100 backend

# Execute command
docker compose exec backend bash
docker compose exec mysql mysql -u root -p

# Pull images
docker compose pull

# Push images
docker compose push

# Down (stop and remove)
docker compose down
docker compose down -v

# Display events
docker compose events

# Pause services
docker compose pause
docker compose unpause

# Get container information
docker compose ps
docker inspect <container-id>
```

---

## Quick Troubleshooting Flow

```
Problem: Services won't start
├─ Check: docker compose ps
├─ If "Exited": docker compose logs <service>
├─ Check image built: docker image ls | grep microservice
├─ Rebuild: docker compose build --no-cache
└─ Retry: docker compose up

Problem: Port conflict
├─ Find: lsof -i :<port>
├─ Kill: kill -9 <PID>
└─ Retry: docker compose up

Problem: MySQL connection error
├─ Check: docker compose ps mysql
├─ If not healthy: docker compose logs mysql
├─ Test: docker compose exec backend mysql -h mysql -u root -p
└─ Rebuild: docker compose down && docker compose up

Problem: Eureka service not registered
├─ Check: curl http://localhost:8761/eureka/apps
├─ Check logs: docker compose logs backend | grep eureka
├─ Verify: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE matches
└─ Restart: docker compose restart backend

Problem: Cannot reach services
├─ Check network: docker network ls | grep compose
├─ Check DNS: docker compose exec backend nslookup mysql
├─ Check firewall: sudo ufw status
└─ Check ports: docker compose ps --format "table {{.Service}}\t{{.Ports}}"
```

---

**Tip:** Bookmark this guide for quick reference during development!
