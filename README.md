# Abhedyam Backend

Backend API for Abhedyam Business Management Application built with Spring Boot 3.2.1 and Java 21.

## Table of Contents

- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Features](#features)
- [Development](#development)
- [Flutter SDK Generation](#flutter-sdk-generation)

---

## Quick Start

### 1. Install Dependencies

```bash
# MySQL
sudo apt-get install mysql-server  # Ubuntu/Debian
brew install mysql                 # macOS

# Redis
sudo apt-get install redis-server  # Ubuntu/Debian
brew install redis                 # macOS
```

### 2. Start Services

```bash
# Start MySQL
sudo systemctl start mysql

# Start Redis
sudo systemctl start redis
```

### 3. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

### 4. Verify

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Swagger UI
open http://localhost:8080/swagger-ui
```

---

## Prerequisites

### System Requirements

- **Java**: JDK 21 or higher
- **Maven**: 3.6+ (for building)
- **MySQL**: 8.0+ (database)
- **Redis**: 6.0+ (caching, OTP storage, job queue)

### Required Environment Variables

**Minimum Required:**
```bash
DB_PASSWORD=your_mysql_password
JWT_SECRET=$(openssl rand -base64 32)  # Generate secure secret
```

**For Full Functionality:**
```bash
# Email Configuration (Primary Notification Method)
EMAIL_ENABLED=true
EMAIL_FROM=marava.technologies@gmail.com
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USERNAME=marava.technologies@gmail.com
EMAIL_PASSWORD=REDACTED_GMAIL_PASSWORD
EMAIL_SMTP_AUTH=true
EMAIL_STARTTLS_ENABLE=true

# 2Factor SMS (Fallback)
TWO_FACTOR_API_KEY=3f77c89e-c18d-11f0-a6b2-0200cd936042
TWO_FACTOR_OTP_TEMPLATE=Abhedyam_OTP_Template
```

**Optional:**
```bash
# Payment Gateway (Stubbed)
```

### Database Setup

The database will be auto-created if it doesn't exist. Alternatively, create manually:

```sql
CREATE DATABASE abhedyam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## Configuration

All configuration is in `src/main/resources/application.yml`. Key settings:

### Email Configuration

Email is the **primary notification method** for OTP and reminders. SMS is used as fallback.

- **Gmail SMTP**: Pre-configured with Marava Technologies credentials
- **From Address**: `marava.technologies@gmail.com`
- **HTML Support**: Enabled via `GmailSender` service

### SMS Configuration

- **Provider**: 2Factor.in
- **API Key**: Pre-configured in `application.yml`
- **Template**: `Abhedyam_OTP_Template` (approved)
- **Template Text**: "XXXX is your Abhedyam App OTP."

### JWT Configuration

- **Secret**: Configure via `JWT_SECRET` environment variable
- **Expiration**: 15 days (1296000000 ms)

---

## API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui
- **OpenAPI Spec**: http://localhost:8080/api-docs
- **Health Check**: http://localhost:8080/api/v1/health

All endpoints are versioned under `/api/v1/`

### Authentication

- `POST /api/v1/auth/google/login` - Owner login with Firebase ID token
- `POST /api/v1/auth/phone/login` - Customer login (Connect app, no OTP)
- `POST /api/v1/auth/otp/send` - Send SMS OTP for web customer login
- `POST /api/v1/auth/otp/verify` - Verify OTP and get customer JWT
- `POST /api/v1/auth/refresh` - Issue a fresh JWT from a still-valid token

### Key Endpoints

- **Products**: `/api/v1/products`
- **Customers**: `/api/v1/customers`
- **Sales**: `/api/v1/sales`
- **Payments**: `/api/v1/payments`
- **Reminders**: `/api/v1/reminders`
- **Notifications**: `/api/v1/notifications`
- **Stats**: `/api/v1/stats`

---

## Project Structure

```
src/main/java/com/abhedyam/
├── config/          # Configuration classes
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── OpenApiConfig.java
│   └── WebConfig.java
├── controller/      # REST controllers
├── dto/             # Data Transfer Objects
├── exception/       # Custom exceptions
├── model/           # Entity models
├── repository/      # JPA repositories
├── security/        # Security configuration
├── service/         # Business logic
│   └── interfaces/  # Service interfaces
└── util/            # Utility classes
```

---

## Features

### ✅ Implemented

- **Authentication**: OTP-based auth with JWT (email primary, SMS fallback)
- **Product Management**: CRUD, search, stock management
- **Customer Management**: CRUD, search, profile summary
- **Sales Flow**: Multi-item sales, stock deduction, idempotency
- **Stock Management**: Purchase In, Sale Out, Manual Adjustment
- **Stats & Analytics**: Daily aggregation, date range queries
- **Reminders**: In-app and email notifications
- **Notifications**: In-app notification system
- **Call Logs**: Optional call log sync
- **Audit Logging**: Financial operations tracking
- **File Storage**: Local storage (extensible to S3)
- **Structured Logging**: JSON logs with correlation IDs
- **Metrics**: Prometheus endpoint
- **OpenAPI**: Complete API documentation

---

## Development

### Running Tests

```bash
mvn test
```

### Building

```bash
mvn clean package
```

### Code Quality

- **Read-only transactions** on all read operations
- **Owner-based authorization** on all resources
- **Idempotency** for critical operations
- **Structured error responses** with error codes

---

## Flutter SDK Generation

### Prerequisites

```bash
npm install -g @openapitools/openapi-generator-cli
```

### Generate SDK

```bash
# Using provided script
chmod +x generate-flutter-sdk.sh
./generate-flutter-sdk.sh

# Or manually
openapi-generator-cli generate \
  -i http://localhost:8080/api-docs \
  -g dart \
  -o ./generated/flutter-client \
  --additional-properties=serializationLibrary=json_serializable \
  --package-name=abhedyam_api
```

### Use in Flutter

```dart
import 'package:abhedyam_api/api.dart';

final api = DefaultApi();
api.setAccessToken('your-jwt-token');
final response = await api.getCustomers();
```

---

## Troubleshooting

### Database Connection Failed
- Check MySQL is running: `sudo systemctl status mysql`
- Verify credentials in environment variables

### Redis Connection Failed
- Check Redis is running: `redis-cli ping` (should return PONG)
- Verify `REDIS_HOST` and `REDIS_PORT`

### Email Not Sending
- Verify Gmail credentials in `application.yml`
- Check logs for SMTP errors
- Ensure 2FA is enabled and App Password is used

### OTP Not Sending
- Email is tried first, then SMS fallback
- Check email service logs
- Verify 2Factor API key is configured

---

## Production Considerations

### Security
- Change `JWT_SECRET` to a strong random value
- Use environment variables for all secrets
- Enable HTTPS/TLS
- Restrict database and Redis network access

### Performance
- Use connection pooling (already configured)
- Consider Redis Cluster for high availability
- Migrate file storage to S3
- Add database indexes on frequently queried fields

### Monitoring
- Health endpoint: `/api/v1/health`
- Metrics: `/actuator/prometheus`
- Logs: `logs/abhedyam-backend.log`

---

## License

Apache 2.0

---

## Updates & References

- **Last Updated**: 2025-11-15
- **Spring Boot**: 3.2.1
- **Java**: 21
- **MySQL**: 8.0+
- **Redis**: 6.0+

For detailed API documentation, visit Swagger UI at `/swagger-ui` when the application is running.
# abhedyam_backend
