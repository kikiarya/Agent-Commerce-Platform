import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authAPI } from '../services/api';

function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login(username, password);
      const userData = response.data;
      login(userData);
      
      // Redirect based on role
      if (userData.role === 'ADMIN') {
        navigate('/admin');
      } else {
        navigate('/user');
      }
    } catch (err) {
      if (err.code === 'ERR_NETWORK' || !err.response) {
        setError('Cannot connect to server. Please make sure backend is running on port 8080.');
      } else if (err.response?.status === 401) {
        setError('Invalid username or password');
      } else {
        setError(`Login failed: ${err.message || 'Please try again.'}`);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleQuickLogin = async (user, pass) => {
    setUsername(user);
    setPassword(pass);
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login(user, pass);
      const userData = response.data;
      login(userData);
      
      // Redirect based on role
      if (userData.role === 'ADMIN') {
        navigate('/admin');
      } else {
        navigate('/user');
      }
    } catch (err) {
      if (err.code === 'ERR_NETWORK' || !err.response) {
        setError('Cannot connect to server. Please make sure backend is running on port 8080.');
      } else if (err.response?.status === 401) {
        setError('Invalid username or password');
      } else {
        setError(`Login failed: ${err.message || 'Please try again.'}`);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container">
      <div className="login-container">
        <p className="brand-kicker">DISTRIBUTED COMMERCE</p>
        <h1 className="text-center mb-4">Commerce Platform</h1>
        <h2 className="text-center mb-4 text-secondary" style={{ fontSize: '24px' }}>
          Login to Continue
        </h2>

        {error && (
          <div className="alert alert-error">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter username"
              required
            />
          </div>

          <div className="form-group">
            <label>Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
              required
            />
          </div>

          <button 
            type="submit" 
            className="btn btn-primary btn-full"
            disabled={loading}
          >
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </form>

        <div className="login-link-section">
          <p>
            Don't have an account?{' '}
            <Link to="/register">
              Register here
            </Link>
          </p>
        </div>

        <div className="quick-login-section">
          <p>Quick Login:</p>
          <div className="quick-login-buttons">
            <button 
              className="btn btn-secondary" 
              onClick={() => handleQuickLogin('customer', 'COMP5348')}
            >
              Customer (customer / COMP5348)
            </button>
            <button 
              className="btn btn-secondary" 
              onClick={() => handleQuickLogin('admin', 'admin123')}
            >
              Admin (admin / admin123)
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Login;




