import { Link } from 'react-router-dom';
import './Footer.css';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer-glow" />
      <div className="container">
        <div className="footer-grid">
          <div className="footer-brand">
            <div className="footer-logo">
              <div className="logo-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <path d="M12 2L3 7l9 5 9-5-9-5z" fill="currentColor"/>
                  <path d="M3 7v10l9 5V12L3 7z" fill="currentColor" opacity="0.6"/>
                </svg>
              </div>
              <span>NGO<span style={{color:'var(--primary-light)'}}>Connect</span></span>
            </div>
            <p>Bridging compassion with action. Connecting NGOs and volunteers to create meaningful impact across communities.</p>
            <div className="social-links">
              <a href="#" className="social-btn">🐦</a>
              <a href="#" className="social-btn">📘</a>
              <a href="#" className="social-btn">📸</a>
              <a href="#" className="social-btn">💼</a>
            </div>
          </div>

          <div className="footer-links-col">
            <h4>Platform</h4>
            <Link to="/ngos">Browse NGOs</Link>
            <Link to="/events">Events</Link>
            <Link to="/needs">Needs Board</Link>
            <Link to="/donate">Donate</Link>
          </div>

          <div className="footer-links-col">
            <h4>Account</h4>
            <Link to="/register">Register</Link>
            <Link to="/login">Login</Link>
            <Link to="/register?role=NGO">Register as NGO</Link>
            <Link to="/dashboard">Dashboard</Link>
          </div>

          <div className="footer-links-col">
            <h4>About</h4>
            <a href="#">How it works</a>
            <a href="#">Our Mission</a>
            <a href="#">Privacy Policy</a>
            <a href="#">Contact Us</a>
          </div>
        </div>

        <div className="footer-bottom">
          <p>© 2025 NGOConnect. Built with ❤️ to make the world better.</p>
          <div className="footer-badges">
            <span className="badge badge-accent">🌱 Eco Friendly</span>
            <span className="badge badge-primary">🔒 Secure</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
