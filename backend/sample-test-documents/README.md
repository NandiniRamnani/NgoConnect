# Sample test documents

These are **synthetic, placeholder files** for demoing/testing the NGO
document-verification feature. They are not real government-issued
documents — every file is stamped "SPECIMEN / SAMPLE" and uses a made-up
organisation name, PAN, and registration number that don't belong to any
real entity. Don't present these as genuine IDs outside of this demo.

Use them on the **"Upload verification documents"** step (step 3) of
`Register an NGO`. Suggested demo order:

| File | Upload as | What it proves |
|---|---|---|
| `1-registration-certificate-digital.pdf` | Registration Certificate | Text-native PDF → read directly, ✓ valid |
| `2-pan-card-digital.pdf` | PAN Card | Text-native PDF → read directly, ✓ valid |
| `3-registration-certificate-scanned.pdf` | Registration Certificate | PDF with **no text layer** (simulates a scanned copy) → falls back to **Tesseract OCR**, still comes back ✓ valid |
| `4-pan-card-photo.jpg` | PAN Card | Raw **photo upload** (not a PDF at all) → OCR reads it directly, ✓ valid |
| `5-wrong-document-electricity-bill.pdf` | PAN Card (upload it into the PAN Card slot on purpose) | Completely unrelated document → flagged ⚠ "doesn't look like a PAN Card" |

For the PAN/registration cross-check bonus to trigger (the strongest,
"number on the document matches what you typed" message), enter these on
step 2 of the form before uploading:
- **Organisation PAN:** `AAATG1234C`
- **Registration number:** `TR/2019/00456`

If you type different values, the files still pass on keywords alone
(e.g. "INCOME TAX DEPARTMENT", "CERTIFICATE OF REGISTRATION") — just with a
slightly less specific message.

Regenerate these anytime with the generator script referenced in
`GenerateSampleDocs.java` (kept outside the repo, in the session scratchpad)
if you want different placeholder data.
