# Keycloak Setup Guide

## Starting Keycloak

1. Start the infrastructure containers:
   ```bash
   cd backend/infrastructure/docker
   docker-compose up -d
   ```

2. Access Keycloak Admin Console:
   - URL: http://localhost:9090
   - Username: `admin`
   - Password: `admin`

## Realm Configuration

### 1. Create Realm

1. Click on the realm dropdown (currently "master")
2. Click "Create Realm"
3. Name: `ticketing-platform`
4. Click "Create"

### 2. Create Client for Backend API

1. Go to "Clients" → "Create client"
2. **General Settings**:
   - Client type: `OpenID Connect`
   - Client ID: `ticketing-api-client`
   - Click "Next"

3. **Capability config**:
   - Client authentication: `ON`
   - Authorization: `OFF`
   - Authentication flow: Enable `Standard flow` and `Direct access grants`
   - Click "Next"

4. **Login settings**:
   - Valid redirect URIs: `http://localhost:8090/*`
   - Valid post logout redirect URIs: `http://localhost:8090/*`
   - Web origins: `http://localhost:8090`
   - Click "Save"

### 3. Configure Google OAuth2

1. **Get Google OAuth2 Credentials**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select existing
   - Enable "Google+ API"
   - Go to "Credentials" → "Create Credentials" → "OAuth 2.0 Client ID"
   - Application type: Web application
   - Authorized redirect URIs: `http://localhost:8180/realms/ticketing-platform/broker/google/endpoint`
   - Copy Client ID and Client Secret

2. **Configure in Keycloak**:
   - Go to "Identity providers" → "Add provider" → Select "Google"
   - Client ID: `<your-google-client-id>`
   - Client Secret: `<your-google-client-secret>`
   - Click "Add"

### 4. Configure Realm Roles

1. Go to "Realm roles" → "Create role"
2. Create the following roles:
   - `USER` (default role for all users)
   - `ADMIN` (for administrators)

3. Set default role:
   - Go to "Realm settings" → "User registration"
   - Default roles: Select `USER`

### 5. Configure Token Claims

1. Go to "Client scopes"
2. Create new scope: `user-info`
3. Add mappers:
   - **Email Mapper**:
     - Name: `email`
     - Mapper type: `User Property`
     - Property: `email`
     - Token Claim Name: `email`
     - Claim JSON Type: `String`
   
   - **Given Name Mapper**:
     - Name: `given_name`
     - Mapper type: `User Property`
     - Property: `firstName`
     - Token Claim Name: `given_name`
   
   - **Family Name Mapper**:
     - Name: `family_name`
     - Mapper type: `User Property`
     - Property: `lastName`
     - Token Claim Name: `family_name`

4. Assign scope to client:
   - Go to "Clients" → `ticketing-api-client` → "Client scopes"
   - Add `user-info` as a default scope

## Testing Authentication

### 1. Create Test User

1. Go to "Users" → "Add user"
2. Username: `testuser`
3. Email: `testuser@example.com`
4. First name and Last name
5. Click "Create"

6. Go to "Credentials" tab
7. Click "Set password"
8. Password: `password123`
9. Temporary: `OFF`
10. Click "Save"

### 2. Get Access Token

Using cURL:

```bash
curl -X POST http://localhost:8180/realms/ticketing-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ticketing-api-client" \
  -d "client_secret=<your-client-secret>" \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=password123"
```

**Note**: Get the client secret from Keycloak Admin Console:
- Clients → `ticketing-api-client` → "Credentials" tab → Client secret

### 3. Test Protected Endpoints

```bash
# Sync user from Keycloak
curl -X POST http://localhost:8080/api/auth/sync \
  -H "Authorization: Bearer <access-token>"

# Get current user
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <access-token>"
```

## Frontend Integration

For frontend applications, use the Authorization Code Flow:

1. Redirect user to:
   ```
   http://localhost:8180/realms/ticketing-platform/protocol/openid-connect/auth?
     client_id=ticketing-api-client&
     redirect_uri=http://localhost:3000/callback&
     response_type=code&
     scope=openid email profile
   ```

2. Exchange code for token at your backend

3. Include token in API requests:
   ```
   Authorization: Bearer <access-token>
   ```

## Common Issues

1. **CORS errors**: Make sure Keycloak client has correct web origins configured
2. **Invalid token**: Check token expiration and issuer URL in application.yml
3. **User not found**: Call `/api/auth/sync` after first authentication
