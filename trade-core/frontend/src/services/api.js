import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to automatically add user ID to header
api.interceptors.request.use(
  (config) => {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        if (user.userId) {
          config.headers['X-User-Id'] = user.userId;
        }
      } catch (e) {
        // Failed to parse user data
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Auth API
export const authAPI = {
  login: (username, password) => 
    api.post('/auth/login', { username, password }),
  register: (username, password, email) =>
    api.post('/auth/register', { username, password, email }),
  checkUsername: (username) =>
    api.get(`/auth/check-username?username=${username}`),
};

// Products API
export const productsAPI = {
  getAll: () => api.get('/products'),
  getById: (id) => api.get(`/products/${id}`),
  create: (product) => api.post('/products', product),
  update: (id, product) => api.put(`/products/${id}`, product),
  delete: (id) => api.delete(`/products/${id}`),
};

// Orders API
export const ordersAPI = {
  getAll: (userId) => api.get(`/orders${userId ? `?userId=${userId}` : ''}`),
  getById: (id) => api.get(`/orders/${id}`),
  create: (productId, quantity) => {
    const idempotencyKey = `order-${Date.now()}-${Math.random()}`;
    return api.post('/orders', 
      { productId, quantity },
      { headers: { 'Idempotency-Key': idempotencyKey } }
    );
  },
  cancel: (id) => api.post(`/orders/${id}/cancel`),
};

// Warehouses API
export const warehousesAPI = {
  getAll: () => api.get('/warehouses'),
  getById: (id) => api.get(`/warehouses/${id}`),
  create: (warehouse) => api.post('/warehouses', warehouse),
  update: (id, warehouse) => api.put(`/warehouses/${id}`, warehouse),
  delete: (id) => api.delete(`/warehouses/${id}`),
  getAllStocks: () => api.get('/warehouses/stocks'),
  getWarehouseStocks: (id) => api.get(`/warehouses/${id}/stocks`),
};

export default api;




