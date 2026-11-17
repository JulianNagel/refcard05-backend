# RefCard05 – Backend (Spring Boot)

Backend der RefCard05 Full-Stack-Anwendung auf AWS: Spring Boot + Amazon RDS (MariaDB) + Amazon S3, ausgeliefert als Container über Amazon ECR und Amazon ECS (Fargate).

Frontend-Repo: [refcard05-frontend](../../../refcard05-frontend)

> **Mitwirkende:** Offene Punkte stehen in [TODO.md](TODO.md).

> **Template:** Der Spring-Boot-Code (Vorgabe: [RefCard-03-be-SpringBootBusinessTrips v1-start](https://github.com/bbwlc/RefCard-03-be-SpringBootBusinessTrips/tree/v1-start) inkl. Bilder-Endpoint aus RefCard04) wird noch hinzugefügt. Solange kein `pom.xml` existiert, überspringt die Pipeline Build und Deploy.

## Struktur

| Pfad | Zweck |
|---|---|
| `.github/workflows/ci-cd.yml` | Pipeline: build → test → Image nach ECR → ECS-Redeploy |
| `Dockerfile` | Multi-Stage: Maven-Build → schlankes JRE-Image (non-root, Healthcheck) |
| `src/main/resources/application-aws.properties` | Profil `aws`: RDS + S3, alles über Umgebungsvariablen |
| `src/main/resources/application-local.properties` | Profil `local`: Docker Compose, lokales Volume |
| `aws/task-definition.example.json` | Beispiel-Task-Definition (Fargate, Healthcheck, Secrets) |
| `aws/s3-least-privilege-policy.json` | IAM-Policy, beschränkt auf genau einen Bucket |
| `compose/docker-compose.yml` | Lokale Entwicklung: Frontend + Backend + MariaDB |

## CI/CD-Pipeline

1. **check** – überspringt alles, solange noch kein Code im Repo ist
2. **build-test** – `mvn verify` mit Maven-Cache, Test-Reports als Artefakt
3. **image** – Login in ECR, Build & Push mit Docker-Layer-Cache
4. **deploy** – aktuelle Task-Definition holen, neues Image eintragen, Service updaten, auf Stabilität warten (→ automatisches Redeploy)

Pull Requests laufen nur bis *build-test*.

### GitHub Secrets (Settings → Secrets and variables → Actions)

| Secret | Quelle |
|---|---|
| `AWS_ACCESS_KEY_ID` | Learner Lab → *AWS Details* → *AWS CLI* |
| `AWS_SECRET_ACCESS_KEY` | dito |
| `AWS_SESSION_TOKEN` | dito |

### GitHub Variables

| Variable | Beispiel |
|---|---|
| `AWS_REGION` | `us-east-1` |
| `ECR_REPOSITORY` | `refcard05-backend` |
| `ECS_CLUSTER` | `refcard05-cluster` |
| `ECS_SERVICE` | `refcard05-backend-service` (leer → kein Deploy) |
| `ECS_TASK_FAMILY` | `refcard05-backend` |
| `CONTAINER_NAME` | `backend` |

## Versionierung (Git-Tags → ECR)

SemVer-Tags auf `main`:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

| Ereignis | Image-Tags in ECR |
|---|---|
| Push auf `main` | `latest`, `sha-<7 Zeichen>` |
| Tag `v1.2.3` | `1.2.3`, `1.2`, `sha-<7 Zeichen>` |

Frontend und Backend verwenden dasselbe Schema, damit zusammengehörige Releases über beide Repos nachvollziehbar sind.

## RDS (MariaDB)

1. RDS → *Create database* → MariaDB, Free tier, Region `us-east-1`, DB-Name `refcard`
2. Security Group: Port 3306 nur von der Security Group des Backend-Service erlauben
3. Verbindungsdaten als SSM-Parameter (SecureString) ablegen: `/refcard05/DB_HOST`, `/refcard05/DB_USER`, `/refcard05/DB_PASSWORD`
4. Die Task-Definition referenziert sie unter `secrets` – keine Zugangsdaten im Code oder Image

## S3 (Bilder-Storage) & IAM

1. Bucket erstellen (z. B. `refcard05-images-<name>`), *Block public access* aktiv lassen
2. `aws/s3-least-privilege-policy.json`: `REPLACE_BUCKET_NAME` ersetzen – erlaubt nur List/Get/Put/Delete auf diesem einen Bucket
3. Task-Rolle des Backends mit dieser Policy verknüpfen; das AWS SDK holt sich die Credentials automatisch aus der Task-Rolle

**Learner-Lab-Einschränkung:** `iam:CreateRole` ist gesperrt, es steht nur `LabRole` zur Verfügung (deutlich zu weit gefasst). In einem produktiven Account würde eine eigene Task-Rolle mit genau obiger Policy angelegt. Fehlende Rechte zeigen sich als `AccessDenied` (HTTP 403) im Log – das Backend sollte diesen Fall abfangen und mit einer verständlichen Fehlermeldung antworten.

## ECS-Deployment

1. ECR-Repository `refcard05-backend` erstellen
2. Cluster (Fargate) erstellen
3. Task-Definition aus `aws/task-definition.example.json` registrieren (Platzhalter ersetzen)
4. Service erstellen (1 Task, öffentliche IP oder Load Balancer, Port 8080)
5. Ab dann deployt jeder Push auf `main` / jeder Tag automatisch

Healthcheck: `/actuator/health` (benötigt `spring-boot-starter-actuator`).

## Learner-Lab-Zugangsdaten

Die Zugangsdaten sind temporär und laufen mit der Lab-Session ab (ca. 4 h).

**Vorgehen bei neuer Session:**
1. Learner Lab starten → *AWS Details* → *Show* bei *AWS CLI*
2. Die drei Werte in beiden Repos (Frontend + Backend) als Secrets aktualisieren, z. B.:
   ```bash
   gh secret set AWS_ACCESS_KEY_ID     -R <user>/refcard05-backend
   gh secret set AWS_SECRET_ACCESS_KEY -R <user>/refcard05-backend
   gh secret set AWS_SESSION_TOKEN     -R <user>/refcard05-backend
   ```
3. Fehlgeschlagenen Workflow neu starten

**Vergleich OIDC:** In einem produktiven Account würde GitHub über OIDC eine IAM-Rolle (`role-to-assume`) übernehmen. Dann gibt es keine langlebigen oder manuell kopierten Secrets, die Credentials gelten nur für den einzelnen Workflow-Lauf und die Rolle kann auf Repo/Branch eingeschränkt werden. Im Learner Lab geht das nicht, weil keine Identity Provider und Rollen angelegt werden dürfen.

## Lokale Entwicklung

Beide Repos nebeneinander klonen:

```bash
git clone <url>/refcard05-backend
git clone <url>/refcard05-frontend
cd refcard05-backend/compose
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| MariaDB | localhost:3306 (`refcard` / `refcard`) |

Das Backend startet erst, wenn MariaDB healthy ist. Bilder landen lokal im Volume `images` statt in S3.

## Nachweise (Screenshots)

<!-- Screenshots unter docs/screenshots/ ablegen und hier verlinken -->

- [ ] Commits / Git-Tags
- [ ] Erfolgreicher Workflow-Lauf
- [ ] ECR-Repository mit getaggten Images
- [ ] ECS-Service (laufender Task, Deployments)
- [ ] S3-Bucket mit hochgeladenen Bildern
- [ ] RDS-Instanz
