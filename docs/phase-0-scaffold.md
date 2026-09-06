# Phase 0 - Project Scaffold

## Goal
Create a Java 21 Spring Boot project with Gradle and default simulator mode.

## Files
- build.gradle
- settings.gradle
- src/main/java/com/aigrama/papermoney/Application.java
- src/main/resources/application.yml
- src/main/resources/application-postgres.yml

## Commands
```bash
./gradlew --version
./gradlew bootRun
```

## Expected Output
- Spring Boot app starts on port 8080
- Active mode is simulator (`paperstock.mode=simulator`)
- H2 console available at `/h2-console`

## Verification Checklist
- [ ] Java toolchain is set to 21
- [ ] Boot app starts with default profile
- [ ] Swagger UI available at `/swagger-ui.html`
