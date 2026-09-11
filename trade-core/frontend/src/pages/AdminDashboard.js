import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { productsAPI, ordersAPI, warehousesAPI } from '../services/api';

function AdminDashboard() {
  const [activeTab, setActiveTab] = useState('products');
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [stocks, setStocks] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [showModal, setShowModal] = useState(false);
  const [modalType, setModalType] = useState('');
  const [editingItem, setEditingItem] = useState(null);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    loadData();
    // The selected tab intentionally controls which endpoint is loaded.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  const loadData = () => {
    if (activeTab === 'products') loadProducts();
    if (activeTab === 'warehouses') loadWarehouses();
    if (activeTab === 'stocks') loadStocks();
    if (activeTab === 'orders') loadOrders();
  };

  const loadProducts = async () => {
    try {
      const response = await productsAPI.getAll();
      setProducts(response.data);
    } catch (error) {
      showMessage('error', 'Failed to load products');
    }
  };

  const loadWarehouses = async () => {
    try {
      const response = await warehousesAPI.getAll();
      setWarehouses(response.data);
    } catch (error) {
      showMessage('error', 'Failed to load warehouses');
    }
  };

  const loadStocks = async () => {
    try {
      const response = await warehousesAPI.getAllStocks();
      setStocks(response.data);
    } catch (error) {
      showMessage('error', 'Failed to load stocks');
    }
  };

  const loadOrders = async () => {
    try {
      const response = await ordersAPI.getAll();
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

  const openModal = (type, item = null) => {
    setModalType(type);
    setEditingItem(item);
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setModalType('');
    setEditingItem(null);
  };

  const handleDeleteProduct = async (id) => {
    if (!window.confirm('Are you sure you want to delete this product?')) return;
    
    setLoading(true);
    try {
      await productsAPI.delete(id);
      showMessage('success', 'Product deleted successfully');
      loadProducts();
    } catch (error) {
      showMessage('error', 'Failed to delete product');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteWarehouse = async (id) => {
    if (!window.confirm('Are you sure you want to delete this warehouse?')) return;
    
    setLoading(true);
    try {
      await warehousesAPI.delete(id);
      showMessage('success', 'Warehouse deleted successfully');
      loadWarehouses();
    } catch (error) {
      showMessage('error', 'Failed to delete warehouse');
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadgeClass = (status) => {
    const statusMap = {
      'CREATED': 'status-created',
      'PAYMENT_PENDING': 'status-created',
      'PAID': 'status-paid',
      'FULFILLED': 'status-fulfilled',
      'CANCELLED': 'status-cancelled',
      'FAILED': 'status-failed',
      'LOST': 'status-cancelled', // Map LOST to CANCELLED style
    };
    return `status-badge ${statusMap[status] || 'status-created'}`;
  };

  const getDisplayStatus = (status) => {
    const labels = { PAYMENT_PENDING: 'PAYMENT PENDING', LOST: 'PACKAGE LOST' };
    return labels[status] || status;
  };

  return (
    <div>
      <div className="header">
        <div><p className="brand-kicker">OPERATIONS CONSOLE</p><h1>Commerce Platform</h1></div>
        <div className="flex">
          <span style={{ marginRight: '16px', color: '#6c757d' }}>
            Admin: <strong>{user.username}</strong>
          </span>
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

        <div className="nav" style={{ marginBottom: '24px', background: 'white', padding: '12px', borderRadius: '8px' }}>
          <button 
            onClick={() => setActiveTab('products')}
            style={{ 
              background: activeTab === 'products' ? '#667eea' : 'transparent',
              color: activeTab === 'products' ? 'white' : '#667eea'
            }}
          >
            Products
          </button>
          <button 
            onClick={() => setActiveTab('warehouses')}
            style={{ 
              background: activeTab === 'warehouses' ? '#667eea' : 'transparent',
              color: activeTab === 'warehouses' ? 'white' : '#667eea'
            }}
          >
            Warehouses
          </button>
          <button 
            onClick={() => setActiveTab('stocks')}
            style={{ 
              background: activeTab === 'stocks' ? '#667eea' : 'transparent',
              color: activeTab === 'stocks' ? 'white' : '#667eea'
            }}
          >
            Stock Levels
          </button>
          <button 
            onClick={() => setActiveTab('orders')}
            style={{ 
              background: activeTab === 'orders' ? '#667eea' : 'transparent',
              color: activeTab === 'orders' ? 'white' : '#667eea'
            }}
          >
            All Orders
          </button>
        </div>

        {/* Products Tab */}
        {activeTab === 'products' && (
          <div>
            <div className="flex-between mb-4">
              <h2 style={{ color: '#333' }}>Products Management</h2>
              <button onClick={() => openModal('product')} className="btn btn-primary">
                + Add Product
              </button>
            </div>

            <div className="card">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>SKU</th>
                    <th>Name</th>
                    <th>Price</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map(product => (
                    <tr key={product.id}>
                      <td>{product.id}</td>
                      <td>{product.sku}</td>
                      <td>{product.name}</td>
                      <td>${product.price}</td>
                      <td>
                        <div className="flex" style={{ gap: '8px' }}>
                          <button 
                            onClick={() => openModal('product', product)}
                            className="btn btn-secondary"
                            disabled={loading}
                            style={{ fontSize: '12px', padding: '6px 12px' }}
                          >
                            Edit
                          </button>
                          <button 
                            onClick={() => handleDeleteProduct(product.id)}
                            className="btn btn-danger"
                            disabled={loading}
                            style={{ fontSize: '12px', padding: '6px 12px' }}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Warehouses Tab */}
        {activeTab === 'warehouses' && (
          <div>
            <div className="flex-between mb-4">
              <h2 style={{ color: '#333' }}>Warehouses Management</h2>
              <button onClick={() => openModal('warehouse')} className="btn btn-primary">
                + Add Warehouse
              </button>
            </div>

            <div className="card">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Name</th>
                    <th>Location</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {warehouses.map(warehouse => (
                    <tr key={warehouse.id}>
                      <td>{warehouse.id}</td>
                      <td>{warehouse.name}</td>
                      <td>{warehouse.location}</td>
                      <td>
                        <div className="flex" style={{ gap: '8px' }}>
                          <button 
                            onClick={() => openModal('warehouse', warehouse)}
                            className="btn btn-secondary"
                            disabled={loading}
                            style={{ fontSize: '12px', padding: '6px 12px' }}
                          >
                            Edit
                          </button>
                          <button 
                            onClick={() => handleDeleteWarehouse(warehouse.id)}
                            className="btn btn-danger"
                            disabled={loading}
                            style={{ fontSize: '12px', padding: '6px 12px' }}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Stocks Tab */}
        {activeTab === 'stocks' && (
          <div>
            <h2 className="mb-4" style={{ color: '#333' }}>Stock Levels</h2>

            <div className="card">
              <table className="table">
                <thead>
                  <tr>
                    <th>Warehouse</th>
                    <th>Product</th>
                    <th>Quantity</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {stocks.map(stock => (
                    <tr key={stock.id}>
                      <td>{stock.warehouseName}</td>
                      <td>{stock.productName}</td>
                      <td><strong>{stock.quantity}</strong></td>
                      <td>
                        <span className={`status-badge ${stock.quantity > 20 ? 'status-fulfilled' : stock.quantity > 5 ? 'status-created' : 'status-failed'}`}>
                          {stock.quantity > 20 ? 'Good Stock' : stock.quantity > 5 ? 'Low Stock' : 'Very Low'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Orders Tab */}
        {activeTab === 'orders' && (
          <div>
            <h2 className="mb-4" style={{ color: '#333' }}>All Orders</h2>

            <div className="card">
              <table className="table">
                <thead>
                  <tr>
                    <th>Order ID</th>
                    <th>Total Amount</th>
                    <th>Status</th>
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
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* Modal for Add/Edit */}
      {showModal && (
        <ProductWarehouseModal
          type={modalType}
          item={editingItem}
          onClose={closeModal}
          onSuccess={() => {
            closeModal();
            loadData();
            showMessage('success', `${modalType === 'product' ? 'Product' : 'Warehouse'} saved successfully`);
          }}
          onError={(msg) => showMessage('error', msg)}
        />
      )}
    </div>
  );
}

// Modal Component for Product/Warehouse Add/Edit
function ProductWarehouseModal({ type, item, onClose, onSuccess, onError }) {
  const [formData, setFormData] = useState(
    item || (type === 'product' 
      ? { sku: '', name: '', price: '' }
      : { name: '', location: '' })
  );
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      if (type === 'product') {
        const productData = {
          ...formData,
          price: parseFloat(formData.price)
        };
        
        if (item) {
          await productsAPI.update(item.id, productData);
        } else {
          await productsAPI.create(productData);
        }
      } else {
        if (item) {
          await warehousesAPI.update(item.id, formData);
        } else {
          await warehousesAPI.create(formData);
        }
      }
      onSuccess();
    } catch (error) {
      onError(error.response?.data?.message || 'Failed to save');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  return (
    <div className="modal">
      <div className="modal-content">
        <div className="modal-header">
          <h2>{item ? 'Edit' : 'Add'} {type === 'product' ? 'Product' : 'Warehouse'}</h2>
          <button className="modal-close" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          {type === 'product' ? (
            <>
              <div className="form-group">
                <label>SKU</label>
                <input
                  name="sku"
                  value={formData.sku}
                  onChange={handleChange}
                  placeholder="e.g., SK8-001"
                  required
                />
              </div>
              <div className="form-group">
                <label>Name</label>
                <input
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  placeholder="e.g., Professional Skateboard"
                  required
                />
              </div>
              <div className="form-group">
                <label>Price</label>
                <input
                  type="number"
                  step="0.01"
                  name="price"
                  value={formData.price}
                  onChange={handleChange}
                  placeholder="e.g., 199.99"
                  required
                />
              </div>
            </>
          ) : (
            <>
              <div className="form-group">
                <label>Name</label>
                <input
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  placeholder="e.g., Sydney Warehouse"
                  required
                />
              </div>
              <div className="form-group">
                <label>Location</label>
                <input
                  name="location"
                  value={formData.location}
                  onChange={handleChange}
                  placeholder="e.g., Sydney, NSW"
                  required
                />
              </div>
            </>
          )}

          <div className="flex" style={{ gap: '8px', marginTop: '20px' }}>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : 'Save'}
            </button>
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default AdminDashboard;




