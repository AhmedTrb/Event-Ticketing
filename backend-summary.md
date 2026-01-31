# Backend Development Summary – Event Ticketing Platform

This document provides a comprehensive overview of the backend architecture, data models, and workflows developed so far. It serves as a reference for frontend development and future backend enhancements.

---

## 1. System Architecture
The backend follows a **Modular Monolith to Microservices** transition architecture, utilizing:
- **Spring Boot 3.4.1** with Java 17.
- **Maven Multi-module** structure for centralized management.
- **Keycloak** for Identity and Access Management (OIDC/JWT).
- **RabbitMQ** for asynchronous communication between services.
- **Redis** for distributed locking (seat reservation) and real-time notifications.
- **PostgreSQL** as the primary relational database.

### Core Modules:
- `shared`: Common enums, messaging contracts, and exceptions.
- `ticketing-api`: The main user-facing REST API.
- `registration-worker`: Asynchronous consumer for processing bookings and seat allocation.
- `notification-service`: Manages emails and real-time WebSocket/Redis notifications.

---

## 2. Database Schema & Entities

### 2.1 Users (`users` table)
Synchronized from Keycloak upon first login.
| Attribute | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key (Local) |
| `keycloak_sub` | String | Unique ID from Keycloak (Authority) |
| `email` | String | Unique email address |
| `first_name` | String | User's given name |
| `last_name` | String | User's family name |
| `role` | Enum | `USER`, `ADMIN` |
| `status` | Enum | `ACTIVE`, `SUSPENDED` |

### 2.2 Venues (`venues` table)
| Attribute | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `name` | String | Venue name |
| `location` | String | Physical address |
| `type` | Enum | `MOVIE_THEATRE`, `STADIUM`, `CONCERT_HALL`, etc. |
| `capacity` | Integer | Total seat count |
| `row_count` | Integer | (Optional) Rows for procedural theatres |
| `col_count` | Integer | (Optional) Columns for procedural theatres |
| `seating_map` | Text (JSON) | Custom SVG or coordinate data for stadiums |

### 2.3 Events (`events` table)
| Attribute | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `name` | String | Event title |
| `description` | Text | Detailed description |
| `start_date` | Instant | UTC Start Time |
| `end_date` | Instant | UTC End Time |
| `event_type` | Enum | `MOVIE`, `CONCERT`, `SPORTS_EVENT`, etc. |
| `status` | Enum | `UPCOMING`, `ONGOING`, `COMPLETED`, `CANCELLED` |
| `venue_id` | UUID | Foreign Key -> `venues` |
| `max_capacity` | Integer | Override for default venue capacity |

### 2.4 Tickets (`tickets` table)
Managed primarily by the `registration-worker`.
| Attribute | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `booking_id` | UUID | ID group for a single transaction |
| `user_id` | UUID | Foreign Key -> `users` |
| `event_id` | UUID | Foreign Key -> `events` |
| `seat_id` | String | Identifier for the seat (e.g., "A-12") |
| `status` | String | `CONFIRMED`, `PENDING` |

---

## 3. Core Workflows

### 3.1 Authentication Workflow
1. **Login**: Frontend authenticates with Keycloak (OAuth2 Authorization Code Flow).
2. **Token**: Keycloak returns a JWT.
3. **Sync**: Frontend calls `/api/auth/sync` with the JWT.
4. **Local Entry**: Backend verifies the token and creates/updates the User record in the local DB for relational integrity.

### 3.2 High-Demand Registration Workflow (Asynchronous)
1. **Request**: User sends a registration request to `ticketing-api`.
2. **Locking**: `ticketing-api` attempts to acquire a short-lived lock in **Redis** for the selected seats (TTL = 5-10 mins).
3. **Queueing**: If seats are locked, a `ProcessBookingCommand` is sent to **RabbitMQ**.
4. **Processing**: `registration-worker` consumes the message, verifies the Redis lock, and persists the `Ticket` entities to PostgreSQL.
5. **Notification**: Once saved, a notification event is published via Redis/WebSocket to inform the user.

---

## 4. API Endpoints (Snapshot)

### Authentication
- `POST /api/auth/sync`: Synchronizes authenticated user from Keycloak.
- `GET /api/auth/me`: Returns current user profile.

### Events & Discovery
- `GET /api/events`: Paginated list of upcoming events.
- `GET /api/events/{id}`: Detailed event information.

### Registration
- `POST /api/register/simple`: Immediate booking for low-demand events.
- `POST /api/register/queue`: Joins async queue for high-demand events.

---

## 5. Development Tokens & Enums
For frontend logic, ensure consistency with these backend enums:
- **EventStatus**: `UPCOMING`, `ONGOING`, `COMPLETED`, `CANCELLED`
- **EventType**: `MOVIE`, `CONCERT`, `SPORTS_EVENT`, `THEATRE`
- **VenueType**: `MOVIE_THEATRE`, `STADIUM`, `CONCERT_HALL`
- **UserRole**: `USER`, `ADMIN`
