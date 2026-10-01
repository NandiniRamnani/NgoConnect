# NGO Connect

A platform that connects donors and volunteers with verified NGOs: donations with 80G receipts,
events, urgent needs, sponsorable food slots, and photo/video galleries.

| Folder | What it is | Tech |
|---|---|---|
| [`backend/`](backend) | REST API | Java 17, Spring Boot 4, MongoDB, Cloudinary, Razorpay |
| [`frontend/`](frontend) | Web app | React 19, Vite, React Router |

---

## Run it locally

**Needs:** Java 17, Node 20+, and a MongoDB database (a free MongoDB Atlas cluster works).

```bash
# 1. Backend  →  http://localhost:8082
cd backend
cp .env.example .env        # then fill in your own values (PowerShell: Copy-Item .env.example .env)
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run

# 2. Frontend  →  http://localhost:5173   (in a second terminal)
cd frontend
npm install
npm run dev
```

In development the frontend calls `/api/...`, and the Vite dev server forwards those calls to the
backend on port 8082, so nothing else needs configuring.

---

## Deploy it

Three free services, one per piece:

```
 Browser ──► Vercel (frontend/)  ──API calls──►  Render (backend/, Docker)  ──►  MongoDB Atlas
                                                         └──► Cloudinary, Razorpay, Gmail SMTP
```

### 1. Database — MongoDB Atlas
1. Create a free cluster and a database user.
2. **Network Access → Add IP Address → `0.0.0.0/0`** (Render's free tier has no fixed IP).
3. Copy the connection string (put the database name before the `?`). This is `MONGODB_URI`.

### 2. Backend — Render
1. Render dashboard → **New + → Blueprint** → choose this repository.
   Render reads [`render.yaml`](render.yaml) and sets up a Docker service from `backend/`.
2. Fill in the secrets it asks for (same names as [`backend/.env.example`](backend/.env.example)).
   Set `APP_FRONTEND_URL` after step 3, once you know the Vercel address.
3. Wait for the first deploy, then open `https://<your-service>.onrender.com/api/ngos`;
   a JSON list (even `[]`) means it is up.

> The free tier sleeps after ~15 minutes without traffic; the first request after that takes
> 30–60 seconds while it wakes up.

### 3. Frontend — Vercel
1. Vercel → **Add New → Project** → import this repository.
2. **Root Directory: `frontend`**. The framework (Vite) is detected automatically.
3. **Environment Variables:** `VITE_API_URL` = your Render URL, e.g. `https://ngoconnect-backend.onrender.com`
4. Deploy. Then go back to Render and set `APP_FRONTEND_URL` to the Vercel URL
   (e.g. `https://ngoconnect.vercel.app`, no trailing slash). The backend only accepts browser
   requests from that address. Several addresses can be listed, separated by commas.

### 4. Razorpay webhook (optional but recommended)
Razorpay → Settings → Webhooks → add `https://<your-render-service>.onrender.com/api/payments/webhook`
for the `payment.captured` event, and put the secret you chose into `RAZORPAY_WEBHOOK_SECRET`.

### Deploying changes
Push to `main`. Vercel rebuilds the frontend, and Render rebuilds the backend only when something
under `backend/` changed.

---

## Configuration reference

| Where | Variable | Purpose |
|---|---|---|
| Backend | `MONGODB_URI` | Database connection |
| Backend | `APP_FRONTEND_URL` | Frontend address(es): allowed for API calls, used in reset emails |
| Backend | `CLOUDINARY_*` | Photo, video and logo storage |
| Backend | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Payments |
| Backend | `MAIL_USERNAME`, `MAIL_PASSWORD` | Password-reset emails (Gmail App Password) |
| Backend | `NGO_ADMIN_USERNAME`, `NGO_ADMIN_PASSWORD` | Built-in admin login |
| Backend | `PORT` | Set by the host automatically; defaults to 8082 |
| Frontend | `VITE_API_URL` | Backend address; leave unset in development |

Real values go in `backend/.env` locally (git-ignored) and in each host's dashboard when deployed.
Never commit them.
