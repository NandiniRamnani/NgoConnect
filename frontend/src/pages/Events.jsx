import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PartyPopper, Stethoscope, GraduationCap, Leaf, UtensilsCrossed, Sparkles, CalendarDays, MapPin, CheckCircle2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './Events.css';

const CATEGORIES = ['All', 'General', 'Health', 'Education', 'Environment', 'Food', 'Other'];
const CATEGORY_ICON = { General: PartyPopper, Health: Stethoscope, Education: GraduationCap, Environment: Leaf, Food: UtensilsCrossed, Other: Sparkles };

export default function Events() {
  const [activeCategory, setActiveCategory] = useState('All');
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [modalState, setModalState] = useState('confirm'); // confirm, success, error
  const [modalError, setModalError] = useState('');
  const [enrolling, setEnrolling] = useState(false);
  const [selectedEvent, setSelectedEvent] = useState(null);

  const authContext = useAuth();
  const user = authContext?.user;
  const navigate = useNavigate();

  const loadEvents = (category) => {
    setLoading(true); setError('');
    const params = category && category !== 'All' ? { category } : undefined;
    api.get('/events', { params })
      .then(response => setEvents(response.data))
      .catch(() => setError('Events could not be loaded right now.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadEvents(activeCategory); }, [activeCategory]);

  const handleEnrollClick = (event) => {
    if (!user) {
      navigate('/login');
      return;
    }
    setSelectedEvent(event);
    setModalState('confirm');
    setModalError('');
    setShowModal(true);
  };

  const confirmEnrollment = async () => {
    setEnrolling(true);
    try {
      const creds = btoa(`${user.email}:${user._pass || ''}`);
      await api.post(`/events/${selectedEvent.id}/enroll`, null, {
        headers: { Authorization: `Basic ${creds}` },
      });
      setModalState('success');
      setEvents(prev => prev.map(e => e.id === selectedEvent.id ? { ...e, enrolledCount: (e.enrolledCount || 0) + 1 } : e));
      setTimeout(() => { setShowModal(false); setSelectedEvent(null); }, 2000);
    } catch (err) {
      setModalState('error');
      setModalError(err.response?.data?.message || 'Could not enroll in this event.');
    } finally {
      setEnrolling(false);
    }
  };

  return (
    <div className="events-page">
      <div className="events-header">
        <h1>Upcoming Events</h1>
        <p>Volunteer your time and make an impact in your community.</p>

        <div className="events-filters">
          {CATEGORIES.map(cat => (
            <button
              key={cat}
              className={`filter-tab ${activeCategory === cat ? 'active' : ''}`}
              onClick={() => setActiveCategory(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {loading && <p className="no-results">Loading events...</p>}
      {error && <p className="no-results">{error}</p>}
      {!loading && !error && events.length === 0 && <p className="no-results">No events found in this category.</p>}

      <div className="events-list">
        {!loading && !error && events.map(event => {
          const progress = event.maxSpots > 0 ? ((event.enrolledCount || 0) / event.maxSpots) * 100 : 0;
          const isFull = event.maxSpots > 0 && (event.enrolledCount || 0) >= event.maxSpots;

          const CategoryIcon = CATEGORY_ICON[event.category] || Sparkles;
          return (
            <div key={event.id} className="event-card">
              <div className="event-emoji"><CategoryIcon size={24} strokeWidth={1.8} /></div>
              <div className="event-content">
                <div className="event-meta">
                  <span className="event-ngo">{event.ngoName}</span>
                  <span className="event-date"><CalendarDays size={13} /> {event.eventDate ? new Date(event.eventDate).toLocaleDateString() : 'TBA'}</span>
                </div>
                <h3>{event.title}</h3>
                <p className="event-desc">{event.description}</p>
                <div className="event-location"><MapPin size={13} /> {event.location}</div>

                <div className="event-progress-container">
                  <div className="progress-labels">
                    <span>{event.enrolledCount || 0} Enrolled</span>
                    <span>{event.maxSpots} Spots</span>
                  </div>
                  <div className="progress-bar">
                    <div className="progress-fill" style={{ width: `${Math.min(100, progress)}%`, backgroundColor: isFull ? 'var(--error, #ff6b6b)' : 'var(--primary, #6c47ff)' }}></div>
                  </div>
                </div>
              </div>
              <div className="event-action">
                <button
                  className="btn-enroll"
                  disabled={isFull}
                  onClick={() => handleEnrollClick(event)}
                >
                  {isFull ? 'Full' : 'Enroll'}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {showModal && (
        <div className="modal-overlay">
          <div className="modal-content glass-panel">
            {modalState === 'confirm' && (
              <>
                <h2>Confirm Enrollment</h2>
                <p>Are you sure you want to enroll in <strong>{selectedEvent?.title}</strong> by {selectedEvent?.ngoName}?</p>
                <div className="modal-actions">
                  <button className="btn-cancel" onClick={() => setShowModal(false)} disabled={enrolling}>Cancel</button>
                  <button className="btn-confirm" onClick={confirmEnrollment} disabled={enrolling}>
                    {enrolling ? 'Enrolling...' : 'Yes, Enroll'}
                  </button>
                </div>
              </>
            )}
            {modalState === 'success' && (
              <div className="success-state">
                <div className="success-icon"><CheckCircle2 size={40} strokeWidth={1.8} /></div>
                <h2>Successfully Enrolled!</h2>
                <p>Thank you for volunteering. The NGO will be in touch with details.</p>
              </div>
            )}
            {modalState === 'error' && (
              <>
                <h2>Enrollment Failed</h2>
                <p>{modalError}</p>
                <div className="modal-actions">
                  <button className="btn-cancel" onClick={() => setShowModal(false)}>Close</button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
