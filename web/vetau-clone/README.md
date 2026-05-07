Vé Tàu — UI clone (inspired)

This is a lightweight, "inspired" frontend copy of the layout from vetauduongsat.vn for local development and UI work.

Files:
- `index.html` — main page with search form, banners, results.
- `styles.css` — styling.
- `script.js` — client logic and mock data; attempts to call `/trains/search` if available.
- `auth.html` — login/register page (for regular users).
- `checkout.html` — passenger info + payment method step before booking is finalized.
- `booking.html` — booking confirmation page.
- `admin-login.html` — separate admin login portal (NEW - separate from user auth).
- `admin-dashboard.html` — admin dashboard to manage bookings (NEW - professional dashboard).
- `admin.html` — redirects to new admin login (legacy).

Preview locally:

PowerShell / CMD:

```bash
# from project root (train-ticket-java)
cd web/vetau-clone
python -m http.server 8082
# open http://localhost:8082
```

Or integrate into your `web` container by copying the folder into the web image served by nginx.

Current integrated routes (nginx + services):
- `/` -> clone homepage
- `/auth/*` -> auth-service
- `/trains/*` -> trains-service
- `/bookings/*` -> bookings-service
- `/payments/*` -> payment stubs (bookings-service)
- `/admin-login.html` -> Separate admin portal login
- `/admin-dashboard.html` -> Admin booking management dashboard

**NEW: Four Production-Ready Features**

### 1. 🔐 Secure Bootstrap Admin (One-Time Secret)

Create your first admin without manual SQL:

```powershell
$body = @{
    secret = "MySecureBootstrapSecret123!"
    username = "first_admin"
    password = "secure_password_here"
    fullName = "Admin User Name"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/auth/bootstrap-admin' `
    -ContentType 'application/json' -Body $body

# Response includes token and admin user details
# Secret is ONE-TIME use only - after first use, all future attempts rejected
```

**Configuration:**
- Environment variable: `BOOTSTRAP_ADMIN_SECRET` (default: `MySecureBootstrapSecret123!`)
- Endpoint: `POST /auth/bootstrap-admin`
- One-time use: After first successful creation, secret expires permanently
- Security: Returns 403 Forbidden if secret already used or invalid

### 2. 🔑 JWT RS256 Public-Key Verification

Auth service uses RSA signature (RS256) instead of symmetric keys:

```powershell
# Get public key from auth service (cached by bookings-service)
$keyResponse = Invoke-RestMethod -Method Get -Uri 'http://localhost:8081/auth/key'

# Response:
# {
#   "key": "MIIBIjANBgkqhkiG...",  // Base64 encoded X.509 public key
#   "algorithm": "RSA",
#   "format": "X.509"
# }
```

**Benefits:**
- Bookings service verifies tokens **locally** without calling auth service each time
- Auth service holds private key; bookings gets public key
- RSA 2048-bit keys for production security
- JWT includes `userId` and `role` claims in payload
- 1-hour token expiration

### 3. 💳 Payment Stub Endpoints

Three payment method stubs integrated into checkout flow:

#### VNPAY
```powershell
$payment = @{
    amount = 500000
    bookingId = "booking_uuid_here"
    email = "customer@example.com"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/payments/vnpay' `
    -ContentType 'application/json' -Body $payment

# Response: {
#   "success": true,
#   "provider": "VNPAY",
#   "transactionId": "VNPAY_...",
#   "status": "PENDING"
# }
```

#### MOMO
```powershell
$payment = @{
    amount = 500000
    bookingId = "booking_uuid_here"
    phone = "0901234567"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/payments/momo' `
    -ContentType 'application/json' -Body $payment

# Response: { "success": true, "provider": "MOMO", "transactionId": "MOMO_...", ... }
```

#### COD (Cash on Delivery)
```powershell
$payment = @{
    amount = 500000
    bookingId = "booking_uuid_here"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/payments/cod' `
    -ContentType 'application/json' -Body $payment

# Response: { "success": true, "provider": "COD", "status": "CONFIRMED" }
```

**Integration:**
- Checkout.html calls payment endpoint BEFORE creating booking
- Payment verification happens before booking finalizes
- Extensible for real gateway integration

### 4. 🛡️ Separate Admin Portal (New)

Complete admin interface completely separate from user-facing auth:

- **URL**: `http://localhost:8081/admin-login.html` (not mixed with user auth)
- **Login**: Requires admin role (set during bootstrap or SQL)
- **Dashboard**: `http://localhost:8081/admin-dashboard.html`
- **Features**:
  - Real-time bookings list (auto-refresh every 30s)
  - Statistics: Total bookings, pending, completed, revenue
  - Search & filter by status, ID, name, route
  - Passenger/payment details display
  - Admin tokens stored separately in `admin_token` localStorage

**To set admin role for existing users (SQL):**
```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'admin_final';
```

Flow (Regular Users):
1. User searches trains on homepage.
2. Click `Chọn` → if not logged in, redirected to `auth.html`.
3. After login/register, user fills passenger/payment info at `checkout.html`.
4. Checkout calls appropriate payment endpoint (`/payments/vnpay`, `/momo`, `/cod`).
5. Frontend calls `POST /bookings/create` with `Authorization: Bearer <token>`.
6. Confirmation shown at `booking.html?id=<bookingId>`.

Admin Portal Access:
- Click `Admin 🛡️` button on homepage → redirects to `/admin-login.html`
- Login with admin credentials (role must be ADMIN in database)
- Redirected to `/admin-dashboard.html` with full booking management interface
- Completely separate from user authentication flow

Integrated Flow with New Features:
1. Bootstrap first admin via one-time secret
2. Admin logs into separate admin portal
3. Users proceed through normal auth flow
4. Bookings uses RS256 to verify JWTs locally
5. Payment stubs validate and store transaction info
6. Admin views all bookings with payment details

Notes:
- This is an "inspired" UI. Replace logos/images with your own assets as needed.
- If you want a near-exact pixel clone, provide assets and I can refine styles, but be aware of copyright considerations.
- Payment endpoints are stubs (always return success) - integrate real APIs for production
- Admin portal uses same JWT infrastructure as user booking, but with role-based authorization


