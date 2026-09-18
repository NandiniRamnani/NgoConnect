import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Building2, Megaphone, CalendarDays, HeartHandshake, Users, Bell,
  ArrowRight, ShieldCheck, UserPlus, Search, Rocket, TrendingUp, MapPin,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './Home.css';
import Avatar from '../components/Avatar';

const features = [
  { icon: Building2, title: 'NGO Registration', desc: 'NGOs can register, create profiles, and manage their presence on the platform.' },
  { icon: Megaphone, title: 'Post Needs', desc: 'Share resource needs — volunteers, supplies, funds — with the entire community.' },
  { icon: CalendarDays, title: 'Manage Events', desc: 'Create events, track enrollments, and engage with volunteers seamlessly.' },
  { icon: HeartHandshake, title: 'Easy Donations', desc: 'Users can donate money directly to NGOs from the platform securely.' },
  { icon: Users, title: 'Volunteer Enrollment', desc: 'Browse and enroll in events that match your passion and availability.' },
  { icon: Bell, title: 'Real-time Notifications', desc: 'Stay updated with event reminders, NGO announcements, and more.' },
];

const events = [
  { title: 'Tree Plantation Drive', ngo: 'Green Earth', date: 'Aug 18, 2025', location: 'Pune', spots: 50, enrolled: 38 },
  { title: 'Free Health Camp', ngo: 'Health For All', date: 'Aug 22, 2025', location: 'Mumbai', spots: 100, enrolled: 72 },
  { title: 'Book Donation Day', ngo: 'Shiksha Setu', date: 'Sep 1, 2025', location: 'Delhi', spots: 80, enrolled: 45 },
];

const steps = [
  { step: '01', title: 'Create Account', desc: 'Register as a User or NGO in under 2 minutes.', icon: UserPlus },
  { step: '02', title: 'Explore & Connect', desc: 'Browse NGOs, events and community needs.', icon: Search },
  { step: '03', title: 'Take Action', desc: 'Enroll in events, donate, or post your needs.', icon: Rocket },
  { step: '04', title: 'Create Impact', desc: 'Track your contributions and watch change happen.', icon: TrendingUp },
];

export default function Home() {
  const { user } = useAuth();
  const [featuredNgos, setFeaturedNgos] = useState([]);

  useEffect(() => {
    api.get('/ngos').then(r => setFeaturedNgos(r.data.slice(0, 3))).catch(() => {});
  }, []);

  return (
    <div className="home">
      {/* Hero */}
      <section className="hero-section">
        <div className="hero-bg-effects">
          <div className="hero-grid" />
        </div>
        <div className="container">
          <div className="hero-content">
            <span className="eyebrow-label">NGO & Volunteer Platform</span>
            <h1 className="hero-title">
              Connect. Contribute.<br />
              <span className="gradient-text">Change Lives.</span>
            </h1>
            <p className="hero-subtitle">
              A unified platform where NGOs can share their needs, host events, and receive donations — while volunteers and donors find meaningful ways to give back.
            </p>
            <div className="hero-actions">
              {user ? (
                <Link to="/dashboard" className="btn btn-primary btn-lg">Go to Dashboard <ArrowRight size={18} /></Link>
              ) : (
                <>
                  <Link to="/register" className="btn btn-primary btn-lg">Get Started Free</Link>
                  <Link to="/ngos" className="btn btn-secondary btn-lg">Explore NGOs</Link>
                </>
              )}
            </div>
            <div className="hero-trust">
              <ShieldCheck size={16} />
              <span>Every NGO is manually verified before it can raise funds</span>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="section features-section">
        <div className="container">
          <div className="section-header">
            <span className="eyebrow-label">Platform Features</span>
            <h2>Everything you need to make an impact</h2>
            <p>Whether you're an NGO or a volunteer, we've built tools that help you create real change.</p>
          </div>
          <div className="features-grid">
            {features.map((f, i) => (
              <div key={i} className="feature-card">
                <div className="feature-icon"><f.icon size={22} strokeWidth={1.8} /></div>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Featured NGOs */}
      <section className="section">
        <div className="container">
          <div className="section-header">
            <span className="eyebrow-label">Featured NGOs</span>
            <h2>Making a Difference Daily</h2>
            <p>Join these incredible organizations and help amplify their impact.</p>
          </div>
          {featuredNgos.length > 0 ? (
            <div className="events-list">
              {featuredNgos.map(ngo => (
                <div key={ngo.id} className="event-preview-card card">
                  <Avatar src={ngo.logoUrl} name={ngo.ngoName} size={56} />
                  <div className="event-info">
                    <h3>{ngo.ngoName} <span className="verified-inline"><ShieldCheck size={13} /> Verified</span></h3>
                    <p>{ngo.ngoType} · {ngo.location}</p>
                    <p className="ngo-desc-preview">{ngo.description?.substring(0, 100)}{ngo.description?.length > 100 ? '...' : ''}</p>
                  </div>
                  <Link to={`/ngos/${ngo.id}`} className="btn btn-primary btn-sm">View</Link>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state card">
              <h3>Verified organisations are joining soon</h3>
              <p>Browse the directory to see organisations after an admin approves their application.</p>
            </div>
          )}
          <div className="section-cta">
            <Link to="/ngos" className="btn btn-primary">Browse All NGOs <ArrowRight size={16} /></Link>
          </div>
        </div>
      </section>

      {/* Upcoming Events */}
      <section className="section events-preview-section">
        <div className="container">
          <div className="section-header">
            <span className="eyebrow-label">Upcoming Events</span>
            <h2>Ready to Volunteer?</h2>
            <p>These events need your help. Enroll now and make a difference this weekend.</p>
          </div>
          <div className="events-list">
            {events.map((ev, i) => (
              <div key={i} className="event-preview-card card">
                <div className="event-icon-tile"><CalendarDays size={24} strokeWidth={1.8} /></div>
                <div className="event-info">
                  <h3>{ev.title}</h3>
                  <p>by <strong>{ev.ngo}</strong> · <MapPin size={12} className="inline-icon" /> {ev.location}</p>
                  <div className="event-meta">
                    <span>{ev.date}</span>
                    <span>{ev.enrolled}/{ev.spots} enrolled</span>
                  </div>
                  <div className="progress-bar">
                    <div className="progress-fill" style={{ width: `${(ev.enrolled/ev.spots)*100}%` }} />
                  </div>
                </div>
                <Link to="/events" className="btn btn-primary btn-sm">Enroll</Link>
              </div>
            ))}
          </div>
          <div className="section-cta">
            <Link to="/events" className="btn btn-secondary">See All Events</Link>
          </div>
        </div>
      </section>

      {/* Donate CTA */}
      <section className="section donate-cta">
        <div className="container">
          <div className="donate-cta-inner">
            <div className="donate-cta-content">
              <span className="eyebrow-label">Make a Difference</span>
              <h2>Your donation creates<br />real, lasting change</h2>
              <p>Even ₹100 can help an NGO buy school supplies, plant a tree, or feed a family. Every rupee counts.</p>
              <div className="amount-chips">
                {['₹100', '₹500', '₹1000', '₹5000', 'Custom'].map(a => (
                  <Link key={a} to="/donate" className="amount-chip">{a}</Link>
                ))}
              </div>
              <Link to="/donate" className="btn btn-accent btn-lg">Donate Now</Link>
            </div>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="section">
        <div className="container">
          <div className="section-header">
            <span className="eyebrow-label">How It Works</span>
            <h2>Simple. Powerful. Impactful.</h2>
          </div>
          <div className="how-grid">
            {steps.map((step, i) => (
              <div key={i} className="how-step">
                <div className="step-number">{step.step}</div>
                <div className="step-icon"><step.icon size={26} strokeWidth={1.8} /></div>
                <h3>{step.title}</h3>
                <p>{step.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
