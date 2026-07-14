# PostgreSQL + pgvector decision PoC

This isolated project tests the G2 database decision without creating a second
production source of truth. It covers Flyway empty/upgrade/repeat migration,
MyBatis CRUD and batch behavior, JSONB/array/time handling, transaction rollback,
and consent-bound vector retrieval at 10k and 100k memory rows.

The tests start the pinned PostgreSQL 16 + pgvector 0.8.1 image through
Testcontainers. No host database or credentials are required.

```powershell
$env:JAVA_HOME = 'C:\Users\dengb\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64'
..\..\mvnw.cmd -f pom.xml verify
..\..\mvnw.cmd -f pom.xml -Pbenchmark verify
```

The Compose service is available for manual SQL inspection only:

```powershell
docker compose up -d --wait
docker compose down -v
```

The benchmark hard gates are deliberately local and conservative: 10k p95 must
be at most 100 ms and 100k p95 at most 150 ms after warm-up. Every returned row
must satisfy `user_id`, consent scope, active status, and retention predicates in
the SQL query itself; application-side post-filtering is prohibited.
