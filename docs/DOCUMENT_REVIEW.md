# Review of the supplied mini project report

Reviewed 18 November 2026. Source: **Mini Project Doc.pdf**, 67 PDF pages, project **Spam Blocker for Blogs**, academic year 2021–22. This review distinguishes errors visible in the document from choices made for this reconstruction. The lost source code, original CSV, original assets, live database, and original test results were not available.

References below give the **PDF viewer page first**, followed by the printed report page where present. Front matter and blank separator pages account for the difference.

## Main finding

The project idea is implementable, and the report contains enough workflow detail for a functional reconstruction. It is not a complete, internally consistent implementation specification. The most significant issues concern the technology stack, the fate of flagged comments, the database schema, and account blocking. The included project makes those decisions explicit and runs as Java/JSP.

## Corrections needed

### 1. Pick one backend and one database engine

**Where:** PDF p.9 (abstract); p.16 / printed p.4 (software requirements); p.21 / printed p.8 (Fig. 3.1); pp.35–40 / printed pp.20–24 (technologies and pseudocode); pp.52–60 (screenshots); p.66 / printed p.43 (references).

The abstract and requirements name PHP/WAMP. The implementation describes Java/JSP, shows `.jsp` pages and a Tomcat file path, and the screenshots show port 8080. The architecture diagram labels the database “SQL SERVER.” The resource list calls “PHP MyAdmin” the database, while the table notation includes MySQL-style `auto_increment`. These details do not identify one consistent stack. phpMyAdmin is a database administration interface, not the database engine.

**Suggested correction for the reconstructed version:** “Backend: Java 21 with Jakarta Servlets and JSP; server: Apache Tomcat 10.1; persistence: JDBC with embedded H2; frontend: HTML, CSS, JavaScript.” Replace the PHP keywords with Java, JSP, JDBC, CSV, comment moderation. Replace “SQL SERVER” in the architecture figure with the actual chosen database. If describing the historical system, confirm the original engine before naming MySQL, MariaDB, or SQL Server.

### 2. State exactly what happens to each comment

**Where:** PDF p.9 (abstract); p.11 / printed p.1; pp.37–38 / printed pp.21–22; pp.48–49 / printed pp.30–31.

The abstract says flagged comments are stored and later checked by an authorized user. The pseudocode instead shows a warning and inserts only comments with no dictionary match. The test cases say “Comment sent to admin” for clean comments but define no approval state or review process. Those are different behaviors.

**Suggested correction:** Use one state machine: clean submission → `PUBLISHED`; dictionary match → `PENDING`; authorized reviewer → `PUBLISHED` or `REJECTED`. Pending/rejected comments are visible only to the author and staff. This is the rule implemented in the reconstruction. If the desired project is rejection-only, revise the abstract to remove the queue/verification claim instead.

### 3. Move the enforcement claim to the server

**Where:** PDF p.11 / printed p.1; pp.37–40 / printed pp.21–24.

The introduction says comment validity is determined “on the client side,” but the shown JSP, file reads, session access, and JDBC operations execute on the server. Browser-only checks can be bypassed.

**Suggested correction:** “The server validates and filters every submitted comment. Browser validation improves usability but does not authorize publication.” Require an authenticated, active reader and a valid forum ID; reject blank or overlong input server-side.

### 4. Fix CSV matching and parsing

**Where:** PDF pp.37–38 / printed pp.21–22.

`for (int i=0; i<s.length-1; i++)` ignores the final token of each CSV row. A row containing a single term is skipped entirely. `String.equals` is case-sensitive, and splitting comments only on whitespace misses punctuation such as `spam!`. A plain comma split also fails for quoted CSV fields. The original dictionary file is not included, so its entries cannot be recovered.

**Suggested correction:** Parse all CSV cells, normalize case and Unicode, and match complete tokens/phrases after punctuation normalization. Document the tokenization rule and limitations. The reconstruction handles quoted cells, escaped quotes, a byte-order mark, and all entries including the last one. It uses a new illustrative dictionary, not a claimed copy of the original.

### 5. Define the strike threshold and do not delete an account to block it

**Where:** PDF pp.38–39 / printed pp.22–23; p.48 / printed p.30; p.59 / printed p.39.

The code increments an existing counter while `count <= 2`, then deletes the user when a later match occurs. Under a zero-initialized counter this blocks on the fourth matching event. Table 5.4 says “more than 3 times,” which supports four flagged submissions, but the counter is inside nested word-matching loops. One comment containing several matches could therefore generate multiple strikes. No code for initializing a missing counter row is shown. Deleting a user is not equivalent to blocking access and can leave inconsistent references or historical data.

**Suggested correction:** “Each flagged comment adds exactly one strike. At four cumulative strikes, set `blocked=true`, deny authenticated access, and preserve the account and comment history. A moderator may restore the account and reset strikes. Approving a comment alone does not reset strikes.” Use a transaction and row locking to avoid lost updates under concurrent submissions. The chosen number is a reconstruction decision supported by the table; the original implementation cannot be verified.

### 6. Complete the database design

**Where:** PDF pp.30–32 / printed pp.17–19; pp.38–39 / printed pp.22–23.

The schema omits the `usercount` table referenced by the pseudocode. A verifier entity appears in the diagrams but has no database definition. Most tables lack primary keys, unique constraints, or foreign keys. Comments refer to a forum by its name, which is ambiguous when titles change or repeat. There are no fields for review status, reviewer, strike count, active/blocked state, or review history. `ddate` is not clearly defined and is stored as text.

**Suggested correction:** Define account IDs and roles; stable forum IDs; category IDs; comment IDs, author/forum references, status, match reason, reviewer, and timestamps; strike/block state; and audit events. Store dates/timestamps using suitable types and define each field's meaning. The new `config/schema.sql` provides keys, unique names/emails, foreign keys, checks, and indexes. Birth date is validated ISO date text in this local implementation; timestamps use SQL `TIMESTAMP`. Images are stored with their forum instead of joining by file name.

### 7. Store password hashes and escape comment output

**Where:** PDF pp.31–32 / printed pp.18–19; pp.39–40 / printed pp.23–24; p.14 / printed p.2.

The design lists a `pwd varchar(40)` field without any password-hashing specification; it should not imply plaintext password storage. JSP expressions output comments directly into HTML without visible escaping, creating a stored-script injection risk. The report's “secure login” statement lacks implementation requirements for authorization, sessions, form forgery protection, or blocked sessions. The excerpt does use prepared statements for several queries, which is a useful practice to retain.

**Suggested correction:** Specify salted password hashes in a sufficiently long field; escaped output; prepared statements; per-request role checks; CSRF-protected state changes; session renewal at login; session expiry; and rechecking account status on requests. For a public deployment add HTTPS and secure session cookies. The reconstruction implements these local application controls; its default transport remains loopback HTTP.

### 8. Resolve the password-validation contradiction

**Where:** PDF p.49 / printed p.31, Table 5.6.

The rule says “at least 10 characters” while its error message says “only ten characters.” A minimum and an exact length are different constraints. Testing an email merely for `@` and `.` is also insufficient as a full validation specification.

**Suggested correction:** “Password must contain at least 10 characters”; use that same message. The reconstruction accepts 10–128 characters and does not trim password content. Phone is optional but must contain exactly 10 ASCII digits when provided, matching the report's local-format assumption. Email receives a practical syntax check and uniqueness constraint; mailbox ownership verification is outside this demo.

### 9. Align role permissions in prose, diagrams, and tests

**Where:** PDF p.14 / printed p.2; pp.24–29 / printed pp.11–16; screenshots pp.52–61.

The admin process paragraph includes verifier duties and repeats subsection number 2.1.2 for the user process. The text says the admin adds verifiers, but it does not specify how verifier records or authorization are stored. Reviewing flagged comments is central to the abstract but is not fully specified as a use case or sequence.

**Suggested correction:** Add a role-permission matrix and the review flow to the diagrams. Use 2.1.1 Admin, 2.1.2 Verifier, 2.1.3 User. The reconstruction grants both staff roles review and reader-access duties; admins alone update the CSV dictionary (Fig. 3.2), add verifiers, and create/edit forums; both staff roles can remove forums. “Delete user” is implemented as deactivation; “delete forum” is an archive from public view. Make those retention semantics explicit.

### 10. Replace hard-coded paths and incomplete code excerpts

**Where:** PDF pp.37–40 / printed pp.21–24.

The code contains an absolute Windows path, malformed/incomplete JSP directives and imports, missing spaces or delimiters, and excerpts with unmatched or omitted structural elements. It assumes a matching upload row exists, calls `rs1.next()` without checking its result, and does not show resource cleanup. JavaScript alerts and redirects do not replace server-side transaction and control-flow handling. These snippets are not a complete compilable project.

**Suggested correction:** Label the snippets as illustrative fragments or replace them with an actual repository reference and launch instructions. Use relative/configurable paths and try-with-resources. Separate database and filter logic from presentation. Use server redirects after successful POSTs. The included reconstruction does this; it uses an environment-configured project home and a portable CSV path.

### 11. Make testing reproducible and measurable

**Where:** PDF pp.43–49 / printed pp.25–31.

The tables report “Successful” but provide no dataset, build identifier, environment, reproducible steps, or retained execution evidence. “Enter all fields” is not a concrete input. The invalid registration row's expected and actual messages differ. There are no specific tests for final CSV entries, case/punctuation, false positives, pending visibility, moderation decisions, duplicate reviews, four-warning boundaries, concurrent updates, role bypass, escaping, or image validation. Non-functional claims such as high responsiveness and scalability have no measurable targets.

**Suggested correction:** Record real inputs, preconditions, steps, expected status/state, actual result, environment, and build date. Add negative and boundary tests and distinguish observed results from performance claims. This delivery includes executable core and HTTP tests plus browser screenshots; it does not claim a load test or independent third-party acceptance testing.

### 12. Correct terminology, scope, numbering, and presentation

**Where:** PDF p.9 (abstract); p.11 / printed p.1; pp.31–32 / printed pp.18–19; p.35 / printed p.20; p.64 / printed p.42; contents and list pages.

- “spam comments ... in the bugs” should say “in blogs”; “Spam is a blog” should describe spam **on** blogs.
- The conclusion calls the project “Spam Blogger for Blogs” instead of the title “Spam Blocker for Blogs.”
- The database tables are numbered 4.6.x within Section 3.7. Use consistent 3.7.x labels and include those tables in the list of tables.
- Use consistent “Figure”/“Screenshot” captions, section numbering, and figure/table cross-references. Several PDF pages are blank or separator-only; omit unnecessary blank pages in the final export.
- The implementation's HTML explanation is historical; avoid presenting modern HTML as an SGML application, and avoid implying JavaScript is a variant of Java.
- “This application can be extended into internet network” conflicts with earlier descriptions of internet-hosted blogs. Say “deploy the local prototype as an internet-accessible service,” if that is intended.
- The future-scope reference to product-update SMS and mail is not grounded in this project's stated requirements. Replace it with moderation notifications, appeal/recovery workflows, language support, better detection, or mobile-friendly access.
- Revise vague resource claims (such as a mandatory 500 GB disk) into measured storage/runtime needs. A mini project should state the tested environment rather than assert unnecessary hardware requirements.
- Check bibliography author names, book titles, editions, and publication years against actual editions. Most references concern Java while the resource table says PHP. Do not claim corrected bibliographic metadata without verifying it.
- Replace “eradication” or general “quality” guarantees with a narrower description of dictionary-based detection and human review; include false positives and false negatives.

## Replacement text for the rebuilt project's report

### Abstract

Spam Blocker for Blogs is a Java web application that supports moderated discussions through administrator, verifier, and reader roles. Readers register, browse forums, and submit comments. The server validates each comment and compares normalized whole words and phrases against a configurable CSV dictionary. Comments without a dictionary match are published immediately. Flagged comments enter a private review queue, where an administrator or verifier can approve or reject them. Each flagged submission adds one warning; the fourth blocks the reader account until a moderator restores it. Accounts and moderation history are retained. The application uses Java, JSP, JDBC, Apache Tomcat, and an embedded H2 database. Dictionary matching provides a simple moderation signal and does not guarantee detection of every form of spam or abusive content.

**Keywords:** Java, JSP, JDBC, CSV dictionary, comment moderation, role-based access.

### Software requirements

JDK 21 or later; Python 3.9 or later for the local launcher; a modern web browser. Apache Tomcat 10.1.61 and H2 2.5.252 are bundled. The server, database files, and uploaded image data run locally. PHP/WAMP/phpMyAdmin are not required for this implementation.

### Comment and access rules

1. Only authenticated, active, unblocked readers may submit comments to active forums.
2. A comment must contain non-whitespace content and have at most 3,000 characters.
3. The dictionary is read server-side. Matching normalizes Unicode and case, and treats punctuation as word separators. Whole words and contiguous phrases are compared; substrings inside a longer word do not match.
4. No match: publish immediately. One or more matches: store as pending, add one warning, and show the author a review message.
5. At four cumulative flagged submissions, set the account as blocked and deny authenticated access.
6. Admins and verifiers may approve or reject pending comments. Approval publishes the comment. Rejection retains a private record. A completed decision cannot be overwritten by resubmitting the original review form.
7. Moderators can block, deactivate, or restore readers. Restore reactivates the account and resets warnings. Comment approval alone does not change warnings.
8. Staff actions are recorded. Reader-facing views never expose another reader's pending/rejected comments.

### Conclusion and future work

The rebuilt application demonstrates role-controlled forums, configurable dictionary filtering, private review, and account controls. Automated checks and browser execution evidence accompany the source. Future work may include appeals and warning reversal policies, richer contextual detection, multilingual matching, optional notifications, and measured performance/accessibility testing. Public deployment requires additional operational controls and is outside this local reconstruction.

## What remains an assumption

The original database engine, full CSV vocabulary, credentials, original image files, counter initialization logic, and intended staff approval behavior cannot be recovered from this PDF alone. The new interface reproduces the workflows rather than the old stop-sign visual theme. The thresholds, state machine, retention rules, sample content, and embedded database are explicitly documented decisions. Do not present this reconstruction or its new test evidence as the unchanged 2021–22 implementation.

## External technical references for the reconstruction

- [Apache Tomcat 10 downloads and Jakarta namespace migration](https://tomcat.apache.org/download-10.cgi)
- [Tomcat version and specification compatibility](https://tomcat.apache.org/whichversion.html)
- [H2 Database Engine and JDBC/embedded features](https://www.h2database.com/html/main.html)

The document review itself is based on the supplied PDF; the links above support the modern runtime choices.
