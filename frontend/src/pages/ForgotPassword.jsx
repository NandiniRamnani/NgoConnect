import { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axios';
import './Login.css';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async e => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/accounts/forgot-password', { email });
      setSent(true);
    } catch (err) {
      // Show what the server actually said rather than a blanket message. When the mail server
      // is misconfigured this is the difference between "Something went wrong" and "Could not
      // send the reset email", which is the clue that points at the credentials.
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

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
            <h2>Forgot Password</h2>
            <p>Enter your email and we'll send you a link to reset it.</p>
          </div>

          {sent ? (
            <div className="alert success">
              If that email is registered, a reset link has been sent. Check your inbox (and spam folder).
            </div>
          ) : (
            <form className="auth-form" onSubmit={handleSubmit}>
              {error && <div className="alert error">{error}</div>}
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                  placeholder="you@example.com"
                />
              </div>
              <button type="submit" className="submit-btn" disabled={loading}>
                {loading ? 'Sending...' : 'Send reset link'}
              </button>
            </form>
          )}

          <div className="auth-footer">
            <p><Link to="/login">Back to sign in</Link></p>
          </div>
        </div>

        <div className="auth-right login-right">
          <div className="illustration-panel">
            <h3>Locked out?</h3>
            <p>It happens. We'll get you back into your account in a couple of minutes.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
