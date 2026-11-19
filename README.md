# Spam Blocker for Blogs

A working reconstruction of the supplied 2021–22 mini project report. This is new source code based on the report, not a recovery of the lost original files. It preserves the Java/JSP design visible in the code examples and screenshots, and provides an updated interface.

## Run it

Requirements: **Python 3.9+** (launcher only) and **JDK 21+** (the web application). Tomcat 10.1.61 and H2 2.5.252 are included. No Maven, PHP, WAMP, Node.js, MySQL setup, or Python packages are needed.

From this folder:

```sh
python3 run.py --open
```

Open **http://127.0.0.1:8080/app/forums**. Stop with **Ctrl+C** in the terminal.

On this Mac, a JDK has already been placed in the project's `.runtime/` directory. You can also double-click `start.command`. On Windows with Python and a JDK installed, double-click `start.bat` or run `py -3 run.py --open`.

The portable ZIP excludes the platform-specific JDK. If you extract it on another Mac or Linux computer without a JDK, run:

```sh
python3 run.py --setup-java --open
```

That command downloads a JDK into this project's `.runtime/` directory; it does not install system-wide software. Internet access is needed only for that optional JDK download. On Windows, install a JDK 21+ and set `JAVA_HOME` first. macOS execution was verified; Windows/Linux launch paths are supplied but were not executed here.

For another port:

```sh
python3 run.py --port 8765 --open
```

The live execution prepared for this delivery uses **http://127.0.0.1:8765/app/forums**.

## Demo accounts

| Role | Username | Password |
| --- | --- | --- |
| Admin | `admin` | `Admin@12345` |
| Verifier | `verifier` | `Verifier@12345` |
| Reader | `user` | `User@12345` |

These accounts and three sample forums are created on the first run of an empty database. The app binds to `127.0.0.1` for local evaluation.

## A five-minute demonstration

1. Sign in as `user`, open a forum, and post `This is a helpful discussion.` It appears publicly immediately.
2. Post `SPAM! Please review this example.` It is held privately, with warning 1 of 4.
3. Sign out and sign in as `verifier`. Open **Moderation**, read the held comment, and approve or reject it. Approved comments become public; rejected comments stay private.
4. As a reader, submit four flagged comments in total. The fourth blocks the account and ends its authenticated access. A comment containing several dictionary terms counts once.
5. As a verifier or admin, open **People** and use **Restore & reset**. The account is active again with zero warnings. Approving a comment alone does not reset warnings.
6. As `admin`, create a forum type and a forum with a PNG/JPEG cover, edit it, and create a verifier. As a verifier, remove that test forum.

The sample dictionary is intentionally small: `spam`, `scam`, `idiot`, `buy now`, and `free money`. As admin, open **Word dictionary** to edit the CSV in the browser, or edit `config/spam-words.csv` directly. A successful browser save keeps the previous CSV as `config/spam-words.csv.bak`. Changes take effect on the next submitted comment. Missing, empty, or malformed dictionary files stop comment acceptance with an error instead of silently bypassing filtering.

## Included features

- Unified sign-in with server-enforced admin, verifier, and reader permissions.
- Reader registration; profile editing including the report's address, city, state, gender, birth date, and mobile fields.
- Admin-created forum types; forum creation and editing; validated image uploads.
- Admin-only dictionary CSV editor with validation, atomic replacement, and a previous-version backup.
- Public forum browsing, searching, filtering, and published comments.
- CSV-based case-insensitive whole-word/phrase matching with Unicode normalization.
- Private pending/rejected history for the comment author; moderator review with a note.
- Automatic block on the fourth flagged submission; manual block, restore, and deactivation.
- Moderation and management audit log; persistent database; responsive desktop/mobile views.
- Password hashing with random salts, prepared SQL statements, escaped HTML, CSRF checks, session ID rotation, and bounded image uploads.

## Explicit choices where the report conflicts

The source report calls the stack PHP in one section but shows Java/JSP implementation code. This reconstruction uses **Java 21 + JSP + JDBC + Tomcat**, with **H2 embedded storage** instead of an external SQL service. Python only compiles and starts the Java application.

Clean comments publish immediately. Dictionary matches enter a `PENDING` queue; staff choose `PUBLISHED` or `REJECTED`. The fourth flagged submission blocks the account. Blocking retains the account rather than deleting it. Removing a forum archives it from public view and retains its history. The admin and verifier share review and reader-access duties; only admins update the CSV dictionary, create or edit forums, and add verifiers. The original CSV and assets were not supplied; sample content, demo accounts, and UI visuals are new.

Read [the document review](docs/DOCUMENT_REVIEW.md) for page references, errors, replacement wording, and limitations.

## Source layout

```text
src/main/java/blog/
  AppServlet.java       routes, authorization, transactions, uploads, moderation
  Db.java               JDBC operations and sample-data initialization
  Security.java         password hashing, tokens, output escaping
  SpamFilter.java       CSV parser and dictionary matcher
src/main/webapp/
  WEB-INF/view.jsp      server-rendered UI
  WEB-INF/web.xml       servlet, JSP compiler, session, and upload configuration
  assets/               local CSS and JavaScript; no CDN dependency
config/schema.sql       schema with keys, constraints, roles, strikes, and review state
config/spam-words.csv    editable filtering dictionary
lib/                    included H2 JDBC driver and license
vendor/                 included Tomcat distribution, license, and notices
run.py                  compile/test/start launcher
start.command           macOS launcher
start.bat               Windows launcher
scripts/package.py      creates a portable ZIP and a deployable WAR
scripts/export_db.py    exports a stopped database to SQL for inspection/backup
tests/                 Java core and HTTP integration checks
docs/                  review, execution record, and screenshots
```

## How requests and data flow

```text
Browser → Tomcat → AppServlet → JDBC → data/blog.mv.db
                         ↓
                 config/spam-words.csv
                         ↓
       clean → PUBLISHED | matched → PENDING → staff review
```

`GET /app/forums` and `/app/forum?id=...` are public. Management routes check the current database role on every request. All state changes require POST and a session CSRF token. A blocked or inactive account loses access even if its browser still has a session cookie. Flagged submissions lock the account row in a transaction before incrementing warnings; duplicate review requests cannot overwrite a completed decision.

`config/schema.sql` is the authoritative schema. The embedded H2 engine stores accounts, categories, forums (including normalized image bytes), comments, and audit history. The database is not exposed through a network console. Uploaded images are decoded, checked, and re-encoded as PNG.

## Verify it

Stop the running server before rebuilding or running core tests:

```sh
python3 run.py --test
```

Start the app in one terminal. In a second terminal, run:

```sh
python3 tests/integration_test.py --base-url http://127.0.0.1:8080
```

The integration suite creates uniquely named local demo fixtures. Use a disposable demo database for test runs; it deliberately exercises registration, reviews, blocks, uploads, and forum removal. It does not delete existing accounts. See `docs/EXECUTION.md` for the actual verification performed for this delivery.

## Inspect, back up, or reset data

Stop the server before copying or exporting the database. Back up the **entire `data/` folder**, and retain `config/` with it. To inspect readable SQL:

```sh
python3 scripts/export_db.py
```

This writes `data/backup.sql`, containing sensitive account and profile data, for local inspection. To begin a new demo, stop the server and **move `data/blog.mv.db` to a backup folder**, then restart. Initialization recreates the demo accounts and sample forums. Do not remove data while the server is running.

## Build and deploy

```sh
python3 run.py --build-only
python3 scripts/package.py
```

The package script writes `dist/spam-blocker.war` and a portable source/runtime ZIP in the parent outputs directory. The WAR targets Tomcat 10.1 / Jakarta Servlet 6.0; it is not compatible with Tomcat 7's `javax.servlet` APIs. For external deployment, set `BLOG_HOME` to the absolute project folder (which must contain `config/` and a writable `data/`). Deploy as `ROOT.war`: the application uses root-relative URLs. The included launcher configures this automatically.

The local runtime selects `Asia/Kolkata` for displayed server timestamps. Change that JVM option in `run.py` if required.

## Scope and limitations

This is an executed local mini project, not a public production deployment. A dictionary is a transparent heuristic, not a reliable detector of all spam, abuse, or intent. Obfuscations such as inserted letters, homographs, or other languages can evade it; acceptable uses can also be flagged. Approval does not automatically reverse a warning, and existing comments are not rescanned when the CSV changes.

The report's contact screenshot has no documented backend. This version provides support guidance; it does not pretend to send mail. SMS, password recovery, notifications, ML classification, cloud deployment, and public sign-up hardening are outside the reconstructed scope. For an actual internet service, replace demo credentials, configure HTTPS/secure cookies, add account recovery and shared rate limiting, and establish a moderation/privacy policy. Login throttling here is a basic in-process, per-IP limit suitable for the local demo.
