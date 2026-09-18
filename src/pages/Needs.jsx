import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import './Needs.css';

const TYPES = ['All', 'NEED', 'VOLUNTEERING'];
const TYPE_LABEL = { NEED: 'Goods / Materials', VOLUNTEERING: 'Volunteers' };
const TYPE_EMOJI = { NEED: '📦', VOLUNTEERING: '🙋' };

export default function Needs() {
  const [activeType, setActiveType] = useState('All');
  const [needs, setNeeds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const authContext = useAuth();
  const user = authContext?.user;
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true); setError('');
    const params = activeType !== 'All' ? { type: activeType } : undefined;
    api.get('/ngos/notifications', { params })
      .then(response => setNeeds(response.data))
      .catch(() => setError('Needs could not be loaded right now.'))
      .finally(() => setLoading(false));
  }, [activeType]);

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
              className={`filter-chip ${activeType === type ? 'active' : ''}`}
              onClick={() => setActiveType(type)}
            >
              {type === 'All' ? 'All' : TYPE_LABEL[type]}
            </button>
          ))}
        </div>
      </div>

      {loading && <p className="no-results">Loading needs...</p>}
      {error && <p className="no-results">{error}</p>}
      {!loading && !error && needs.length === 0 && <p className="no-results">No open needs right now.</p>}

      <div className="masonry-grid">
        {!loading && !error && needs.map(need => (
          <div key={need.id} className="need-card">
            <div className="need-badges">
              <span className="type-badge" data-type={need.type}>{TYPE_LABEL[need.type] || need.type}</span>
              {need.type === 'VOLUNTEERING' && need.requiredVolunteers && (
                <span className="urgency-badge">{need.requiredVolunteers} needed</span>
              )}
            </div>

            <div className="need-content-header">
              <div className="need-emoji">{TYPE_EMOJI[need.type] || '📌'}</div>
              <h3>{need.title}</h3>
            </div>

            <p className="need-desc">{need.description}</p>
            {need.resourceDetails && <p className="need-desc"><strong>Details:</strong> {need.resourceDetails}</p>}
            {need.type === 'NEED' && need.deliveryAddress && (
              <p className="need-desc"><strong>📦 Send items to:</strong> {need.deliveryAddress}</p>
            )}
            {need.location && <p className="need-desc">📍 {need.location}</p>}

            <div className="need-footer">
              <div className="need-meta">
                <span className="need-ngo">🏢 {need.ngoName || 'NGO'}</span>
                {need.createdAt && <span className="need-date">⏱️ {new Date(need.createdAt).toLocaleDateString()}</span>}
              </div>
              <button className="btn-respond" onClick={() => handleRespond(need)}>
                {need.type === 'NEED' ? 'Donate' : 'Respond'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
