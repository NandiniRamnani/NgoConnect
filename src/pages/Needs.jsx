import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Package, Users, MapPin, Clock, Building2, AlertTriangle, UtensilsCrossed } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import { toIsoDay, addDays } from '../utils/foodSlots';
import './Needs.css';

// URGENT is a view filter, not a backend type: it shows urgent posts of either type.
const TYPES = ['All', 'URGENT', 'NEED', 'VOLUNTEERING'];
const TYPE_LABEL = { URGENT: 'Urgent', NEED: 'Goods / Materials', VOLUNTEERING: 'Volunteers' };
const TYPE_ICON = { NEED: Package, VOLUNTEERING: Users };

export default function Needs() {
  const [activeType, setActiveType] = useState('All');
  const [needs, setNeeds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openMeals, setOpenMeals] = useState(0);
  const authContext = useAuth();
  const user = authContext?.user;
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true); setError('');
    const params = activeType === 'NEED' || activeType === 'VOLUNTEERING' ? { type: activeType } : undefined;
    api.get('/ngos/notifications', { params })
      .then(response => setNeeds(activeType === 'URGENT' ? response.data.filter(n => n.urgent) : response.data))
      .catch(() => setError('Needs could not be loaded right now.'))
      .finally(() => setLoading(false));
  }, [activeType]);

  // Food slots live on their own page; this just points people there when a meal is at risk soon.
  useEffect(() => {
    const today = new Date();
    api.get('/food-slots', { params: { from: toIsoDay(today), to: toIsoDay(addDays(today, 1)), status: 'OPEN' } })
      .then(r => setOpenMeals(r.data.length))
      .catch(() => setOpenMeals(0));
  }, []);

  const handleRespond = (need) => {
    if (need.type === 'NEED') {
      if (!user) { navigate('/login'); return; }
      navigate('/donate', { state: { ngoId: need.ngoId, ngoName: need.ngoName } });
      return;
    }
    navigate(`/ngos/${need.ngoId}`);
  };

  return (
    <div className="needs-page">
      <div className="needs-header">
        <h1>Needs & Requests</h1>
        <p>See what NGOs need right now and help out.</p>

        <div className="needs-filters">
          {TYPES.map(type => (
            <button
              key={type}
              className={`filter-chip ${activeType === type ? 'active' : ''} ${type === 'URGENT' ? 'chip-urgent' : ''}`}
              onClick={() => setActiveType(type)}
            >
              {type === 'All' ? 'All' : TYPE_LABEL[type]}
            </button>
          ))}
        </div>
      </div>

      {openMeals > 0 && (
        <Link to="/food-slots" className="food-banner">
          <UtensilsCrossed size={20} />
          <span>
            <strong>{openMeals} meal{openMeals === 1 ? '' : 's'}</strong> for children, elders or blind people have no sponsor for today or tomorrow.
          </span>
          <span className="food-banner-cta">Sponsor a meal →</span>
        </Link>
      )}

      {loading && <p className="no-results">Loading needs...</p>}
      {error && <p className="no-results">{error}</p>}
      {!loading && !error && needs.length === 0 && (
        <p className="no-results">{activeType === 'URGENT' ? 'No urgent needs right now.' : 'No open needs right now.'}</p>
      )}

      <div className="masonry-grid">
        {!loading && !error && needs.map(need => {
          const TypeIcon = TYPE_ICON[need.type] || Package;
          return (
          <div key={need.id} className={`need-card ${need.urgent ? 'urgent' : ''}`}>
            <div className="need-badges">
              <span className="type-badge" data-type={need.type}>{TYPE_LABEL[need.type] || need.type}</span>
              {need.urgent && <span className="urgent-badge"><AlertTriangle size={12} /> Urgent</span>}
              {need.type === 'VOLUNTEERING' && need.requiredVolunteers && (
                <span className="urgency-badge">{need.requiredVolunteers} needed</span>
              )}
            </div>

            <div className="need-content-header">
              <div className="need-emoji"><TypeIcon size={20} strokeWidth={1.8} /></div>
              <h3>{need.title}</h3>
            </div>

            <p className="need-desc">{need.description}</p>
            {need.resourceDetails && <p className="need-desc"><strong>Details:</strong> {need.resourceDetails}</p>}
            {need.type === 'NEED' && need.deliveryAddress && (
              <p className="need-desc"><strong><Package size={13} className="inline-icon" /> Send items to:</strong> {need.deliveryAddress}</p>
            )}
            {need.location && <p className="need-desc"><MapPin size={13} className="inline-icon" /> {need.location}</p>}
            {need.deadline && (
              <p className="need-desc need-deadline"><Clock size={13} className="inline-icon" /> Needed by {new Date(need.deadline).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}</p>
            )}

            <div className="need-footer">
              <div className="need-meta">
                <span className="need-ngo"><Building2 size={13} /> {need.ngoName || 'NGO'}</span>
                {need.createdAt && <span className="need-date"><Clock size={13} /> {new Date(need.createdAt).toLocaleDateString()}</span>}
              </div>
              <button className="btn-respond" onClick={() => handleRespond(need)}>
                {need.type === 'NEED' ? 'Donate' : 'Respond'}
              </button>
            </div>
          </div>
          );
        })}
      </div>
    </div>
  );
}
