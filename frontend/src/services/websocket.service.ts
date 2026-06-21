import type { WsMessage, WsResponse } from '../types/game.types';

type MessageHandler = (data: WsResponse) => void;

/**
 * WebSocket service — singleton wrapper around the browser WebSocket API.
 * Handles reconnection, queueing messages while connecting, and typed event dispatch.
 */
class WebSocketService {
  private ws: WebSocket | null = null;
  private handlers = new Map<string, Set<MessageHandler>>();
  private queue: string[] = [];
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private url = '';
  private intentionalClose = false;

  // ---------------------------------------------------------------------------
  // Connection management
  // ---------------------------------------------------------------------------

  connect(url: string): Promise<void> {
    this.url = url;
    this.intentionalClose = false;
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(url);

        this.ws.onopen = () => {
          console.log('[WS] Connected to', url);
          // Flush queued messages
          while (this.queue.length > 0) {
            this.ws!.send(this.queue.shift()!);
          }
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const response: WsResponse = JSON.parse(event.data as string);
            this.dispatch(response);
          } catch (e) {
            console.error('[WS] Failed to parse message', e);
          }
        };

        this.ws.onclose = (event) => {
          console.log('[WS] Closed', event.code, event.reason);
          if (!this.intentionalClose) {
            this.scheduleReconnect();
          }
        };

        this.ws.onerror = (err) => {
          console.error('[WS] Error', err);
          reject(err);
        };
      } catch (e) {
        reject(e);
      }
    });
  }

  disconnect() {
    this.intentionalClose = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = setTimeout(() => {
      console.log('[WS] Reconnecting…');
      this.connect(this.url).catch(() => this.scheduleReconnect());
    }, 3000);
  }

  // ---------------------------------------------------------------------------
  // Send
  // ---------------------------------------------------------------------------

  send(type: string, payload?: unknown) {
    const msg: WsMessage = { type, payload };
    const json = JSON.stringify(msg);
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(json);
    } else {
      this.queue.push(json);
    }
  }

  // ---------------------------------------------------------------------------
  // Event subscriptions
  // ---------------------------------------------------------------------------

  on(type: string, handler: MessageHandler): () => void {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set());
    this.handlers.get(type)!.add(handler);
    return () => this.off(type, handler);
  }

  off(type: string, handler: MessageHandler) {
    this.handlers.get(type)?.delete(handler);
  }

  private dispatch(response: WsResponse) {
    const set = this.handlers.get(response.type);
    if (set) set.forEach((h) => h(response));
    // Wildcard handler
    const all = this.handlers.get('*');
    if (all) all.forEach((h) => h(response));
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

// Export singleton
export const wsService = new WebSocketService();

// Compute WS URL dynamically (works in both dev proxy and prod Nginx)
export const getWsUrl = (): string => {
  const envUrl = import.meta.env.VITE_WS_URL as string | undefined;
  if (envUrl) return envUrl;
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws/game`;
};

