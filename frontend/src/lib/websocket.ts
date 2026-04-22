import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/api/ws';

export type QueueUpdateCallback = (data: any) => void;

class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, any> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;

  connect() {
    if (this.client?.connected) return;

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as any,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        console.log('[WS] Connected');
        this.reconnectAttempts = 0;
        // Re-subscribe all
        this.subscriptions.forEach((sub, topic) => {
          if (sub.callback) {
            this.doSubscribe(topic, sub.callback);
          }
        });
      },
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message']);
      },
      onDisconnect: () => {
        console.log('[WS] Disconnected');
      },
      onWebSocketClose: () => {
        this.reconnectAttempts++;
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          console.error('[WS] Max reconnect attempts reached');
        }
      },
    });

    this.client.activate();
  }

  private doSubscribe(topic: string, callback: QueueUpdateCallback) {
    if (!this.client?.connected) return;

    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (e) {
        console.error('[WS] Parse error:', e);
      }
    });

    const existing = this.subscriptions.get(topic);
    this.subscriptions.set(topic, { ...existing, subscription, callback });
  }

  subscribeToQueue(branchId: number, callback: QueueUpdateCallback) {
    const topic = `/topic/queue/${branchId}`;
    this.subscriptions.set(topic, { callback });
    if (this.client?.connected) {
      this.doSubscribe(topic, callback);
    }
  }

  subscribeToToken(tokenId: number, callback: QueueUpdateCallback) {
    const topic = `/topic/token/${tokenId}`;
    this.subscriptions.set(topic, { callback });
    if (this.client?.connected) {
      this.doSubscribe(topic, callback);
    }
  }

  subscribeToCounter(branchId: number, counterId: number, callback: QueueUpdateCallback) {
    const topic = `/topic/counter/${branchId}/${counterId}`;
    this.subscriptions.set(topic, { callback });
    if (this.client?.connected) {
      this.doSubscribe(topic, callback);
    }
  }

  subscribeToTokenCalled(branchId: number, callback: QueueUpdateCallback) {
    const topic = `/topic/queue/${branchId}/called`;
    this.subscriptions.set(topic, { callback });
    if (this.client?.connected) {
      this.doSubscribe(topic, callback);
    }
  }

  unsubscribe(topic: string) {
    const sub = this.subscriptions.get(topic);
    if (sub?.subscription) {
      sub.subscription.unsubscribe();
    }
    this.subscriptions.delete(topic);
  }

  disconnect() {
    this.subscriptions.forEach((sub) => {
      if (sub?.subscription) sub.subscription.unsubscribe();
    });
    this.subscriptions.clear();
    this.client?.deactivate();
    this.client = null;
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

// Singleton
const wsService = new WebSocketService();
export default wsService;
