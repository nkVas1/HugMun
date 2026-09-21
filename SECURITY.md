# Security policy

## Scope

HugMun is a local-first Android application. There is no server, no account and no
backend, so the attack surface is the device itself: the encrypted Room database, the
exported archive, the camera stream used during a breathing session, and anything an
installed app could reach through exported components or intents.

## Supported versions

The project is pre-1.0. Only the `main` branch receives fixes.

## Reporting a vulnerability

Please **do not open a public issue** for a vulnerability.

Use GitHub's private vulnerability reporting on this repository
(Security → Report a vulnerability). Include what you found, how to reproduce it, the
device and Android version, and what an attacker could obtain.

Expect an acknowledgement within a few days. Because the project handles health-adjacent
personal data — names and photographs of the user's family, mood entries, blood-pressure
readings, raw pulse data — reports touching data exposure are treated as high severity
by default.

## What we consider in scope

- Any path by which another app, or a user without the device passcode, can read stored
  personal data.
- Weaknesses in the database key handling or in the Android Keystore usage.
- Unintended export of components, or intent handling that leaks data.
- An export archive that contains more than the user was told it would.
- Anything that causes camera frames to be persisted to storage.

## What is out of scope

- Physical access to an unlocked device.
- Rooted or otherwise compromised devices.
- Issues that require the user to install a malicious app *and* grant it privileges.
- Absence of certificate pinning or similar network hardening — the app makes no
  network requests.

## Safety defects

A failure of a *safety* control — the photosensitivity gate, the daily cap, the abort
control, the adverse-event lock — is not a security issue but is treated with the same
priority. See [`SAFETY.md`](SAFETY.md).
