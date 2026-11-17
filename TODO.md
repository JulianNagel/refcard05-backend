# TODO – Backend

Offene Punkte für Setup & Konfiguration (Code nicht aufgeführt).

## AWS (Learner Lab, `us-east-1`)

- [ ] ECR-Repository `refcard05-backend`
- [ ] RDS MariaDB, DB-Name `refcard`; Security Group: 3306 nur vom Backend-Service
- [ ] SSM-Parameter (SecureString): `/refcard05/DB_HOST`, `/refcard05/DB_USER`, `/refcard05/DB_PASSWORD`
- [ ] S3-Bucket (z. B. `refcard05-images-<name>`), Public Access blockiert
- [ ] Bucket-Name in `aws/s3-least-privilege-policy.json` eintragen (Learner Lab: `LabRole` verwenden, Policy dokumentieren)
- [ ] ECS-Cluster (Fargate)
- [ ] Task-Definition aus `aws/task-definition.example.json` registrieren (`REPLACE_*` ersetzen)
- [ ] ECS-Service `refcard05-backend-service`, Port 8080 offen

## GitHub Secrets

Aus Learner Lab → *AWS Details* → *AWS CLI*. **Laufen pro Session ab – bei jeder neuen Session aktualisieren.**

- [ ] `AWS_ACCESS_KEY_ID`
- [ ] `AWS_SECRET_ACCESS_KEY`
- [ ] `AWS_SESSION_TOKEN`

## GitHub Variables

- [ ] `AWS_REGION` = `us-east-1`
- [ ] `ECR_REPOSITORY` = `refcard05-backend`
- [ ] `ECS_CLUSTER`
- [ ] `ECS_SERVICE` (leer → kein Deploy)
- [ ] `ECS_TASK_FAMILY` = `refcard05-backend`
- [ ] `CONTAINER_NAME` = `backend`

## Template erwartet

- `/actuator/health` (Healthcheck in Dockerfile und Task-Definition)
- Env-Variablen `DB_*`, `S3_BUCKET`, `AWS_REGION` (siehe `application-aws.properties`)

## Abschluss

- [ ] Release taggen (`v1.0.0`) → Image in ECR, ECS-Task neu gestartet
- [ ] Backend-URL als `VITE_API_BASE_URL` im Frontend-Repo setzen
- [ ] Screenshots (Commits, Workflow, ECR, ECS, S3, RDS) ins README
