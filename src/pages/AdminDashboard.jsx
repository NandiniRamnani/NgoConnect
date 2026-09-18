import { useState, useEffect } from 'react';
import api from '../api/axios';
import PasswordInput from '../components/PasswordInput';
import './AdminDashboard.css';

const SECTIONS = ['ngos', 'accounts', 'events', 'donations', 'withdrawals', 'needs'];
const SECTION_LABEL = { ngos: 'NGO Applications', accounts: 'Users & NGOs', events: 'Events', donations: 'Donations', withdrawals: 'Withdrawals', needs: 'Needs' };

// The queue an admin actually works through, in the order a request moves.
const WITHDRAWAL_STATUSES = ['REQUESTED', 'APPROVED', 'PAID', 'REJECTED', 'FAILED'];

// sessionStorage (not localStorage): survives a refresh, like a normal login session,
// but clears when the tab closes — admin credentials shouldn't linger in the browser forever.
const loadStoredCreds = () => {
  try { return JSON.parse(sessionStorage.getItem('ngo_admin_creds')) || { email: '', password: '' }; }
  catch { return { email: '', password: '' }; }
};

export default function AdminDashboard() {
  const [creds, setCreds] = useState(loadStoredCreds);
  const [auth, setAuth] = useState(false);
  const [apps, setApps] = useState([]);
  const [status, setStatus] = useState('PENDING');
  const [counts, setCounts] = useState({ PENDING: 0, APPROVED: 0, REJECTED: 0 });
  const [sel, setSel] = useState(null);
  const [tab, setTab] = useState('details');
  const [note, setNote] = useState('');
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);
  const [docLoading, setDocLoading] = useState(false);

  const [section, setSection] = useState('ngos');
  const [accounts, setAccounts] = useState([]);
  const [events, setEvents] = useState([]);
  const [donations, setDonations] = useState([]);
  const [needs, setNeeds] = useState([]);
  const [withdrawals, setWithdrawals] = useState([]);
  const [wStatus, setWStatus] = useState('REQUESTED');
  const [pendingPayouts, setPendingPayouts] = useState(0);
  const [destination, setDestination] = useState(null);   // revealed bank details, one request at a time

  const headers = () => ({ Authorization: 'Basic ' + btoa(creds.email + ':' + creds.password) });

  const load = async (s) => {
    s = s || status;
    setLoading(true); setErr('');
    try {
      const res = await api.get('/admin/ngos', { params: { status: s }, headers: headers() });
      setApps(res.data); setAuth(true); setStatus(s);
      sessionStorage.setItem('ngo_admin_creds', JSON.stringify(creds));
      const all = {};
      for (const st of ['PENDING', 'APPROVED', 'REJECTED']) {
        try { const r = await api.get('/admin/ngos', { params: { status: st }, headers: headers() }); all[st] = r.data.length; }
        catch { all[st] = 0; }
      }
      setCounts(all);
    } catch (e) {
      if (e.response?.status === 401) { setErr('Invalid admin credentials.'); sessionStorage.removeItem('ngo_admin_creds'); }
      else setErr('Failed to load.');
    }
    finally { setLoading(false); }
  };

  const login = e => { e.preventDefault(); load(); };

  // On first mount, if a session was already signed in before the refresh, log straight back
  // in with the saved credentials instead of showing an empty login form.
  useEffect(() => {
    if (creds.email && creds.password) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const goToSection = async (s) => {
    setSection(s); setErr(''); setLoading(true);
    try {
      if (s === 'ngos') { await load(); return; }
      if (s === 'accounts') setAccounts((await api.get('/admin/accounts', { headers: headers() })).data);
      if (s === 'events') setEvents((await api.get('/admin/events', { headers: headers() })).data);
      if (s === 'donations') setDonations((await api.get('/admin/donations', { headers: headers() })).data);
      if (s === 'needs') setNeeds((await api.get('/admin/notifications', { headers: headers() })).data);
      if (s === 'withdrawals') await loadWithdrawals(wStatus);
    } catch (e) {
      setErr(e.response?.status === 401 ? 'Invalid admin credentials.' : 'Failed to load.');
    } finally {
      setLoading(false);
    }
  };

  const loadWithdrawals = async (status) => {
    setWStatus(status);
    const res = await api.get('/admin/withdrawals', { params: { status }, headers: headers() });
    setWithdrawals(res.data);
    try {
      const c = await api.get('/admin/withdrawals/pending-count', { headers: headers() });
      setPendingPayouts(c.data.count);
    } catch { /* the badge is cosmetic; a failure here must not blank the queue */ }
  };

  // The one call in the app that returns an unmasked account number, so it is deliberate:
  // the admin asks for it, for one request, at the moment they are about to transfer.
  const revealDestination = async (w) => {
    try {
      const res = await api.get(`/admin/withdrawals/${w.id}/payout-destination`, { headers: headers() });
      setDestination({ ...res.data, request: w });
    } catch { alert('Could not load the payout details.'); }
  };

  const actOnWithdrawal = async (w, action, body) => {
    try {
      await api.patch(`/admin/withdrawals/${w.id}/${action}`, body || {}, { headers: headers() });
      setDestination(null);
      await loadWithdrawals(wStatus);
    } catch (e) {
      alert(e.response?.data?.message || 'Could not update the request.');
    }
  };

  const approveWithdrawal = (w) => {
    if (!window.confirm(`Approve ${w.ngoName}'s withdrawal of Rs.${w.amount}? The money stays on hold until you mark it paid.`)) return;
    actOnWithdrawal(w, 'approve', {});
  };

  const rejectWithdrawal = (w) => {
    const note = window.prompt('Why is this being declined? The NGO will see this.');
    if (!note || !note.trim()) return;
    actOnWithdrawal(w, 'reject', { note: note.trim() });
  };

  const markWithdrawalPaid = (w) => {
    const ref = window.prompt(`Transfer reference (UTR) for Rs.${w.amount} sent to ${w.payoutMaskedDestination}:`);
    if (!ref || !ref.trim()) return;
    actOnWithdrawal(w, 'paid', { paymentReference: ref.trim() });
  };

  const markWithdrawalFailed = (w) => {
    const note = window.prompt("What went wrong? The amount returns to the NGO's available balance.");
    if (!note || !note.trim()) return;
    actOnWithdrawal(w, 'failed', { note: note.trim() });
  };

  const toggleAccountActive = async (acc) => {
    try {
      const res = await api.patch(`/admin/accounts/${acc.id}/active`, { active: !acc.active }, { headers: headers() });
      setAccounts(prev => prev.map(a => a.id === acc.id ? res.data : a));
    } catch { alert('Could not update account.'); }
  };

  const deleteAccount = async (acc) => {
    if (!window.confirm(`Permanently delete ${acc.email}? This cannot be undone.`)) return;
    try {
      await api.delete(`/admin/accounts/${acc.id}`, { headers: headers() });
      setAccounts(prev => prev.filter(a => a.id !== acc.id));
    } catch { alert('Could not delete account.'); }
  };

  const deleteEvent = async (ev) => {
    if (!window.confirm(`Delete event "${ev.title}"? This cannot be undone.`)) return;
    try {
      await api.delete(`/admin/events/${ev.id}`, { headers: headers() });
      setEvents(prev => prev.filter(e => e.id !== ev.id));
    } catch { alert('Could not delete event.'); }
  };

  const setDonationStatus = async (don, newStatus) => {
    try {
      const res = await api.patch(`/admin/donations/${don.id}/status`, { status: newStatus }, { headers: headers() });
      setDonations(prev => prev.map(d => d.id === don.id ? res.data : d));
    } catch { alert('Could not update donation.'); }
  };

  const deleteNeed = async (n) => {
    if (!window.confirm(`Delete "${n.title}"? This cannot be undone.`)) return;
    try {
      await api.delete(`/admin/notifications/${n.id}`, { headers: headers() });
      setNeeds(prev => prev.filter(x => x.id !== n.id));
    } catch { alert('Could not delete need.'); }
  };

  const viewDoc = async (doc) => {
    setDocLoading(true);
    try {
      const res = await api.get(`/admin/ngos/${sel.id}/document-url`, {
        params: { cloudinaryId: doc.cloudinaryId, resourceType: doc.resourceType },
        headers: headers()
      });
      window.open(res.data.url, '_blank');
    } catch { alert('Could not generate document URL. Try again.'); }
    finally { setDocLoading(false); }
  };

  const review = async (decision) => {
    if (decision === 'REJECTED' && !note.trim()) return setErr('A rejection reason is required.');
    setLoading(true); setErr('');
    try {
      await api.patch(`/admin/ngos/${sel.id}/verification`, { status: decision, reviewNote: note }, { headers: headers() });
      setSel(null); setNote(''); load();
    } catch (e) { setErr(e.response?.data?.message || 'Review failed.'); setLoading(false); }
  };

  const fmtDate = d => d ? new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '-';
  const statusClass = s => s === 'PENDING' ? 'pending' : s === 'APPROVED' ? 'approved' : 'rejected';

  if (!auth && loading && creds.email && creds.password) return (
    <div className="admin-login-page">
      <div className="admin-login-card">
        <h1>NGO<span>Connect</span> Admin</h1>
        <p>Signing you back in&hellip;</p>
      </div>
    </div>
  );

  if (!auth) return (
    <div className="admin-login-page">
      <div className="admin-login-card">
        <h1>NGO<span>Connect</span> Admin</h1>
        <p>Sign in with your admin credentials</p>
        {err && <div className="admin-error">{err}</div>}
        <form onSubmit={login}>
          <label>Email<input value={creds.email} onChange={e => setCreds(c => ({ ...c, email: e.target.value }))} required /></label>
          <label>Password<PasswordInput value={creds.password} onChange={e => setCreds(c => ({ ...c, password: e.target.value }))} required /></label>
          <button disabled={loading}>{loading ? 'Signing in...' : 'Sign In'}</button>
        </form>
      </div>
    </div>
  );

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <div className="admin-brand">NGO<span>Connect</span></div>
        <p className="admin-role">Admin Panel</p>

        <nav className="admin-section-nav">
          {SECTIONS.map(s => (
            <button key={s} className={`admin-section-btn ${section === s ? 'active' : ''}`} onClick={() => goToSection(s)}>
              {SECTION_LABEL[s]}
            </button>
          ))}
        </nav>

        {section === 'ngos' && (
          <div className="admin-stats">
            {['PENDING', 'APPROVED', 'REJECTED'].map(s => (
              <button key={s} className={`admin-stat-btn ${status === s ? 'active' : ''} ${statusClass(s)}`} onClick={() => load(s)}>
                <strong>{counts[s]}</strong><span>{s.charAt(0) + s.slice(1).toLowerCase()}</span>
              </button>
            ))}
          </div>
        )}
        <button className="admin-signout" onClick={() => { setAuth(false); setCreds({ email: '', password: '' }); sessionStorage.removeItem('ngo_admin_creds'); }}>Sign Out</button>
      </aside>

      <main className="admin-main">
        {section === 'ngos' && <>
        <header className="admin-page-header">
          <h1>NGO Applications &middot; <span className={statusClass(status)}>{status.charAt(0) + status.slice(1).toLowerCase()}</span></h1>
          <span>{apps.length} application{apps.length !== 1 ? 's' : ''}</span>
        </header>

        {loading && <p className="admin-loading">Loading...</p>}
        {!loading && apps.length === 0 && <p className="admin-empty">No {status.toLowerCase()} applications.</p>}

        <div className="app-grid">
          {apps.map(a => (
            <div key={a.id} className="app-card">
              <div className="app-card-top">
                <span className={`status-badge ${statusClass(a.verificationStatus)}`}>{a.verificationStatus}</span>
                <span className="doc-count-badge">{a.documentCount} doc{a.documentCount !== 1 ? 's' : ''}</span>
              </div>
              <h3>{a.ngoName}</h3>
              <p className="app-meta">{a.ngoType} &middot; {a.location}</p>
              <p className="app-date">Submitted {fmtDate(a.registeredAt)}</p>
              <button className="review-btn" onClick={() => { setSel(a); setTab('details'); setNote(a.reviewNote || ''); setErr(''); }}>Review</button>
            </div>
          ))}
        </div>
        </>}

        {section === 'accounts' && (
          <>
            <header className="admin-page-header"><h1>Users &amp; NGOs</h1><span>{accounts.length} account{accounts.length !== 1 ? 's' : ''}</span></header>
            {loading && <p className="admin-loading">Loading...</p>}
            {!loading && accounts.length === 0 && <p className="admin-empty">No accounts found.</p>}
            {!loading && accounts.length > 0 && (
              <table className="admin-table">
                <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {accounts.map(a => (
                    <tr key={a.id}>
                      <td>{a.fullName}</td>
                      <td>{a.email}</td>
                      <td>{a.role}</td>
                      <td><span className={`status-badge ${a.active ? 'approved' : 'rejected'}`}>{a.active ? 'Active' : 'Deactivated'}</span></td>
                      <td className="admin-table-actions">
                        <button className="doc-view-btn" onClick={() => toggleAccountActive(a)}>{a.active ? 'Deactivate' : 'Reactivate'}</button>
                        <button className="reject-btn" onClick={() => deleteAccount(a)}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {section === 'events' && (
          <>
            <header className="admin-page-header"><h1>Events</h1><span>{events.length} event{events.length !== 1 ? 's' : ''}</span></header>
            {loading && <p className="admin-loading">Loading...</p>}
            {!loading && events.length === 0 && <p className="admin-empty">No events found.</p>}
            {!loading && events.length > 0 && (
              <table className="admin-table">
                <thead><tr><th>Title</th><th>NGO</th><th>Date</th><th>Enrolled</th><th>Active</th><th></th></tr></thead>
                <tbody>
                  {events.map(ev => (
                    <tr key={ev.id}>
                      <td>{ev.title}</td>
                      <td>{ev.ngoName}</td>
                      <td>{fmtDate(ev.eventDate)}</td>
                      <td>{ev.enrolledCount ?? 0} / {ev.maxSpots}</td>
                      <td><span className={`status-badge ${ev.active ? 'approved' : 'rejected'}`}>{ev.active ? 'Active' : 'Closed'}</span></td>
                      <td className="admin-table-actions">
                        <button className="reject-btn" onClick={() => deleteEvent(ev)}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {section === 'donations' && (
          <>
            <header className="admin-page-header"><h1>Donations</h1><span>{donations.length} donation{donations.length !== 1 ? 's' : ''}</span></header>
            {loading && <p className="admin-loading">Loading...</p>}
            {!loading && donations.length === 0 && <p className="admin-empty">No donations found.</p>}
            {!loading && donations.length > 0 && (
              <table className="admin-table">
                <thead><tr><th>NGO ID</th><th>User ID</th><th>Amount</th><th>Mode</th><th>Status</th><th>Reference</th></tr></thead>
                <tbody>
                  {donations.map(d => (
                    <tr key={d.id}>
                      <td>{d.ngoId}</td>
                      <td>{d.userId}</td>
                      <td>&#8377;{d.amount}</td>
                      <td>{d.paymentMode}</td>
                      <td>
                        <select value={d.status} onChange={e => setDonationStatus(d, e.target.value)}>
                          {['PENDING_PAYMENT', 'PAID', 'FAILED'].map(s => <option key={s} value={s}>{s}</option>)}
                        </select>
                      </td>
                      <td>{d.paymentReference}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {section === 'withdrawals' && (
          <>
            <header className="admin-page-header">
              <h1>Withdrawals</h1>
              <span>{pendingPayouts} awaiting review</span>
            </header>

            <div className="admin-stats">
              {WITHDRAWAL_STATUSES.map(st => (
                <button
                  key={st}
                  className={`admin-stat-btn ${wStatus === st ? 'active' : ''}`}
                  onClick={() => loadWithdrawals(st)}
                >
                  {st}
                </button>
              ))}
            </div>

            {loading && <p className="admin-loading">Loading...</p>}
            {!loading && withdrawals.length === 0 && <p className="admin-empty">No {wStatus.toLowerCase()} requests.</p>}
            {!loading && withdrawals.length > 0 && (
              <table className="admin-table">
                <thead>
                  <tr><th>NGO</th><th>Amount</th><th>Destination</th><th>Requested</th><th>Note</th><th></th></tr>
                </thead>
                <tbody>
                  {withdrawals.map(w => (
                    <tr key={w.id}>
                      <td>{w.ngoName}</td>
                      <td><strong>&#8377;{Number(w.amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
                      <td>
                        {w.payoutAccountHolderName}<br />
                        <small>{w.payoutMaskedDestination}{w.payoutIfsc ? ` · ${w.payoutIfsc}` : ''}</small>
                      </td>
                      <td>{new Date(w.requestedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</td>
                      <td><small>{w.ngoNote || w.adminNote || w.paymentReference || '—'}</small></td>
                      <td className="admin-table-actions">
                        {w.status === 'REQUESTED' && (
                          <>
                            <button className="approve-btn" onClick={() => approveWithdrawal(w)}>Approve</button>
                            <button className="reject-btn" onClick={() => rejectWithdrawal(w)}>Decline</button>
                          </>
                        )}
                        {w.status === 'APPROVED' && (
                          <>
                            <button className="doc-view-btn" onClick={() => revealDestination(w)}>Pay &#8599;</button>
                            <button className="reject-btn" onClick={() => markWithdrawalFailed(w)}>Failed</button>
                          </>
                        )}
                        {['PAID', 'REJECTED', 'FAILED'].includes(w.status) && <small>&#8212;</small>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {section === 'needs' && (
          <>
            <header className="admin-page-header"><h1>Needs</h1><span>{needs.length} post{needs.length !== 1 ? 's' : ''}</span></header>
            {loading && <p className="admin-loading">Loading...</p>}
            {!loading && needs.length === 0 && <p className="admin-empty">No needs found.</p>}
            {!loading && needs.length > 0 && (
              <table className="admin-table">
                <thead><tr><th>Title</th><th>NGO</th><th>Type</th><th>Active</th><th></th></tr></thead>
                <tbody>
                  {needs.map(n => (
                    <tr key={n.id}>
                      <td>{n.title}</td>
                      <td>{n.ngoName}</td>
                      <td>{n.type}</td>
                      <td><span className={`status-badge ${n.active ? 'approved' : 'rejected'}`}>{n.active ? 'Active' : 'Inactive'}</span></td>
                      <td className="admin-table-actions">
                        <button className="reject-btn" onClick={() => deleteNeed(n)}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      </main>

      {/* The only place unmasked bank details appear. Shown on demand, for one request,
          and closed as soon as the transfer is recorded. */}
      {/* The only place unmasked bank details appear. Shown on demand, for one request,
          and closed as soon as the transfer is recorded. */}
      {destination && (
        <div className="admin-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) setDestination(null); }}>
          <div className="admin-modal">
            <div className="modal-header">
              <h2>Transfer &#8377;{Number(destination.request.amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</h2>
              <button className="modal-close" onClick={() => setDestination(null)}>&times;</button>
            </div>

            <div className="modal-body">
              <p className="admin-empty" style={{ marginBottom: '1rem' }}>
                Make this transfer from your bank, then record the reference. These details were
                frozen when {destination.request.ngoName} made the request, so they cannot have
                changed since.
              </p>
              <div className="detail-grid">
                <div className="detail-item"><label>NGO</label><span>{destination.request.ngoName}</span></div>
                <div className="detail-item"><label>Account Holder</label><span>{destination.accountHolderName}</span></div>
                {destination.type === 'UPI' ? (
                  <div className="detail-item full"><label>UPI ID</label><span>{destination.upiId}</span></div>
                ) : (
                  <>
                    <div className="detail-item"><label>Account Number</label><span>{destination.accountNumber}</span></div>
                    <div className="detail-item"><label>IFSC</label><span>{destination.ifsc}</span></div>
                    <div className="detail-item"><label>Bank</label><span>{destination.bankName || '-'}</span></div>
                  </>
                )}
                {destination.request.ngoNote && (
                  <div className="detail-item full"><label>NGO Note</label><span>{destination.request.ngoNote}</span></div>
                )}
              </div>
            </div>

            <div className="review-section">
              <div className="review-actions">
                <button className="approve-btn" onClick={() => markWithdrawalPaid(destination.request)}>
                  &#9989; I have transferred it
                </button>
                <button className="reject-btn" onClick={() => setDestination(null)}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {sel && (
        <div className="admin-modal-backdrop" onClick={e => { if (e.target === e.currentTarget) setSel(null); }}>
          <div className="admin-modal">
            <div className="modal-header">
              <h2>{sel.ngoName} <span className={`status-badge ${statusClass(sel.verificationStatus)}`}>{sel.verificationStatus}</span></h2>
              <button className="modal-close" onClick={() => setSel(null)}>&times;</button>
            </div>
            <div className="modal-tabs">
              {['details', 'documents', 'social'].map(t => (
                <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t === 'details' ? 'Details' : t === 'documents' ? 'Documents' : 'Social Links'}</button>
              ))}
            </div>

            <div className="modal-body">
              {tab === 'details' && (
                <div className="detail-grid">
                  <div className="detail-item"><label>Organisation Name</label><span>{sel.ngoName}</span></div>
                  <div className="detail-item"><label>Email</label><span>{sel.email}</span></div>
                  <div className="detail-item"><label>Phone</label><span>{sel.contactPhone}</span></div>
                  <div className="detail-item"><label>Location</label><span>{sel.location}</span></div>
                  <div className="detail-item full"><label>Address</label><span>{sel.address}</span></div>
                  <div className="detail-item"><label>Type</label><span>{sel.ngoType}</span></div>
                  <div className="detail-item"><label>Legal Structure</label><span>{sel.legalStructure}</span></div>
                  <div className="detail-item"><label>Registration No.</label><span>{sel.registrationNumber}</span></div>
                  <div className="detail-item"><label>PAN Number</label><span>{sel.panNumber}</span></div>
                  <div className="detail-item"><label>NGO Darpan ID</label><span>{sel.ngoDarpanId || '-'}</span></div>
                  <div className="detail-item"><label>Representative</label><span>{sel.authorizedPersonName}</span></div>
                  <div className="detail-item"><label>Designation</label><span>{sel.authorizedPersonDesignation}</span></div>
                  <div className="detail-item"><label>Application ID</label><span>{sel.uniqueNgoId}</span></div>
                  <div className="detail-item full"><label>Description</label><span>{sel.description}</span></div>
                </div>
              )}
              {tab === 'documents' && (
                <div className="doc-list">
                  {(!sel.documents || sel.documents.length === 0) && <p className="admin-empty">No documents uploaded.</p>}
                  {sel.documents?.map((d, i) => (
                    <div key={i} className="doc-row">
                      <span className="doc-icon">{d.mimeType?.startsWith('image') ? '\ud83d\uddbc\ufe0f' : '\ud83d\udcc4'}</span>
                      <div className="doc-info">
                        <strong>{d.fileName}</strong>
                        <span>{d.docType?.replace(/_/g, ' ')} &middot; {d.mimeType} &middot; {fmtDate(d.uploadedAt)}</span>
                      </div>
                      <button className="doc-view-btn" onClick={() => viewDoc(d)} disabled={docLoading}>{docLoading ? '...' : 'View \u2197'}</button>
                    </div>
                  ))}
                </div>
              )}
              {tab === 'social' && (
                <div className="social-list">
                  {[['Website', sel.websiteUrl, '\ud83c\udf10'], ['Facebook', sel.facebookUrl, '\ud83d\udcd8'], ['Instagram', sel.instagramUrl, '\ud83d\udcf8'], ['LinkedIn', sel.linkedinUrl, '\ud83d\udcbc']].map(([name, url, icon]) => (
                    <div key={name} className="social-row">
                      <span>{icon}</span>
                      <div><strong>{name}</strong><p>{url || 'Not provided'}</p></div>
                      {url && <a href={url.startsWith('http') ? url : 'https://' + url} target="_blank" rel="noreferrer" className="social-open-btn">Open \u2197</a>}
                    </div>
                  ))}
                  <div className="social-verify-note">\u26a0\ufe0f Visit each link and verify the NGO name, phone number and address match the submitted information above.</div>
                </div>
              )}
            </div>

            {sel.verificationStatus === 'PENDING' && (
              <div className="review-section">
                {err && <div className="admin-error">{err}</div>}
                <textarea className="modal-note-area" placeholder="Decision note (required if rejecting)" value={note} onChange={e => setNote(e.target.value)} rows="3" />
                <div className="review-actions">
                  <button className="approve-btn" onClick={() => review('APPROVED')} disabled={loading}>{loading ? '...' : '\u2705 Approve'}</button>
                  <button className="reject-btn" onClick={() => review('REJECTED')} disabled={loading}>{loading ? '...' : '\u274c Reject'}</button>
                </div>
              </div>
            )}
            {sel.verificationStatus !== 'PENDING' && sel.reviewNote && (
              <div className="review-note-display"><strong>Admin Note:</strong> {sel.reviewNote}</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
