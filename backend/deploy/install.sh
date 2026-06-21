#!/usr/bin/env bash
# =============================================================================
#  One-shot deployment script for the Payment App backend on a Linux server.
#  Run as root (or with sudo) from the project root after building the jar.
#
#  Usage:
#    mvn clean package -DskipTests
#    sudo bash deploy/install.sh
# =============================================================================
set -euo pipefail

APP_USER="payment"
APP_DIR="/opt/payment-app"
LOG_DIR="/var/log/payment-app"
JAR_SRC="target/payment-app-backend-1.0.0.jar"
SERVICE_SRC="deploy/payment-app.service"

echo ">> Checking build artifact..."
if [[ ! -f "${JAR_SRC}" ]]; then
  echo "ERROR: ${JAR_SRC} not found. Run 'mvn clean package -DskipTests' first." >&2
  exit 1
fi

echo ">> Creating service user '${APP_USER}' (if missing)..."
id -u "${APP_USER}" &>/dev/null || \
  useradd --system --no-create-home --shell /usr/sbin/nologin "${APP_USER}"

echo ">> Creating directories..."
mkdir -p "${APP_DIR}" "${LOG_DIR}"

echo ">> Installing jar..."
install -o "${APP_USER}" -g "${APP_USER}" -m 0644 "${JAR_SRC}" "${APP_DIR}/app.jar"

echo ">> Fixing ownership..."
chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}" "${LOG_DIR}"

echo ">> Installing systemd unit..."
install -m 0644 "${SERVICE_SRC}" /etc/systemd/system/payment-app.service

echo ">> Reloading systemd and (re)starting service..."
systemctl daemon-reload
systemctl enable --now payment-app
systemctl restart payment-app

echo
echo ">> Done. Useful commands:"
echo "     systemctl status payment-app"
echo "     journalctl -u payment-app -f"
echo "     tail -f ${LOG_DIR}/payment-app.log"

