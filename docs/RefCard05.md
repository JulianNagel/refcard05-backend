# RefCard05 – Fullstack auf AWS (ECR, ECS, RDS, S3)

Kurz gesagt: Frontend und Backend haben je ein eigenes Repo, eine eigene Pipeline, ein eigenes ECR-Repo und einen eigenen
ECS-Service. Die Datenbank läuft auf RDS, Mitarbeiterfotos landen in S3. Ein Load Balancer (ALB) bündelt alles unter einer URL.

- Backend: https://github.com/JulianNagel/refcard05-backend
- Frontend: https://github.com/JulianNagel/refcard05-frontend
- Compose: [`compose/`](../compose/docker-compose.yml) in diesem Repo
- App: http://biztrips-alb-1775505033.us-east-1.elb.amazonaws.com/

## So hängt alles zusammen

```
Browser ──> ALB biztrips-alb (Port 80)
              ├── /v1/*, /actuator/*  ──> ECS biztrips-backend-service  (Spring Boot, :8080)
              │                              ├── RDS MariaDB biztrips-db (nicht öffentlich)
              │                              └── S3 biztrips-photos-851725276744
              └── alles andere        ──> ECS biztrips-frontend-service (nginx, :80)
```

Warum ein ALB? Fargate-Tasks bekommen bei jedem Redeploy eine neue IP. Der ALB hat eine feste URL, und genau die wird beim
Frontend-Build als `VITE_API_BASE_URL` eingebaut. Da Frontend und Backend über dieselbe URL laufen, gibt es auch kein CORS-Problem.

Security Groups (nur das Nötigste ist offen):

| SG | Erlaubt |
|---|---|
| `biztrips-alb-sg` | Port 80 aus dem Internet |
| `biztrips-fe-sg` | Port 80 nur vom ALB |
| `biztrips-be-sg` | Port 8080 nur vom ALB |
| `biztrips-db-sg` | Port 3306 nur vom Backend |

---

## Aufgabe 1 – Zwei Repos, zwei Pipelines

Beide Repos haben `.github/workflows/deploy.yml` mit dem gleichen Ablauf:

| Ereignis | Was passiert |
|---|---|
| Push / PR auf `main` | `test` → `build` (kein Deploy) |
| Git-Tag `vX.Y.Z` pushen | `test` → `build` → Image nach ECR → ECS-Redeploy → Health-Check |

- **Frontend:** Multi-Stage-Dockerfile (Node baut mit Vite → nginx liefert `dist/` aus). `VITE_API_BASE_URL` kommt aus der GitHub-Variable gleichen Namens = ALB-URL.
- **Backend:** Multi-Stage-Dockerfile (JDK + Maven baut das Jar → schlankes `eclipse-temurin:21-jre-alpine`).

### Versionierung mit Git-Tags

Der Git-Tag bestimmt den Image-Tag in ECR. Beide Repos verwenden dasselbe SemVer-Schema:

```bash
git tag v1.0.0
git push origin v1.0.0
```

→ ECR bekommt `1.0.0`, `sha-<commit>` und `latest`. In der Task-Definition steht immer die exakte Version (`:1.0.0`), nicht `latest`,
damit man in ECS sieht, welche Version läuft, und gezielt zurückrollen kann.
`0.0.0` / `0.0.1` = einmalige Start-Images, lokal gepusht, damit die ECS-Services angelegt werden konnten (ab `v1.0.0` macht das die Pipeline).

📸 *Screenshot: ECR-Repos mit Tags `1.0.0` / `sha-…`*
📸 *Screenshot: Workflow-Runs (Push auf main = ohne Deploy, Tag = mit Deploy)*

---

## Aufgabe 2 – Backend an RDS und S3

### RDS (MariaDB)

- Instanz `biztrips-db` (db.t3.micro, MariaDB, nicht öffentlich erreichbar, DB `db_biztrips`, User `admin`).
- Das Passwort wurde zufällig generiert und liegt **nur** in AWS Secrets Manager (`biztrips-db-password`).
- Das Backend liest alles aus Umgebungsvariablen (`application-mariadb.properties`):

  ```properties
  spring.datasource.url=jdbc:mariadb://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:db_biztrips}?sslMode=${DB_SSL_MODE:disable}
  spring.datasource.username=${DB_USER:root}
  spring.datasource.password=${DB_PASSWORD}
  ```

- **Stolperstein TLS:** RDS-MariaDB erzwingt verschlüsselte Verbindungen (`require_secure_transport=ON`). Der erste Deploy scheiterte mit
  `Connections using insecure transport are prohibited`. Lösung: `DB_SSL_MODE=trust` in der Task-Definition (verschlüsselt), lokal bleibt es `disable`.

- In ECS setzt die Task-Definition `DB_HOST`, `DB_PORT`, `DB_NAME` und `DB_USER`. `DB_PASSWORD` kommt über den Block `secrets`
  direkt aus Secrets Manager in den Container. Im Code und im Repo steht kein Passwort.
- Nachweis: `http://<alb>/actuator/health` zeigt `"db": {"status": "UP", "database": "MariaDB"}`.

📸 *Screenshot: RDS-Instanz + `/actuator/health`*

### S3 (Mitarbeiterfotos, Fortsetzung RefCard04)

- Endpoints wie in EX-04:
  - `POST /v1/employees/{id}/photo` (multipart, Feld `file`, nur JPEG/PNG, max. 5 MB)
  - `GET /v1/employees/{id}/photo`
- `FileStorageService`: Ist `APP_STORAGE_S3_BUCKET` gesetzt, wird nach S3 geschrieben, sonst in einen lokalen Ordner (Tests, Compose).
  In der DB steht nur der Dateiname/S3-Key (UUID), nicht das Bild selbst.
- Bucket `biztrips-photos-851725276744`: Public Access komplett blockiert.
- Fehlerfälle: falscher Dateityp gibt `400`, fehlende S3-Rechte geben `502` mit AWS-Fehlercode statt eines Stacktraces.

```bash
curl -F "file=@foto.jpg;type=image/jpeg" http://<alb>/v1/employees/1/photo
curl http://<alb>/v1/employees/1/photo -o foto-download.jpg
aws s3 ls s3://biztrips-photos-851725276744/
```

### IAM Least Privilege – und warum es im Learner Lab anders läuft

Eigentlich würde man eine eigene Task-Rolle mit genau dieser Policy erstellen ([`s3-least-privilege-policy.json`](s3-least-privilege-policy.json)):
nur `s3:GetObject` und `s3:PutObject`, nur auf `biztrips-photos-851725276744/*`.

Im Learner Lab geht das nicht:

```
aws iam create-role --role-name biztrips-backend-s3-task-role ...
→ AccessDenied: ... is not authorized to perform: iam:CreateRole
```

Darum läuft der Backend-Task mit der vorgegebenen `LabRole` als `taskRoleArn`. Damit der Bucket trotzdem abgeschottet ist,
schränkt eine **Bucket-Policy** ([`s3-bucket-policy.json`](s3-bucket-policy.json)) den Zugriff auf Objekte ein.
Lesen und Schreiben dürfen nur `LabRole` (Backend) und `voclabs` (mein Lab-User für Kontrolle und Screenshots), alle anderen werden per `Deny` blockiert.

📸 *Screenshot: S3-Bucket mit hochgeladenem Foto + Bucket-Policy*

---

## Aufgabe 3 – ECS: zwei Services, Redeploy, Zugangsdaten

- Cluster `biztrips-cluster` mit zwei Fargate-Services:
  - `biztrips-frontend-service` (nginx, 0.25 vCPU / 512 MB)
  - `biztrips-backend-service` (Spring Boot, 0.5 vCPU / 1 GB)
- Health-Checks über die ALB-Target-Groups: Frontend `/`, Backend `/actuator/health`. Ein ungesunder Task wird ersetzt.
- **Deployment Circuit Breaker mit Rollback:** Startet eine neue Version nicht, rollt ECS automatisch auf die letzte funktionierende zurück.
- **Automatisches Redeploy:** Jede Pipeline rendert die Task-Definition mit dem neuen Image-Tag und deployt sie
  (`amazon-ecs-deploy-task-definition`, wartet auf Stabilität). Danach prüft ein Health-Check-Step per `curl` über den ALB,
  ob die neue Version wirklich läuft. Sonst wird der Workflow rot.

📸 *Screenshot: ECS-Cluster mit beiden Services + neue Task-Definition-Revision nach einem Tag*

### Zugangsdaten: Learner Lab statt OIDC

Im Learner Lab funktionieren OIDC und eigene IAM-Rollen nicht. Deshalb stehen die temporären Zugangsdaten aus
„AWS Details → AWS CLI“ als **GitHub Secrets** in beiden Repos:

| Secret / Variable | Wo | Wofür |
|---|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Secrets (beide Repos) | `aws-actions/configure-aws-credentials` |
| `VITE_API_BASE_URL` | Variable (Frontend) | ALB-URL, wird beim Build eingebaut |
| `APP_URL` | Variable (Backend) | ALB-URL für den Health-Check nach dem Deploy |
| DB-Passwort | AWS Secrets Manager | gelangt nur zur Laufzeit in den Container |

**Die Tokens laufen mit der Lab-Session ab (ca. 4 h).** Bei jeder neuen Session:

1. Learner Lab → **Start Lab** → **AWS Details** → **AWS CLI: Show**
2. Werte in `~/.aws/credentials` einfügen
3. Die drei Secrets in beiden Repos erneuern:

   ```bash
   for repo in JulianNagel/refcard05-backend JulianNagel/refcard05-frontend; do
     gh secret set AWS_ACCESS_KEY_ID     -R $repo -b "$(aws configure get aws_access_key_id)"
     gh secret set AWS_SECRET_ACCESS_KEY -R $repo -b "$(aws configure get aws_secret_access_key)"
     gh secret set AWS_SESSION_TOKEN     -R $repo -b "$(aws configure get aws_session_token)"
   done
   ```

Typischer Fehler bei abgelaufenen Secrets: `ExpiredToken` / `The security token included in the request is expired`.

**Vergleich mit OIDC (Best Practice in einem echten AWS-Account):**

| | Learner Lab (Access Keys) | OIDC |
|---|---|---|
| Was liegt in GitHub? | Echte (temporäre) Schlüssel | Nichts Geheimes, nur eine Rollen-ARN |
| Ablauf | Manuell erneuern alle ~4 h | GitHub holt pro Run ein kurzlebiges Token |
| Bei einem Leak | Schlüssel gültig bis Session-Ende | Token nur Minuten gültig, nur für dieses Repo/diesen Branch |
| Rechte | Alles, was der Lab-User darf | Eigene Rolle mit genau den nötigen Rechten |

---

## Aufgabe 4 – Lokal alles zusammen starten

Beide Repos nebeneinander klonen:

```
IdeaProjects/
├── RefCard-03-be-SpringBootBusinessTrips
└── refcard-03-fe-react-vite
```

Dann im Backend-Repo:

```bash
docker compose -f compose/docker-compose.yml up --build
```

| Was | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080/v1/trips |
| Health | http://localhost:8080/actuator/health |

- Startreihenfolge über Healthchecks: erst MariaDB, dann das Backend und erst wenn dieses `UP` ist, das Frontend.
- Lokal gibt es kein S3. Die Fotos landen im Docker-Volume `photos`, die DB-Daten im Volume `db-data`.
- Stoppen mit `docker compose -f compose/docker-compose.yml down` (`-v` löscht zusätzlich die Daten).

📸 *Screenshot: `docker compose ps` + Frontend auf localhost:5173*

---

## Aufräumen (Lab-Budget!)

RDS, ALB und Fargate kosten auch dann, wenn niemand die App benutzt. Nach der Bewertung:

```bash
aws ecs update-service --cluster biztrips-cluster --service biztrips-backend-service  --desired-count 0
aws ecs update-service --cluster biztrips-cluster --service biztrips-frontend-service --desired-count 0
aws rds stop-db-instance --db-instance-identifier biztrips-db   # startet nach 7 Tagen automatisch wieder
```

## Screenshot-Checkliste

- [ ] Commits (beide Repos) + Git-Tags
- [ ] Workflow-Runs grün, inkl. Health-Check-Step
- [ ] ECR: beide Repos mit Versions-Tags
- [ ] ECS: Cluster, beide Services „Running“, Task-Definition-Revisionen
- [ ] RDS-Instanz „Available“
- [ ] S3-Bucket mit Foto + Bucket-Policy
- [ ] App im Browser über die ALB-URL
- [ ] GitHub → Settings → Secrets (nur Namen sichtbar)
