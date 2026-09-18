import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';
import './NGOs.css';
import Avatar from '../components/Avatar';

const CAUSES = ['All', 'Education', 'Healthcare', 'Environment', 'Food', 'Other'];

export default function NGOs() {
  const [ngos, setNgos] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeCause, setActiveCause] = useState('All');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    api.get('/ngos')
      .then(response => setNgos(response.data))
      .catch(() => setError('NGOs could not be loaded right now.'))
      .finally(() => setLoading(false));
  }, []);

  const filteredNgos = useMemo(() => ngos.filter(ngo => {
    const search = searchTerm.toLowerCase();
    const matchesSearch = ngo.ngoName.toLowerCase().includes(search) || ngo.description.toLowerCase().includes(search);
    const matchesCause = activeCause === 'All' || ngo.ngoType === activeCause;
    return matchesSearch && matchesCause;
  }), [ngos, searchTerm, activeCause]);

  return (
    <div className="ngos-page">
      <div className="ngos-header">
        <h1>Discover NGOs</h1>
        <p>Find and support verified organisations making a difference.</p>
        <div className="ngos-controls">
          <input type="text" placeholder="Search NGOs..." value={searchTerm} onChange={event => setSearchTerm(event.target.value)} className="ngos-search" />
          <div className="ngos-filters">
            {CAUSES.map(cause => <button key={cause} className={`filter-btn ${activeCause === cause ? 'active' : ''}`} onClick={() => setActiveCause(cause)}>{cause}</button>)}
          </div>
        </div>
      </div>

      {error && <p className="no-results">{error}</p>}
      {loading && <p className="no-results">Loading verified NGOs...</p>}
      {!loading && !error && filteredNgos.length === 0 && <p className="no-results">No verified NGOs are available yet.</p>}
      {!loading && !error && filteredNgos.length > 0 && <div className="ngos-grid">
        {filteredNgos.map(ngo => (
          <article key={ngo.id} className="ngo-card">
            <div className="ngo-card-header">
              <Avatar src={ngo.logoUrl} name={ngo.ngoName} size={56} className="ngo-emoji-avatar" />
              <div className="ngo-title-area"><h3>{ngo.ngoName} <span className="verified-badge">✓</span></h3><span className="cause-badge">{ngo.ngoType}</span></div>
            </div>
            <p className="ngo-desc">{ngo.description}</p>
            <div className="ngo-location">{ngo.location}</div>
            <div className="ngo-actions"><button className="btn-secondary" onClick={() => navigate('/ngos/' + ngo.id)}>View Details</button><button className="btn-primary">Donate</button></div>
          </article>
        ))}
      </div>}
    </div>
  );
}