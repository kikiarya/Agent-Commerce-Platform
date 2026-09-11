import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class WebSocketService {
  constructor() {
    this.client = null;
    this.isConnected = false;
    this.subscriptions = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
  }

  connect(userId, onMessage, onError) {
    if (this.isConnected && this.client && this.client.active) {
      return;
    }

    const configuredWsUrl = process.env.REACT_APP_WS_URL;
    const serverUrl =
      configuredWsUrl === 'same-origin'
        ? window.location.origin
        : (configuredWsUrl || 'http://localhost:8080');

    // Use SockJS for WebSocket connection
    const socket = new SockJS(`${serverUrl}/ws`);
    this.client = new Client({
      webSocketFactory: () => socket,
      debug: () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.isConnected = true;
        this.reconnectAttempts = 0;
        
        // Subscribe to user-specific notifications
        const subscription = this.client.subscribe(
          `/topic/notifications/${userId}`,
          (message) => {
            try {
              const data = JSON.parse(message.body);
              if (onMessage) {
                onMessage(data);
              }
            } catch (error) {
              // Failed to parse WebSocket message
            }
          }
        );
        
        this.subscriptions.set(`/topic/notifications/${userId}`, subscription);
      },
      onDisconnect: () => {
        this.isConnected = false;
      },
      onStompError: (frame) => {
        if (onError) {
          onError(frame);
        }
      },
      onWebSocketClose: () => {
        this.isConnected = false;
        this.reconnectAttempts++;
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          setTimeout(() => {
            this.connect(userId, onMessage, onError);
          }, 5000);
        }
      }
    });

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      // Unsubscribe from all topics
      this.subscriptions.forEach((subscription) => {
        subscription.unsubscribe();
      });
      this.subscriptions.clear();
      
      this.client.deactivate();
      this.isConnected = false;
    }
  }

  getIsConnected() {
    return this.isConnected;
  }
}

const websocketService = new WebSocketService();
export default websocketService;
