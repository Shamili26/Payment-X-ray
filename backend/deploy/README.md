
# Logging & Linux Deployment

This document explains how application logs are saved to local files and how to
deploy the backend on a Linux server. The same build works on Windows (dev) and
Linux (prod) with no code changes.

---

## 1. Logging

Logging is configured in [`src/main/resources/logback-spring.xml`](../src/main/resources/logback-spring.xml).

### Log files produced

| File                        | Contents                              |
|-----------------------------|---------------------------------------|
| `payment-app.log`           | All logs (INFO and above by default)  |
| `payment-app-error.log`     | ERROR-level logs only                 |
| `payment-app.json`          | Structured JSON logs (only in `json` profile) |

Files roll over **daily or at 10 MB**, are gzipped, kept for **30 days**, and the
total size is capped (1 GB for the main log) so they never fill the disk.

### Where logs are written

The directory is resolved in this order (first match wins):

1. `LOG_PATH` environment variable — e.g. `export LOG_PATH=/var/log/payment-app`
2. `log.path` property — e.g. `--log.path=/var/log/payment-app`
3. Default `logs/` folder next to the running app

### Plain text vs JSON

- **Default** (no profile): human-readable console + plain-text files.
- **JSON** (for ELK / Grafana Loki): activate the `json` Spring profile.

```bash
# Plain text (default)
java -jar app.jar --log.path=/var/log/payment-app

# Structured JSON
java -jar app.jar --log.path=/var/log/payment-app --spring.profiles.active=json
# or
SPRING_PROFILES_ACTIVE=json LOG_PATH=/var/log/payment-app java -jar app.jar
```

> Tests use `src/test/resources/logback-test.xml`, which logs to console only and
> never creates files.

---

## 2. Deploy on Linux with systemd

Files live in the [`deploy/`](.) folder:

| File                       | Purpose                                  |
|----------------------------|------------------------------------------|
| `payment-app.service`      | systemd unit (auto-start, auto-restart)  |
| `install.sh`               | One-shot install/upgrade script          |
| `payment-app.env.example`  | Template for secrets & overrides         |

### Quick start

```bash
# 1. Build the jar
mvn clean package -DskipTests

# 2. Install + start as a service (creates user, dirs, copies jar & unit)
sudo bash deploy/install.sh
```

### Manage the service

```bash
sudo systemctl status payment-app      # current status
sudo systemctl restart payment-app     # restart
sudo journalctl -u payment-app -f      # live stdout/stderr (via journald)
tail -f /var/log/payment-app/payment-app.log   # live application log file
```

### Configure secrets (recommended)

```bash
sudo mkdir -p /etc/payment-app
sudo cp deploy/payment-app.env.example /etc/payment-app/payment-app.env
sudo chmod 600 /etc/payment-app/payment-app.env
# edit the file, then uncomment EnvironmentFile=... in payment-app.service
sudo systemctl daemon-reload && sudo systemctl restart payment-app
```

The service runs as a dedicated unprivileged `payment` user and is hardened
(`ProtectSystem`, `PrivateTmp`, `NoNewPrivileges`), writing only to
`/var/log/payment-app`.

---

## 3. AWS IAM permissions (prod profile)

Under the `prod` profile the app reaches several AWS services. Credentials are
**never** in the codebase — they come from the instance role (EC2 instance
profile on Elastic Beanstalk, or the role attached to the host/task). Grant that
role the permissions below.

| Service | Used for | Actions |
|---------|----------|---------|
| Secrets Manager | DB credentials + JWT secret (`payment-app/prod`) | `secretsmanager:GetSecretValue` |
| SNS | OTP SMS delivery | `sns:Publish` |
| CloudWatch | Micrometer metrics export | `cloudwatch:PutMetricData` |
| X-Ray | Distributed tracing daemon | `xray:PutTraceSegments`, `xray:PutTelemetryRecords` |

### Easiest path — AWS managed policy for X-Ray

For X-Ray, attach the managed policy **`AWSXRayDaemonWriteAccess`** to the
instance role. The X-Ray daemon (enabled on Elastic Beanstalk via
[`.ebextensions/xray-daemon.config`](../.ebextensions/xray-daemon.config)) needs
this to upload segments; without it traces never reach the X-Ray console.

```bash
aws iam attach-role-policy \
  --role-name <your-instance-role> \
  --policy-arn arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess
```

### Inline policy covering all prod-profile permissions

Attach this as a custom (inline or managed) policy to the same role. Replace the
account ID, region, secret name, and SNS scope to match your environment.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadAppSecret",
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:payment-app/prod-*"
    },
    {
      "Sid": "SendOtpSms",
      "Effect": "Allow",
      "Action": "sns:Publish",
      "Resource": "*"
    },
    {
      "Sid": "PublishMetrics",
      "Effect": "Allow",
      "Action": "cloudwatch:PutMetricData",
      "Resource": "*"
    },
    {
      "Sid": "WriteXRayTraces",
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords",
        "xray:GetSamplingRules",
        "xray:GetSamplingTargets"
      ],
      "Resource": "*"
    }
  ]
}
```

> **Notes**
> - `sns:Publish` and `cloudwatch:PutMetricData` do not support resource-level
>   restrictions for these calls, so `"Resource": "*"` is expected. Narrow with
>   condition keys (e.g. `sns:Publish` is for SMS, not a topic ARN) if your
>   security policy requires it.
> - The Secrets Manager ARN ends with `-*` because AWS appends a random 6-character
>   suffix to every secret name.
> - Region for all calls comes from `-Daws.region` in the `Procfile`; the X-Ray
>   daemon address defaults to `127.0.0.1:2000`.

