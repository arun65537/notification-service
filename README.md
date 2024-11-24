# Notification Service

Dedicated email notification service for OrderNest.

This service exposes a REST endpoint that sends emails using the Resend API.

## Run
```bash
./gradlew bootRun
```

Runs by default on `http://localhost:8091`.

## Configuration
This project loads extra config from:
- `./etc/secrets/config.properties`

Sample file:
```properties
RESEND_API_KEY=re_xxxxxxxxx
NOTIFICATION_FROM_EMAIL=onboarding@resend.dev
NOTIFICATION_FROM_NAME=OrderNest Notification
```

Replace `re_xxxxxxxxx` with your real Resend API key.

## API
### Send email
`POST /notifications/email`

Request body:
```json
{
  "to": "user@example.com",
  "subject": "Verify your email",
  "body": "<p>Click this link to verify...</p>"
}
```

Example `curl`:
```bash
curl --location 'http://localhost:8091/notifications/email' \
--header 'Content-Type: application/json' \
--data-raw '{
  "to": "user@example.com",
  "subject": "Hello from OrderNest",
  "body": "<p>Your notification service is working.</p>"
}'
```

## Postman
Import:
- `postman/notification-service.postman_collection.json`

## Environment variables
- `RESEND_API_KEY` (required, replace `re_xxxxxxxxx` with your real key)
- `NOTIFICATION_FROM_EMAIL`, `NOTIFICATION_FROM_NAME`

## Health endpoint
- `GET /actuator/health`

## GitHub Actions (Render deploy)
This repo includes:
- `.github/workflows/render-deploy.yml`

To enable deployment from GitHub Actions, add this repository secret:
- `RENDER_DEPLOY_HOOK_URL` = your Render deploy hook URL
