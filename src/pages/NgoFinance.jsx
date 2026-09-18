import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './NgoFinance.css';

const formatRupees = (value) =>
  Number(value ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

// Each ledger row says which direction money moved and into which bucket.
const LEDGER_META = {
  DONATION_RECEIVED:   { label: 'Donation received', sign: '+', className: 'credit' },
  CLEARED:             { label: 'Funds cleared',     sign: '',  className: 'neutral' },
  WITHDRAWAL_RESERVED: { label: 'Withdrawal held',   sign: '−', className: 'debit' },
  WITHDRAWAL_PAID:     { label: 'Withdrawal paid',   sign: '−', className: 'debit' },
  WITHDRAWAL_REVERSED: { label: 'Returned',          sign: '+', className: 'credit' },
};

const STATUS_META = {
  REQUESTED: { label: 'Awaiting review', className: 'pending' },
  APPROVED:  { label: 'Approved',        className: 'approved' },
  PAID:      { label: 'Paid',            className: 'paid' },
  REJECTED:  { label: 'Declined',        className: 'failed' },
  FAILED:    { label: 'Transfer failed', className: 'failed' },
};

const NgoFinance = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [balance, setBalance] = useState(null);
  const [ledger, setLedger] = useState([]);
  const [withdrawals, setWithdrawals] = useState([]);
  const [payoutMethod, setPayoutMethod] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [editingPayout, setEditingPayout] = useState(false);
  const [payoutForm, setPayoutForm] = useState({
    type: 'BANK_ACCOUNT', accountHolderName: '', accountNumber: '', ifsc: '', bankName: '', upiId: '',
  });

  const authHeaders = useCallback(() => ({
    headers: { Authorization: `Basic ${btoa(`${user?.email}:${user?._pass || ''}`)}` },
  }), [user]);

  const base = `/ngos/${user?.id}/finance`;

  const load = useCallback(async () => {
    try {
      // Four independent reads, fired together rather than one after another.
      const [bal, led, wds, pm] = await Promise.all([
        api.get(`${base}/balance`, authHeaders()),
        api.get(`${base}/ledger`, authHeaders()),
        api.get(`${base}/withdrawals`, authHeaders()),
        api.get(`${base}/payout-method`, authHeaders()),
      ]);
      setBalance(bal.data);
      setLedger(led.data);
      setWithdrawals(wds.data);
      setPayoutMethod(pm.data || null);
    } catch {
      setError('Could not load your finances. Please refresh and try again.');
    } finally {
      setLoading(false);
    }
  }, [authHeaders, base]);

  useEffect(() => {
    if (!user) { navigate('/login'); return; }
    if (user.role !== 'NGO') { navigate('/dashboard'); return; }
    load();
  }, [user, navigate, load]);

  const savePayoutMethod = async (e) => {
    e.preventDefault();
    setError(''); setSuccess(''); setSubmitting(true);
    try {
      const payload = payoutForm.type === 'BANK_ACCOUNT'
        ? { type: 'BANK_ACCOUNT', accountHolderName: payoutForm.accountHolderName,
            accountNumber: payoutForm.accountNumber, ifsc: payoutForm.ifsc, bankName: payoutForm.bankName }
        : { type: 'UPI', accountHolderName: payoutForm.accountHolderName, upiId: payoutForm.upiId };

      const res = await api.put(`${base}/payout-method`, payload, authHeaders());
      setPayoutMethod(res.data);
      setEditingPayout(false);
      setSuccess('Payout details saved. Withdrawals unlock after the security hold, and we have emailed you about the change.');
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save your payout details.');
    } finally {
      setSubmitting(false);
    }
  };

  const requestWithdrawal = async (e) => {
    e.preventDefault();
    setError(''); setSuccess(''); setSubmitting(true);
    try {
      await api.post(`${base}/withdrawals`, { amount: Number(amount), note }, authHeaders());
      setAmount(''); setNote('');
      setSuccess('Withdrawal requested. The amount is held aside until the admin reviews it.');
      // Re-read everything — the balance buckets all shifted server-side.
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not submit the withdrawal request.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="fin-container"><p className="fin-loading">Loading your finances…</p></div>;

  const available = Number(balance?.available ?? 0);

  return (
    <div className="fin-container">
      <header className="fin-header">
        <h1>Funds &amp; Withdrawals</h1>
        <p>Donations reach you here. Withdraw cleared funds to your bank or UPI.</p>
      </header>

      {error && <p className="fin-message error">{error}</p>}
      {success && <p className="fin-message success">{success}</p>}

      {/* ── The three buckets. Showing all three is the point: an NGO who only sees
              one number will ask why they cannot withdraw everything. ───────── */}
      <section className="bucket-row">
        <div className="bucket bucket-clearing">
          <span className="bucket-label">Clearing</span>
          <span className="bucket-value">₹{formatRupees(balance?.clearing)}</span>
          <small>Received, still settling. Not yet withdrawable.</small>
        </div>
        <div className="bucket bucket-available">
          <span className="bucket-label">Available</span>
          <span className="bucket-value">₹{formatRupees(balance?.available)}</span>
          <small>Ready to withdraw today.</small>
        </div>
        <div className="bucket bucket-reserved">
          <span className="bucket-label">On hold</span>
          <span className="bucket-value">₹{formatRupees(balance?.reserved)}</span>
          <small>Locked in an open withdrawal request.</small>
        </div>
        <div className="bucket bucket-lifetime">
          <span className="bucket-label">Withdrawn to date</span>
          <span className="bucket-value">₹{formatRupees(balance?.lifetimeWithdrawn)}</span>
          <small>Of ₹{formatRupees(balance?.lifetimeReceived)} received in total.</small>
        </div>
      </section>

      <div className="fin-grid">
        {/* ── Payout destination ─────────────────────────────────────────── */}
        <section className="fin-card">
          <h2>Where we send your money</h2>

          {payoutMethod && !editingPayout && (
            <div className="payout-current">
              <div className="payout-line">
                <span>{payoutMethod.type === 'UPI' ? 'UPI ID' : 'Bank account'}</span>
                <strong>{payoutMethod.maskedDestination}</strong>
              </div>
              <div className="payout-line">
                <span>Account holder</span>
                <strong>{payoutMethod.accountHolderName}</strong>
              </div>
              {payoutMethod.ifsc && (
                <div className="payout-line">
                  <span>IFSC</span>
                  <strong>{payoutMethod.ifsc}{payoutMethod.bankName ? ` · ${payoutMethod.bankName}` : ''}</strong>
                </div>
              )}
              <div className="payout-line">
                <span>Status</span>
                <strong className={payoutMethod.verified ? 'verified' : 'unverified'}>
                  {payoutMethod.verified ? '✓ Verified by admin' : 'Awaiting admin verification'}
                </strong>
              </div>
              <p className="fin-note">
                We only ever show the masked form, even to you. The admin sees the full details
                once, at the moment they make the transfer.
              </p>
              <button className="btn-secondary" onClick={() => setEditingPayout(true)}>Change details</button>
            </div>
          )}

          {(!payoutMethod || editingPayout) && (
            <form onSubmit={savePayoutMethod} className="payout-form">
              <div className="type-toggle">
                {['BANK_ACCOUNT', 'UPI'].map(t => (
                  <button
                    key={t}
                    type="button"
                    className={payoutForm.type === t ? 'selected' : ''}
                    onClick={() => setPayoutForm({ ...payoutForm, type: t })}
                  >
                    {t === 'BANK_ACCOUNT' ? 'Bank account' : 'UPI'}
                  </button>
                ))}
              </div>

              <label>
                Account holder name
                <input
                  required
                  value={payoutForm.accountHolderName}
                  onChange={e => setPayoutForm({ ...payoutForm, accountHolderName: e.target.value })}
                  placeholder="Exactly as registered"
                />
              </label>

              {payoutForm.type === 'BANK_ACCOUNT' ? (
                <>
                  <label>
                    Account number
                    <input
                      required inputMode="numeric"
                      value={payoutForm.accountNumber}
                      onChange={e => setPayoutForm({ ...payoutForm, accountNumber: e.target.value })}
                      placeholder="9 to 18 digits"
                    />
                  </label>
                  <label>
                    IFSC
                    <input
                      required
                      value={payoutForm.ifsc}
                      onChange={e => setPayoutForm({ ...payoutForm, ifsc: e.target.value.toUpperCase() })}
                      placeholder="HDFC0001234"
                    />
                  </label>
                  <label>
                    Bank name <span className="optional">(optional)</span>
                    <input
                      value={payoutForm.bankName}
                      onChange={e => setPayoutForm({ ...payoutForm, bankName: e.target.value })}
                    />
                  </label>
                </>
              ) : (
                <label>
                  UPI ID
                  <input
                    required
                    value={payoutForm.upiId}
                    onChange={e => setPayoutForm({ ...payoutForm, upiId: e.target.value })}
                    placeholder="name@bank"
                  />
                </label>
              )}

              <p className="fin-note warn">
                Changing these details pauses withdrawals for 24 hours and sends you an email.
                If that email ever arrives unexpectedly, change your password immediately.
              </p>

              <div className="form-actions">
                <button className="btn-primary" disabled={submitting}>
                  {submitting ? 'Saving…' : 'Save details'}
                </button>
                {payoutMethod && (
                  <button type="button" className="btn-secondary" onClick={() => setEditingPayout(false)}>
                    Cancel
                  </button>
                )}
              </div>
            </form>
          )}
        </section>

        {/* ── Request a withdrawal ───────────────────────────────────────── */}
        <section className="fin-card">
          <h2>Request a withdrawal</h2>
          {!payoutMethod ? (
            <p className="fin-empty">Add a bank account or UPI ID first.</p>
          ) : available <= 0 ? (
            <p className="fin-empty">
              Nothing available yet. Donations become withdrawable once they finish clearing.
            </p>
          ) : (
            <form onSubmit={requestWithdrawal} className="withdraw-form">
              <label>
                Amount (₹)
                <input
                  required type="number" min="100" step="0.01" max={available}
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder={`Up to ${formatRupees(available)}`}
                />
              </label>
              <label>
                Note for the admin <span className="optional">(optional)</span>
                <input
                  value={note}
                  onChange={e => setNote(e.target.value)}
                  placeholder="e.g. School supplies for the June camp"
                />
              </label>
              <button
                className="btn-primary"
                disabled={submitting || !amount || Number(amount) > available}
              >
                {submitting ? 'Submitting…' : `Request ₹${amount || '0'}`}
              </button>
              <p className="fin-note">
                The amount moves to <strong>On hold</strong> straight away, so it cannot be
                requested twice while the admin reviews it.
              </p>
            </form>
          )}
        </section>
      </div>

      {/* ── Withdrawal history ───────────────────────────────────────────── */}
      <section className="fin-card fin-wide">
        <h2>Withdrawal requests</h2>
        {withdrawals.length === 0 ? (
          <p className="fin-empty">No withdrawal requests yet.</p>
        ) : (
          <div className="table-scroll">
            <table className="fin-table">
              <thead>
                <tr><th>Requested</th><th>Amount</th><th>To</th><th>Status</th><th>Reference / note</th></tr>
              </thead>
              <tbody>
                {withdrawals.map(w => {
                  const meta = STATUS_META[w.status] || { label: w.status, className: '' };
                  return (
                    <tr key={w.id}>
                      <td>{new Date(w.requestedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</td>
                      <td className="num">₹{formatRupees(w.amount)}</td>
                      <td>{w.payoutMaskedDestination}</td>
                      <td><span className={`pill ${meta.className}`}>{meta.label}</span></td>
                      <td className="muted">{w.paymentReference || w.adminNote || '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── Ledger ───────────────────────────────────────────────────────── */}
      <section className="fin-card fin-wide">
        <h2>Account history</h2>
        {ledger.length === 0 ? (
          <p className="fin-empty">No activity yet. It will appear here as donations come in.</p>
        ) : (
          <ul className="ledger-list">
            {ledger.map(entry => {
              const meta = LEDGER_META[entry.type] || { label: entry.type, sign: '', className: '' };
              return (
                <li key={entry.id} className="ledger-row">
                  <div className="ledger-detail">
                    <strong>{entry.description || meta.label}</strong>
                    <small>
                      {new Date(entry.createdAt).toLocaleString('en-IN', {
                        day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
                      })}
                      {entry.type === 'DONATION_RECEIVED' && !entry.cleared && ' · clearing'}
                    </small>
                  </div>
                  <div className="ledger-amount-block">
                    <span className={`ledger-amount ${meta.className}`}>
                      {meta.sign}₹{formatRupees(entry.amount)}
                    </span>
                    {entry.availableAfter != null && (
                      <small className="ledger-balance">Available ₹{formatRupees(entry.availableAfter)}</small>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
};

export default NgoFinance;
