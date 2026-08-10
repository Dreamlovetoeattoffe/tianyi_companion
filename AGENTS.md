# Project conventions

## Build / packaging

- After finishing code changes, always run `.\gradlew.bat build` and produce the jar under `build\libs\`.

## Versioning

- Bump `mod_version` in `gradle.properties` after each change batch.
- Rule: increment the patch digit within `0.5.x` (0.5.0 -> 0.5.1 -> 0.5.2 ...).
- When the patch digit reaches 11, the next version becomes `0.6.0` (i.e. 0.5.11 -> 0.6.0), then continue 0.6.1, 0.6.2, ...
