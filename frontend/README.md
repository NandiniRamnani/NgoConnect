# NGOConnect — Frontend

React single-page application for NGOConnect, a platform connecting verified
NGOs with donors and volunteers. Built with React 19 and Vite.

> **The Spring Boot API lives in a separate repository.** This application is
> only the interface; every piece of data it shows and every action it performs
> goes through that API. Start the backend first, then this.

## Running it

```powershell
npm install        # first time only
npm run dev
```

Vite prints a local address, usually <http://localhost:5173>.

The development server proxies `/api` to the backend on port 8082, so both must
be running. If API calls fail with a connection error, the backend is not up.

## Screens

| Route | Purpose |
| --- | --- |
| `/` | Landing page with featured NGOs and impact figures |
| `/ngos`, `/ngos/:id` | Browse verified organisations and view a profile |
| `/donate` | Three-step donation flow — gateway or wallet balance |
| `/wallet` | Balance, top-up and the transaction passbook |
| `/events`, `/needs` | Volunteer opportunities and material requirements |
| `/dashboard` | Donor or NGO dashboard, depending on the signed-in role |
| `/ngo/finance` | NGO balance, ledger, payout details and withdrawals |
| `/admin` | Administrator console for approvals and oversight |
| `/login`, `/register` | Authentication, with password reset by email |

## Payments

Donations and wallet top-ups open Razorpay Checkout. Card and UPI details go
directly to Razorpay and never reach this application or its backend.

When the backend reports `demoMode`, a built-in payment sheet opens instead of
Razorpay. It simulates the gateway so the rest of the flow can be demonstrated
without depending on a live payment provider, and includes a deliberate
"simulate a failed payment" action for showing error handling. Everything after
the payment — crediting, receipts, ledger entries — still runs for real.

## Structure

```text
src/
  api/          # Axios instance, base URL and interceptors
  components/   # Shared UI: navbar, footer, avatar, demo checkout
  context/      # Authentication context
  pages/        # One component and stylesheet per route
```

## Two things that are easy to get wrong

- **File uploads must clear the content type.** The Axios instance defaults
  every request to `application/json`. A multipart upload has to carry
  `multipart/form-data` with a browser-generated boundary, so every upload
  passes `'Content-Type': undefined` to let Axios set it correctly.
- **PDF downloads must pass `responseType: 'blob'`.** Without it Axios assumes
  text and runs the bytes through string decoding, producing a file that
  downloads successfully and then refuses to open.

## Build

```powershell
npm run build      # output in dist/
npm run preview    # serve the production build locally
```
