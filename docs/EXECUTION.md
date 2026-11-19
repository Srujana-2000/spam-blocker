# Execution and validation record

Verified on **18 November 2026, Asia/Kolkata**, on this Mac (Apple silicon, macOS).

This is evidence for the new reconstruction. It does not validate the historical application's claimed test results.

## Actual environment

- Eclipse Temurin JDK **21.0.13+9**, downloaded into the project-local `.runtime/` directory.
- Apache Tomcat **10.1.61**, bundled in `vendor/`.
- H2 JDBC driver **2.5.252**, bundled in `lib/`.
- Java sources compiled with `javac --release 21`; JSP compilation configured for Java 17 language features on the Java 21 runtime.
- Python **3.14** used to launch the Java application and execute the HTTP tests.
- Installed Google Chrome used through Playwright for browser verification.
- Local server bound to **127.0.0.1:8765**. No public deployment was performed.

## Commands executed

```sh
python3 run.py --test
python3 run.py --port 8765
python3 -u tests/integration_test.py --base-url http://127.0.0.1:8765
```

After server shutdown:

```sh
python3 scripts/export_db.py
```

The export reopened the persisted database in a separate JVM and included the newly created comments and moderation audit history. Temporary test records were then moved out of the delivered application's data folder, and the final demo was initialized from the normal sample data.

## Results

| Verification | Result |
| --- | --- |
| Compile application Java sources | Passed |
| Compile/serve JSP view through Tomcat | Passed |
| Java core checks | **15 / 15 passed** |
| HTTP integration checks | **98 / 98 passed** |
| Browser page errors | **None observed** |
| Desktop screenshot | 1440px viewport, inspected |
| Mobile screenshot | 390px viewport, inspected |
| Mobile horizontal overflow | **None observed** |
| Browser sign-in, admin dashboard, moderation, dictionary editor, sign-out | Passed |
| Registration accessible labels | Verified |
| Database persistence after shutdown | SQL export verified |
| Document review PDF | Six pages rendered and visually inspected |
| Portable ZIP | Extracted into a path containing spaces; core tests and separate HTTP launch passed |

Portable package evidence: [portable-package-results.txt](portable-package-results.txt).

Logs: [core-results.txt](core-results.txt), [integration-results.txt](integration-results.txt), and [browser-results.json](browser-results.json).

## What the tests cover

The Java checks exercise final CSV entries, quoted cells and a byte-order mark, case/punctuation handling, phrase matching, Unicode normalization, whole-word boundaries, clean input, repeated terms, malformed/empty dictionaries, salted password verification, HTML escaping, and CSRF-token comparison.

The HTTP checks exercise public pages, missing/invalid IDs, direct JSP protection, cookie flags and CSP, required fields and validation, registration/uniqueness, correct and incorrect sign-in, role boundaries, blank/overlong comments, clean publication, escaped stored markup, profile persistence, private held comments, staff screens, review approval/rejection, duplicate review protection, warning thresholds, blocked sign-in, account restoration, PNG upload/retrieval, forum editing, invalid/oversized uploads, forum removal, verifier creation, manual blocking/deactivation, existing blocked sessions, audit entries, dictionary editing/authorization/validation and live reload, and logout.

## Screenshots

- [Desktop community home](home-desktop.png)
- [Mobile community home](home-mobile.png)
- [Admin dashboard](admin-dashboard.png)
- [Moderation page](moderation.png)
- [Admin dictionary editor](dictionary-editor.png)

These show actual rendered pages. The final screenshots use a fresh database with the three sample forums and three demo accounts.

## Limits of the evidence

There was no load test, penetration test, formal accessibility audit, external email/SMS test, or public-hosting test. Windows and Linux execution were not performed. The included automated tests write demo fixtures, so future runs should target a disposable local database. The browser automation used for this delivery is an environment-level validation tool; the delivered app itself has no Node.js or Playwright dependency.
