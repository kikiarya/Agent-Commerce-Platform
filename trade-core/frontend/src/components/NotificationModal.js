import React, { useEffect } from 'react';
import './NotificationModal.css';

function NotificationModal({ notification, onClose }) {
  useEffect(() => {
    // Auto close after 5 seconds
    const timer = setTimeout(() => {
      onClose();
    }, 5000);

    return () => clearTimeout(timer);
  }, [onClose]);

  if (!notification) {
    return null;
  }

  const getStatusIcon = (status) => {
    switch (status.toUpperCase()) {
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
      default:
        return '📧';
    }
  };

  const getStatusColor = (status) => {
    switch (status.toUpperCase()) {
      case 'PAID':
        return '#28a745';
      case 'PICKUP':
        return '#17a2b8';
      case 'IN_TRANSIT':
        return '#ffc107';
      case 'OUT_FOR_DELIVERY':
        return '#fd7e14';
      case 'DELIVERED':
        return '#28a745';
      case 'CANCELLED':
        return '#dc3545';
      case 'REFUNDED':
        return '#6c757d';
      case 'PACKAGE_MISSING':
        return '#dc3545';
      default:
        return '#667eea';
    }
  };

  return (
    <div className="notification-overlay">
      <div className="notification-modal">
        <div className="notification-header" style={{ borderLeftColor: getStatusColor(notification.status) }}>
          <div className="notification-icon">
            {getStatusIcon(notification.status)}
          </div>
          <h3>{notification.subject}</h3>
          <button className="notification-close" onClick={onClose}>×</button>
        </div>
        <div className="notification-body">
          <p>{notification.body}</p>
          {notification.orderId && (
            <p className="notification-order-id">
              Order ID: #{notification.orderId}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export default NotificationModal;
