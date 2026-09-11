import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { productsAPI, ordersAPI, warehousesAPI } from '../services/api';
import websocketService from '../services/websocket';
import NotificationModal from '../components/NotificationModal';
import NotificationBell from '../components/NotificationBell';

function UserDashboard() {
  const [activeTab, setActiveTab] = useState('products');
  const [products, setProducts] = useState([]);
  const [stocks, setStocks] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [search, setSearch] = useState('');
  const [notification, setNotification] = useState(null);
  const [notifications, setNotifications] = useState(() => {
    // Try to load from localStorage on initial mount
    try {
      const userStr = localStorage.getItem('user');
      if (userStr) {
        const user = JSON.parse(userStr);
        if (user && user.userId) {
          const saved = localStorage.getItem(`notifications_${user.userId}`);
          if (saved) {
            const parsed = JSON.parse(saved);
            return Array.isArray(parsed) ? parsed : [];
          }
        }
      }
    } catch (e) {
      // Failed to load notifications
    }
    return [];
  });
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  // Load notifications from localStorage when user changes
  useEffect(() => {
    if (user && user.userId) {
      try {
        const saved = localStorage.getItem(`notifications_${user.userId}`);
        if (saved) {
          const parsed = JSON.parse(saved);
          const loadedNotifications = Array.isArray(parsed) ? parsed : [];
          // Only update if different to avoid unnecessary re-renders
          setNotifications(prev => {
            if (JSON.stringify(prev) !== JSON.stringify(loadedNotifications)) {
              return loadedNotifications;
            }
            return prev;
          });
        } else {
          setNotifications([]);
        }
      } catch (e) {
        setNotifications([]);
      }
    } else {
      setNotifications([]);
    }
  }, [user]);

  // Save notifications to localStorage whenever they change
  useEffect(() => {
    if (user && user.userId) {
      try {
        localStorage.setItem(`notifications_${user.userId}`, JSON.stringify(notifications));
      } catch (e) {
        // Failed to save notifications
      }
    }
  }, [notifications, user]);

  useEffect(() => {
    loadProducts();
    loadStocks();
    loadOrders();
    // Initial dashboard load; action handlers refresh individual resources later.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    // Setup WebSocket connection
    if (user && user.userId) {
      websocketService.connect(
        user.userId,
        (data) => {
          // Handle incoming notification - show popup and add to list
          setNotification(data);
          
          // Add to notifications list (use functional update to ensure we don't overwrite)
          const newNotification = {
            id: Date.now() + Math.random(),
            orderId: data.orderId,
            status: data.status,
            subject: data.subject,
            body: data.body,
            timestamp: new Date().toISOString(),
            read: false
          };
          setNotifications(prev => {
            // Check if notification already exists (avoid duplicates)
            const exists = prev.some(n => 
              n.orderId === data.orderId && 
              n.status === data.status && 
              Math.abs(new Date(n.timestamp).getTime() - new Date(newNotification.timestamp).getTime()) < 1000
            );
            if (exists) {
              return prev;
            }
            return [newNotification, ...prev];
          });
          
          // If this is an order status notification, refresh orders list
          const statusTypes = ['PAID', 'DELIVERED', 'FULFILLED', 'CANCELLED', 'REFUNDED', 'PACKAGE_MISSING', 'LOST', 'OUT_FOR_DELIVERY'];
          if (data.status && statusTypes.includes(data.status.toUpperCase())) {
            // Delay slightly to ensure backend has updated
            setTimeout(() => {
              loadOrders();
            }, 800);
          }
          
          // If PAID, refresh after 1 second to show DELIVERING status
          if (data.status && data.status.toUpperCase() === 'PAID') {
            setTimeout(() => {
              loadOrders();
            }, 1000);
          }
        },
        (error) => {
          // WebSocket error
        }
      );

      // Cleanup on unmount or user change
      return () => {
        websocketService.disconnect();
      };
    }
    // Reconnect only when the authenticated user changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const loadProducts = async () => {
    try {
      const response = await productsAPI.getAll();
      setProducts(response.data);
    } catch (error) {
      showMessage('error', 'Failed to load products');
    }
  };

  const loadStocks = async () => {
    try {
      const response = await warehousesAPI.getAllStocks();
      setStocks(response.data);
    } catch (error) {
      // Don't show error message as stock loading failure shouldn't block the user
    }
  };

  const loadOrders = async () => {
    try {
      const response = await ordersAPI.getAll(user.userId);
      setOrders(response.data);
    } catch (error) {
      showMessage('error', 'Failed to load orders');
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const showMessage = (type, text) => {
    setMessage({ type, text });
    setTimeout(() => setMessage({ type: '', text: '' }), 5000);
  };

  const handlePlaceOrder = async () => {
    if (!selectedProduct || quantity < 1) {
      showMessage('error', 'Please select a product and valid quantity');
      return;
    }

    setLoading(true);
    try {
      const response = await ordersAPI.create(selectedProduct.id, quantity);
      
      showMessage('success', `Order #${response.data.orderId} placed successfully!`);
      setSelectedProduct(null);
      setQuantity(1);
      
      // Reload orders and stock after placing order
      await Promise.all([loadOrders(), loadStocks()]);
    } catch (error) {
      if (error.code === 'ERR_NETWORK' || !error.response) {
        showMessage('error', 'Cannot connect to server. Please check backend is running.');
      } else if (error.response?.data?.message) {
        showMessage('error', error.response.data.message);
      } else if (error.response?.status === 400) {
        showMessage('error', 'Invalid order data or insufficient stock.');
      } else {
        showMessage('error', `Failed to place order: ${error.message}`);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId) => {
    if (!window.confirm('Are you sure you want to cancel this order?')) {
      return;
    }

    setLoading(true);
    try {
      await ordersAPI.cancel(orderId);
      showMessage('success', 'Order cancelled successfully!');
      // Reload orders and stock after cancellation
      await Promise.all([loadOrders(), loadStocks()]);
    } catch (error) {
      showMessage('error', error.response?.data?.message || 'Failed to cancel order');
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadgeClass = (status) => {
    const statusMap = {
      'CREATED': 'status-created',
      'PAYMENT_PENDING': 'status-created',
      'PAID': 'status-paid',
      'DELIVERING': 'status-delivering',
      'FULFILLED': 'status-fulfilled',
      'CANCELLED': 'status-cancelled',
      'FAILED': 'status-failed',
      'LOST': 'status-cancelled', // Map LOST to CANCELLED style
    };
    return `status-badge ${statusMap[status] || 'status-created'}`;
  };

  const getDisplayStatus = (status) => {
    const labels = {
      PAYMENT_PENDING: 'PAYMENT PENDING',
      OUT_FOR_DELIVERY: 'OUT FOR DELIVERY',
      LOST: 'PACKAGE LOST'
    };
    return labels[status] || status;
  };

  const canCancelOrder = (status) => {
    return status === 'PAID';
  };

  const getTotalStock = (productId) => {
    return stocks
      .filter(stock => stock.productId === productId)
      .reduce((total, stock) => total + stock.quantity, 0);
  };

  const filteredProducts = products.filter(product =>
    `${product.name} ${product.sku}`.toLowerCase().includes(search.trim().toLowerCase())
  );

  const activeOrders = orders.filter(order =>
    !['FULFILLED', 'CANCELLED', 'FAILED', 'LOST'].includes(order.status)
  ).length;
  const totalInventory = stocks.reduce((total, stock) => total + stock.quantity, 0);

  const handleMarkNotificationAsRead = (notificationId) => {
    setNotifications(prev =>
      prev.map(n => n.id === notificationId ? { ...n, read: true } : n)
    );
  };

  const handleClearAllNotifications = () => {
    setNotifications([]);
    if (user && user.userId) {
      try {
        localStorage.removeItem(`notifications_${user.userId}`);
      } catch (e) {
        // Failed to clear notifications
      }
    }
  };

  return (
    <div>
      {notification && (
        <NotificationModal
          notification={notification}
          onClose={() => setNotification(null)}
        />
      )}
      
      <div className="header">
        <div><p className="brand-kicker">DISTRIBUTED COMMERCE</p><h1>Commerce Platform</h1></div>
        <div className="flex">
          <span className="text-secondary" style={{ marginRight: '16px' }}>
            Welcome, <strong>{user.username}</strong>
          </span>
          <NotificationBell
            notifications={notifications}
            onMarkAsRead={handleMarkNotificationAsRead}
            onClearAll={handleClearAllNotifications}
          />
          <button onClick={handleLogout} className="btn btn-secondary">
            Logout
          </button>
        </div>
      </div>

      <div className="container">
        {message.text && (
          <div className={`alert alert-${message.type}`}>
            {message.text}
          </div>
        )}

        <section className="metric-grid" aria-label="Store overview">
          <div className="metric-card"><span>Catalog</span><strong>{products.length}</strong><small>products available</small></div>
          <div className="metric-card"><span>Inventory</span><strong>{totalInventory}</strong><small>units across warehouses</small></div>
          <div className="metric-card"><span>Active orders</span><strong>{activeOrders}</strong><small>in progress</small></div>
        </section>

        <div className="nav mb-4">
          <button 
            onClick={() => setActiveTab('products')}
            style={{ 
              background: activeTab === 'products' ? 'var(--apple-blue)' : 'transparent',
              color: activeTab === 'products' ? 'white' : 'var(--apple-blue)'
            }}
          >
            Browse Products
          </button>
          <button 
            onClick={() => setActiveTab('orders')}
            style={{ 
              background: activeTab === 'orders' ? 'var(--apple-blue)' : 'transparent',
              color: activeTab === 'orders' ? 'white' : 'var(--apple-blue)'
            }}
          >
            My Orders
          </button>
        </div>

        {activeTab === 'products' && (
          <div>
            <div className="section-heading">
              <div><p className="eyebrow">CATALOG</p><h2>Available Products</h2></div>
              <input
                className="search-input"
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search product or SKU"
                aria-label="Search products"
              />
            </div>
            
            {selectedProduct && (
              <div className="card mb-4" style={{ background: 'var(--apple-bg-secondary)' }}>
                <h3>Place Order</h3>
                <div className="grid-cols-2">
                  <div>
                    <p><strong>{selectedProduct.name}</strong></p>
                    <p>Price: ${selectedProduct.price}</p>
                    <p className={`stock-info ${getTotalStock(selectedProduct.id) > 0 ? 'stock-available' : 'stock-out'}`}>
                      Available Stock: {getTotalStock(selectedProduct.id)}
                    </p>
                    <div className="form-group mt-4">
                      <label>Quantity</label>
                      <input
                        type="number"
                        min="1"
                        max={getTotalStock(selectedProduct.id)}
                        value={quantity}
                        onChange={(e) => setQuantity(parseInt(e.target.value))}
                      />
                    </div>
                    <p style={{ marginTop: '8px', fontSize: '24px', fontWeight: 600, color: 'var(--apple-blue)', letterSpacing: '-0.022em' }}>
                      Total: ${(selectedProduct.price * quantity).toFixed(2)}
                    </p>
                  </div>
                  <div className="flex">
                    <button onClick={handlePlaceOrder} className="btn btn-success" disabled={loading}>
                      {loading ? 'Processing...' : 'Place Order'}
                    </button>
                    <button onClick={() => setSelectedProduct(null)} className="btn btn-secondary">
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            )}

            <div className="grid">
              {filteredProducts.map(product => {
                const totalStock = getTotalStock(product.id);
                return (
                  <div key={product.id} className="product-card">
                    <div className="sku">SKU: {product.sku}</div>
                    <h3>{product.name}</h3>
                    <div className="price">${product.price}</div>
                    <div className={`stock-info ${totalStock > 0 ? 'stock-available' : 'stock-out'}`}>
                      Stock: {totalStock > 0 ? `${totalStock} available` : 'Out of stock'}
                    </div>
                    <button 
                      onClick={() => setSelectedProduct(product)}
                      className="btn btn-primary"
                      style={{ width: '100%', marginTop: '12px' }}
                      disabled={totalStock === 0}
                    >
                      {totalStock > 0 ? 'Select to Order' : 'Out of Stock'}
                    </button>
                  </div>
                );
              })}
            </div>
            {products.length > 0 && filteredProducts.length === 0 && (
              <div className="empty-state"><h3>No matching products</h3><p>Try another product name or SKU.</p></div>
            )}

            {products.length === 0 && (
              <div className="empty-state">
                <h3>No products available</h3>
              </div>
            )}
          </div>
        )}

        {activeTab === 'orders' && (
          <div>
            <h2>My Orders</h2>
            
            {orders.length > 0 ? (
              <div className="card">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Order ID</th>
                      <th>Total Amount</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.map(order => (
                      <tr key={order.orderId}>
                        <td>#{order.orderId}</td>
                        <td>${order.totalAmount}</td>
                        <td>
                          <span className={getStatusBadgeClass(order.status)}>
                            {getDisplayStatus(order.status)}
                          </span>
                        </td>
                        <td>
                          {canCancelOrder(order.status) && (
                            <button 
                              onClick={() => handleCancelOrder(order.orderId)}
                              className="btn btn-danger"
                              disabled={loading}
                            >
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-state">
                <h3>No orders yet</h3>
                <p>Start shopping to see your orders here!</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default UserDashboard;




