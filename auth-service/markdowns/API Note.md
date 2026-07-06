### A. Authentication Controller (`/api/v1/auth`)
Endpoints under this path do not require authentication (public access) except for logout.

* **Register User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/register`
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com",
      "name": "John Doe",
      "password": "Password123!"
    }
    ```
* **Login**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/login`
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com",
      "password": "Password123!"
    }
    ```
  * **Note:** Response returns access token in body and sets HttpOnly cookie `refreshToken`.
* **Refresh Token**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/refresh`
  * **Headers/Cookies:** Pass `refreshToken` in Cookie OR `X-Refresh-Token` header.
  * **Body (JSON - Optional):**
    ```json
    {
      "refreshToken": "<your_refresh_token>"
    }
    ```
* **Verify Email**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/auth/verify-email`
  * **Query Params:** `token` (String, UUID)
* **Resend Verification Email**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/resend-verification`
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
* **Forgot Password**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/forgot-password`
  * **Body (JSON):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
* **Reset Password**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/reset-password`
  * **Body (JSON):**
    ```json
    {
      "token": "<reset_token>",
      "newPassword": "NewPassword123!"
    }
    ```
* **Logout**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/auth/logout`
  * **Headers:** `Authorization: Bearer <accessToken>`
  * **Cookies:** Cookie `refreshToken` (optional, parsed from request)

---

### B. User Management Controller (`/api/v1/users`)
All endpoints under this path require a valid access token in the Authorization header.
* **Headers:** `Authorization: Bearer <accessToken>`

* **Create User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/users`
  * **Body (JSON):**
    ```json
    {
      "email": "another@example.com",
      "name": "Jane Doe",
      "password": "Password123!"
    }
    ```
* **Get All Users**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users`
* **Get User by ID**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)
* **Get User by Email**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/users/email/{email}`
* **Update User**
  * **Method:** `PUT`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)
  * **Body (JSON):**
    ```json
    {
      "name": "Jane Smith",
      "image": "profile.jpg"
    }
    ```
* **Delete User**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/users/{userId}` (Replace `{userId}` with UUID)

---

### C. Role & Permission Management (Admin) (`/api/v1/admin`)
All endpoints require a valid access token belonging to a user with the `ROLE_ADMIN` role.
* **Headers:** `Authorization: Bearer <adminAccessToken>`

* **Create Role**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/roles`
  * **Query Params:** `name` (String, e.g. `ADMIN` or `USER`)
* **Get All Roles**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/admin/roles`
* **Create Permission**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/permissions`
  * **Query Params:** `name` (String, e.g. `user:read`)
* **Get All Permissions**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/admin/permissions`
* **Assign Permission to Role**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/roles/{roleId}/permissions/{permissionId}`
* **Assign Role to User**
  * **Method:** `POST`
  * **URL:** `http://localhost:8083/api/v1/admin/users/{userId}/roles/{roleId}`

---

### D. Session Management Controller (`/api/v1/sessions`)
All endpoints under this path require a valid access token in the Authorization header.
* **Headers:** `Authorization: Bearer <accessToken>`

* **Get All Active Sessions**
  * **Method:** `GET`
  * **URL:** `http://localhost:8083/api/v1/sessions`
  * **Note:** Returns a list of active sessions with client metadata (OS, browser, IP, start time, expiration) and a `currentSession` flag indicating the active caller device.
* **Revoke Specific Session**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/sessions/{sessionId}` (Replace `{sessionId}` with the refresh token database ID)
* **Revoke All Other Sessions**
  * **Method:** `DELETE`
  * **URL:** `http://localhost:8083/api/v1/sessions/other`
  * **Note:** Revokes all active sessions for the user *except* the one issuing the request.