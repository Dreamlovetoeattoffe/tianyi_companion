# Project conventions

## Build / packaging

- After finishing code changes, always run `.\gradlew.bat build` and produce the jar under `build\libs\`.

## Versioning

- Bump `mod_version` in `gradle.properties` after each change batch.
- Rule: increment the patch digit, then continue 0.6.1, 0.6.2, ... up to 0.6.10.
- When the patch digit is 10 and another version is needed, the next version becomes `0.7.0` (i.e. 0.6.10 -> 0.7.0), then continue 0.7.1, 0.7.2, ... up to 0.7.10, then 0.8.0, and so on.
