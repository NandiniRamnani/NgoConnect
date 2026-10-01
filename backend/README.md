# NGOConnect — Backend

REST API for NGOConnect, a platform connecting verified NGOs with donors and
volunteers. Spring Boot 4.1.1, Java 17, MongoDB.

> **The React frontend lives in a separate repository.** The two are deployed
> independently and talk over HTTP, so they are versioned independently too.
> Run this API first (port 8082), then start the frontend against it.

## What this service does

- **NGO onboarding** — registration with statutory documents, automatic text
  extraction and keyword verification (Apache PDFBox, falling back to Tesseract
  OCR for scanned files), then administrative approval before an organisation
  becomes publicly visible.
- **Donations** — funded either through the Razorpay gateway or from an internal
  wallet. Gateway payments are verified by HMAC-SHA256 signature and claimed
  atomically, so a payment is credited exactly once no matter how many times it
  is submitted.
- **Digital wallet** — balances held as integer paise, changed only by
  conditional atomic operations that cannot overdraw, with an append-only
  transaction ledger.
- **80G tax receipts** — generated as PDFs with financial-year sequential
  numbering drawn from an atomic counter, and emailed to the donor.
- **Payment recovery** — a signed webhook endpoint credits payments the browser
  never reported.
- **NGO finance** — clearing / available / reserved balances, a scheduled job
  that promotes cleared funds, payout methods and admin-approved withdrawals.
- **Engagement** — events with enrolment and attendance, published needs,
  one-to-one chat and notifications.

## Running it

```powershell
.\run-backend.bat          # starts on http://localhost:8082
```

The script clears port 8082 first and uses the Maven wrapper, so no Maven goal
has to be typed by hand. The equivalent command is `mvnw.cmd spring-boot:run`.

Useful extras:

| Script | Purpose |
| --- | --- |
| `run-backend.bat` | Start the API |
| `check-mail.bat` | Test the SMTP credentials in `.env` without sending mail |

## Design documentation

UML diagrams (use case, activity, sequence, class) live in `docs/uml/` as
PlantUML sources with rendered PNGs beside them. Open `docs/diagrams.html` in a
browser to view them all together.

## Source layout

Application source is kept in a single folder:

```text
src/
  main/
    java/
      app/
        controller/        # HTTP controllers
        service/           # Business logic
        repository/        # Database repositories
        model/             # Database models
        enums/             # Enumerations
    resources/
      application.properties
  test/
    java/
      app/                 # Tests go here
```

## Where to add files

- Add application classes in the appropriate package under `src/main/java/app`.
- Add tests in `src/test/java/app`.
- Put configuration in `src/main/resources/application.properties`.

## Configuration

All credentials live in a `.env` file at the project root, which is gitignored.
`application.properties` pulls it in via `spring.config.import` and refers to
each value as a `${PLACEHOLDER}`, so no secret is ever committed.

To set up a fresh checkout:

```bash
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
```

then fill in the values. Precedence, highest first:

1. real OS environment variables (how you configure a deployment)
2. `.env`
3. the defaults written after `:` in each placeholder

Values with no default — `MONGODB_URI`, the Cloudinary keys, the Razorpay keys —
fail startup loudly if missing, rather than letting the app come up half-configured.

> **MongoDB property name:** Spring Boot 4 moved the connection settings from
> `spring.data.mongodb.*` to `spring.mongodb.*`. The old names are deprecated at
> `level=error`, so they are ignored silently and the app falls back to
> `mongodb://localhost/test`. Mapping settings such as
> `spring.data.mongodb.auto-index-creation` did **not** move.

## Admin portal

Open `http://localhost:5173/admin` and sign in with the values of
`NGO_ADMIN_USERNAME` / `NGO_ADMIN_PASSWORD` from your `.env` .

> **Change these before deploying.** Any credentials you use locally should not
> survive into a public deployment.

Leaving both blank disables the built-in admin account entirely — `SecurityConfig`
reads them as `${ngo-admin.username:}` and skips it when empty.

`src/main/java` and `src/test/java` are Maven-required source locations. Do not create another `src` folder inside them.
