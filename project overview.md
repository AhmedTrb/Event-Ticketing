# Event Ticketing System

## 1. Project Overview

The **Event Ticketing System** is a full-stack web platform designed for **high-concurrency event registration and seat-based ticketing**.

It allows users to explore and register for different types of events such as **movie screenings, concerts, and sports events**, while ensuring **consistency, scalability, and fault tolerance** under extreme load.

The primary objective of this project is to design and implement a **real-world ticketing platform** capable of handling **tens of thousands to millions of concurrent users** during high-demand events, without suffering from overbooking, duplicate reservations, or system outages.

The platform incorporates:

- A **queue-based access system** for popular events
- **Seat locking** during the booking session
- **Interactive seating maps** (procedural or SVG-based)
- Strong consistency guarantees for bookings
- High availability for event browsing and discovery

This project aims to closely mirror the architecture and behavior of **industry-grade ticketing platforms** used in cinemas, concerts, and large sports events.

---

## 2. Problems in Traditional Ticketing Systems

Conventional ticketing platforms often fail under high traffic due to several well-known issues:

- **Overbooking** caused by race conditions during seat selection
- **Duplicate bookings** when concurrent requests are not handled correctly
- **System crashes or degraded performance** during flash-sale events
- **High response times** during peak traffic
- Tight coupling between user-facing APIs and heavy booking logic

This project addresses these problems by introducing:

- Controlled access through queuing
- Temporary seat locking
- Asynchronous processing of booking requests
- Clear separation between read-heavy and write-heavy workloads

---

## 3. Functional Requirements

The system involves two primary actors:

- **Users**, who explore events and purchase tickets
- **Administrators**, who manage events, venues, and platform monitoring

---

### 3.1 User Functional Requirements

- The user must be able to create an account and authenticate on the platform.
- The user must be able to explore and search for available events.
- The user must be able to view detailed event information (date, venue, pricing, availability).
- The user must be able to enter a **waiting queue** for high-demand events.
- The user must receive a **queue position and estimated waiting time (ETA)**.
- The user must be able to select tickets and choose seats using an **interactive seating map**.
- The selected seats must be **locked for a predefined time window** during the booking session.
- The user must be able to complete payment before the session expires.
- Upon successful payment:
    - A digital ticket must be generated
    - A **PDF ticket with a QR code** must be sent via email
    - The ticket must be saved in the user’s account
- The user must be able to view, download, or cancel tickets within allowed time constraints.

---

### 3.2 Admin Functional Requirements

- The admin must be able to create, update, and manage events.
- The admin must be able to assign an event type (movie, concert, sports event, etc.).
- The admin must be able to create and manage venues.
- The admin must be able to configure venue layouts:
    - Procedurally generated seating (e.g. movie theatres)
    - SVG-based seating maps (e.g. stadiums, concert halls)
- The admin must be able to assign a venue and sessions to an event.
- The admin must be able to define ticket pricing per session and seating category.
- The admin must be able to monitor:
    - Event status
    - Registration volume over time
    - System performance metrics

---

## 4. Non-Functional Requirements

### Scalability

- The system must support **very high concurrency**, up to **millions of users for a single popular event**.
- The platform must scale horizontally to handle traffic spikes.

### Performance

- Event browsing and search operations should have **low latency**.
- The booking process should respond within **acceptable time bounds** (target < 500ms for user-facing actions).

### Availability and Consistency

- The system must prioritize **availability** for browsing and event discovery.
- The system must prioritize **strong consistency** for ticket booking:
    - No double booking
    - No overselling of seats

---

## 5. User Workflows

### 5.1 Registration for a Large Event (With Queue)

1. The user explores available events.
2. The user selects a high-demand upcoming event.
3. The user views the event details (pricing, availability, venue, registration time).
4. When registration opens and demand is high:
    - The user enters a waiting queue.
    - The system displays the queue position and estimated waiting time.
5. When the user reaches the front of the queue:
    - A booking session starts.
    - The user selects the number of tickets.
    - The user chooses seats via the interactive seating map.
    - Selected seats are locked temporarily.
6. The user proceeds to checkout and completes payment.
7. Upon successful payment:
    - The booking is confirmed.
    - Tickets are generated and emailed.
    - Tickets are stored in the user’s account.
8. The booking session ends.

---

### 5.2 Registration for a Small Event (Without Queue)

1. The user selects an event.
2. The user reviews the event details.
3. The user clicks on “Register”.
4. A booking session starts and selected seats are locked.
5. The user confirms the order and completes payment.
6. Tickets are generated, emailed, and saved to the user’s account.
7. The booking session ends.

---

### 5.3 Admin Workflow: Creating a New Event

1. The admin fills in event details:
    - Title and description
    - Event type
    - Dates and times
    - Ticket pricing
2. The admin selects or creates a venue.
3. The admin assigns the venue and configures sessions.
4. The event is published and becomes visible to users.

---

### 5.4 Admin Workflow: Creating a New Venue

- Movie theatres:
    - Defined using a procedural rectangular layout (rows and columns)
    - Displayed using a dynamically generated seating map
- Stadiums or large venues:
    - Defined using uploaded SVG seating maps
    - Seats and sections are mapped to pricing categories

---

## 6. Core Domain Entities (High-Level)

- User
- Event
- Event Type (Movie, Concert, Sports Event, etc.)
- Venue
- Venue Section / Room
- Session (Event Time Slot)
- Seat
- Booking
- Ticket
- Payment