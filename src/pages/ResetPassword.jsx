import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/axios';
import './Login.css';

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async e => {
    e.preventDefault();
    setError('');
    if (password.length < 8) return setError('Use a password with at least 8 characters.');
    if (password !== confirmPassword) return setError('Passwords do not match.');

    setLoading(true);
    try {
      await api.post('/accounts/reset-password', { token, newPassword: password });
      navigate('/login', { state: { message: 'Password updated. Please sign in.' } });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not reset password. The link may have expired.');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-left login-left">
            <div className="auth-header"><h2>Invalid link</h2></div>
            <div className="alert error">This reset link is missing its token. Please request a new one.</div>
            <div className="auth-footer"><p><Link to="/forgot-password">Request a new link</Link></p></div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-background">
        <div className="orb orb-1"></div>
        <div className="orb orb-2"></div>
        <div className="orb orb-3"></div>
      </div>

      <div className="auth-card">
        <div className="auth-left login-left">
          <div className="auth-header">
            <h2>Choose a new password</h2>
            <p>Make it at least 8 characters.</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit}>
            {error && <div className="alert error">{error}</div>}
            <div className="form-group">
              <label>New password</label>
              <input type="password" value={password} onChange={e => setPassword(e.target.value)} required placeholder="••••••••" />
            </div>
            <div className="form-group">
              <label>Confirm new password</label>
              <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} required placeholder="••••••••" />
            </div>
            <button type="submit" className="submit-btn" disabled={loading}>
              {loading ? 'Updating...' : 'Update password'}
            </button>
          </form>

          <div className="auth-footer">
            <p><Link to="/login">Back to sign in</Link></p>
          </div>
        </div>

        <div className="auth-right login-right">
          <div className="illustration-panel">
            <h3>Almost there</h3>
            <p>Pick a strong password you haven't used before.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
