#!/bin/bash
# =============================================================================
# Hetzner Download-Area Setup
# Einmalig ausführen als: deployer@46.225.83.170
# =============================================================================
set -e

DOWNLOAD_DIR="/var/www/downloads"
DEPLOY_SCRIPT="/opt/claude-deploy/deploy.sh"
NGINX_CONF="/etc/nginx/sites-available/claude-downloads"
REPO="https://github.com/Crambambouli/Claude.git"

echo "=== [1/6] Pakete installieren ==="
sudo apt-get update -q
sudo apt-get install -y -q git zip unzip curl

echo "=== [2/6] Verzeichnisse anlegen ==="
sudo mkdir -p "$DOWNLOAD_DIR"
sudo mkdir -p /opt/claude-deploy
sudo chown deployer:deployer "$DOWNLOAD_DIR"
sudo chown deployer:deployer /opt/claude-deploy

echo "=== [3/6] Deploy-Skript erstellen ==="
cat > "$DEPLOY_SCRIPT" << 'DEPLOY_EOF'
#!/bin/bash
# Wird stündlich per Cronjob ausgeführt.
# Neue Projekte hier als BRANCH=Projektname eintragen.
set -e

REPO="https://github.com/Crambambouli/Claude.git"
DOWNLOAD_DIR="/var/www/downloads"
TMP_DIR=$(mktemp -d)

trap 'rm -rf "$TMP_DIR"' EXIT

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# === Projektliste ===
# Format: "branch_name:ordner_name"
PROJECTS=(
    "claude/android-kotlin-compose-app-ODFfK:puzzle_android"
)

for ENTRY in "${PROJECTS[@]}"; do
    BRANCH="${ENTRY%%:*}"
    PROJECT="${ENTRY##*:}"

    log "Deploying $PROJECT von Branch $BRANCH ..."
    git clone --depth 1 -b "$BRANCH" "$REPO" "$TMP_DIR/$PROJECT" --quiet 2>/dev/null

    if [ -d "$TMP_DIR/$PROJECT/$PROJECT" ]; then
        zip -r "$DOWNLOAD_DIR/$PROJECT.zip" \
            "$TMP_DIR/$PROJECT/$PROJECT/" \
            --exclude "*.git*" \
            -q
        log "OK: $DOWNLOAD_DIR/$PROJECT.zip"
    else
        log "WARNUNG: Ordner $PROJECT nicht im Branch $BRANCH gefunden."
    fi
done

log "Deployment abgeschlossen."
DEPLOY_EOF

chmod +x "$DEPLOY_SCRIPT"

echo "=== [4/6] Nginx konfigurieren ==="
sudo tee "$NGINX_CONF" > /dev/null << 'NGINX_EOF'
server {
    listen 8080;
    server_name _;

    root /var/www/downloads;

    autoindex on;
    autoindex_exact_size off;
    autoindex_localtime on;

    location / {
        add_header Content-Disposition "attachment";
        add_header X-Robots-Tag "noindex, nofollow";
    }
}
NGINX_EOF

sudo ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/claude-downloads
sudo nginx -t
sudo systemctl reload nginx

echo "=== [5/6] Cronjob einrichten (stündlich) ==="
CRON_LINE="0 * * * * $DEPLOY_SCRIPT >> /var/log/claude-deploy.log 2>&1"
(crontab -l 2>/dev/null | grep -v "claude-deploy"; echo "$CRON_LINE") | crontab -

echo "=== [6/6] Erstes Deployment jetzt ausführen ==="
bash "$DEPLOY_SCRIPT"

echo ""
echo "============================================="
echo " Setup abgeschlossen!"
echo " Download-URL: http://46.225.83.170:8080"
echo " puzzle_android.zip ist bereit zum Download."
echo "============================================="
