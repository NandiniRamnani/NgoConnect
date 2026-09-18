import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './Donate.css';
import DemoCheckout from '../components/DemoCheckout';

const AMOUNTS = [100, 500, 1000, 5000];

const Donate = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [selectedNGO, setSelectedNGO] = useState(null);
  const [ngos, setNgos] = useState([]);
  const [amount, setAmount] = useState('');
  const [customAmount, setCustomAmount] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [paymentReference, setPaymentReference] = useState('');

  // 'wallet' pays from the NGOConnect balance in one request; 'razorpay' opens Checkout.
  const [payMethod, setPayMethod] = useState('razorpay');
  const [wallet, setWallet] = useState(null);

  /** Set when the backend reports demoMode; carries the order the simulated sheet is paying for. */
  const [demoOrder, setDemoOrder] = useState(null);

  // Every authenticated call reuses these HTTP Basic credentials.
  const authHeaders = useCallback(() => ({
    headers: { Authorization: `Basic ${btoa(`${user?.email}:${user?._pass || ''}`)}` },
  }), [user]);

  useEffect(() => {
    api.get('/ngos').then(response => setNgos(response.data)).catch(() => setNgos([]));
  }, []);

  // Load the balance so step 3 can show it and disable the wallet option when it is short.
  // GET /api/wallet creates an empty wallet on first call, so a new donor sees 0.00, not an error.
  useEffect(() => {
    if (!user) { setWallet(null); return; }
    api.get('/wallet', authHeaders())
      .then(response => setWallet(response.data))
      .catch(() => setWallet(null)); // e.g. an NGO login, which has no wallet — just hide the option
  }, [user, authHeaders]);

  const handleNext = () => setStep((prev) => Math.min(prev + 1, 4));
  const handleBack = () => setStep((prev) => Math.max(prev - 1, 1));

  const currentAmount = amount === 'custom' ? customAmount : amount;

  const walletBalance = Number(wallet?.balance ?? 0);
  const walletCovers = wallet != null && walletBalance >= Number(currentAmount || 0);

  // If the donor picks the wallet and then raises the amount past their balance, silently
  // fall back to Razorpay — otherwise the Pay button would fire a request the backend must reject.
  useEffect(() => {
    if (payMethod === 'wallet' && !walletCovers) setPayMethod('razorpay');
  }, [payMethod, walletCovers]);

  /**
   * One request, no popup. The backend checks the balance, subtracts it and marks the
   * donation PAID inside a single call — see DonationService.donateFromWallet.
   * The reference that comes back is the wallet ledger row id, which plays the same role
   * a Razorpay payment id plays in the gateway flow.
   */
  const handleWalletPay = async () => {
    if (!user) { navigate('/login'); return; }
    setSubmitError('');
    setSubmitting(true);
    try {
      const response = await api.post('/donations/wallet', {
        ngoId: selectedNGO.id,
        amount: Number(currentAmount),
      }, authHeaders());
      setPaymentReference(response.data.paymentReference);
      // Re-read the balance rather than subtracting locally — the server is the source of truth.
      api.get('/wallet', authHeaders()).then(r => setWallet(r.data)).catch(() => {});
      handleNext();
    } catch (err) {
      setSubmitError(err.response?.data?.message || 'Could not complete the wallet payment. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Step 3, shared by the real popup and the demo sheet, so both prove out the same code path.
   */
  const confirmDonation = async (razorpayOrderId, response) => {
    try {
      await api.post('/donations/verify', {
        razorpayOrderId,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature,
      }, authHeaders());
      setPaymentReference(response.razorpay_payment_id);
      handleNext();
    } catch {
      setSubmitError(`Payment went through but we couldn't confirm it automatically. Reference: ${response.razorpay_payment_id} — contact support with this ID.`);
    } finally {
      setSubmitting(false);
      setDemoOrder(null);
    }
  };

  // Opens the real Razorpay Checkout popup. Two backend round-trips: one to reserve an
  // order before the popup opens, one to verify the payment's signature after it closes —
  // see DonationService.java for why both steps exist.
  const handlePay = async () => {
    if (!user) { navigate('/login'); return; }
    setSubmitError('');
    setSubmitting(true);
    try {
      const orderRes = await api.post('/donations/create-order', {
        ngoId: selectedNGO.id,
        amount: Number(currentAmount),
      }, authHeaders());
      const order = orderRes.data;

      // The server states which checkout applies; the browser never guesses. In demo mode the
      // Razorpay script is not involved at all.
      if (order.demoMode) { setDemoOrder(order); return; }

      if (!window.Razorpay) {
        setSubmitError('Payment system failed to load. Please refresh and try again.');
        setSubmitting(false);
        return;
      }

      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        order_id: order.razorpayOrderId,
        name: 'NGOConnect',
        description: `Donation to ${selectedNGO.ngoName}`,
        prefill: { name: user.fullName || '', email: user.email || '' },
        theme: { color: '#16a34a' },
        handler: async (response) => {
          try {
            await api.post('/donations/verify', {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            }, authHeaders());
            setPaymentReference(response.razorpay_payment_id);
            handleNext();
          } catch {
            setSubmitError(`Payment went through but we couldn't confirm it automatically. Reference: ${response.razorpay_payment_id} — contact support with this ID.`);
          } finally {
            setSubmitting(false);
          }
        },
        modal: {
          ondismiss: () => setSubmitting(false), // user closed the popup without paying
        },
      };

      const rzp = new window.Razorpay(options);
      rzp.on('payment.failed', (response) => {
        setSubmitError(response.error?.description || 'Payment failed. Please try again.');
        setSubmitting(false);
      });
      rzp.open();
    } catch (err) {
      setSubmitError(err.response?.data?.message || 'Could not start payment. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="donate-container">
      {demoOrder && (
        <DemoCheckout
          amountPaise={demoOrder.amount}
          description={`Donation to ${selectedNGO?.ngoName || 'NGO'}`}
          onSuccess={(response) => confirmDonation(demoOrder.razorpayOrderId, response)}
          onFailure={(message) => { setSubmitError(message); setSubmitting(false); setDemoOrder(null); }}
          onClose={() => { setDemoOrder(null); setSubmitting(false); }}
        />
      )}
      <div className="donate-header">
        <h1>Make a Donation</h1>
        <div className="progress-bar-container">
          <div className="progress-bar" style={{ width: `${(step / 4) * 100}%` }}></div>
        </div>
        <p className="step-indicator">Step {step} of 4</p>
      </div>

      <div className="donate-content">
        <div className="donate-main">
          {step === 1 && (
            <div className="step-content step-1 animate-fade-in">
              <h2>Select an NGO to Support</h2>
              <div className="ngo-grid">
                {ngos.map((ngo) => (
                  <div 
                    key={ngo.id} 
                    className={`ngo-card ${selectedNGO?.id === ngo.id ? 'selected' : ''}`}
                    onClick={() => setSelectedNGO(ngo)}
                  >
                    <div className="ngo-emoji">{ngo.emoji}</div>
                    <h3>{ngo.ngoName}</h3>
                    <span className="ngo-cause">{ngo.ngoType}</span>
                    <p>{ngo.description}</p>
                  </div>
                ))}
                {ngos.length === 0 && <p className="no-results">No approved NGOs are available for donations yet.</p>}
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="step-content step-2 animate-fade-in">
              <h2>Select Donation Amount (₹)</h2>
              <div className="amount-grid">
                {AMOUNTS.map((amt) => (
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
                    placeholder="Enter amount"
                    value={customAmount}
                    onChange={(e) => setCustomAmount(e.target.value)}
                  />
                </div>
              )}
            </div>
          )}

          {step === 3 && (
            <div className="step-content step-3 animate-fade-in">
              <h2>Review &amp; Pay</h2>
              <p>You're donating <strong>₹{currentAmount}</strong> to <strong>{selectedNGO?.ngoName}</strong>.</p>

              <div className="pay-method-list">
                {/* Wallet option — only offered to donors who actually have a wallet. */}
                {wallet && (
                  <label className={`pay-method ${payMethod === 'wallet' ? 'selected' : ''} ${walletCovers ? '' : 'disabled'}`}>
                    <input
                      type="radio"
                      name="payMethod"
                      value="wallet"
                      checked={payMethod === 'wallet'}
                      disabled={!walletCovers}
                      onChange={() => setPayMethod('wallet')}
                    />
                    <span className="pay-method-body">
                      <strong>NGOConnect Wallet</strong>
                      <small>
                        Balance ₹{walletBalance.toFixed(2)}
                        {walletCovers
                          ? ' · pays instantly, no checkout popup'
                          : ' · not enough for this donation'}
                      </small>
                    </span>
                  </label>
                )}

                <label className={`pay-method ${payMethod === 'razorpay' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="payMethod"
                    value="razorpay"
                    checked={payMethod === 'razorpay'}
                    onChange={() => setPayMethod('razorpay')}
                  />
                  <span className="pay-method-body">
                    <strong>UPI / Card / Netbanking</strong>
                    <small>Opens Razorpay's secure checkout. Your card and UPI details go directly to Razorpay — this site never sees them.</small>
                  </span>
                </label>
              </div>

              {wallet && !walletCovers && (
                <p className="pay-method-hint">
                  Want one-tap donations? <button type="button" className="link-button" onClick={() => navigate('/wallet')}>Top up your wallet</button>.
                </p>
              )}

              {submitError && <p className="no-results" style={{ color: 'var(--error, #dc2626)' }}>{submitError}</p>}
            </div>
          )}

          {step === 4 && (
            <div className="step-content step-4 animate-scale-up">
              <div className="success-icon">🎉</div>
              <h2>Thank You!</h2>
              <p>Your generous donation of <strong>₹{currentAmount}</strong> to <strong>{selectedNGO?.ngoName}</strong> has been processed successfully.</p>
              {paymentReference && (
                <p className="donation-reference">
                  Paid via {payMethod === 'wallet' ? 'wallet balance' : 'Razorpay'} · Reference: {paymentReference}
                </p>
              )}
              <div className="confetti-container">
                <span className="confetti">✨</span>
                <span className="confetti">💖</span>
                <span className="confetti">🙌</span>
                <span className="confetti">🌟</span>
              </div>
              <button className="btn-primary mt-4" onClick={() => navigate('/dashboard')}>
                Go to Dashboard
              </button>
            </div>
          )}
        </div>

        {step < 4 && (
          <div className="donate-sidebar animate-slide-in">
            <div className="summary-card">
              <h3>Donation Summary</h3>
              <div className="summary-item">
                <span>NGO:</span>
                <strong>{selectedNGO ? selectedNGO.ngoName : 'Not selected'}</strong>
              </div>
              <div className="summary-item">
                <span>Amount:</span>
                <strong>{currentAmount ? `₹${currentAmount}` : '-'}</strong>
              </div>
              
              <div className="summary-actions">
                {step > 1 && (
                  <button className="btn-secondary" onClick={handleBack}>
                    Back
                  </button>
                )}
                
                {step === 1 && (
                  <button 
                    className="btn-primary" 
                    disabled={!selectedNGO} 
                    onClick={handleNext}
                  >
                    Continue
                  </button>
                )}
                
                {step === 2 && (
                  <button 
                    className="btn-primary" 
                    disabled={!currentAmount || currentAmount <= 0} 
                    onClick={handleNext}
                  >
                    Continue to Payment
                  </button>
                )}

                {step === 3 && (
                  <button
                    className="btn-primary"
                    disabled={submitting}
                    onClick={payMethod === 'wallet' ? handleWalletPay : handlePay}
                  >
                    {submitting
                      ? 'Processing...'
                      : payMethod === 'wallet'
                        ? `Pay ₹${currentAmount} from wallet`
                        : `Pay ₹${currentAmount}`}
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Donate;
