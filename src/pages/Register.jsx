import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import PasswordInput from '../components/PasswordInput';
import './Register.css';

const initialForm = { fullName: '', email: '', password: '', confirmPassword: '', ngoType: 'Education', legalStructure: 'Trust', registrationNumber: '', panNumber: '', ngoDarpanId: '', authorizedPersonName: '', authorizedPersonDesignation: '', description: '', location: '', address: '', contactPhone: '', websiteUrl: '', facebookUrl: '', instagramUrl: '', linkedinUrl: '' };

export default function Register() {
  const [activeTab, setActiveTab] = useState('user');
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState(initialForm);
  const [files, setFiles] = useState({ regCert: null, panCard: null, darpanCert: null, otherDoc: null });
  const [docChecks, setDocChecks] = useState({}); // key -> { checking, status, message }
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const isNgo = activeTab === 'ngo';

  const change = e => setFormData(d => ({ ...d, [e.target.name]: e.target.value }));
  const chooseTab = tab => { setActiveTab(tab); setStep(1); setError(''); setFiles({ regCert: null, panCard: null, darpanCert: null, otherDoc: null }); setDocChecks({}); };

  const DOC_TYPES = { regCert: 'REGISTRATION_CERT', panCard: 'PAN_CARD', darpanCert: 'DARPAN_CERT', otherDoc: 'OTHER' };

  // Runs the moment a file is picked, so a wrong document is caught right away
  // instead of only after the whole form is submitted. Advisory only — it never
  // blocks Continue, it just shows the user a heads-up next to the file.
  const checkDocument = async (key, file) => {
    setDocChecks(d => ({ ...d, [key]: { checking: true } }));
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('docType', DOC_TYPES[key]);
      if (formData.panNumber) fd.append('panNumber', formData.panNumber);
      if (formData.registrationNumber) fd.append('registrationNumber', formData.registrationNumber);
      const res = await api.post('/ngos/verify-document', fd, { headers: { 'Content-Type': undefined } });
      setDocChecks(d => ({ ...d, [key]: { checking: false, status: res.data.status, message: res.data.message } }));
    } catch {
      setDocChecks(d => ({ ...d, [key]: { checking: false, status: 'CHECK_FAILED', message: "Couldn't reach the automatic document check right now — your file was still attached." } }));
    }
  };

  const handleFile = (key, e) => {
    const file = e.target.files[0];
    if (file && file.size > 10 * 1024 * 1024) { setError('File size must be under 10MB'); return; }
    setFiles(f => ({ ...f, [key]: file || null }));
    setError('');
    if (file) checkDocument(key, file);
    else setDocChecks(d => ({ ...d, [key]: undefined }));
  };
  const removeFile = key => { setFiles(f => ({ ...f, [key]: null })); setDocChecks(d => ({ ...d, [key]: undefined })); };
  const formatSize = bytes => bytes < 1024 ? bytes + ' B' : bytes < 1048576 ? (bytes / 1024).toFixed(1) + ' KB' : (bytes / 1048576).toFixed(1) + ' MB';

  const required1 = ['fullName', 'email', 'contactPhone', 'location', 'address', 'ngoType', 'description'];
  const required2 = ['legalStructure', 'registrationNumber', 'panNumber', 'authorizedPersonName', 'authorizedPersonDesignation'];

  const next = () => {
    if (step === 1) {
      if (required1.some(k => !formData[k]?.trim())) return setError('Please complete all required fields.');
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim())) return setError('Enter a valid email address.');
      if (formData.contactPhone.replace(/[^0-9]/g, '').length < 10) return setError('Enter a valid contact phone number.');
    }
    if (step === 2) {
      if (required2.some(k => !formData[k]?.trim())) return setError('Please complete all required fields.');
      if (!/^[A-Z]{5}[0-9]{4}[A-Z]$/i.test(formData.panNumber.trim())) return setError('Enter a valid 10-character PAN.');
      const regNo = formData.registrationNumber.trim();
      if (formData.legalStructure === 'Section 8 Company') {
        if (!/^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$/i.test(regNo))
          return setError('Enter a valid CIN, e.g. U85300MH2015NPL123456.');
      } else if (!/^[A-Z0-9][A-Z0-9 ./-]{3,28}[A-Z0-9]$/i.test(regNo)) {
        return setError('Enter a valid registration number (5-30 characters).');
      }
    }
    if (step === 3) {
      if (!files.regCert) return setError('Registration Certificate is required.');
      if (!files.panCard) return setError('PAN Card is required.');
    }
    setError(''); setStep(step + 1); window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const submit = async e => {
    e.preventDefault(); setError('');
    if (isNgo && step < 4) return next();
    if (formData.password.length < 8) return setError('Use a password with at least 8 characters.');
    if (formData.password !== formData.confirmPassword) return setError('Passwords do not match.');
    setLoading(true);
    try {
      if (isNgo) {
        const fd = new FormData();
        fd.append('data', JSON.stringify({ ngoName: formData.fullName, email: formData.email, password: formData.password, ngoType: formData.ngoType, legalStructure: formData.legalStructure, registrationNumber: formData.registrationNumber, panNumber: formData.panNumber.toUpperCase(), ngoDarpanId: formData.ngoDarpanId, authorizedPersonName: formData.authorizedPersonName, authorizedPersonDesignation: formData.authorizedPersonDesignation, description: formData.description, location: formData.location, address: formData.address, contactPhone: formData.contactPhone, websiteUrl: formData.websiteUrl, facebookUrl: formData.facebookUrl, instagramUrl: formData.instagramUrl, linkedinUrl: formData.linkedinUrl }));
        fd.append('regCert', files.regCert);
        fd.append('panCard', files.panCard);
        if (files.darpanCert) fd.append('darpanCert', files.darpanCert);
        if (files.otherDoc) fd.append('otherDoc', files.otherDoc);
        const res = await api.post('/ngos/register', fd, { headers: { 'Content-Type': undefined } });
        navigate('/login', { state: { message: `Application received! Your ID is ${res.data.uniqueNgoId}. Save it for tracking.` } });
      } else {
        await api.post('/accounts/register', { fullName: formData.fullName, email: formData.email, password: formData.password, role: 'USER' });
        navigate('/login', { state: { message: 'Registration successful. Please sign in.' } });
      }
    } catch (err) { setError(err.response?.data?.message || 'Registration failed. Please try again.'); }
    finally { setLoading(false); }
  };

  const field = (label, name, opts = {}) => {
    const { optional, ...inputOpts } = opts;
    const isPassword = inputOpts.type === 'password';
    return (
      <label className="registration-field">
        <span>{label}{optional && <em>Optional</em>}</span>
        {isPassword
          ? <PasswordInput name={name} value={formData[name]} onChange={change} required={!optional} {...inputOpts} />
          : <input name={name} value={formData[name]} onChange={change} required={!optional} {...inputOpts} />}
      </label>
    );
  };
  const passwords = <><div className="field-grid">{field('Password', 'password', { type: 'password', placeholder: 'At least 8 characters' })}{field('Confirm password', 'confirmPassword', { type: 'password', placeholder: 'Repeat your password' })}</div>{formData.password && <p className="password-helper">Use at least 8 characters. A mix of letters, numbers and symbols is best.</p>}</>;

  const docCheckBanner = key => {
    const check = docChecks[key];
    if (!check) return null;
    if (check.checking) return <p className="doc-check doc-check-pending">Checking document…</p>;
    if (check.status === 'CHECK_FAILED') return <p className="doc-check doc-check-warn">⚠ {check.message}</p>;
    if (check.status === 'LOOKS_VALID') return <p className="doc-check doc-check-ok">✓ {check.message}</p>;
    if (check.status === 'LOOKS_SUSPICIOUS') return <p className="doc-check doc-check-warn">⚠ {check.message}</p>;
    return <p className="doc-check doc-check-info">ℹ {check.message}</p>; // UNREADABLE
  };

  const fileInput = (key, label, required) => (
    <div className="registration-field">
      <span>{label}{required ? <span className="upload-required">*required</span> : <span className="upload-optional">optional</span>}</span>
      {files[key] ? (
        <>
          <div className="file-chip">
            <span className="file-info">📄 {files[key].name} <span className="file-size">({formatSize(files[key].size)})</span></span>
            <button type="button" className="remove-file" onClick={() => removeFile(key)}>✕</button>
          </div>
          {docCheckBanner(key)}
        </>
      ) : (
        <div className="upload-zone" onClick={() => document.getElementById('file-' + key)?.click()}>
          <label><span className="upload-icon">📁</span> Click to select file (PDF, JPG, PNG — max 10MB)</label>
          <input id={'file-' + key} type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={e => handleFile(key, e)} />
        </div>
      )}
    </div>
  );

  const stepNames = ['Organisation', 'Legal & Social', 'Documents', 'Account'];

  return <main className="registration-page"><section className="registration-aside"><Link to="/" className="registration-logo"><b>NGO</b>Connect</Link><div className="aside-copy"><span className="eyebrow">{isNgo ? 'PARTNER WITH PURPOSE' : 'COMMUNITY STARTS HERE'}</span><h1>{isNgo ? "Bring your organisation's impact to more people." : 'A more connected way to make an impact.'}</h1><p>{isNgo ? 'Build trust with a thoughtful application and reach volunteers, donors, and communities who care.' : 'Find local causes, volunteer opportunities, and organisations making a real difference.'}</p></div>{isNgo && <div className="trust-note"><span>✦</span><div><strong>A careful review, built for trust</strong><p>Your PAN and documents are visible only to authorised reviewers.</p></div></div>}<p className="aside-footer">Already a member? <Link to="/login">Sign in</Link></p></section>
  <section className="registration-content"><div className="registration-top"><p>Already have an account? <Link to="/login">Sign in</Link></p></div>
  <div className="registration-panel"><div className="registration-switch"><button type="button" onClick={() => chooseTab('user')} className={!isNgo ? 'active' : ''}>Join as an individual</button><button type="button" onClick={() => chooseTab('ngo')} className={isNgo ? 'active' : ''}>Register an NGO</button></div>
  {isNgo && <div className="progress"><div className="progress-steps">{stepNames.map((name, i) => <div className={step >= i + 1 ? 'done' : ''} key={name}><i>{i + 1}</i><span>{name}</span></div>)}</div><div className="progress-line"><b style={{ width: `${((step - 1) / 3) * 100}%` }} /></div></div>}
  <header className="registration-heading"><span className="eyebrow">{isNgo ? `STEP ${step} OF 4` : 'JOIN NGO CONNECT'}</span><h2>{isNgo ? ['Tell us about your organisation', 'Legal details & online presence', 'Upload verification documents', 'Create your secure account'][step - 1] : 'Create your account'}</h2><p>{isNgo ? ['These details help supporters discover the work you do.', 'We verify your identity and social presence to protect the community.', 'Upload registration documents for admin verification.', 'Choose sign-in details. We will notify you once reviewed.'][step - 1] : 'It takes less than a minute to get started.'}</p></header>
  {error && <div className="registration-error">{error}</div>}
  <form onSubmit={submit} className="registration-form">
    {!isNgo && <><div className="field-grid">{field('Full name', 'fullName', { placeholder: 'Your name' })}{field('Email address', 'email', { type: 'email', placeholder: 'you@example.com' })}</div>{passwords}</>}
    {isNgo && step === 1 && <><div className="field-grid">{field('Organisation name', 'fullName', { placeholder: 'e.g. Green Earth Foundation' })}{field('Email address', 'email', { type: 'email', placeholder: 'hello@organisation.org' })}</div><div className="field-grid">{field('Contact number', 'contactPhone', { type: 'tel', placeholder: '+91 98765 43210' })}{field('City and state', 'location', { placeholder: 'Mumbai, Maharashtra' })}</div><label className="registration-field"><span>Full address</span><textarea name="address" value={formData.address} onChange={change} required placeholder="Street, area, city, state, PIN code — this is shown to donors so they know where to send items." rows="2" /></label><p className="social-help-text">This is the address donors will see when you post a need for physical items (you can give a different address per post if needed).</p><label className="registration-field"><span>Primary focus area</span><select name="ngoType" value={formData.ngoType} onChange={change}>{['Education', 'Healthcare', 'Environment', 'Food & hunger relief', 'Women & child welfare', 'Animal welfare', 'Other'].map(x => <option key={x}>{x}</option>)}</select></label><label className="registration-field"><span>What does your NGO do?</span><textarea name="description" value={formData.description} onChange={change} required placeholder="Describe the people you serve and the change you are working towards." rows="4" /></label></>}
    {isNgo && step === 2 && <><div className="field-grid"><label className="registration-field"><span>Legal structure</span><select name="legalStructure" value={formData.legalStructure} onChange={change}>{['Trust', 'Society', 'Section 8 Company', 'Other registered non-profit'].map(x => <option key={x}>{x}</option>)}</select></label>{field('Registration number', 'registrationNumber', { placeholder: formData.legalStructure === 'Section 8 Company' ? 'U85300MH2015NPL123456' : 'e.g. E-12345 or S/12345/2020' })}</div><div className="field-grid">{field('Organisation PAN', 'panNumber', { placeholder: 'AABCT1234A', maxLength: 10 })}{field('NGO Darpan ID', 'ngoDarpanId', { placeholder: 'If available', optional: true })}</div><div className="field-grid">{field('Authorised representative', 'authorizedPersonName', { placeholder: 'Full name' })}{field('Designation', 'authorizedPersonDesignation', { placeholder: 'Trustee, Secretary, Director...' })}</div><p className="privacy-note">🔒 Your PAN is never shown publicly. Only authorised admins review it.</p><div className="social-section-heading">ONLINE PRESENCE FOR VERIFICATION</div><p className="social-help-text">Our admin visits these links to confirm your NGO is real and active online. Provide at least one.</p><div className="field-grid">{field('Website URL', 'websiteUrl', { type: 'url', placeholder: 'https://yourorganisation.org', optional: true })}{field('Facebook page', 'facebookUrl', { type: 'url', placeholder: 'https://facebook.com/yourpage', optional: true })}</div><div className="field-grid">{field('Instagram', 'instagramUrl', { type: 'url', placeholder: 'https://instagram.com/yourhandle', optional: true })}{field('LinkedIn', 'linkedinUrl', { type: 'url', placeholder: 'https://linkedin.com/company/...', optional: true })}</div></>}
    {isNgo && step === 3 && <div className="doc-section">{fileInput('regCert', 'Registration Certificate', true)}{fileInput('panCard', 'PAN Card', true)}{fileInput('darpanCert', 'NGO Darpan Certificate', false)}{fileInput('otherDoc', 'Other Supporting Document', false)}<div className="doc-privacy-note">🔒 Documents are stored securely and only viewed by authorized admins. Never shared publicly.</div></div>}
    {isNgo && step === 4 && <>{passwords}<label className="consent"><input type="checkbox" required /><span>I confirm I am authorised to submit this application and that the information provided is accurate.</span></label><div className="review-summary"><span>APPLICATION SUMMARY</span><strong>{formData.fullName || 'Your organisation'}</strong><p>{formData.legalStructure} · {formData.ngoType} · {formData.location}</p><p>{[files.regCert, files.panCard, files.darpanCert, files.otherDoc].filter(Boolean).length} document(s) attached</p></div></>}
    <div className="form-actions">{isNgo && step > 1 && <button type="button" className="back-button" onClick={() => { setStep(step - 1); setError(''); }}>Back</button>}<button className="continue-button" disabled={loading}>{loading ? 'Submitting...' : isNgo && step < 4 ? 'Continue' : isNgo ? 'Submit application' : 'Create account'} <span>→</span></button></div>
  </form></div></section></main>;
}
