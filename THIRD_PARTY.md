# Bundled third-party software

- **Apache Tomcat 10.1.61:** unmodified upstream distribution in `vendor/apache-tomcat-10.1.61/`. Its `LICENSE`, `NOTICE`, and component notices are retained. Source: https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.61/bin/ . The downloaded archive was checked against Apache's published SHA-512 checksum.
- **H2 Database Engine 2.5.252:** JDBC driver in `lib/h2-2.5.252.jar`. Dual-licensed under MPL 2.0 or EPL 1.0; upstream text is in `lib/H2-LICENSE.txt`. Source: https://repo.maven.apache.org/maven2/com/h2database/h2/2.5.252/ .
- **JDK:** the platform-specific JDK in the local `.runtime/` folder is excluded from the portable ZIP. Its own upstream license files are retained locally. The optional `--setup-java` launcher downloads a Temurin JDK directly from Adoptium into the project folder.

Dependency download URLs and SHA-256 hashes are recorded in `docs/DEPENDENCIES.json`. Screenshots, sample content, CSS illustrations, and demo credentials were created for this reconstruction; the original report's images and personal details are not redistributed in the project ZIP.
