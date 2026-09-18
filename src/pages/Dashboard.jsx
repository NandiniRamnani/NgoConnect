import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Building2, UserRound, CalendarDays, CheckCircle2, Users, Ticket, HeartHandshake,
  XCircle, Clock, Upload, X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './Dashboard.css';
import Avatar, { PRESET_NAMES } from '../components/Avatar';

const Dashboard = () => {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  const [showEventModal, setShowEventModal] = useState(false);
  const [showNeedModal, setShowNeedModal] = useState(false);
  const [eventForm, setEventForm] = useState({ title: '', eventDate: '', location: '', description: '', maxSpots: '', category: 'General' });
  const [needForm, setNeedForm]   = useState({ title: '', type: 'NEED', description: '', resourceDetails: '', deliveryAddress: '' });
  const [events, setEvents]       = useState([]);
  const [enrollments, setEnrollments] = useState([]);
  const [donations, setDonations] = useState([]);
  const [receiptError, setReceiptError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState('');
  const [modalSuccess, setModalSuccess] = useState('');
  const [media, setMedia] = useState([]);
  const [showPhotoModal, setShowPhotoModal] = useState(false);
  const [showVideoModal, setShowVideoModal] = useState(false);
  const [photoFile, setPhotoFile] = useState(null);
  const [photoCaption, setPhotoCaption] = useState('');
  const [videoUrl, setVideoUrl] = useState('');
  const [videoCaption, setVideoCaption] = useState('');
  const [mediaLoading, setMediaLoading] = useState(false);
  const [mediaError, setMediaError] = useState('');

  /**
   * The avatar is held in local state, seeded from the logged-in user, so a change shows
   * immediately rather than only after a re-login. saveAvatar() also writes it back into the
   * stored session, which is what keeps the navbar in sync on the very same click.
   */
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || null);
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [avatarError, setAvatarError] = useState('');

  useEffect(() => {
    if (!user) { navigate('/login'); return; }

    const creds = btoa(`${user.email}:${user._pass || ''}`);
    const headers = { Authorization: `Basic ${creds}` };

    if (user.role === 'NGO' && user.id) {
      api.get(`/ngos/${user.id}/events`).then(r => setEvents(r.data)).catch(() => {});
      api.get(`/ngos/${user.id}/media`).then(r => setMedia(r.data)).catch(() => {});
    } else if (user.role === 'USER') {
      api.get('/events/my-enrollments', { headers }).then(r => setEnrollments(r.data)).catch(() => {});
      api.get('/donations', { headers }).then(r => setDonations(r.data)).catch(() => {});
    }
  }, [user, navigate]);

  if (!user) return null;

  const isNGO = user.role === 'NGO';
  const displayName = user.fullName || user.ngoName || user.email?.split('@')[0] || 'Welcome';
  const firstLetter = displayName[0]?.toUpperCase() || '?';

  /**
   * Every authenticated call in this file rebuilt these credentials by hand. One helper means the
   * Basic-auth header is assembled the same way everywhere, so a change to how auth works is one
   * edit rather than eight.
   */
  const authHeader = () => ({ Authorization: 'Basic ' + btoa(user.email + ':' + (user._pass || '')) });

  /**
   * Download the 80G tax receipt for one donation.
   *
   * This cannot be a plain <a href> link. The endpoint requires the Basic auth header, and a link
   * navigation carries no custom headers — the browser would just be sent to a 401 page. So the
   * PDF is fetched with axios like any other authenticated call, then handed to the browser as a
   * download afterwards.
   *
   * `responseType: 'blob'` is the line that makes it work. Without it axios assumes text and runs
   * the PDF's raw bytes through string decoding, which silently mangles them — you get a file that
   * downloads fine and then refuses to open. 'blob' keeps the bytes untouched.
   */
  const downloadReceipt = async (donation) => {
    const creds = btoa(user.email + ':' + (user._pass || ''));
    setReceiptError('');
    try {
      const res = await api.get(`/donations/${donation.id}/receipt`, {
        headers: { Authorization: 'Basic ' + creds },
        responseType: 'blob',
      });

      // A blob lives in memory with no address, so createObjectURL mints a temporary URL pointing
      // at it. A hidden link is clicked to start the download, then both are cleaned up — the
      // element so it does not accumulate in the DOM, the URL so the blob can be garbage collected.
      const url = URL.createObjectURL(res.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `ngoconnect-receipt-${(donation.receiptNumber || donation.id).replace(/\//g, '-')}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch {
      setReceiptError('Could not download that receipt. Please try again.');
    }
  };

  /**
   * Persist a new avatar, then mirror it into the stored session.
   *
   * That second half is the part worth explaining. `user` came out of localStorage at page load,
   * so changing only the server would leave the navbar drawing the old picture until the next
   * login. Writing the new value back through login() updates the context and localStorage
   * together, and every component reading `user` re-renders with the new avatar at once.
   */
  const saveAvatar = (newUrl) => {
    setAvatarUrl(newUrl);
    login({ ...user, avatarUrl: newUrl });
    setShowAvatarPicker(false);
  };

  const handleAvatarFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setAvatarBusy(true); setAvatarError('');

    const fd = new FormData();
    fd.append('file', file);
    // NGOs and donors have separate endpoints because the picture hangs off different records.
    const url = isNGO ? `/profile/ngo/${user.id}/logo` : '/profile/avatar';

    try {
      /*
       * 'Content-Type': undefined is required, not optional.
       *
       * The axios instance sets a default header of application/json for every request. That is
       * right for the JSON calls that make up most of this file, but wrong for a file upload: a
       * multipart request has to carry "multipart/form-data; boundary=..." where the boundary is
       * a random delimiter the browser generates per request. Leaving the JSON default in place
       * means the server is told to parse a JSON document and instead receives multipart bytes,
       * so it rejects the request before the file is ever read.
       *
       * Clearing the header lets axios detect the FormData body and set the correct value,
       * boundary included. This is the same thing the NGO photo upload below does, which is why
       * that one worked while this one failed every time.
       */
      const res = await api.post(url, fd, {
        headers: { ...authHeader(), 'Content-Type': undefined },
      });
      saveAvatar(res.data.avatarUrl);
    } catch (err) {
      setAvatarError(err.response?.data?.message || 'Could not upload that image.');
    } finally {
      setAvatarBusy(false);
      e.target.value = ''; // lets the same file be re-picked after a failure
    }
  };

  const handlePresetPick = async (preset) => {
    setAvatarBusy(true); setAvatarError('');
    const url = isNGO ? `/profile/ngo/${user.id}/logo/preset` : '/profile/avatar/preset';
    try {
      // Sent as a query parameter to match the @RequestParam on the backend. An empty value is
      // meaningful: it clears the picture and goes back to initials.
      const res = await api.put(`${url}?preset=${preset || ''}`, null, { headers: authHeader() });
      saveAvatar(res.data.avatarUrl);
    } catch (err) {
      setAvatarError(err.response?.data?.message || 'Could not update your picture.');
    } finally {
      setAvatarBusy(false);
    }
  };

  const handlePhotoUpload = async (e) => {
    e.preventDefault();
    if (!photoFile) return setMediaError('Please select a photo.');
    setMediaLoading(true); setMediaError('');
    const fd = new FormData();
    fd.append('file', photoFile);
    if (photoCaption) fd.append('caption', photoCaption);
    const creds = btoa(user.email + ':' + (user._pass || ''));
    try {
      const res = await api.post(`/ngos/${user.id}/media/photo`, fd, { headers: { Authorization: 'Basic ' + creds, 'Content-Type': undefined } });
      setMedia(prev => [res.data, ...prev]);
      setShowPhotoModal(false); setPhotoFile(null); setPhotoCaption('');
    } catch (err) { setMediaError(err.response?.data?.message || 'Upload failed.'); }
    finally { setMediaLoading(false); }
  };

  const handleVideoLink = async (e) => {
    e.preventDefault();
    if (!videoUrl.trim()) return setMediaError('Please enter a video URL.');
    setMediaLoading(true); setMediaError('');
    const creds = btoa(user.email + ':' + (user._pass || ''));
    try {
      const res = await api.post(`/ngos/${user.id}/media/video`, { videoUrl: videoUrl.trim(), caption: videoCaption }, { headers: { Authorization: 'Basic ' + creds } });
      setMedia(prev => [res.data, ...prev]);
      setShowVideoModal(false); setVideoUrl(''); setVideoCaption('');
    } catch (err) { setMediaError(err.response?.data?.message || 'Failed to save video.'); }
    finally { setMediaLoading(false); }
  };

  const handleDeleteMedia = async (item) => {
    if (!window.confirm('Delete this item?')) return;
    const creds = btoa(user.email + ':' + (user._pass || ''));
    try {
      await api.delete(`/ngos/${user.id}/media/${item.id}`, { headers: { Authorization: 'Basic ' + creds } });
      setMedia(prev => prev.filter(m => m.id !== item.id));
    } catch { alert('Failed to delete.'); }
  };

  // ── Modal handlers ──────────────────────────────────────────────────────────

  const handleEventSubmit = async (e) => {
    e.preventDefault();
    setModalError(''); setModalSuccess('');
    if (!eventForm.title || !eventForm.eventDate || !eventForm.location || !eventForm.maxSpots) {
      return setModalError('Please fill in all required fields.');
    }
    setSubmitting(true);
    try {
      const payload = {
        title: eventForm.title,
        description: eventForm.description,
        location: eventForm.location,
        eventDate: new Date(eventForm.eventDate).toISOString(),
        maxSpots: parseInt(eventForm.maxSpots),
        category: eventForm.category,
      };
      const creds = btoa(`${user.email}:${user._pass || ''}`);
      const res = await api.post(`/ngos/${user.id}/events`, payload, {
        headers: { Authorization: `Basic ${creds}` },
      });
      setEvents(prev => [res.data, ...prev]);
      setModalSuccess('Event created successfully!');
      setEventForm({ title: '', eventDate: '', location: '', description: '', maxSpots: '', category: 'General' });
      setTimeout(() => { setShowEventModal(false); setModalSuccess(''); }, 1500);
    } catch (err) {
      setModalError(err.response?.data?.message || 'Failed to create event.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleNeedSubmit = async (e) => {
    e.preventDefault();
    setModalError(''); setModalSuccess('');
    if (!needForm.title || !needForm.description) {
      return setModalError('Title and description are required.');
    }
    setSubmitting(true);
    try {
      const payload = {
        title: needForm.title,
        type: needForm.type,
        description: needForm.description,
        resourceDetails: needForm.resourceDetails,
        deliveryAddress: needForm.deliveryAddress,
      };
      const creds = btoa(`${user.email}:${user._pass || ''}`);
      await api.post(`/ngos/${user.id}/notifications`, payload, {
        headers: { Authorization: `Basic ${creds}` },
      });
      setModalSuccess('Need posted successfully!');
      setNeedForm({ title: '', type: 'NEED', description: '', resourceDetails: '', deliveryAddress: '' });
      setTimeout(() => { setShowNeedModal(false); setModalSuccess(''); }, 1500);
    } catch (err) {
      setModalError(err.response?.data?.message || 'Failed to post need.');
    } finally {
      setSubmitting(false);
    }
  };

  const formatDate = (iso) => {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }); }
    catch { return iso; }
  };

  return (
    <div className="dashboard-container">
      {/* Sidebar */}
      <div className="dashboard-sidebar">
        <div className="user-profile">
          <div className="avatar-editable">
            <Avatar src={avatarUrl} name={displayName} size={72} />
            <button className="avatar-edit-btn" onClick={() => setShowAvatarPicker(true)} title="Change picture">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                <circle cx="12" cy="13" r="4"/>
              </svg>
            </button>
          </div>
          <h2>{displayName}</h2>
          <span className="role-badge">{isNGO ? <><Building2 size={13} /> NGO</> : <><UserRound size={13} /> User</>}</span>
          <p className="email">{user.email}</p>
        </div>
        <nav className="dashboard-nav">
          <a href="#overview" className="active">Overview</a>
          {isNGO && <a href="#events">My Events</a>}
          {!isNGO && <a href="#enrollments">My Events</a>}
          <Link to="/ngos">Browse NGOs</Link>
          <Link to="/donate">Donate</Link>
        </nav>
      </div>

      {/* Main content */}
      <div className="dashboard-main">
        <header className="dashboard-header">
          <h1>Welcome back, {displayName}! 👋</h1>
          <p className="header-sub">{isNGO ? 'Manage your NGO activities below.' : 'Here\'s your impact at a glance.'}</p>
        </header>

        {/* ── NGO Dashboard ──────────────────────────────────────────────── */}
        {isNGO ? (
          <div className="ngo-dashboard animate-fade-in">
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-icon"><CalendarDays size={20} strokeWidth={1.8} /></div>
                <div className="stat-info">
                  <h3>Total Events</h3>
                  <p className="stat-value">{events.length}</p>
                </div>
              </div>
              <div className="stat-card">
                <div className="stat-icon"><CheckCircle2 size={20} strokeWidth={1.8} /></div>
                <div className="stat-info">
                  <h3>Active Events</h3>
                  <p className="stat-value">{events.filter(e => e.active).length}</p>
                </div>
              </div>
              <div className="stat-card">
                <div className="stat-icon"><Users size={20} strokeWidth={1.8} /></div>
                <div className="stat-info">
                  <h3>Total Enrolled</h3>
                  <p className="stat-value">{events.reduce((s, e) => s + (e.enrolledCount || 0), 0)}</p>
                </div>
              </div>
            </div>

            <div className="action-buttons">
              <button className="btn btn-primary" onClick={() => { setModalError(''); setModalSuccess(''); setShowEventModal(true); }}>
                + Post Event
              </button>
              <button className="btn btn-secondary" onClick={() => { setModalError(''); setModalSuccess(''); setShowNeedModal(true); }}>
                + Post Need
              </button>
            </div>

            <div className="section-card" id="events">
              <h2>My Events</h2>
              {events.length === 0 ? (
                <div className="empty-state">
                  <CalendarDays size={32} strokeWidth={1.5} />
                  <p>No events posted yet. Click <strong>Post Event</strong> to create your first event.</p>
                </div>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr><th>Title</th><th>Date</th><th>Enrolled</th><th>Max Spots</th><th>Status</th></tr>
                  </thead>
                  <tbody>
                    {events.map(ev => (
                      <tr key={ev.id}>
                        <td>{ev.title}</td>
                        <td>{formatDate(ev.eventDate)}</td>
                        <td>{ev.enrolledCount ?? 0}</td>
                        <td>{ev.maxSpots}</td>
                        <td><span className={`status-badge ${ev.active ? 'upcoming' : 'completed'}`}>{ev.active ? 'Active' : 'Closed'}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

        ) : (
        /* ── User Dashboard ───────────────────────────────────────────────── */
          <div className="user-dashboard animate-fade-in">
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-icon"><Ticket size={20} strokeWidth={1.8} /></div>
                <div className="stat-info">
                  <h3>Events Enrolled</h3>
                  <p className="stat-value">{enrollments.length}</p>
                </div>
              </div>
              <div className="stat-card">
                <div className="stat-icon"><HeartHandshake size={20} strokeWidth={1.8} /></div>
                <div className="stat-info">
                  <h3>Donations Made</h3>
                  <p className="stat-value">{donations.length}</p>
                </div>
              </div>
            </div>

            <div className="dashboard-sections">
              <div className="section-card" id="enrollments">
                <h2>My Enrolled Events</h2>
                {enrollments.length === 0 ? (
                  <div className="empty-state">
                    <Ticket size={32} strokeWidth={1.5} />
                    <p>You haven't enrolled in any events yet. <Link to="/events">Browse events →</Link></p>
                  </div>
                ) : (
                  <div className="event-list">
                    {enrollments.map(en => (
                      <div className="event-item" key={en.id}>
                        <div className="event-date">{formatDate(en.enrolledAt)}</div>
                        <div className="event-details">
                          <h4>Event ID: {en.eventId}</h4>
                          <p className="attendance-status">
                            {en.attended === true ? <><CheckCircle2 size={14} /> Attended</> : en.attended === false ? <><XCircle size={14} /> Marked Absent</> : <><Clock size={14} /> Upcoming</>}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="section-card">
                <h2>Donation History</h2>
                {donations.length === 0 ? (
                  <div className="empty-state">
                    <HeartHandshake size={32} strokeWidth={1.5} />
                    <p>No donations yet. <Link to="/donate">Make your first donation →</Link></p>
                  </div>
                ) : (
                  <>
                    {receiptError && <p className="wallet-message error">{receiptError}</p>}
                    <table className="data-table">
                      <thead>
                        <tr><th>NGO</th><th>Amount</th><th>Mode</th><th>Date</th><th>Status</th><th>Receipt</th></tr>
                      </thead>
                      <tbody>
                        {donations.map(d => (
                          <tr key={d.id}>
                            <td>{d.ngoId}</td>
                            <td>₹{d.amount}</td>
                            <td>{d.paymentMode}</td>
                            <td>{formatDate(d.createdAt)}</td>
                            <td><span className="status-badge upcoming">{d.status}</span></td>
                            <td>
                              {/* Only a paid donation has a receipt — there is nothing to certify
                                  about one that is still pending or that failed. */}
                              {d.status === 'PAID' ? (
                                <button className="receipt-btn" onClick={() => downloadReceipt(d)}>
                                  ⬇ 80G Receipt
                                </button>
                              ) : (
                                <span className="receipt-na">—</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── Post Event Modal ──────────────────────────────────────────────────── */}
      {showEventModal && (
        <div className="modal-overlay animate-fade-in" onClick={() => setShowEventModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <button className="close-btn" onClick={() => setShowEventModal(false)}>×</button>
            <h2>Post New Event</h2>
            {modalError   && <div className="alert alert-error">{modalError}</div>}
            {modalSuccess && <div className="alert alert-success">{modalSuccess}</div>}
            <form className="modal-form" onSubmit={handleEventSubmit}>
              <div className="form-group">
                <label className="form-label">Event Title *</label>
                <input className="form-input" type="text" placeholder="e.g. Tree Plantation Drive"
                  value={eventForm.title} onChange={e => setEventForm({ ...eventForm, title: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Date & Time *</label>
                <input className="form-input" type="datetime-local"
                  value={eventForm.eventDate} onChange={e => setEventForm({ ...eventForm, eventDate: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Location *</label>
                <input className="form-input" type="text" placeholder="e.g. Central Park, Mumbai"
                  value={eventForm.location} onChange={e => setEventForm({ ...eventForm, location: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Category</label>
                <select className="form-input" value={eventForm.category} onChange={e => setEventForm({ ...eventForm, category: e.target.value })}>
                  {['General','Health','Education','Environment','Food','Other'].map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Max Spots *</label>
                <input className="form-input" type="number" min="1" placeholder="e.g. 50"
                  value={eventForm.maxSpots} onChange={e => setEventForm({ ...eventForm, maxSpots: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Description</label>
                <textarea className="form-input" rows="3" placeholder="What is this event about?"
                  value={eventForm.description} onChange={e => setEventForm({ ...eventForm, description: e.target.value })} />
              </div>
              <button type="submit" className="btn btn-primary btn-full" disabled={submitting}>
                {submitting ? <span className="spinner" /> : '+ Create Event'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* ── Post Need Modal ───────────────────────────────────────────────────── */}
      {showNeedModal && (
        <div className="modal-overlay animate-fade-in" onClick={() => setShowNeedModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <button className="close-btn" onClick={() => setShowNeedModal(false)}>×</button>
            <h2>Post a Need</h2>
            {modalError   && <div className="alert alert-error">{modalError}</div>}
            {modalSuccess && <div className="alert alert-success">{modalSuccess}</div>}
            <form className="modal-form" onSubmit={handleNeedSubmit}>
              <div className="form-group">
                <label className="form-label">Need Title *</label>
                <input className="form-input" type="text" placeholder="e.g. Blankets for winter"
                  value={needForm.title} onChange={e => setNeedForm({ ...needForm, title: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Type</label>
                <select className="form-input" value={needForm.type} onChange={e => setNeedForm({ ...needForm, type: e.target.value })}>
                  <option value="NEED">Goods / Materials</option>
                  <option value="VOLUNTEERING">Volunteers</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Description *</label>
                <textarea className="form-input" rows="3" placeholder="Describe what you need and why..."
                  value={needForm.description} onChange={e => setNeedForm({ ...needForm, description: e.target.value })} required />
              </div>
              <div className="form-group">
                <label className="form-label">Resource Details</label>
                <input className="form-input" type="text" placeholder="e.g. 100 blankets, XL size"
                  value={needForm.resourceDetails} onChange={e => setNeedForm({ ...needForm, resourceDetails: e.target.value })} />
              </div>
              {needForm.type === 'NEED' && (
                <div className="form-group">
                  <label className="form-label">Where should donors send this? (optional)</label>
                  <input className="form-input" type="text" placeholder="Leave blank to use your registered address"
                    value={needForm.deliveryAddress} onChange={e => setNeedForm({ ...needForm, deliveryAddress: e.target.value })} />
                </div>
              )}
              <button type="submit" className="btn btn-primary btn-full" disabled={submitting}>
                {submitting ? <span className="spinner" /> : '+ Post Need'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* ── Media Gallery (NGO only) ──────────────────────────────── */}
      {isNGO && (
        <div className="section-card" style={{ marginTop: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h2 style={{ margin: 0 }}>Media Gallery</h2>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-primary" onClick={() => { setMediaError(''); setShowPhotoModal(true); }}>+ Upload Photo</button>
              <button className="btn btn-secondary" onClick={() => { setMediaError(''); setShowVideoModal(true); }}>+ Add Video</button>
            </div>
          </div>
          {media.length === 0 ? (
            <div className="empty-state"><span style={{ fontSize: 36 }}>🖼️</span><p>No media posted yet. Upload photos or add video links to showcase your work.</p></div>
          ) : (
            <div className="media-gallery-grid">
              {media.map(item => (
                <div key={item.id} className="media-gallery-card">
                  {item.mediaType === 'IMAGE'
                    ? <img src={item.mediaUrl} alt={item.caption || 'NGO photo'} style={{ width: '100%', height: 160, objectFit: 'cover', borderRadius: 8 }} />
                    : <div style={{ height: 160, background: 'rgba(28, 92, 66,0.15)', borderRadius: 8, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                        <span style={{ fontSize: 36 }}>🎬</span>
                        <a href={item.mediaUrl} target="_blank" rel="noreferrer" style={{ color: 'var(--primary)', fontSize: 13 }}>Watch Video ↗</a>
                      </div>
                  }
                  {item.caption && <p style={{ fontSize: 12, color: 'var(--text2, #b0b0cc)', marginTop: 6 }}>{item.caption}</p>}
                  <button onClick={() => handleDeleteMedia(item)} style={{ marginTop: 6, background: 'none', border: '1px solid var(--error)', color: 'var(--error)', borderRadius: 6, padding: '4px 10px', cursor: 'pointer', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 4 }}><X size={12} /> Remove</button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Photo Upload Modal */}
      {/* Profile picture picker: upload a real image, choose a bundled illustration, or clear it. */}
      {showAvatarPicker && (
        <div className="modal-overlay" onClick={() => setShowAvatarPicker(false)}>
          {/* stopPropagation so clicking inside the card does not close it via the overlay above. */}
          <div className="modal-card avatar-picker" onClick={e => e.stopPropagation()}>
            <h3>{isNGO ? 'Organisation logo' : 'Profile picture'}</h3>
            <p className="avatar-picker-hint">
              {isNGO
                ? 'A real logo makes your organisation far more recognisable to donors.'
                : 'Upload a photo, or pick an illustration if you would rather not use one.'}
            </p>

            {avatarError && <p className="wallet-message error">{avatarError}</p>}

            <label className={`avatar-upload-btn ${avatarBusy ? 'busy' : ''}`}>
              {avatarBusy ? 'Working…' : <><Upload size={14} /> Upload an image</>}
              {/* The input is visually hidden and driven by this label, because a raw file input
                  cannot be styled consistently across browsers. */}
              <input type="file" accept="image/*" hidden disabled={avatarBusy} onChange={handleAvatarFile} />
            </label>
            <small className="avatar-upload-note">JPG, PNG or WebP · up to 5MB</small>

            <div className="avatar-divider"><span>or choose one</span></div>

            <div className="avatar-preset-grid">
              {PRESET_NAMES.map(name => (
                <button
                  key={name}
                  className={`avatar-preset ${avatarUrl === `preset:${name}` ? 'selected' : ''}`}
                  disabled={avatarBusy}
                  onClick={() => handlePresetPick(name)}
                  title={name}
                >
                  <Avatar src={`preset:${name}`} name={name} size={46} />
                </button>
              ))}
            </div>

            <div className="modal-actions">
              {/* Passing an empty preset clears the picture and falls back to initials. */}
              <button className="btn-secondary" disabled={avatarBusy} onClick={() => handlePresetPick('')}>
                Remove picture
              </button>
              <button className="btn-secondary" onClick={() => setShowAvatarPicker(false)}>Close</button>
            </div>
          </div>
        </div>
      )}

      {showPhotoModal && (
        <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) setShowPhotoModal(false); }}>
          <div className="modal-card">
            <div className="modal-card-header">
              <h3>Upload Photo</h3>
              <button className="modal-close-btn" onClick={() => setShowPhotoModal(false)}>×</button>
            </div>
            {mediaError && <div className="alert alert-error">{mediaError}</div>}
            <form onSubmit={handlePhotoUpload}>
              <div className="form-group">
                <label className="form-label">Select Image *</label>
                <input className="form-input" type="file" accept="image/*" onChange={e => setPhotoFile(e.target.files[0] || null)} required />
              </div>
              <div className="form-group">
                <label className="form-label">Caption (optional)</label>
                <input className="form-input" type="text" placeholder="Describe this photo..." value={photoCaption} onChange={e => setPhotoCaption(e.target.value)} />
              </div>
              <button type="submit" className="btn btn-primary btn-full" disabled={mediaLoading}>
                {mediaLoading ? <span className="spinner" /> : '+ Upload Photo'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Video Link Modal */}
      {showVideoModal && (
        <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) setShowVideoModal(false); }}>
          <div className="modal-card">
            <div className="modal-card-header">
              <h3>Add Video Link</h3>
              <button className="modal-close-btn" onClick={() => setShowVideoModal(false)}>×</button>
            </div>
            {mediaError && <div className="alert alert-error">{mediaError}</div>}
            <form onSubmit={handleVideoLink}>
              <div className="form-group">
                <label className="form-label">Video URL *</label>
                <input className="form-input" type="url" placeholder="https://youtube.com/watch?v=..." value={videoUrl} onChange={e => setVideoUrl(e.target.value)} required />
              </div>
              <div className="form-group">
                <label className="form-label">Caption (optional)</label>
                <input className="form-input" type="text" placeholder="Describe this video..." value={videoCaption} onChange={e => setVideoCaption(e.target.value)} />
              </div>
              <button type="submit" className="btn btn-primary btn-full" disabled={mediaLoading}>
                {mediaLoading ? <span className="spinner" /> : '+ Add Video'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
