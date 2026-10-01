import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import './NGODetail.css';
import Avatar from '../components/Avatar';
import MediaGallery from '../components/MediaGallery';
import MealIcon from '../components/MealIcon';
import { useAuth } from '../context/AuthContext';
import {
  MEAL_TYPES, MEAL_LABEL, GROUP_LABEL, toIsoDay, addDays, dayLabel, longDayLabel, formatRupees, costPerPerson, sponsorSlot,
} from '../utils/foodSlots';

export default function NGODetail() {
  const { id } = useParams();
  const [ngo, setNgo] = useState(null);
  const [media, setMedia] = useState([]);
  const [events, setEvents] = useState([]);
  const [foodSlots, setFoodSlots] = useState([]);
  const [tab, setTab] = useState('media');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { user } = useAuth() || {};
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    const today = new Date();
    Promise.all([
      api.get(`/ngos/${id}`),
      api.get(`/ngos/${id}/media`),
      api.get(`/ngos/${id}/events`).catch(() => ({ data: [] })),
      api.get('/food-slots', { params: { ngoId: id, from: toIsoDay(today), to: toIsoDay(addDays(today, 13)) } })
        .catch(() => ({ data: [] })),
    ]).then(([ngoRes, mediaRes, eventsRes, slotsRes]) => {
      setNgo(ngoRes.data);
      setMedia(mediaRes.data);
      setEvents(eventsRes.data);
      setFoodSlots(slotsRes.data);
    }).catch(() => setError('NGO not found.'))
    .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="ngo-detail"><p className="ngo-loading">Loading...</p></div>;
  if (error || !ngo) return <div className="ngo-detail"><p className="ngo-error">{error || 'NGO not found.'}</p><Link to="/ngos" className="back-link">&larr; Back to NGOs</Link></div>;


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
        <button className={tab === 'food' ? 'active' : ''} onClick={() => setTab('food')}>
          Food Slots ({foodSlots.filter(s => s.status === 'OPEN').length} open)
        </button>
      </div>

      {tab === 'food' && (
        <div>
          {/* The NGO's own numbers, so a donor can see how each slot's price is made up. */}
          {(Object.keys(ngo.beneficiaryCounts || {}).length > 0 || MEAL_TYPES.some(m => ngo.mealCostPerPerson?.[m] != null)) && (
            <div className="food-costs-box">
              {Object.keys(ngo.beneficiaryCounts || {}).length > 0 && (
                <p>
                  <strong>We feed:</strong>{' '}
                  {Object.entries(ngo.beneficiaryCounts).map(([g, n]) => `${n} ${GROUP_LABEL[g]?.toLowerCase() || g}`).join(' · ')}
                </p>
              )}
              {MEAL_TYPES.some(m => ngo.mealCostPerPerson?.[m] != null) && (
                <p>
                  <strong>Cost per person:</strong>{' '}
                  {MEAL_TYPES.filter(m => ngo.mealCostPerPerson?.[m] != null)
                    .map(m => `${MEAL_LABEL[m]} ${formatRupees(ngo.mealCostPerPerson[m])}`).join(' · ')}
                  {costPerPerson(ngo, 'FULL_DAY') != null && <> · <strong>Whole day {formatRupees(costPerPerson(ngo, 'FULL_DAY'))}</strong></>}
                </p>
              )}
            </div>
          )}
          {foodSlots.length === 0 && <p className="ngo-empty">No food slots posted for the next two weeks.</p>}
          {[...new Set(foodSlots.map(s => s.date))].map(date => (
          <div key={date} className="slot-day-group">
          <h4 className="slot-day-heading">
            <span>{dayLabel(date)}</span> {longDayLabel(date)}
            <small>{foodSlots.filter(s => s.date === date && s.status === 'OPEN').length} need a sponsor</small>
          </h4>
          <div className="slot-list">
            {foodSlots.filter(s => s.date === date).map(s => (
              <div key={s.id} className={`slot-row ${s.status === 'OPEN' ? 'open' : ''}`}>
                <div>
                  <strong className="slot-row-meal"><MealIcon meal={s.mealType} size={15} /> {MEAL_LABEL[s.mealType]}</strong>
                  <p>
                    {s.peopleCount} {GROUP_LABEL[s.beneficiaryGroup].toLowerCase()}
                    {s.costPerPerson != null ? ` · ${formatRupees(s.costPerPerson)} per person` : ''}
                    {s.menu ? ` · ${s.menu}` : ''}
                  </p>
                </div>
                {s.status === 'OPEN' ? (
                  user?.role === 'NGO'
                    ? <span className="slot-cost">{formatRupees(s.amount)}</span>
                    : <button className="slot-sponsor" onClick={() => sponsorSlot(navigate, user, s)}>
                        Sponsor · {formatRupees(s.amount)}
                      </button>
                ) : (
                  <span className="slot-covered">✓ {s.sponsorName ? `Sponsored by ${s.sponsorName}` : 'Covered'}</span>
                )}
              </div>
            ))}
          </div>
          </div>
          ))}
        </div>
      )}

      {tab === 'media' && (
        <MediaGallery items={media} emptyText={`${ngo.ngoName} hasn't posted any photos or videos yet.`} />
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
