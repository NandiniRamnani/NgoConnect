import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Users, MapPin, CheckCircle2, UtensilsCrossed, Phone, BadgeCheck, ChevronLeft, ChevronRight, HeartHandshake,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import Avatar from '../components/Avatar';
import MealIcon from '../components/MealIcon';
import {
  GROUPS, GROUP_LABEL, MEAL_LABEL, nextDays, dayParts, dayLabel, longDayLabel, formatRupees, sponsorSlot,
} from '../utils/foodSlots';
import './FoodSlots.css';

const DAYS_SHOWN = 14;
// Serving order reads naturally to a donor; the whole day comes last as the "big" option.
const MEAL_SECTIONS = ['BREAKFAST', 'LUNCH', 'DINNER', 'FULL_DAY'];

export default function FoodSlots() {
  const { user } = useAuth() || {};
  const navigate = useNavigate();
  const stripRef = useRef(null);

  const [slots, setSlots] = useState([]);
  const [ngosById, setNgosById] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [day, setDay] = useState(null);        // a "YYYY-MM-DD"; chosen once slots arrive
  const [group, setGroup] = useState('ALL');
  const [ngoFilter, setNgoFilter] = useState('ALL');
  const [openOnly, setOpenOnly] = useState(false);

  const days = useMemo(() => nextDays(DAYS_SHOWN), []);

  useEffect(() => {
    api.get('/food-slots', { params: { from: days[0], to: days[days.length - 1] } })
      .then(r => {
        setSlots(r.data);
        // Open on the first day that actually needs help, not on a day that is already covered.
        const firstOpen = days.find(d => r.data.some(s => s.date === d && s.status === 'OPEN'));
        setDay(firstOpen || days[0]);
      })
      .catch(() => { setError('Food slots could not be loaded right now.'); setDay(days[0]); })
      .finally(() => setLoading(false));
    // The slot only carries the NGO's name. Its logo, address, phone and verification come from
    // the public NGO list, so each card can say exactly who is asking and where they are.
    api.get('/ngos')
      .then(r => setNgosById(Object.fromEntries(r.data.map(n => [n.id, n]))))
      .catch(() => {});
  }, [days]);

  // NGOs that actually posted slots in this window, for the NGO filter.
  const postingNgos = useMemo(() => {
    const seen = new Map();
    slots.forEach(s => seen.set(s.ngoId, s.ngoName));
    return [...seen.entries()].sort((a, b) => (a[1] || '').localeCompare(b[1] || ''));
  }, [slots]);

  // Group and NGO filters apply to the calendar too, so its counts match what the donor is looking for.
  const matching = slots.filter(s =>
    (group === 'ALL' || s.beneficiaryGroup === group) &&
    (ngoFilter === 'ALL' || s.ngoId === ngoFilter));

  const statsByDay = useMemo(() => {
    const stats = {};
    matching.forEach(s => {
      const d = (stats[s.date] ||= { total: 0, open: 0, people: 0 });
      d.total += 1;
      if (s.status === 'OPEN') { d.open += 1; d.people += s.peopleCount || 0; }
    });
    return stats;
  }, [matching]);

  const daySlots = matching.filter(s => s.date === day && (!openOnly || s.status === 'OPEN'));
  const dayStats = statsByDay[day] || { total: 0, open: 0, people: 0 };
  const nextOpenDay = days.find(d => d !== day && statsByDay[d]?.open > 0);

  const totalOpen = slots.filter(s => s.status === 'OPEN').length;
  const peopleWaiting = slots.filter(s => s.status === 'OPEN').reduce((sum, s) => sum + (s.peopleCount || 0), 0);

  const scrollStrip = (dir) => stripRef.current?.scrollBy({ left: dir * 320, behavior: 'smooth' });

  return (
    <div className="food-page">
      <header className="food-header">
        <span className="eyebrow-label">Sponsor a meal</span>
        <h1>Food Slots</h1>
        <p>
          When no one is providing a meal for orphan children, elders or blind people, their NGO posts it here
          with what it costs. Pick a day, pay for one meal — or a whole day — and they eat.
        </p>
      </header>

      {!loading && !error && (
        <div className="food-stats">
          <div><strong>{totalOpen}</strong><span>meal{totalOpen === 1 ? '' : 's'} need a sponsor</span></div>
          <div><strong>{peopleWaiting}</strong><span>people waiting for food</span></div>
          <div><strong>{postingNgos.length}</strong><span>NGO{postingNgos.length === 1 ? '' : 's'} posting</span></div>
        </div>
      )}

      <ol className="food-how">
        <li><strong>Choose a day</strong> and a meal from a verified NGO</li>
        <li><strong>Pay the posted price</strong> by UPI, card, netbanking or wallet</li>
        <li><strong>The NGO serves it</strong> — you get an 80G receipt by email</li>
      </ol>

      {/* ── Calendar ─────────────────────────────────────────── */}
      <section className="food-calendar" aria-label="Choose a day">
        <div className="food-calendar-head">
          <h2>Choose a day</h2>
          <div className="food-calendar-nav">
            <button type="button" onClick={() => scrollStrip(-1)} aria-label="Earlier days"><ChevronLeft size={18} /></button>
            <button type="button" onClick={() => scrollStrip(1)} aria-label="Later days"><ChevronRight size={18} /></button>
          </div>
        </div>
        <div className="food-day-strip" ref={stripRef} role="tablist">
          {days.map(d => {
            const p = dayParts(d);
            const st = statsByDay[d];
            const state = !st ? 'empty' : st.open > 0 ? 'open' : 'covered';
            return (
              <button key={d} role="tab" aria-selected={day === d}
                className={`food-day ${state} ${day === d ? 'active' : ''}`} onClick={() => setDay(d)}>
                <span className="food-day-weekday">{p.isToday ? 'Today' : p.weekday}</span>
                <span className="food-day-num">{p.day}</span>
                <span className="food-day-month">{p.month}</span>
                <span className="food-day-status">
                  {state === 'open' && `${st.open} need${st.open === 1 ? 's' : ''} help`}
                  {state === 'covered' && 'All covered'}
                  {state === 'empty' && 'No meals'}
                </span>
              </button>
            );
          })}
        </div>
        <div className="food-legend">
          <span><i className="dot open" /> Needs a sponsor</span>
          <span><i className="dot covered" /> All meals covered</span>
          <span><i className="dot empty" /> Nothing posted</span>
        </div>
      </section>

      {/* ── Filters ──────────────────────────────────────────── */}
      <div className="food-filters">
        <div className="food-chips" role="group" aria-label="Who the meal is for">
          <button className={`filter-chip ${group === 'ALL' ? 'active' : ''}`} onClick={() => setGroup('ALL')}>Everyone</button>
          {GROUPS.map(g => (
            <button key={g} className={`filter-chip ${group === g ? 'active' : ''}`} onClick={() => setGroup(g)}>
              {GROUP_LABEL[g]}
            </button>
          ))}
        </div>
        <div className="food-filter-right">
          {postingNgos.length > 1 && (
            <select className="food-ngo-select" value={ngoFilter} onChange={e => setNgoFilter(e.target.value)} aria-label="NGO">
              <option value="ALL">All NGOs</option>
              {postingNgos.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
            </select>
          )}
          <label className="food-switch">
            <input type="checkbox" checked={openOnly} onChange={e => setOpenOnly(e.target.checked)} />
            <span className="food-switch-track" aria-hidden="true" />
            Only meals needing a sponsor
          </label>
        </div>
      </div>

      {user?.role === 'NGO' && (
        <p className="food-ngo-hint">
          You're signed in as an NGO. Post and manage your own slots from your <Link to="/dashboard#food-slots">dashboard</Link>.
        </p>
      )}

      {loading && <p className="food-empty">Loading food slots…</p>}
      {error && <p className="food-empty">{error}</p>}

      {!loading && !error && day && (
        <section className="food-day-panel">
          <div className="food-day-panel-head">
            <div>
              <span className="food-day-eyebrow">{dayLabel(day)}</span>
              <h2>{longDayLabel(day)}</h2>
              {dayStats.total > 0 && (
                <p>
                  {dayStats.open > 0
                    ? <><strong>{dayStats.open} of {dayStats.total}</strong> meals still need a sponsor · {dayStats.people} people waiting</>
                    : <>All {dayStats.total} meals on this day are covered. Thank you!</>}
                </p>
              )}
            </div>
            {dayStats.total > 0 && (
              <div className="food-progress" title={`${dayStats.total - dayStats.open} of ${dayStats.total} covered`}>
                <div style={{ width: `${((dayStats.total - dayStats.open) / dayStats.total) * 100}%` }} />
                <span>{dayStats.total - dayStats.open}/{dayStats.total} covered</span>
              </div>
            )}
          </div>

          {daySlots.length === 0 ? (
            <div className="food-empty">
              <UtensilsCrossed size={32} strokeWidth={1.5} />
              <p>
                {dayStats.total === 0
                  ? 'No NGO has posted meals for this day yet.'
                  : 'Every meal on this day already has a sponsor.'}
              </p>
              {nextOpenDay && (
                <button className="food-jump" onClick={() => setDay(nextOpenDay)}>
                  See {dayLabel(nextOpenDay)} — {statsByDay[nextOpenDay].open} meal{statsByDay[nextOpenDay].open === 1 ? '' : 's'} need help →
                </button>
              )}
            </div>
          ) : (
            MEAL_SECTIONS.map(meal => {
              const list = daySlots.filter(s => s.mealType === meal)
                .sort((a, b) => (a.status === 'OPEN' ? 0 : 1) - (b.status === 'OPEN' ? 0 : 1));
              if (list.length === 0) return null;
              return (
                <div key={meal} className="food-meal-section">
                  <h3 className={`food-meal-title meal-${meal.toLowerCase()}`}>
                    <span className="food-meal-icon"><MealIcon meal={meal} size={16} /></span>
                    {MEAL_LABEL[meal]}
                    <small>{list.filter(s => s.status === 'OPEN').length} open · {list.length} posted</small>
                  </h3>
                  <div className="food-grid">
                    {list.map(slot => (
                      <SlotCard key={slot.id} slot={slot} ngo={ngosById[slot.ngoId]} user={user}
                        onSponsor={() => sponsorSlot(navigate, user, slot)} />
                    ))}
                  </div>
                </div>
              );
            })
          )}
        </section>
      )}
    </div>
  );
}

function SlotCard({ slot, ngo, user, onSponsor }) {
  const open = slot.status === 'OPEN';
  const place = ngo?.address || ngo?.location || slot.ngoLocation;
  return (
    <article className={`food-card ${open ? 'open' : 'filled'}`}>
      {/* Who is asking, and where — so a donor knows exactly whose kitchen they are paying. */}
      <Link to={`/ngos/${slot.ngoId}`} className="food-card-ngo">
        <Avatar src={ngo?.logoUrl} name={slot.ngoName} size={36} />
        <span>
          <strong>
            {slot.ngoName}
            {ngo?.verificationStatus === 'APPROVED' && <BadgeCheck size={14} className="food-verified" aria-label="Verified NGO" />}
          </strong>
          {place && <small><MapPin size={12} /> {place}</small>}
        </span>
      </Link>

      <div className="food-card-body">
        <div className="food-card-who">
          <h4>{GROUP_LABEL[slot.beneficiaryGroup]}</h4>
          <span className="food-people"><Users size={13} /> {slot.peopleCount} people</span>
        </div>
        {slot.menu && <p className="food-menu"><span>Menu</span>{slot.menu}</p>}
        {slot.note && <p className="food-note">“{slot.note}”</p>}
        {ngo?.contactPhone && (
          <a className="food-phone" href={`tel:${ngo.contactPhone}`}><Phone size={12} /> {ngo.contactPhone}</a>
        )}
      </div>

      <div className="food-card-footer">
        {open ? (
          <>
            <div className="food-price">
              <strong>{formatRupees(slot.amount)}</strong>
              {slot.costPerPerson != null && (
                <span>{formatRupees(slot.costPerPerson)} × {slot.peopleCount} people</span>
              )}
            </div>
            {user?.role !== 'NGO' && (
              <button className="food-sponsor-btn" onClick={onSponsor}>
                <HeartHandshake size={16} />
                {slot.mealType === 'FULL_DAY' ? 'Sponsor the whole day' : 'Sponsor this meal'}
              </button>
            )}
          </>
        ) : (
          <p className="food-sponsored">
            <CheckCircle2 size={16} />
            {slot.sponsorName ? `Sponsored by ${slot.sponsorName}` : 'This meal is covered'}
          </p>
        )}
      </div>
    </article>
  );
}
