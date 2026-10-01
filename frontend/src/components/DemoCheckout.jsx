import { useState } from 'react';
import './DemoCheckout.css';

/**
 * A stand-in for the Razorpay Checkout popup, used when the backend reports demoMode.
 *
 * WHY THIS EXISTS
 * The real integration is complete and stays in the code — but a live demo depends on things
 * outside the project: the internet, Razorpay's test environment being up, and their test-mode
 * rules about which cards an Indian account may use. Any one of those failing in front of an
 * audience looks like a broken project when nothing is broken at all.
 *
 * So the gateway round trip is simulated while everything on our side of the line runs for real:
 * the backend still writes a PENDING record, still claims it atomically, still moves the balance,
 * still credits the NGO, still issues the 80G receipt. What is being demonstrated is the system
 * that was built; only the third party is stood in for.
 *
 * It deliberately offers a FAILURE button as well. Error handling is a real part of the design,
 * and showing it on purpose is far better than hoping it never appears by accident.
 */
const METHODS = [
  { id: 'upi',        icon: '📱', label: 'UPI',         detail: 'demo@okaxis' },
  { id: 'card',       icon: '💳', label: 'Card',        detail: '•••• •••• •••• 4242' },
  { id: 'netbanking', icon: '🏦', label: 'Netbanking',  detail: 'Demo Bank' },
  { id: 'wallet',     icon: '👛', label: 'Wallet',      detail: 'Demo Wallet' },
];

export default function DemoCheckout({ amountPaise, description, onSuccess, onFailure, onClose }) {
  const [method, setMethod] = useState('upi');
  const [processing, setProcessing] = useState(false);

  // The backend speaks paise; only this last step converts to rupees for display.
  const rupees = (amountPaise / 100).toLocaleString('en-IN', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });

  /**
   * A short delay before reporting the result, purely so the simulation reads like a payment
   * rather than an instant state flip. A real gateway takes a moment, and a demo that resolves
   * with no pause makes people wonder whether anything happened at all.
   */
  const settle = (succeeded) => {
    setProcessing(true);
    setTimeout(() => {
      setProcessing(false);
      if (succeeded) {
        // Shaped like the object Razorpay's real handler receives, so the calling page can treat
        // both paths identically instead of branching on which checkout produced the result.
        onSuccess({
          razorpay_payment_id: 'pay_demo_' + Math.random().toString(36).slice(2, 12),
          razorpay_signature: 'demo_signature_not_verified',
        });
      } else {
        onFailure('Payment was declined by the bank (simulated).');
      }
    }, 900);
  };

  return (
    <div className="demo-checkout-overlay" onClick={processing ? undefined : onClose}>
      {/* stopPropagation so a click inside the sheet does not reach the overlay's close handler. */}
      <div className="demo-checkout" onClick={(e) => e.stopPropagation()}>

        <div className="demo-checkout-head">
          <div>
            <span className="demo-badge">DEMO MODE</span>
            <h3>NGOConnect</h3>
            <p>{description}</p>
          </div>
          <button className="demo-close" onClick={onClose} disabled={processing} aria-label="Close">×</button>
        </div>

        <div className="demo-amount">
          <span>Amount payable</span>
          <strong>₹{rupees}</strong>
        </div>

        <div className="demo-methods">
          {METHODS.map((m) => (
            <button
              key={m.id}
              className={`demo-method ${method === m.id ? 'selected' : ''}`}
              onClick={() => setMethod(m.id)}
              disabled={processing}
            >
              <span className="demo-method-icon">{m.icon}</span>
              <span className="demo-method-text">
                <strong>{m.label}</strong>
                <small>{m.detail}</small>
              </span>
              <span className="demo-radio" />
            </button>
          ))}
        </div>

        <button className="demo-pay" onClick={() => settle(true)} disabled={processing}>
          {processing ? 'Processing…' : `Pay ₹${rupees}`}
        </button>

        <button className="demo-fail" onClick={() => settle(false)} disabled={processing}>
          Simulate a failed payment
        </button>

        <p className="demo-footnote">
          No real money moves. The Razorpay integration is implemented in full — this sheet
          stands in for the gateway so the rest of the flow can be demonstrated reliably.
        </p>
      </div>
    </div>
  );
}
