import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api/axios';
import './NGODetail.css';
import Avatar from '../components/Avatar';

export default function NGODetail() {
  const { id } = useParams();
  const [ngo, setNgo] = useState(null);
  const [media, setMedia] = useState([]);
  const [events, setEvents] = useState([]);
  const [tab, setTab] = useState('media');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get(`/ngos/${id}`),
      api.get(`/ngos/${id}/media`),
      api.get(`/ngos/${id}/events`).catch(() => ({ data: [] }))
    ]).then(([ngoRes, mediaRes, eventsRes]) => {
      setNgo(ngoRes.data);
      setMedia(mediaRes.data);
      setEvents(eventsRes.data);
    }).catch(() => setError('NGO not found.'))
    .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="ngo-detail"><p className="ngo-loading">Loading...</p></div>;
  if (error || !ngo) return <div className="ngo-detail"><p className="ngo-error">{error || 'NGO not found.'}</p><Link to="/ngos" className="back-link">&larr; Back to NGOs</Link></div>;

  const images = media.filter(m => m.mediaType === 'IMAGE');
  const videos = media.filter(m => m.mediaType === 'VIDEO');

  return (
    <div className="ngo-detail">
      <Link to="/ngos" className="back-link">&larr; All NGOs</Link>
      <div className="ngo-detail-hero">
        <Avatar src={ngo.logoUrl} name={ngo.ngoName} size={96} />
        <div>
          <h1>{ngo.ngoName} <span className="verified-tag">&#10003; Verified</span></h1>
          <div className="ngo-meta-row">
            <span className="ngo-type-badge">{ngo.ngoType}</span>
            <span>&#128205; {ngo.location}</span>
            {ngo.websiteUrl && <a href={ngo.websiteUrl.startsWith('http') ? ngo.websiteUrl : 'https://' + ngo.websiteUrl} target="_blank" rel="noreferrer">&#127760; Website</a>}
            {ngo.instagramUrl && <a href={ngo.instagramUrl.startsWith('http') ? ngo.instagramUrl : 'https://' + ngo.instagramUrl} target="_blank" rel="noreferrer">&#128248; Instagram</a>}
          </div>
        </div>
      </div>

      {ngo.description && <p className="ngo-description">{ngo.description}</p>}

      <div className="ngo-contact-row">
        <span>&#128231; {ngo.email}</span>
        {ngo.contactPhone && <span>&#128222; {ngo.contactPhone}</span>}
      </div>
      {ngo.address && <p className="ngo-description"><strong>Address:</strong> {ngo.address}</p>}

      <div className="detail-tabs">
        <button className={tab === 'media' ? 'active' : ''} onClick={() => setTab('media')}>Photos &amp; Videos ({media.length})</button>
        <button className={tab === 'events' ? 'active' : ''} onClick={() => setTab('events')}>Events ({events.length})</button>
      </div>

      {tab === 'media' && (
        <div>
          {media.length === 0 && <p className="ngo-empty">No media posted yet.</p>}
          {images.length > 0 && (
            <div className="media-grid">
              {images.map(m => (
                <div key={m.id} className="media-card">
                  <img src={m.mediaUrl} alt={m.caption || 'NGO photo'} loading="lazy" />
                  {m.caption && <p className="media-caption">{m.caption}</p>}
                </div>
              ))}
            </div>
          )}
          {videos.length > 0 && (
            <div className="video-list">
              {videos.map(m => (
                <div key={m.id} className="video-card">
                  <span className="video-icon">&#127916;</span>
                  <div>
                    <p>{m.caption || 'Video'}</p>
                    <a href={m.mediaUrl} target="_blank" rel="noreferrer">Watch Video &#8599;</a>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'events' && (
        <div>
          {events.length === 0 && <p className="ngo-empty">No events scheduled.</p>}
          <div className="events-list">
            {events.map(ev => (
              <div key={ev.id} className="event-card">
                <h3>{ev.title}</h3>
                <div className="event-meta">
                  <span>&#128197; {new Date(ev.eventDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</span>
                  <span>&#128205; {ev.location}</span>
                </div>
                {ev.description && <p className="event-desc">{ev.description}</p>}
                {ev.maxSpots > 0 && (
                  <div className="event-progress">
                    <div className="event-bar"><div style={{ width: `${Math.min(100, ((ev.enrolledCount || 0) / ev.maxSpots) * 100)}%` }} /></div>
                    <span>{ev.enrolledCount || 0} / {ev.maxSpots} enrolled</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
