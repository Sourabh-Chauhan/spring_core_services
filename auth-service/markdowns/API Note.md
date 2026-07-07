### A. Authentication Controller (`/api/v1/auth`)
Endpoints under this path do not require authentication (public access) except for logout.

* **Register User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/register`
  * **Headers:** None
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com",
      "name": "John Doe",
      "password": "Password123!"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
      "email": "user@example.com",
      "name": "John Doe",
      "image": null,
      "enable": true,
      "emailVerified": false,
      "createdAt": "2026-07-07T08:49:21Z",
      "updatedAt": "2026-07-07T08:49:21Z",
      "provider": "LOCAL",
      "roles": []
    }
    ```

* **Login**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/login`
  * **Headers:** None
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com",
      "password": "Password123!"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...",
      "refreshToken": "79b4a45a-c603-4c91-9e2d-45dbfa2cd04c",
      "expiresIn": 900,
      "tokenType": "Bearer",
      "user": {
        "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
        "email": "user@example.com",
        "name": "John Doe",
        "image": null,
        "enable": true,
        "emailVerified": true,
        "createdAt": "2026-07-07T08:49:21Z",
        "updatedAt": "2026-07-07T08:49:21Z",
        "provider": "LOCAL",
        "roles": [
          {
            "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
            "name": "ROLE_USER"
          }
        ]
      }
    }
    ```
  * **Note:** Response returns access token in body and sets HttpOnly cookie `refreshToken`.

* **Refresh Token**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/refresh`
  * **Headers/Cookies:** Pass `refreshToken` in Cookie OR `X-Refresh-Token` header.
  * **Query Params:** None
  * **Body (JSON - Optional):**
    ```json
    {
      "refreshToken": "<your_refresh_token>"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...",
      "refreshToken": "79b4a45a-c603-4c91-9e2d-45dbfa2cd04c",
      "expiresIn": 900,
      "tokenType": "Bearer",
      "user": {
        "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
        "email": "user@example.com",
        "name": "John Doe",
        "image": null,
        "enable": true,
        "emailVerified": true,
        "createdAt": "2026-07-07T08:49:21Z",
        "updatedAt": "2026-07-07T08:49:21Z",
        "provider": "LOCAL",
        "roles": [
          {
            "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
            "name": "ROLE_USER"
          }
        ]
      }
    }
    ```

* **Verify Email**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/auth/verify-email`
  * **Headers:** None
  * **Query Params:** `token` (String, UUID)
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    {
      "message": "Email verified successfully. You can now log in."
    }
    ```

* **Resend Verification Email**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/resend-verification`
  * **Headers:** None
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "message": "If the email is registered, a new verification link has been sent."
    }
    ```

* **Forgot Password**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/forgot-password`
  * **Headers:** None
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "message": "If the email is registered, a password reset link has been sent."
    }
    ```

* **Reset Password**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/reset-password`
  * **Headers:** None
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "token": "<reset_token>",
      "newPassword": "NewPassword123!"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "message": "Password reset successfully. You can now log in."
    }
    ```

* **Logout**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/logout`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Cookies:** Cookie `refreshToken` (optional, parsed from request)
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)

---

### B. User Management Controller (`/api/v1/users`)
All endpoints under this path require a valid access token in the Authorization header.

* **Create User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/users`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "email": "another@example.com",
      "name": "Jane Doe",
      "password": "Password123!"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
      "email": "another@example.com",
      "name": "Jane Doe",
      "image": null,
      "enable": true,
      "emailVerified": false,
      "createdAt": "2026-07-07T08:50:00Z",
      "updatedAt": "2026-07-07T08:50:00Z",
      "provider": "LOCAL",
      "roles": []
    }
    ```

* **Get All Users**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    [
      {
        "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
        "email": "user@example.com",
        "name": "John Doe",
        "image": null,
        "enable": true,
        "emailVerified": true,
        "createdAt": "2026-07-07T08:49:21Z",
        "updatedAt": "2026-07-07T08:49:21Z",
        "provider": "LOCAL",
        "roles": [
          {
            "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
            "name": "ROLE_USER"
          }
        ]
      }
    ]
    ```

* **Get User by ID**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    {
      "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
      "email": "user@example.com",
      "name": "John Doe",
      "image": null,
      "enable": true,
      "emailVerified": true,
      "createdAt": "2026-07-07T08:49:21Z",
      "updatedAt": "2026-07-07T08:49:21Z",
      "provider": "LOCAL",
      "roles": [
        {
          "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
          "name": "ROLE_USER"
        }
      ]
    }
    ```

* **Get User by Email**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users/email/{email}`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    {
      "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
      "email": "user@example.com",
      "name": "John Doe",
      "image": null,
      "enable": true,
      "emailVerified": true,
      "createdAt": "2026-07-07T08:49:21Z",
      "updatedAt": "2026-07-07T08:49:21Z",
      "provider": "LOCAL",
      "roles": [
        {
          "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
          "name": "ROLE_USER"
        }
      ]
    }
    ```

* **Update User**
  * **Method:** `PUT`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):**
    ```json
    {
      "name": "Jane Smith",
      "image": "profile.jpg"
    }
    ```
  * **Response (JSON):**
    ```json
    {
      "id": "e4b2d56a-1234-5678-abcd-ef0123456789",
      "email": "user@example.com",
      "name": "Jane Smith",
      "image": "profile.jpg",
      "enable": true,
      "emailVerified": true,
      "createdAt": "2026-07-07T08:49:21Z",
      "updatedAt": "2026-07-07T14:20:00Z",
      "provider": "LOCAL",
      "roles": [
        {
          "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
          "name": "ROLE_USER"
        }
      ]
    }
    ```

* **Delete User**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)

---

### C. Role & Permission Management (Admin) (`/api/v1/admin`)
All endpoints require a valid access token belonging to a user with the `ROLE_ADMIN` role.

* **Create Role**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/roles`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** `name` (String, e.g. `ADMIN` or `USER`)
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    {
      "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
      "name": "ROLE_ADMIN",
      "permissions": []
    }
    ```

* **Get All Roles**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/admin/roles`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    [
      {
        "id": "f5c3e67b-1234-5678-abcd-ef0123456789",
        "name": "ROLE_ADMIN",
        "permissions": [
          {
            "id": "d7e9f80a-1234-5678-abcd-ef0123456789",
            "name": "user:read"
          }
        ]
      }
    ]
    ```

* **Create Permission**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/permissions`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** `name` (String, e.g. `user:read`)
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    {
      "id": "d7e9f80a-1234-5678-abcd-ef0123456789",
      "name": "user:read"
    }
    ```

* **Get All Permissions**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/admin/permissions`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    [
      {
        "id": "d7e9f80a-1234-5678-abcd-ef0123456789",
        "name": "user:read"
      }
    ]
    ```

* **Assign Permission to Role**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/roles/{roleId}/permissions/{permissionId}`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)

* **Assign Role to User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/users/{userId}/roles/{roleId}`
  * **Headers:** `Authorization: Bearer <adminAccessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)

---

### D. Session Management Controller (`/api/v1/sessions`)
All endpoints under this path require a valid access token in the Authorization header.

* **Get All Active Sessions**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/sessions`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):**
    ```json
    [
      {
        "sessionId": "4db75e81-c301-4475-bc44-59e31d45110f",
        "ipAddress": "127.0.0.1",
        "deviceInfo": "Chrome on Windows 10",
        "createdAt": "2026-07-07T08:49:21Z",
        "expiresAt": "2026-07-07T09:04:21Z",
        "currentSession": true
      }
    ]
    ```
  * **Note:** Returns a list of active sessions with client metadata (OS, browser, IP, start time, expiration) and a `currentSession` flag indicating the active caller device.

* **Revoke Specific Session**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/sessions/{sessionId}` (Replace `{sessionId}` with the refresh token database ID)
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)

* **Revoke All Other Sessions**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/sessions/other`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Query Params:** None
  * **Body (JSON):** None
  * **Response (JSON):** None (Returns HTTP `204 No Content`)
  * **Note:** Revokes all active sessions for the user *except* the one issuing the request.