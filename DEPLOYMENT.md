# Indian Rummy — Ubuntu VPS Deployment Guide

## Architecture

```
Internet → Nginx (port 80/443)
               ├── /ws/*   → Spring Boot backend (port 8080)
               ├── /api/*  → Spring Boot backend (port 8080)
               └── /*      → React frontend (port 3000)
```

All services run as Docker containers via `docker-compose`.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Ubuntu | 22.04 LTS or 24.04 LTS |
| RAM | 2 GB minimum (4 GB recommended for 1000 rooms) |
| CPU | 2 vCPUs |
| Disk | 20 GB |
| Ports | 22 (SSH), 80 (HTTP), 443 (HTTPS) |

---

## Step 1 — Initial Server Setup

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Set hostname
sudo hostnamectl set-hostname rummy-server

# Create deploy user
sudo adduser deploy
sudo usermod -aG sudo deploy
sudo usermod -aG docker deploy 2>/dev/null || true
```

---

## Step 2 — Install Docker

```bash
# Install Docker
curl -fsSL https://get.docker.com | sudo sh

# Install Docker Compose plugin
sudo apt install -y docker-compose-plugin

# Enable Docker to start on boot
sudo systemctl enable docker
sudo systemctl start docker

# Verify
docker --version
docker compose version
```

---

## Step 3 — Install Java 21 (for local dev/debugging only)

```bash
sudo apt install -y wget apt-transport-https
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-21-jdk

java -version   # should show 21
```

---

## Step 4 — Deploy the Application

### 4a. Clone / Upload the project

```bash
# Option A — if you have a Git repo
git clone https://github.com/your-org/indian-rummy.git /opt/rummy
cd /opt/rummy

# Option B — scp from your local machine
scp -r ./card-game-backend deploy@YOUR_SERVER_IP:/opt/rummy
ssh deploy@YOUR_SERVER_IP
cd /opt/rummy
```

### 4b. Build and start all services

```bash
cd /opt/rummy

# Build images and start in background
docker compose up -d --build

# Verify all 3 containers are running
docker compose ps
```

Expected output:
```
NAME              STATUS        PORTS
rummy-nginx       Up            0.0.0.0:80->80/tcp
rummy-backend     Up (healthy)  0.0.0.0:8080->8080/tcp
rummy-frontend    Up            0.0.0.0:3000->80/tcp
```

---

## Step 5 — Configure Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# Verify
sudo ufw status
```

---

## Step 6 — Configure DNS (optional but recommended)

Point your domain's A record to your server IP:

```
A    @         YOUR_SERVER_IP
A    www       YOUR_SERVER_IP
```

Then update `nginx.conf` to use your domain:
```nginx
server_name yourdomain.com www.yourdomain.com;
```

---

## Step 7 — Enable HTTPS with Let's Encrypt

```bash
# Install Certbot
sudo apt install -y certbot

# Stop Nginx temporarily to obtain cert
docker compose stop nginx

# Obtain certificate
sudo certbot certonly --standalone -d yourdomain.com -d www.yourdomain.com

# Copy certs to project
sudo mkdir -p /opt/rummy/nginx/certs
sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem /opt/rummy/nginx/certs/
sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem /opt/rummy/nginx/certs/
sudo chown -R deploy:deploy /opt/rummy/nginx/certs
```

Then edit `nginx/nginx.conf` — uncomment the HTTPS server block and the HTTP→HTTPS redirect.

```bash
# Restart with HTTPS enabled
docker compose up -d nginx

# Auto-renew certs via cron
echo "0 3 * * * root certbot renew --quiet && docker compose -f /opt/rummy/docker-compose.yml restart nginx" \
  | sudo tee /etc/cron.d/certbot-renew
```

---

## Step 8 — Monitoring & Logs

```bash
# Live logs for all services
docker compose logs -f

# Only backend logs
docker compose logs -f backend

# Check backend health
curl http://localhost/api/health

# Active rooms count
curl http://localhost/api/rooms
```

---

## Step 9 — Update Deployment

```bash
cd /opt/rummy

# Pull latest code
git pull origin main

# Rebuild and restart (zero-downtime for frontend; brief restart for backend)
docker compose up -d --build backend
docker compose up -d --build frontend
docker compose restart nginx
```

---

## Step 10 — Scaling & Tuning

### JVM memory

Edit `docker-compose.yml` to adjust backend memory:
```yaml
environment:
  - JAVA_OPTS=-XX:+UseContainerSupport -Xmx1g -Xms512m
```

### Nginx worker connections

For 1000+ rooms (4000 concurrent WebSockets):
```nginx
worker_processes auto;
events { worker_connections 8192; }
```

### OS limits

```bash
# Increase file descriptor limits for WebSocket connections
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf
```

---

## Step 11 — Backup & Recovery

Since all game state is in-RAM only (by design), there is nothing to backup for game state.
If you want to persist room codes or player names for analytics, add a lightweight append-log:

```bash
# Simple backup of application config
tar czf /backup/rummy-config-$(date +%Y%m%d).tar.gz /opt/rummy/nginx /opt/rummy/docker-compose.yml
```

---

## Architecture Sequence Diagram

```
Client                  Nginx               Backend (Spring)
  |                       |                       |
  |-- GET /            -->|                       |
  |                       |-- proxy to frontend --| (React static files)
  |<-- HTML/JS/CSS ------|                       |
  |                       |                       |
  |-- WS /ws/game ----->|                       |
  |                       |-- WS upgrade -------->|
  |<--------------------- CONNECTED -------------|
  |                       |                       |
  |-- CREATE_ROOM ------->|                       |
  |                       |-- forward ----------->|
  |                       |<-- ROOM_CREATED ------|
  |<-- ROOM_CREATED ------|                       |
  |                       |                       |
  |-- JOIN_ROOM (×3) ---->|                       |
  |                       |-- forward × 3 ------->|
  |                       |<-- GAME_STARTED ------|   (auto-start on 4th join)
  |<-- CARD_DISTRIBUTED --|-- broadcast to all ---|
  |                       |                       |
  |-- DRAW_CARD ---------->|                      |
  |                       |-- forward ----------->|
  |                       |<-- CARD_DRAWN --------|
  |<-- CARD_DRAWN --------|                       |
  |                       |                       |
  |-- DISCARD_CARD ------->|                      |
  |                       |<-- TURN_CHANGED ------|
  |<-- TURN_CHANGED ------|-- broadcast to all ---|
  |                       |                       |
  |-- DECLARE_WIN -------->|                      |
  |                       |<-- PLAYER_WON --------|
  |<-- PLAYER_WON --------|-- broadcast to all ---|
```

---

## Component Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                     Ubuntu VPS                          │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │              Docker Network (rummy-net)           │  │
│  │                                                   │  │
│  │  ┌────────────┐   proxy   ┌──────────────────┐   │  │
│  │  │   Nginx    │─/ws/─────▶│  Spring Boot 21  │   │  │
│  │  │  :80/:443  │─/api/────▶│    :8080         │   │  │
│  │  │            │           │                  │   │  │
│  │  │            │   proxy   │  ┌────────────┐  │   │  │
│  │  │            │──/*──────▶│  │React Nginx │  │   │  │
│  │  └────────────┘           │  │  :3000     │  │   │  │
│  │                           │  └────────────┘  │   │  │
│  │                           │                  │   │  │
│  │                           │  In-RAM storage  │   │  │
│  │                           │  ConcurrentHashMap│  │   │
│  │                           └──────────────────┘   │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Troubleshooting

| Issue | Fix |
|---|---|
| WebSocket disconnects after 60s | Nginx `proxy_read_timeout` set to 3600s ✓ |
| `502 Bad Gateway` | Check `docker compose ps` — backend may still be starting |
| Frontend shows blank page | Ensure `SPA fallback` in Nginx config is active |
| Players can't reconnect | Verify playerId stored in `localStorage` on client side |
| High memory usage | Reduce `game.max-rooms` in `application.properties` |

---

## Quick Commands Reference

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Restart one service
docker compose restart backend

# View real-time logs
docker compose logs -f

# Shell into backend container
docker compose exec backend sh

# Check active rooms
curl http://localhost/api/rooms | python3 -m json.tool
```

