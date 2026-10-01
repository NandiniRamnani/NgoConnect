import { Link } from 'react-router-dom';
import { Leaf, Lock } from 'lucide-react';
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
              <a href="#" className="social-btn" aria-label="Twitter">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M18.9 2H22l-7.6 8.7L23.3 22h-7l-5.5-7.2L4.5 22H1.3l8.1-9.3L1 2h7.2l5 6.6L18.9 2zm-1.2 18h1.7L7.4 4H5.6l12.1 16z"/></svg>
              </a>
              <a href="#" className="social-btn" aria-label="Facebook">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M13.5 21v-8.2h2.8l.4-3.2h-3.2V7.4c0-.9.3-1.6 1.7-1.6h1.7V2.9C16.6 2.8 15.5 2.7 14.3 2.7c-2.7 0-4.5 1.6-4.5 4.6v2.3H7v3.2h2.8V21h3.7z"/></svg>
              </a>
              <a href="#" className="social-btn" aria-label="Instagram">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.5" cy="6.5" r="1"/></svg>
              </a>
              <a href="#" className="social-btn" aria-label="LinkedIn">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M6.9 8.4H3.3V21h3.6V8.4zM5.1 3a2.1 2.1 0 1 0 0 4.2 2.1 2.1 0 0 0 0-4.2zM21 13.9c0-3.6-1.9-5.3-4.5-5.3-2.1 0-3 1.1-3.5 1.9V8.4h-3.6v12.6H13v-7c0-1.9 1-3 2.5-3s2.4 1 2.4 3v7H21v-7.1z"/></svg>
              </a>
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
            <span className="badge badge-accent"><Leaf size={12} /> Eco Friendly</span>
            <span className="badge badge-primary"><Lock size={12} /> Secure</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
