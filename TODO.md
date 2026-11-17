# TODO – Backend (für Mitwirkende)

Dieses Repo ist ein **Template**. Pipeline, Dockerfile, Compose und Doku-Gerüst sind fertig – folgendes fehlt noch. Bitte erledigte Punkte abhaken (`[x]`) und mitcommitten.

Aufgabe & Bewertungsraster: RefCard05 (Fullstack auf AWS: ECR, ECS, RDS, S3).

## 1. Code einbringen

- [ ] Spring-Boot-Projekt einfügen (Vorgabe: [RefCard-03-be-SpringBootBusinessTrips v1-start](https://github.com/bbwlc/RefCard-03-be-SpringBootBusinessTrips/tree/v1-start) inkl. Bilder-Endpoint aus RefCard04)
- [ ] `pom.xml` liegt im Repo-Root (sonst überspringt die Pipeline den Build)
- [ ] Java 21 (Pipeline und Dockerfile nutzen Temurin 21) – sonst Version in `ci-cd.yml` und `Dockerfile` anpassen
- [ ] Bestehende `src/main/resources/application-aws.properties` / `application-local.properties` **nicht überschreiben**, sondern übernehmen bzw. mergen

## 2. Abhängigkeiten im `pom.xml`

- [ ] `spring-boot-starter-actuator` – für den Healthcheck `/actuator/health` (Dockerfile + ECS)
- [ ] `org.mariadb.jdbc:mariadb-java-client`
- [ ] AWS SDK v2 S3 (`software.amazon.awssdk:s3`) oder Spring Cloud AWS S3
- [ ] Tests laufen mit `mvn verify` grün (z. B. H2 oder Testcontainers für Tests, keine echte DB nötig)

## 3. Bilder-Storage auf S3 umstellen

- [ ] Storage-Service aus RefCard04 so umbauen, dass er je nach `storage.type` (`local` / `s3`) lokal oder in S3 speichert
- [ ] Bucket/Region aus `storage.s3.bucket` / `storage.s3.region` lesen (kommen aus `S3_BUCKET` / `AWS_REGION`)
- [ ] **Keine Access Keys im Code** – S3-Client mit Default Credentials Provider erstellen (holt sich die Task-Rolle automatisch)
- [ ] Fehlerfall abfangen: `AccessDenied` / fehlender Bucket → verständliche Fehlermeldung statt Stacktrace

## 4. AWS einrichten (Learner Lab, `us-east-1`)

- [ ] ECR-Repository `refcard05-backend`
- [ ] RDS MariaDB (Free tier), DB-Name `refcard`; Security Group: 3306 nur vom Backend-Service
- [ ] SSM-Parameter (SecureString): `/refcard05/DB_HOST`, `/refcard05/DB_USER`, `/refcard05/DB_PASSWORD`
- [ ] S3-Bucket (z. B. `refcard05-images-<name>`), Public Access blockiert
- [ ] `aws/s3-least-privilege-policy.json`: Bucket-Name eintragen (Learner Lab: kann nicht als eigene Rolle angelegt werden → `LabRole` verwenden und Policy in der Doku zeigen)
- [ ] ECS-Cluster (Fargate)
- [ ] Task-Definition aus `aws/task-definition.example.json` registrieren (alle `REPLACE_*` ersetzen)
- [ ] ECS-Service `refcard05-backend-service` (Port 8080 offen)

## 5. GitHub konfigurieren (Settings → Secrets and variables → Actions)

Secrets (Learner Lab → *AWS Details* → *AWS CLI*; laufen pro Session ab!):
- [ ] `AWS_ACCESS_KEY_ID`
- [ ] `AWS_SECRET_ACCESS_KEY`
- [ ] `AWS_SESSION_TOKEN`

Variables:
- [ ] `AWS_REGION` = `us-east-1`
- [ ] `ECR_REPOSITORY` = `refcard05-backend`
- [ ] `ECS_CLUSTER`
- [ ] `ECS_SERVICE` (solange leer, wird nicht deployt)
- [ ] `ECS_TASK_FAMILY` = `refcard05-backend`
- [ ] `CONTAINER_NAME` = `backend`

## 6. Release & Redeploy prüfen

- [ ] Push auf `main` → Workflow grün, Image `latest` + `sha-…` in ECR
- [ ] Ersten Release taggen: `git tag v1.0.0 && git push origin v1.0.0` → Image `1.0.0` in ECR
- [ ] ECS startet automatisch einen neuen Task, Healthcheck grün
- [ ] Backend-URL an Frontend weitergeben (Variable `VITE_API_BASE_URL` im Frontend-Repo)

## 7. Lokal testen

- [ ] Beide Repos nebeneinander klonen, `cd compose && docker compose up --build`
- [ ] Frontend http://localhost:3000 spricht mit Backend http://localhost:8080, Daten landen in MariaDB

## 8. Dokumentation (README.md)

- [ ] Screenshots unter `docs/screenshots/` ablegen und im README verlinken: Commits/Tags, Workflow-Lauf, ECR, ECS, S3-Bucket, RDS-Instanz
- [ ] Individuelle Lösung erkennbar (eigene Ressourcennamen, eigene Commits)
- [ ] Checkliste "Nachweise" im README abhaken

## Regeln

- Keine Zugangsdaten, Passwörter oder `.env`-Dateien committen
- Kleine, aussagekräftige Commits (zählt für "Nachvollziehbarkeit")
- Versionen nur über Git-Tags `vMAJOR.MINOR.PATCH`, gleiches Schema wie im Frontend
