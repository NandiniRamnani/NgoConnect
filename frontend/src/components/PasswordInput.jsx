import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import './PasswordInput.css';

/**
 * A password <input> with a show/hide eye toggle. Drop-in replacement for
 * <input type="password" ...> - every other prop passes straight through.
 */
export default function PasswordInput({ className = '', ...inputProps }) {
  const [visible, setVisible] = useState(false);

  return (
    <div className={`password-input-wrap ${className}`}>
      <input {...inputProps} type={visible ? 'text' : 'password'} />
      <button
        type="button"
        className="password-toggle"
        onClick={() => setVisible(v => !v)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        aria-pressed={visible}
        tabIndex={-1}
      >
        {visible ? <EyeOff size={17} /> : <Eye size={17} />}
      </button>
    </div>
  );
}
