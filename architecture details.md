# Architecture & Implementation Details

## 1. High-Level Architecture Overview

The Event Ticketing Platform is designed to handle **high concurrency**, **large-scale traffic spikes**, and **strong consistency guarantees** during seat booking, while maintaining **high availability** for event discovery.

The system follows a **modular backend architecture** built around:

- **Spring Boot**
- **Asynchronous message processing**
- **Distributed caching**
- **Event-driven seat locking**
- **Real-time user feedback**

### Core Architectural Principles

- **CQRS-style separation** between read-heavy operations (explore events) and write-critical operations (booking).
- **Event-driven architecture** for registrations and seat allocation.
- **Horizontal scalability** via stateless services and independent consumers.
- **Strong consistency** for seat locking and ticket issuance.
- **Loose coupling** between user-facing APIs and heavy processing logic.

---

## 2. Backend Logical Components

### 2.1 Main Backend Services

| Service | Responsibility |
| --- | --- |
| **API Gateway (optional)** | Auth delegation, rate limiting, routing |
| **Event Service** | Event & venue management, availability |
| **Ticketing API** | User-facing booking & registration endpoints |
| **Registration Workers** | Async processing of bookings |
| **Payment Service** | Payment intent & confirmation |
| **Notification Service** | Email + WebSocket notifications |
| **Auth (Keycloak)** | IAM, user identity & roles |

---

## 3. Backend Project Structure (Spring Boot)

### Recommended Structure (Modular Monolith → Microservices Ready)

```
ticketing-platform/
│
├── ticketing-api/                       # User-facing application
│   ├── src/main/java/com/platform/api/
│   │
│   │   ├── controller/                  # REST controllers
│   │   │   ├── EventController.java
│   │   │   ├── VenueController.java
│   │   │   ├── TicketController.java
│   │   │   └── QueueController.java
│   │   │
│   │   ├── service/                     # Application services
│   │   │   ├── EventService.java
│   │   │   ├── TicketService.java
│   │   │   ├── SeatAvailabilityService.java
│   │   │   └── QueueService.java
│   │   │
│   │   ├── domain/                      # Core domain model
│   │   │   ├── event/
│   │   │   ├── venue/
│   │   │   ├── ticket/
│   │   │   └── seat/
│   │   │
│   │   ├── repository/                  # JPA repositories
│   │   │   ├── EventRepository.java
│   │   │   ├── TicketRepository.java
│   │   │   └── VenueRepository.java
│   │   │
│   │   ├── dto/                         # Request / Response models
│   │   │   ├── request/
│   │   │   └── response/
│   │   │
│   │   ├── messaging/                   # RabbitMQ publishers
│   │   │   ├── RegistrationPublisher.java
│   │   │   └── SeatLockPublisher.java
│   │   │
│   │   ├── websocket/                   # WebSocket endpoints
│   │   │   ├── SeatStatusSocket.java
│   │   │   └── QueueStatusSocket.java
│   │   │
│   │   ├── security/                    # Keycloak / IAM
│   │   │   ├── SecurityConfig.java
│   │   │   └── KeycloakConfig.java
│   │   │
│   │   └── config/                      # Infra configuration
│   │       ├── RedisConfig.java
│   │       ├── RabbitMQConfig.java
│   │       └── WebSocketConfig.java
│   │
│   └── src/test/                        # Unit & integration tests
│
├── registration-worker/                 # Async booking processor
│   ├── src/main/java/com/platform/worker/
│   │
│   │   ├── consumer/                    # RabbitMQ consumers
│   │   │   ├── RegistrationConsumer.java
│   │   │   └── SeatLockConsumer.java
│   │   │
│   │   ├── service/                     # Booking logic
│   │   │   ├── BookingService.java
│   │   │   ├── SeatLockService.java
│   │   │   └── PaymentOrchestrationService.java
│   │   │
│   │   ├── domain/                      # Booking domain
│   │   │   ├── booking/
│   │   │   ├── seat/
│   │   │   └── session/
│   │   │
│   │   ├── repository/                  # DB access
│   │   │   ├── BookingRepository.java
│   │   │   └── SeatReservationRepository.java
│   │   │
│   │   ├── messaging/                   # Event publishers
│   │   │   ├── BookingEventPublisher.java
│   │   │   └── NotificationPublisher.java
│   │   │
│   │   └── config/                      # Infra configuration
│   │       ├── RabbitMQConfig.java
│   │       └── RedisConfig.java
│   │
│   └── src/test/
│
├── notification-service/                # Notifications & emails
│   ├── src/main/java/com/platform/notify/
│   │
│   │   ├── consumer/                    # Event consumers
│   │   │   ├── TicketIssuedConsumer.java
│   │   │   └── BookingFailedConsumer.java
│   │   │
│   │   ├── service/
│   │   │   ├── EmailService.java
│   │   │   ├── PdfTicketService.java
│   │   │   └── WebSocketNotifier.java
│   │   │
│   │   ├── templates/                   # Email templates
│   │   │   ├── ticket-confirmation.html
│   │   │   └── payment-failed.html
│   │   │
│   │   └── config/
│   │       └── MailConfig.java
│   │
│   └── src/test/
│
├── shared/                              # Shared kernel
│   ├── domain/                          # Shared entities (read-only)
│   ├── enums/
│   │   ├── EventType.java
│   │   ├── BookingStatus.java
│   │   ├── SeatStatus.java
│   │   └── PaymentStatus.java
│   │
│   ├── messaging/
│   │   ├── MessageEnvelope.java
│   │   ├── EventPayloads.java
│   │   └── CommandPayloads.java
│   │
│   ├── exceptions/
│   │   ├── BusinessException.java
│   │   └── TechnicalException.java
│   │
│   └── util/
│       └── IdGenerator.java
│
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.dev.yml
│   │   └── docker-compose.test.yml
│   │
│   ├── k8s/
│   │   ├── ticketing-api.yaml
│   │   ├── registration-worker.yaml
│   │   └── notification-service.yaml
│   │
│   └── scripts/
│       └── init-broker.sh
│
├── load-testing/
│   ├── k6/
│   │   ├── queue-load.js
│   │   ├── seat-lock.js
│   │   └── checkout.js
│
├── ci/
│   └── github/workflows/
│       ├── build.yml
│       ├── test.yml
│       └── deploy.yml
│
├── pom.xml                              # Parent POM
└── README.md

```


---

## 4. Registration Consumers: Separate or Same App?

### **Best Practice Recommendation**

**Separate Spring Boot applications for consumers**

### Why NOT keep them in the same monolith?

| Problem | Impact |
| --- | --- |
| Heavy booking load | API latency increases |
| Seat-lock logic | Blocks request threads |
| Queue spikes | Risk of API crashes |

### Recommended Approach

| Component | Deployment |
| --- | --- |
| Ticketing API | Stateless, scaled on HTTP traffic |
| Registration Worker | Independently scaled via queue depth |

> This allows scaling consumers based on RabbitMQ queue size, not HTTP traffic.

---

## 5. Registration Types Handling

### 5.1 Registration Without Queue (Small Events)

**Flow:**

1. User calls `POST /events/{id}/register`
2. API locks seats in Redis
3. Payment session starts
4. On success → Ticket created
5. Redis lock removed

**Characteristics:**

- Synchronous flow
- Low contention
- Short seat TTL

---

### 5.2 Registration With Queue (Large Events)

**Flow:**

1. User enters queue (RabbitMQ)
2. Queue assigns position + ETA
3. Worker consumes request
4. Seat lock applied in Redis
5. User notified via WebSocket
6. Payment session starts
7. Ticket finalized

**Characteristics:**

- Fully async
- Controlled throughput
- Prevents database overload

---

## 6. Seat Locking Strategy (Core Design)

### Redis-Based Seat Locking

Each seat is locked using a **distributed lock with TTL**.

```
seat_lock:{event_id}:{seat_id} → user_id (TTL = 5 min)

```

### Guarantees

- No double booking
- Automatic expiration
- High-speed access
- Idempotent operations

---

## 7. Venue Representation & Seat Modeling

### 7.1 Procedural Venues (Theatres)

**Stored as:**

```
rows
columns
seat_pricing_strategy

```

**Seats generated dynamically:**

- Row + Column index
- Deterministic seat ID
- No need to persist all seats

---

### 7.2 SVG-Based Venues (Stadiums / Arenas)

**Stored as:**

- SVG file reference
- Seat zones
- Zone pricing
- Seat identifiers embedded in SVG

**Runtime:**

- SVG parsed
- Seat IDs mapped to availability
- UI highlights seat status in real time

---

## 8. Data Model – ERD Overview

### Core Entities

# Event Ticketing System – Database Schema

## 1. Users

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| first_name | VARCHAR |  |
| last_name | VARCHAR |  |
| email | VARCHAR | Unique |
| birthdate | DATE |  |
| hashed_password | VARCHAR |  |
| created_at | TIMESTAMP | Default current timestamp |
| updated_at | TIMESTAMP |  |
| role | ENUM | `USER`, `ADMIN` |
| status | ENUM | `ACTIVE`, `SUSPENDED` |

---

## 2. Venues

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| name | VARCHAR |  |
| location | VARCHAR |  |
| type | ENUM | `MOVIE_THEATRE`, `STADIUM`, `OTHER` |
| rows | INT | For procedural theatres |
| columns | INT | For procedural theatres |
| seating_map | JSON/BLOB | For SVG or custom seating map |
| created_at | TIMESTAMP |  |
| updated_at | TIMESTAMP |  |

---

## 3. Events

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| name | VARCHAR |  |
| description | TEXT |  |
| start_date | TIMESTAMP |  |
| end_date | TIMESTAMP |  |
| event_type | ENUM | `MOVIE`, `CONCERT`, `SPORTS_EVENT` |
| status | ENUM | `UPCOMING`, `ONGOING`, `COMPLETED`, `CANCELLED` |
| venue_id | UUID | Foreign Key → Venues(id) |
| performer_id | UUID | Nullable, FK → Performers(id) |
| movie_id | UUID | Nullable, FK → Movies(id) |
| max_capacity | INT | Optional override |
| created_at | TIMESTAMP |  |
| updated_at | TIMESTAMP |  |

---

## 4. Movies

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| title | VARCHAR |  |
| description | TEXT |  |
| director | VARCHAR |  |
| release_date | DATE |  |
| created_at | TIMESTAMP |  |
| updated_at | TIMESTAMP |  |

---

## 5. Performers / Organizers

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| name | VARCHAR |  |
| description | TEXT |  |
| created_at | TIMESTAMP |  |
| updated_at | TIMESTAMP |  |

---

## 6. Tickets / Registrations

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary Key |
| user_id | UUID | Foreign Key → Users(id) |
| event_id | UUID | Foreign Key → Events(id) |
| status | ENUM | `PENDING`, `CONFIRMED`, `CANCELLED` |
| seat_row | INT | Optional (for procedural theatre) |
| seat_column | INT | Optional |
| seat_label | VARCHAR | Optional (for stadium SVG maps) |
| price | DECIMAL |  |
| locked_until | TIMESTAMP | Seat lock expiration time |
| qr_code | VARCHAR | Generated PDF / QR code path |
| created_at | TIMESTAMP |  |
| updated_at | TIMESTAMP |  |

---

## 7. Seat Status (Optional for fast Redis cache)

| Column | Type | Notes |
| --- | --- | --- |
| event_id | UUID | FK → Events(id) |
| seat_row | INT |  |
| seat_column | INT |  |
| seat_label | VARCHAR |  |
| status | ENUM | `AVAILABLE`, `LOCKED`, `BOOKED` |
| locked_by_user | UUID | Nullable FK → Users(id) |
| locked_at | TIMESTAMP |  |

> Note: Redis can store this as a hash map for fast seat locking/availability checks.
> 

---

## 8. Enums

```sql
-- Event Type
EventType: MOVIE| CONCERT| SPORTS_EVENT

-- Event Status
EventStatus: UPCOMING| ONGOING| COMPLETED| CANCELLED

-- User Role
UserRole:USER| ADMIN

-- User Status
UserStatus: ACTIVE| SUSPENDED

-- Ticket Status
TicketStatus: PENDING| CONFIRMED| CANCELLED

-- Seat Status
SeatStatus: AVAILABLE| LOCKED| BOOKED

```

---

## 9. Relationships

- `User` 1..* `Ticket`
- `Event` 1..* `Ticket`
- `Event` 1..1 `Venue`
- `Event` 0..1 `Movie`
- `Event` 0..1 `Performer`
- `Venue` contains `seat_map` (procedural or SVG)
- `Ticket.seat_row / seat_column / seat_label` references the seat in the venue

---

## 10. Notes on Implementation

1. **Procedural vs SVG Seating**
    - Procedural: rows × columns → generate seats dynamically
    - SVG: pre-defined map → store seat IDs/labels
2. **Seat Locking**
    - Store `locked_until` and `locked_by_user` in DB + Redis cache
    - Redis handles **fast availability checks**
    - Worker processes seat confirmations / releases
3. **High Concurrency**
    - Use RabbitMQ for registration queue
    - Workers consume and confirm bookings
    - Prevent double booking with **optimistic DB locks** or **Redis atomic operations**
4. **Ticket Delivery**
    - QR code / PDF stored in a storage service
    - Notification service consumes `TicketConfirmedEvent` to send emails

---

## 10. Real-Time Communication

### WebSocket Use Cases

- Queue position updates
- Seat lock confirmation
- Payment success/failure
- Ticket issued notification

### Channel Example

```
/ws/events/{eventId}/user/{userId}

```

---

## 11. Email & Ticket Delivery

- PDF ticket generation (QR embedded)
- Sent via email
- Stored in user account
- Can be revoked if cancelled

---

## 12. Future Deployment Architecture (Kubernetes)

```
[Ingress]
   |
[API Gateway]
   |
----------------------------
| Ticketing API (HPA)     |
----------------------------
   |
[RabbitMQ Cluster]
   |
----------------------------
| Registration Workers    |
----------------------------
   |
[PostgreSQL]   [Redis]

```

### Scaling Strategy

- API scales with HTTP load
- Workers scale with queue depth
- Redis ensures consistency
- DB protected from traffic spikes