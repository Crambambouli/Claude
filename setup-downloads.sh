#!/bin/bash
# =============================================================================
# Hetzner Download-Area Setup
# Einmalig ausführen als: deployer@46.225.83.170
#
# Strategie: Kein neuer Port – /downloads/ als Nginx-Location auf Port 80/443
# =============================================================================
set -e

DOWNLOAD_DIR="/var/www/downloads"
DEPLOY_SCRIPT="/opt/claude-deploy/deploy.sh"
NGINX_SNIPPET="/etc/nginx/snippets/claude-downloads.conf"
NGINX_VHOST="/etc/nginx/sites-available/claude-downloads"
REPO="https://github.com/Crambambouli/Claude.git"
SERVER_IP="46.225.83.170"

echo "=== [1/6] Pakete installieren ==="
sudo apt-get update -q
sudo apt-get install -y -q git zip unzip curl

echo "=== [2/6] Verzeichnisse anlegen ==="
sudo mkdir -p "$DOWNLOAD_DIR"
sudo mkdir -p /opt/claude-deploy
sudo mkdir -p /etc/nginx/snippets
sudo chown deployer:deployer "$DOWNLOAD_DIR"
sudo chown deployer:deployer /opt/claude-deploy

echo "=== [3/6] Deploy-Skript erstellen ==="
cat > "$DEPLOY_SCRIPT" << 'DEPLOY_EOF'
#!/bin/bash
# Wird stündlich per Cronjob ausgefuehrt.
# Neue Projekte: Zeile im Format "branch:ordnername" zur PROJECTS-Liste hinzufuegen.
set -e

REPO="https://github.com/Crambambouli/Claude.git"
DOWNLOAD_DIR="/var/www/downloads"
TMP_DIR=$(mktemp -d)

trap 'rm -rf "$TMP_DIR"' EXIT

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# === Projektliste ===
PROJECTS=(
    "claude/android-kotlin-compose-app-ODFfK:puzzle_android"
)

for ENTRY in "${PROJECTS[@]}"; do
    BRANCH="${ENTRY%%:*}"
    PROJECT="${ENTRY##*:}"

    log "Deploying $PROJECT (Branch: $BRANCH) ..."
    if git clone --depth 1 -b "$BRANCH" "$REPO" "$TMP_DIR/$PROJECT" --quiet 2>/dev/null; then
        if [ -d "$TMP_DIR/$PROJECT/$PROJECT" ]; then
            zip -r "$DOWNLOAD_DIR/$PROJECT.zip" \
                "$TMP_DIR/$PROJECT/$PROJECT/" \
                --exclude "*.git*" \
                -q
            log "OK: $DOWNLOAD_DIR/$PROJECT.zip ($(du -sh "$DOWNLOAD_DIR/$PROJECT.zip" | cut -f1))"
        else
            log "WARNUNG: Ordner '$PROJECT' nicht in Branch '$BRANCH' gefunden."
        fi
    else
        log "FEHLER: Branch '$BRANCH' konnte nicht geklont werden."
    fi
done

log "Deployment abgeschlossen."
DEPLOY_EOF

chmod +x "$DEPLOY_SCRIPT"

echo "=== [4/6] Nginx-Snippet erstellen ==="
sudo tee "$NGINX_SNIPPET" > /dev/null << 'SNIPPET_EOF'
location /downloads/ {
    alias /var/www/downloads/;
    autoindex on;
    autoindex_exact_size off;
    autoindex_localtime on;
    add_header X-Robots-Tag "noindex, nofollow" always;
    location ~* \.(zip|apk|tar\.gz)$ {
        add_header Content-Disposition "attachment";
    }
}
SNIPPET_EOF

# Eigener vHost fuer direkte IP-Anfragen auf Port 80
sudo tee "$NGINX_VHOST" > /dev/null << VHOST_EOF
server {
    listen 80;
    listen [::]:80;
    server_name $SERVER_IP;

    include /etc/nginx/snippets/claude-downloads.conf;

    location / {
        return 404;
    }
}
VHOST_EOF

sudo ln -sf "$NGINX_VHOST" /etc/nginx/sites-enabled/claude-downloads

echo "=== Nginx-Konfiguration testen ==="
if sudo nginx -t 2>&1; then
    sudo systemctl reload nginx
    echo "Nginx erfolgreich neu geladen."
else
    echo ""
    echo "HINWEIS: Ein anderer vHost ist bereits fuer $SERVER_IP zustaendig."
    echo "Loesung: Folgenden Include in den passenden Server-Block einfuegen:"
    echo ""
    echo "    include /etc/nginx/snippets/claude-downloads.conf;"
    echo ""
    echo "Das Snippet liegt unter: $NGINX_SNIPPET"
    sudo rm -f /etc/nginx/sites-enabled/claude-downloads
fi

echo "=== [5/6] Cronjob einrichten (stuendlich) ==="
CRON_LINE="0 * * * * $DEPLOY_SCRIPT >> /var/log/claude-deploy.log 2>&1"
(crontab -l 2>/dev/null | grep -v "claude-deploy"; echo "$CRON_LINE") | crontab -
echo "Cronjob gesetzt."

echo "=== [6/6] Erstes Deployment jetzt ausfuehren ==="
bash "$DEPLOY_SCRIPT"

echo ""
echo "============================================="
echo " Setup abgeschlossen!"
echo " Download-URL: http://$SERVER_IP/downloads/"
echo " puzzle_android.zip ist bereit."
echo "============================================="
