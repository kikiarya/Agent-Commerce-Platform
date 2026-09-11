import React, { useState } from 'react';
import './NotificationBell.css';

function NotificationBell({ notifications, onClearAll, onMarkAsRead }) {
  const [isOpen, setIsOpen] = useState(false);
  const unreadCount = notifications.filter(n => !n.read).length;

  const handleToggle = () => {
    setIsOpen(!isOpen);
  };

  const handleMarkAsRead = (notificationId) => {
    if (onMarkAsRead) {
      onMarkAsRead(notificationId);
    }
  };

  const handleClearAll = () => {
    if (onClearAll) {
      onClearAll();
    }
    setIsOpen(false);
  };

  const handleNotificationClick = (notification) => {
    if (!notification.read) {
      handleMarkAsRead(notification.id);
    }
  };

  return (
    <div className="notification-bell-container">
      <button 
        className="notification-bell-button"
        onClick={handleToggle}
        aria-label="Notifications"
      >
        🔔
        {unreadCount > 0 && (
          <span className="notification-badge">{unreadCount}</span>
        )}
      </button>
      
      {isOpen && (
        <>
          <div className="notification-backdrop" onClick={() => setIsOpen(false)}></div>
          <div className="notification-dropdown">
            <div className="notification-header">
              <h3>Notifications</h3>
              {notifications.length > 0 && (
                <button 
                  className="clear-all-btn"
                  onClick={handleClearAll}
                >
                  Clear All
                </button>
              )}
            </div>
            <div className="notification-list">
              {notifications.length === 0 ? (
                <div className="notification-empty">
                  <p>No notifications</p>
                </div>
              ) : (
                notifications.slice().reverse().map(notification => (
                  <div
                    key={notification.id}
                    className={`notification-item ${notification.read ? 'read' : 'unread'}`}
                    onClick={() => handleNotificationClick(notification)}
                  >
                    <div className="notification-icon">
                      {getStatusIcon(notification.status)}
                    </div>
                    <div className="notification-content">
                      <div className="notification-title">{notification.subject}</div>
                      <div className="notification-body">{notification.body}</div>
                      {notification.orderId && (
                        <div className="notification-order-id">Order #{notification.orderId}</div>
                      )}
                      <div className="notification-time">
                        {formatTime(notification.timestamp)}
                      </div>
                    </div>
                    {!notification.read && (
                      <div className="notification-dot"></div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function getStatusIcon(status) {
  switch (status?.toUpperCase()) {
    case 'PAID':
      return '💳';
    case 'PICKUP':
      return '📦';
    case 'IN_TRANSIT':
      return '🚚';
    case 'OUT_FOR_DELIVERY':
      return '🚛';
    case 'DELIVERED':
      return '✅';
    case 'CANCELLED':
      return '❌';
    case 'REFUNDED':
      return '💰';
    case 'PACKAGE_MISSING':
      return '⚠️';
    case 'LOST':
      return '⚠️';
    default:
      return '📧';
  }
}

function formatTime(timestamp) {
  if (!timestamp) return '';
  
  const now = new Date();
  const time = new Date(timestamp);
  const diff = now - time;
  
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  
  if (days > 0) return `${days} day${days > 1 ? 's' : ''} ago`;
  if (hours > 0) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
  if (minutes > 0) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
  return 'Just now';
}

export default NotificationBell;
