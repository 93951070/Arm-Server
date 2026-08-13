# Admin 500 Fix (2026-08-13)

## Root cause
The previous manually rebuilt JAR was compiled without Java `-parameters`. `AdminController` used Spring MVC parameters such as `@RequestParam String username` without an explicit request parameter name. Spring Framework 5.3 therefore could not resolve the name at runtime and threw:

`IllegalArgumentException: Name for argument of type [java.lang.String] not specified, and parameter name information not found in class file either.`

## Fix
1. Every `@RequestParam` in `AdminController` now declares an explicit parameter name.
2. Root `build.gradle` adds `-parameters` to every `JavaCompile` task as a second layer of protection.
3. All base (95) and root (20) Java sources were recompiled with Java 11 target + `-parameters`.
4. Final fat JAR keeps Jedis 3.8.0 and all previous Redis/RLock/Socket fixes.

## Runtime verification
Using the final JAR locally:
- `POST /admin/login` with admin/admin123 -> HTTP 302
- authenticated `GET /admin/dashboard` -> HTTP 200
- `GET /admin/users?page=0&size=20` -> HTTP 200
- user/card/notice/version/cache POST endpoints resolve request parameters without the prior exception
- no `parameter name information not found` exception occurs
- signed/encrypted Netty protocol requests still return valid `Armadillo` framed RSA responses

The local container intentionally has no MySQL server, so dashboard shows its handled database error message instead of crashing. On a server with the configured `kang` database, the same route continues to query the real database.
