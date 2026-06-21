# 🃏 Indian Rummy — Multiplayer Card Game

4-player online Indian Rummy built with **Java 21 + Spring Boot 3** (backend) and **React + TypeScript + Vite** (frontend).

---

## Running Locally (Two Terminals)

This is the fastest way to develop and test.

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | 21 | `java -version` |
| Maven | bundled (`./mvnw`) | – |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |

> **macOS tip:** if `java -version` shows Java 11, prefix commands with  
> `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home`

---

### Terminal 1 — Start the Backend

```bash
# From the project root
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home \
  ./mvnw spring-boot:run
```

Backend starts on **http://localhost:8080**

| Endpoint | Description |
|---|---|
| `ws://localhost:8080/ws/game` | WebSocket (game) |
| `GET /api/health` | Health check |
| `GET /api/rooms` | List active rooms |

---

### Terminal 2 — Start the Frontend

```bash
cd frontend
npm install          # first time only
npm run dev
```

Frontend starts on **http://localhost:3000**

The Vite dev server automatically proxies:
- `/ws/*` → `ws://localhost:8080`
- `/api/*` → `http://localhost:8080`

So just open **http://localhost:3000** in your browser (or Safari on iPhone on the same Wi-Fi).

---

### Test on iPhone (same Wi-Fi)

```bash
# Find your Mac's local IP
ipconfig getifaddr en0    # e.g. 192.168.1.42

# Open on iPhone
http://192.168.1.42:3000
```

> The Vite dev server binds to `0.0.0.0` by default so it is reachable on the LAN.
> If it's not, add `--host` flag: `npm run dev -- --host`

---

### Run Tests

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home \
  ./mvnw test
```

65 tests, 0 failures.

---

## Deploying to Render (Free Tier)

The whole app (backend + React frontend) ships as **one JAR** — no Docker needed.

### Step 1 — Push to GitHub

```bash
git init
git add .
git commit -m "initial commit"
git remote add origin https://github.com/YOUR_NAME/indian-rummy.git
git push -u origin main
```

### Step 2 — Create Web Service on Render

1. Go to [render.com](https://render.com) → **New → Web Service**
2. Connect your GitHub repo
3. Fill in:

| Setting | Value |
|---|---|
| **Name** | `indian-rummy` |
| **Runtime** | `Java` |
| **Build Command** | `./mvnw clean package -Pbuild-frontend -DskipTests` |
| **Start Command** | `java -Dserver.port=$PORT $JAVA_OPTS -jar target/indian-rummy-1.0.0.jar` |
| **Plan** | Free |

4. Under **Environment Variables**, add:

| Key | Value |
|---|---|
| `JAVA_VERSION` | `21` |
| `JAVA_OPTS` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75` |

5. Click **Deploy** — Render will:
   - Download Java 21
   - Run `./mvnw clean package -Pbuild-frontend -DskipTests`
     - This builds the React app and packages it inside the JAR
   - Start the JAR on Render's assigned port

Your app will be live at **https://indian-rummy.onrender.com**

> ⚠️ **Free tier sleeps after 15 minutes of inactivity.** First request after sleep takes ~30 seconds.  
> Upgrade to **Starter ($7/mo)** for always-on.

### Step 3 — Use the `render.yaml` Blueprint (alternative)

Instead of manual setup, just run:

```bash
# In the Render dashboard:  New → Blueprint
# Point it to your GitHub repo — render.yaml is auto-detected
```

---

## How the Production Build Works

```
./mvnw clean package -Pbuild-frontend
         │
         ├── frontend-maven-plugin
         │     ├── installs Node v20 locally (no system Node needed on Render)
         │     ├── npm ci
         │     └── npm run build  → frontend/dist/
         │
         └── maven-resources-plugin
               copies frontend/dist/ → target/classes/static/
               (Spring Boot serves /static/* automatically)

Result: target/indian-rummy-1.0.0.jar
        ├── Backend (API + WebSocket)
        └── /static/ (React app)
              React Router works via SpaController (forward → index.html)
```

---

## WebSocket Flow

```
Browser ──── ws://yourapp.onrender.com/ws/game ────▶ Spring Boot

  CREATE_ROOM  { playerName }          ──▶  ROOM_CREATED  { roomCode, playerId }
  JOIN_ROOM    { roomCode, playerName } ──▶  PLAYER_JOINED { playerId, gameState }
               (auto-starts when 4 players join)
                                            CARD_DISTRIBUTED { cards, topDiscard }
  DRAW_CARD    {}                      ──▶  CARD_DRAWN    { card, deckSize }
  DISCARD_CARD { cardId }              ──▶  CARD_DISCARDED + TURN_CHANGED
  REARRANGE_CARDS { groups }           ──▶  CARDS_REARRANGED
  DECLARE_WIN  { groups }              ──▶  PLAYER_WON   { winnerId, winnerName }
  RECONNECT    { roomCode, playerId }  ──▶  RECONNECTED  { gameState }
```

---

## Project Structure

```
card-game-backend/
├── src/main/java/com/cardgame/     ← Spring Boot (Indian Rummy engine)
│   ├── model/   Card Deck Player GameRoom RoomStatus …
│   ├── service/ GameEngine TurnTimerService CleanupService
│   ├── manager/ RoomManager
│   ├── websocket/ GameWebSocketHandler WebSocketSessionManager
│   ├── controller/ RoomController SpaController
│   ├── config/  WebSocketConfig AppConfig
│   └── dto/     all request/response DTOs
├── src/test/java/com/cardgame/     ← 65 JUnit 5 tests
├── frontend/                       ← React + TypeScript + Vite
│   ├── src/
│   │   ├── store/   gameStore.ts  (Zustand)
│   │   ├── services/ websocket.service.ts
│   │   ├── types/   game.types.ts
│   │   └── components/
│   │       ├── lobby/    LobbyScreen
│   │       ├── waiting/  WaitingRoom
│   │       ├── game/     GameTable OtherPlayers DrawPile DiscardPile
│   │       │             PlayerHand CardComponent TurnTimer JokerStatus
│   │       ├── dialogs/  WinnerDialog
│   │       └── common/   Notification ErrorBanner
│   └── public/  manifest.json sw.js (PWA)
├── pom.xml
├── system.properties               ← java.runtime.version=21 (Render)
├── Procfile                        ← web: java ... (Render/Heroku)
└── render.yaml                     ← Render Blueprint
```

