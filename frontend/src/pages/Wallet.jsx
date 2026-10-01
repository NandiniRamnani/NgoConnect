import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import DemoCheckout from '../components/DemoCheckout';
import './Wallet.css';

const TOPUP_AMOUNTS = [100, 500, 1000, 5000];

// The backend sends money twice in every response: `balancePaise` (the exact integer it stores)
// and `balance` (the same value as rupees). We display the rupee one and never do arithmetic
// on it in the browser, because the balance shown must always be whatever the server computed.
const formatRupees = (value) =>
  Number(value ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const TYPE_META = {
  TOPUP: { label: 'Top-up', sign: '+', icon: '↓', className: 'credit' },
  DONATION: { label: 'Donation', sign: '−', icon: '↑', className: 'debit' },
  REFUND: { label: 'Refund', sign: '+', icon: '↺', className: 'credit' },
};

const Wallet = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [wallet, setWallet] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [amount, setAmount] = useState('');
  const [customAmount, setCustomAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  /**
   * Holds the simulated-payment sheet's state when the backend reports demoMode. Null means no
   * sheet is open; otherwise it carries the order this payment belongs to.
   */
  const [demoOrder, setDemoOrder] = useState(null);

  // Every wallet endpoint is behind ROLE_USER, so each call carries HTTP Basic credentials —
  // the same pattern Donate and Dashboard already use.
  const authHeaders = useCallback(() => {
    const creds = btoa(`${user?.email}:${user?._pass || ''}`);
    return { headers: { Authorization: `Basic ${creds}` } };
  }, [user]);

  const loadWallet = useCallback(async () => {
    try {
      // Two independent GETs, fired together rather than one after the other.
      const [walletRes, txnRes] = await Promise.all([
        api.get('/wallet', authHeaders()),
        api.get('/wallet/transactions', authHeaders()),
      ]);
      setWallet(walletRes.data);
      setTransactions(txnRes.data);
    } catch {
      setError('Could not load your wallet. Please refresh and try again.');
    } finally {
      setLoading(false);
    }
  }, [authHeaders]);

  useEffect(() => {
    if (!user) { navigate('/login'); return; }
    loadWallet();
  }, [user, navigate, loadWallet]);

  const currentAmount = amount === 'custom' ? customAmount : amount;

  /**
   * Top-up: the same two-round-trip Razorpay dance the donation flow uses.
   *
   *   1. POST /wallet/topup/create-order  -> backend writes a PENDING row and returns an order id
   *   2. Razorpay Checkout popup          -> the donor actually pays; card details never touch us
   *   3. POST /wallet/topup/verify        -> backend checks the signature and only THEN credits
   *
   * The balance rendered afterwards is the one returned by step 3, not a number added up here.
   */
  const handleTopUp = async () => {
    setError('');
    setSuccess('');
    setSubmitting(true);

    try {
      const orderRes = await api.post('/wallet/topup/create-order',
        { amount: Number(currentAmount) }, authHeaders());
      const order = orderRes.data;

      // The server decides which checkout applies and says so explicitly, so the browser never
      // has to guess. In demo mode the Razorpay script is not used at all.
      if (order.demoMode) { setDemoOrder(order); return; }

      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        order_id: order.razorpayOrderId,
        name: 'NGOConnect',
        description: `Wallet top-up of ₹${currentAmount}`,
        prefill: { name: user.fullName || '', email: user.email || '' },
        theme: { color: '#1c5c42' },
        handler: async (response) => {
          try {
            const verified = await api.post('/wallet/topup/verify', {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            }, authHeaders());
            setWallet(verified.data); // authoritative post-credit balance from the server
            setSuccess(`₹${formatRupees(currentAmount)} added to your wallet.`);
            setAmount('');
            setCustomAmount('');
            loadWallet(); // refresh the passbook so the new TOPUP row appears
          } catch {
            setError(`Payment went through but we couldn't confirm it automatically. Reference: ${response.razorpay_payment_id} — contact support with this ID.`);
          } finally {
            setSubmitting(false);
          }
        },
        modal: {
          ondismiss: () => setSubmitting(false), // closed the popup without paying
        },
      };

      const rzp = new window.Razorpay(options);
      rzp.on('payment.failed', (response) => {
        setError(response.error?.description || 'Top-up failed. Please try again.');
        setSubmitting(false);
      });
      rzp.open();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not start the top-up. Please try again.');
      setSubmitting(false);
    }
  };

  /**
   * Step 3 of the top-up, shared by both checkouts.
   *
   * Pulled out of the Razorpay handler so the demo sheet calls exactly the same verification and
   * the same state updates. If the two paths had their own copies they would drift, and the demo
   * would stop being evidence that the real path works.
   */
  const confirmTopUp = async (razorpayOrderId, response) => {
    try {
      const verified = await api.post('/wallet/topup/verify', {
        razorpayOrderId,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature,
      }, authHeaders());
      setWallet(verified.data);
      setSuccess(`₹${formatRupees(currentAmount)} added to your wallet.`);
      setAmount('');
      setCustomAmount('');
      loadWallet();
    } catch {
      setError(`Payment went through but we couldn't confirm it automatically. Reference: ${response.razorpay_payment_id} — contact support with this ID.`);
    } finally {
      setSubmitting(false);
      setDemoOrder(null);
    }
  };

  if (loading) return <div className="wallet-container"><p className="wallet-loading">Loading your wallet…</p></div>;

  return (
    <div className="wallet-container">
      {demoOrder && (
        <DemoCheckout
          amountPaise={demoOrder.amount}
          description={`Wallet top-up of ₹${currentAmount}`}
          onSuccess={(response) => confirmTopUp(demoOrder.razorpayOrderId, response)}
          onFailure={(message) => { setError(message); setSubmitting(false); setDemoOrder(null); }}
          onClose={() => { setDemoOrder(null); setSubmitting(false); }}
        />
      )}
      <div className="wallet-header">
        <h1>My Wallet</h1>
        <p>Add money once, then donate to any NGO in a single tap.</p>
      </div>

      <div className="wallet-grid">
        <div className="wallet-balance-card">
          <span className="balance-label">Available balance</span>
          <span className="balance-value">₹{formatRupees(wallet?.balance)}</span>
          <button className="btn-secondary balance-donate" onClick={() => navigate('/donate')}>
            Donate from wallet
          </button>
        </div>

        <div className="wallet-topup-card">
          <h2>Add money</h2>
          <div className="amount-grid">
            {TOPUP_AMOUNTS.map((amt) => (
              <button
                key={amt}
                className={`amount-chip ${amount === amt ? 'selected' : ''}`}
                onClick={() => setAmount(amt)}
              >
                ₹{amt}
              </button>
            ))}
            <button
              className={`amount-chip ${amount === 'custom' ? 'selected' : ''}`}
              onClick={() => setAmount('custom')}
            >
              Custom
            </button>
          </div>

          {amount === 'custom' && (
            <div className="custom-amount-input">
              <span>₹</span>
              <input
                type="number"
                min="1"
                placeholder="Enter amount"
                value={customAmount}
                onChange={(e) => setCustomAmount(e.target.value)}
              />
            </div>
          )}

          {error && <p className="wallet-message error">{error}</p>}
          {success && <p className="wallet-message success">{success}</p>}

          <button
            className="btn-primary wallet-topup-btn"
            disabled={submitting || !currentAmount || Number(currentAmount) <= 0}
            onClick={handleTopUp}
          >
            {submitting ? 'Processing…' : currentAmount ? `Add ₹${currentAmount}` : 'Add money'}
          </button>
          <p className="wallet-note">
            Payment is handled by Razorpay. Your card or UPI details go straight to them —
            this site never sees them.
          </p>
        </div>
      </div>

      <div className="wallet-history">
        <h2>Transaction history</h2>
        {transactions.length === 0 ? (
          <p className="wallet-empty">No wallet activity yet. Add money to get started.</p>
        ) : (
          <ul className="txn-list">
            {transactions.map((txn) => {
              const meta = TYPE_META[txn.type] || { label: txn.type, sign: '', icon: '•', className: '' };
              return (
                <li key={txn.id} className={`txn-row ${txn.status === 'PENDING' ? 'pending' : ''}`}>
                  <span className={`txn-icon ${meta.className}`}>{meta.icon}</span>
                  <div className="txn-detail">
                    <strong>{txn.description || meta.label}</strong>
                    <small>
                      {new Date(txn.createdAt).toLocaleString('en-IN', {
                        day: 'numeric', month: 'short', year: 'numeric',
                        hour: '2-digit', minute: '2-digit',
                      })}
                      {txn.status !== 'COMPLETED' && ` · ${txn.status}`}
                    </small>
                  </div>
                  <div className="txn-amount-block">
                    <span className={`txn-amount ${meta.className}`}>
                      {meta.sign}₹{formatRupees(txn.amount)}
                    </span>
                    {txn.balanceAfter != null && (
                      <small className="txn-balance">Bal ₹{formatRupees(txn.balanceAfter)}</small>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
};

export default Wallet;
