import React, { useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import PasswordInput from '../components/PasswordInput';
import './Login.css';

const Login = () => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [notice, setNotice] = useState(location.state?.message || '');

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    if (!formData.email || !formData.password) {
      return setError('Please fill in all fields');
    }

    setLoading(true);
    try {
      const response = await api.post('/accounts/login', formData);
      // Store _pass so Dashboard can make authenticated API calls
      login({ ...response.data, _pass: formData.password });
      // A page that sent the visitor here to log in (e.g. "Sponsor this meal") passes where to go
      // next, so they carry on with what they were doing instead of landing on the dashboard.
      const next = location.state?.redirectTo;
      if (next?.pathname) navigate(next.pathname, { state: next.state });
      else navigate('/dashboard');
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || '';
      if (err.response?.status === 401) setError('Incorrect email or password.');
      else if (err.response?.status === 403) setError(msg || 'Your NGO registration is pending admin approval.');
      else setError('Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-left login-left">
          <div className="auth-header">
            <h2>Welcome Back</h2>
            <p>Log in to continue making an impact</p>
          </div>
          
          {false && <div className="demo-notice">
            <span className="demo-icon">ℹ️</span>
            Backend login endpoint coming soon - demo mode active. Any email/password will log you in.
          </div>}

          {notice && <div className="alert success">{notice}</div>}
          {error && <div className="alert error">{error}</div>}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Email</label>
              <input 
                type="email" 
                name="email" 
                value={formData.email} 
                onChange={handleChange} 
                required 
                placeholder="you@example.com"
              />
            </div>

            <div className="form-group">
              <label>Password</label>
              <PasswordInput
                name="password"
                value={formData.password}
                onChange={handleChange}
                required
                placeholder="Enter your password"
              />
            </div>

            <div className="forgot-password">
              <Link to="/forgot-password">Forgot password?</Link>
            </div>

            <button type="submit" className="submit-btn" disabled={loading}>
              {loading ? 'Logging in...' : 'Log In'}
            </button>
          </form>

          <div className="auth-footer">
            <p>Don't have an account? <Link to="/register">Register here</Link></p>
          </div>
        </div>
        
        <div className="auth-right login-right">
          <div className="illustration-panel">
            <h3>Your Impact Dashboard</h3>
            <p>Track your volunteer hours, see upcoming events, and connect with NGOs you follow.</p>
            <div className="features-list">
              <div className="feature-item">
                <div className="feature-icon">✨</div>
                <span>Discover new opportunities</span>
              </div>
              <div className="feature-item">
                <div className="feature-icon">📈</div>
                <span>Track your volunteer hours</span>
              </div>
              <div className="feature-item">
                <div className="feature-icon">🤝</div>
                <span>Connect with your community</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
