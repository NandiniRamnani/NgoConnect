import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronLeft, ChevronRight, Plus, AlertTriangle, CheckCircle2, HeartHandshake, Users, Trash2, RotateCcw, X,
} from 'lucide-react';
import api from '../api/axios';
import MealIcon from './MealIcon';
import {
  MEAL_TYPES, MEAL_LABEL, GROUPS, GROUP_LABEL, GROUP_SHORT, toIsoDay, addDays, nextDays, dayParts,
  dayLabel, longDayLabel, formatRupees, slotOrder, costPerPerson, slotClash,
} from '../utils/foodSlots';
import './NgoFoodSlots.css';

const MAX_DAYS_AHEAD = 60;          // matches FoodSlotService.MAX_DAYS_AHEAD
const WEEKS = Math.ceil((MAX_DAYS_AHEAD + 1) / 7);
const PICKER_DAYS = 14;

const sortSlots = (list) => [...list].sort((a, b) =>
  a.date.localeCompare(b.date) || slotOrder(a.mealType) - slotOrder(b.mealType));

/** How a slot reads to the NGO: still open, covered by someone they arranged, or paid by a donor here. */
const slotState = (s) => (s.status === 'OPEN' ? 'open' : s.filledVia === 'DONATION' ? 'paid' : 'covered');
const STATE_LABEL = { open: 'Needs sponsor', covered: 'Covered', paid: 'Paid by donor' };

/**
 * The NGO's food slot manager: pricing, a week planner (days × meals) and the forms behind it.
 *
 * `draft` is owned by the dashboard so its "Post Food Slot" button can open the form too:
 * null means closed, an object ({ dates?, meals? }) opens it with those pre-selected.
 */
export default function NgoFoodSlots({ user, authHeader, slots, setSlots, ngoProfile, setNgoProfile, draft, setDraft }) {
  const [week, setWeek] = useState(0);
  const [detail, setDetail] = useState(null);

  const today = toIsoDay(new Date());
  const lastDay = toIsoDay(addDays(new Date(), MAX_DAYS_AHEAD));
  const weekDays = nextDays(7, week * 7);
  const weekSlots = slots.filter(s => s.date >= weekDays[0] && s.date <= weekDays[6]);

  const weekStats = {
    posted: weekSlots.length,
    open: weekSlots.filter(s => slotState(s) === 'open').length,
    covered: weekSlots.filter(s => slotState(s) === 'covered').length,
    paid: weekSlots.filter(s => slotState(s) === 'paid').length,
    raised: weekSlots.filter(s => slotState(s) === 'paid').reduce((sum, s) => sum + Number(s.amount || 0), 0),
  };

  const first = dayParts(weekDays[0]);
  const last = dayParts(weekDays[6]);
  const weekTitle = week === 0 ? 'This week' : week === 1 ? 'Next week' : `In ${week} weeks`;

  const replaceSlot = (updated) => setSlots(prev => prev.map(s => (s.id === updated.id ? updated : s)));
  const removeSlot = (id) => setSlots(prev => prev.filter(s => s.id !== id));

  return (
    <div className="section-card" id="food-slots">
      <div className="section-card-head">
        <h2>Food Slots</h2>
        <Link to="/food-slots" className="section-link">View public page →</Link>
      </div>
      <p className="section-hint">
        Plan your meals day by day. Tap <strong>+</strong> in any empty meal to ask donors to sponsor it, or post it as
        <strong> covered</strong> when someone already provides it — donors then see which days are safe.
      </p>

      <PricingForm user={user} authHeader={authHeader} ngoProfile={ngoProfile} setNgoProfile={setNgoProfile} />

      {/* ── Week planner ─────────────────────────────────── */}
      <div className="planner-toolbar">
        <div className="planner-week-nav">
          <button type="button" onClick={() => setWeek(w => w - 1)} disabled={week === 0} aria-label="Previous week"><ChevronLeft size={18} /></button>
          <div>
            <strong>{weekTitle}</strong>
            <span>{first.day} {first.month} – {last.day} {last.month}</span>
          </div>
          <button type="button" onClick={() => setWeek(w => w + 1)} disabled={week >= WEEKS - 1} aria-label="Next week"><ChevronRight size={18} /></button>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => setDraft({ dates: weekDays.filter(d => d > today).slice(0, 1) })}>
          <Plus size={15} /> Post meals
        </button>
      </div>

      <div className="planner-summary">
        <span><strong>{weekStats.posted}</strong> posted</span>
        <span className="open"><strong>{weekStats.open}</strong> need sponsor</span>
        <span className="covered"><strong>{weekStats.covered}</strong> covered by you</span>
        <span className="paid"><strong>{weekStats.paid}</strong> paid by donors{weekStats.raised > 0 && <> · {formatRupees(weekStats.raised)}</>}</span>
      </div>

      <div className="planner" role="table" aria-label={`Food slots, ${weekTitle.toLowerCase()}`}>
        <div className="planner-head" role="row">
          <span role="columnheader">Day</span>
          {MEAL_TYPES.map(m => (
            <span key={m} role="columnheader" className={`meal-${m.toLowerCase()}`}><MealIcon meal={m} size={14} /> {MEAL_LABEL[m]}</span>
          ))}
        </div>

        {weekDays.map(d => {
          const p = dayParts(d);
          const daySlots = slots.filter(s => s.date === d);
          const fullDay = daySlots.filter(s => s.mealType === 'FULL_DAY');
          const beyond = d > lastDay;
          return (
            <div key={d} role="row" className={`planner-row ${p.isToday ? 'today' : ''} ${beyond ? 'beyond' : ''}`}>
              <div className="planner-day" role="rowheader" style={fullDay.length ? { gridRow: 'span 2' } : undefined}>
                <span className="planner-day-weekday">{p.isToday ? 'Today' : p.weekday}</span>
                <span className="planner-day-date">{p.day} {p.month}</span>
                {daySlots.some(s => s.status === 'OPEN') && (
                  <span className="planner-day-open">{daySlots.filter(s => s.status === 'OPEN').length} open</span>
                )}
              </div>

              {fullDay.length > 0 && (
                <div className="planner-fullday">
                  <span className="planner-fullday-label"><MealIcon meal="FULL_DAY" size={13} /> Whole day</span>
                  {fullDay.map(s => <SlotChip key={s.id} slot={s} onClick={() => setDetail(s)} />)}
                </div>
              )}

              {MEAL_TYPES.map(m => (
                <div key={m} role="cell" className="planner-cell">
                  <span className="planner-cell-label"><MealIcon meal={m} size={12} /> {MEAL_LABEL[m]}</span>
                  {daySlots.filter(s => s.mealType === m).map(s => <SlotChip key={s.id} slot={s} onClick={() => setDetail(s)} />)}
                  {!beyond && (
                    <button type="button" className="planner-add" onClick={() => setDraft({ dates: [d], meals: [m] })}
                      title={`Post ${MEAL_LABEL[m].toLowerCase()} for ${dayLabel(d)}`}>
                      <Plus size={14} /> <span>Add</span>
                    </button>
                  )}
                </div>
              ))}
            </div>
          );
        })}
      </div>

      <div className="planner-legend">
        <span><i className="chip-dot open" /> Needs sponsor — donors can pay for it</span>
        <span><i className="chip-dot covered" /> Covered by someone you arranged</span>
        <span><i className="chip-dot paid" /> Paid by a donor on NGO Connect</span>
      </div>

      {draft && (
        <SlotPostModal draft={draft} onClose={() => setDraft(null)} user={user} authHeader={authHeader}
          slots={slots} setSlots={setSlots} ngoProfile={ngoProfile} />
      )}
      {detail && (
        <SlotDetailModal slot={detail} onClose={() => setDetail(null)} user={user} authHeader={authHeader}
          onUpdated={(s) => { replaceSlot(s); setDetail(s); }} onDeleted={(id) => { removeSlot(id); setDetail(null); }} />
      )}
    </div>
  );
}

function SlotChip({ slot, onClick }) {
  const state = slotState(slot);
  return (
    <button type="button" className={`slot-chip ${state}`} onClick={onClick}
      title={`${GROUP_LABEL[slot.beneficiaryGroup]} · ${STATE_LABEL[state]}`}>
      <span className="slot-chip-top">
        <strong>{GROUP_SHORT[slot.beneficiaryGroup]}</strong>
        <span><Users size={11} /> {slot.peopleCount}</span>
      </span>
      <span className="slot-chip-bottom">
        <span className="slot-chip-state">{STATE_LABEL[state]}</span>
        {slot.amount != null && <span>{formatRupees(slot.amount)}</span>}
      </span>
    </button>
  );
}

// ── Pricing ────────────────────────────────────────────────────────────────────

function PricingForm({ user, authHeader, ngoProfile, setNgoProfile }) {
  const [form, setForm] = useState(() => ({
    costs: { ...(ngoProfile?.mealCostPerPerson || {}) },
    counts: { ...(ngoProfile?.beneficiaryCounts || {}) },
  }));
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState({ type: '', text: '' });
  // The profile arrives after the first render; seed the form from it once it does.
  const [seededFrom, setSeededFrom] = useState(ngoProfile);
  if (ngoProfile !== seededFrom) {
    setSeededFrom(ngoProfile);
    setForm({ costs: { ...(ngoProfile?.mealCostPerPerson || {}) }, counts: { ...(ngoProfile?.beneficiaryCounts || {}) } });
  }

  const pricingSet = MEAL_TYPES.some(m => ngoProfile?.mealCostPerPerson?.[m] != null);
  const fullDayRate = costPerPerson(ngoProfile, 'FULL_DAY');
  const summary = MEAL_TYPES.filter(m => ngoProfile?.mealCostPerPerson?.[m] != null)
    .map(m => `${MEAL_LABEL[m]} ${formatRupees(ngoProfile.mealCostPerPerson[m])}`).join(' · ');

  const handleSave = async (e) => {
    e.preventDefault();
    setMsg({ type: '', text: '' });
    // Blank fields are left out, which the backend treats as "not set".
    const clean = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v != null && Number(v) > 0).map(([k, v]) => [k, Number(v)]));
    setSaving(true);
    try {
      const res = await api.put(`/ngos/${user.id}/food-settings`, {
        mealCostPerPerson: clean(form.costs),
        beneficiaryCounts: clean(form.counts),
      }, { headers: authHeader() });
      setNgoProfile(res.data);
      setMsg({ type: 'success', text: 'Saved. New food slots will be priced from these numbers.' });
    } catch (err) {
      setMsg({ type: 'error', text: err.response?.data?.message || 'Could not save your food pricing.' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <details className="food-pricing" open={!pricingSet || undefined}>
      <summary>
        <span className="food-pricing-title">Food pricing</span>
        <span className={`food-pricing-summary ${pricingSet ? '' : 'warn'}`}>
          {pricingSet ? `${summary} per person` : <><AlertTriangle size={13} /> Not set — set it once and every slot is priced for you</>}
        </span>
      </summary>
      <form onSubmit={handleSave}>
        <p className="section-hint">
          What does one person's meal cost you, and how many people do you feed? Each slot's price is worked out
          from this — e.g. ₹60 lunch × 40 children = ₹2,400. A whole day is breakfast + lunch + dinner.
        </p>
        <div className="food-pricing-grid">
          <fieldset>
            <legend>Cost per person (₹)</legend>
            {MEAL_TYPES.map(m => (
              <label key={m}>
                <span><MealIcon meal={m} size={13} /> {MEAL_LABEL[m]}</span>
                <input className="form-input" type="number" min="1" step="0.01" placeholder="e.g. 60"
                  value={form.costs[m] ?? ''}
                  onChange={e => setForm(p => ({ ...p, costs: { ...p.costs, [m]: e.target.value } }))} />
              </label>
            ))}
            {fullDayRate != null && <small className="form-hint">Whole day: {formatRupees(fullDayRate)} per person</small>}
          </fieldset>
          <fieldset>
            <legend>People you look after</legend>
            {GROUPS.map(g => (
              <label key={g}>
                <span>{GROUP_LABEL[g]}</span>
                <input className="form-input" type="number" min="0" max="5000" placeholder="0"
                  value={form.counts[g] ?? ''}
                  onChange={e => setForm(p => ({ ...p, counts: { ...p.counts, [g]: e.target.value } }))} />
              </label>
            ))}
          </fieldset>
        </div>
        {msg.text && <div className={`alert alert-${msg.type}`}>{msg.text}</div>}
        <button type="submit" className="btn btn-primary btn-sm" disabled={saving}>
          {saving ? 'Saving…' : 'Save food pricing'}
        </button>
      </form>
    </details>
  );
}

// ── Post meals ─────────────────────────────────────────────────────────────────

/**
 * Post one or many slots at once: every chosen day × every chosen meal, for one group. A combination
 * the NGO already posted is skipped up front instead of failing on the server's duplicate check.
 */
function SlotPostModal({ draft, onClose, user, authHeader, slots, setSlots, ngoProfile }) {
  const tomorrow = toIsoDay(addDays(new Date(), 1));
  const today = toIsoDay(new Date());
  const lastDay = toIsoDay(addDays(new Date(), MAX_DAYS_AHEAD));
  const pickerDays = nextDays(PICKER_DAYS);
  const savedCount = (g) => ngoProfile?.beneficiaryCounts?.[g] ?? '';

  const [dates, setDates] = useState(() => (draft.dates?.length ? draft.dates : [tomorrow]));
  const [meals, setMeals] = useState(() => (draft.meals?.length ? draft.meals : ['LUNCH']));
  const [group, setGroup] = useState('ORPHAN_CHILDREN');
  const [people, setPeople] = useState(() => savedCount('ORPHAN_CHILDREN'));
  const [status, setStatus] = useState('OPEN');
  const [sponsorName, setSponsorName] = useState('');
  const [manualRates, setManualRates] = useState({});
  const [menu, setMenu] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [failures, setFailures] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const open = status === 'OPEN';
  const extraDates = dates.filter(d => !pickerDays.includes(d)).sort();

  const toggleDate = (d) => setDates(prev => (prev.includes(d) ? prev.filter(x => x !== d) : [...prev, d]));
  const addOtherDate = (d) => { if (d && d >= today && d <= lastDay && !dates.includes(d)) setDates(prev => [...prev, d]); };

  // A whole day already includes the three meals, so it cannot be combined with them.
  const toggleMeal = (m) => setMeals(prev => {
    if (m === 'FULL_DAY') return prev.includes('FULL_DAY') ? [] : ['FULL_DAY'];
    const singles = prev.filter(x => x !== 'FULL_DAY');
    return singles.includes(m) ? singles.filter(x => x !== m) : [...singles, m];
  });

  const savedRate = (m) => costPerPerson(ngoProfile, m);
  const rateFor = (m) => savedRate(m) ?? (Number(manualRates[m]) > 0 ? Number(manualRates[m]) : null);
  const count = Number(people) || 0;

  const plan = [...dates].sort().flatMap(d => [...meals].sort((a, b) => slotOrder(a) - slotOrder(b)).map(m => {
    const clash = slotClash(slots, d, m, group);
    const rate = rateFor(m);
    return { date: d, meal: m, clash, amount: rate != null && count > 0 ? rate * count : null };
  }));
  const toPost = plan.filter(p => !p.clash);
  const skipped = plan.length - toPost.length;
  const total = toPost.reduce((sum, p) => sum + (p.amount || 0), 0);
  const missingRates = meals.filter(m => rateFor(m) == null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(''); setFailures([]);
    if (dates.length === 0) return setError('Pick at least one day.');
    if (meals.length === 0) return setError('Pick at least one meal.');
    if (count < 1) return setError('How many people will eat? Enter the number of people.');
    if (open && missingRates.length) return setError(`Enter the cost per person for ${missingRates.map(m => MEAL_LABEL[m].toLowerCase()).join(', ')}.`);
    if (toPost.length === 0) return setError('Everything you picked is already posted. Choose other days or meals.');

    setSubmitting(true);
    const created = [];
    const failed = [];
    // One at a time, so a clash on one day does not stop the rest, and each failure has its own reason.
    for (const p of toPost) {
      try {
        const res = await api.post(`/ngos/${user.id}/food-slots`, {
          date: p.date,
          mealType: p.meal,
          beneficiaryGroup: group,
          peopleCount: count,
          // Ignored by the backend when per-person costs are saved; it prices the slot itself.
          amount: savedRate(p.meal) == null && p.amount != null ? p.amount : null,
          menu, note, status,
          sponsorName: open ? null : sponsorName,
        }, { headers: authHeader() });
        created.push(res.data);
      } catch (err) {
        failed.push({ ...p, reason: err.response?.data?.message || 'Could not post this slot.' });
      }
    }
    if (created.length) setSlots(prev => sortSlots([...prev, ...created]));
    setSubmitting(false);
    if (failed.length === 0) onClose();
    else setFailures(failed);
  };

  return (
    <div className="modal-overlay animate-fade-in" onClick={onClose}>
      <div className="modal-content modal-wide slot-post" onClick={e => e.stopPropagation()}>
        <button className="close-btn" onClick={onClose} aria-label="Close">×</button>
        <h2>Post meals</h2>
        <p className="slot-post-sub">Choose the days and meals, who they are for, and whether you still need a sponsor.</p>

        <form onSubmit={handleSubmit} className="slot-post-form">
          {/* 1 · Days */}
          <section className="slot-step">
            <div className="slot-step-head">
              <span className="slot-step-num">1</span>
              <h3>Which days?</h3>
              <span className="slot-step-hint">{dates.length} selected</span>
              <div className="slot-step-actions">
                <button type="button" onClick={() => setDates(nextDays(7, 1))}>Next 7 days</button>
                <button type="button" onClick={() => setDates([])}>Clear</button>
              </div>
            </div>
            <div className="slot-day-grid">
              {pickerDays.map(d => {
                const p = dayParts(d);
                const taken = slots.filter(s => s.date === d).length;
                return (
                  <button type="button" key={d} className={`slot-day ${dates.includes(d) ? 'selected' : ''}`}
                    onClick={() => toggleDate(d)} aria-pressed={dates.includes(d)}>
                    <span className="slot-day-weekday">{p.isToday ? 'Today' : p.weekday}</span>
                    <span className="slot-day-num">{p.day}</span>
                    <span className="slot-day-month">{p.month}</span>
                    {taken > 0 && <span className="slot-day-taken" title={`${taken} slot(s) already posted`}>{taken}</span>}
                  </button>
                );
              })}
            </div>
            <div className="slot-other-date">
              <label>
                <span>Another date</span>
                <input className="form-input" type="date" min={today} max={lastDay} value=""
                  onChange={e => addOtherDate(e.target.value)} />
              </label>
              {extraDates.map(d => (
                <span key={d} className="slot-date-tag">
                  {dayLabel(d)}
                  <button type="button" onClick={() => toggleDate(d)} aria-label={`Remove ${dayLabel(d)}`}><X size={12} /></button>
                </span>
              ))}
            </div>
          </section>

          {/* 2 · Meals */}
          <section className="slot-step">
            <div className="slot-step-head">
              <span className="slot-step-num">2</span>
              <h3>Which meals?</h3>
            </div>
            <div className="slot-meal-grid">
              {[...MEAL_TYPES, 'FULL_DAY'].map(m => (
                <button type="button" key={m} className={`slot-meal meal-${m.toLowerCase()} ${meals.includes(m) ? 'selected' : ''}`}
                  onClick={() => toggleMeal(m)} aria-pressed={meals.includes(m)}>
                  <span className="slot-meal-icon"><MealIcon meal={m} size={18} /></span>
                  <strong>{m === 'FULL_DAY' ? 'Whole day' : MEAL_LABEL[m]}</strong>
                  <small>{savedRate(m) != null ? `${formatRupees(savedRate(m))} / person` : m === 'FULL_DAY' ? 'All 3 meals' : 'Price not set'}</small>
                </button>
              ))}
            </div>
          </section>

          {/* 3 · Who */}
          <section className="slot-step">
            <div className="slot-step-head">
              <span className="slot-step-num">3</span>
              <h3>Who is it for?</h3>
            </div>
            <div className="slot-group-row">
              <div className="slot-group-chips">
                {GROUPS.map(g => (
                  <button type="button" key={g} className={`slot-group ${group === g ? 'selected' : ''}`}
                    onClick={() => { setGroup(g); if (savedCount(g)) setPeople(savedCount(g)); }}>
                    {GROUP_LABEL[g]}
                  </button>
                ))}
              </div>
              <label className="slot-people">
                <span>People</span>
                <input className="form-input" type="number" min="1" max="5000" placeholder="e.g. 40"
                  value={people} onChange={e => setPeople(e.target.value)} />
              </label>
            </div>
          </section>

          {/* 4 · Status */}
          <section className="slot-step">
            <div className="slot-step-head">
              <span className="slot-step-num">4</span>
              <h3>Do you need a sponsor?</h3>
            </div>
            <div className="slot-status-picker">
              <label className={open ? 'selected' : ''}>
                <input type="radio" name="slotStatus" checked={open} onChange={() => setStatus('OPEN')} />
                <HeartHandshake size={18} />
                <span><strong>Yes, needs a sponsor</strong><small>Donors can pay for it</small></span>
              </label>
              <label className={!open ? 'selected covered' : ''}>
                <input type="radio" name="slotStatus" checked={!open} onChange={() => setStatus('FILLED')} />
                <CheckCircle2 size={18} />
                <span><strong>No, already covered</strong><small>Someone provides it</small></span>
              </label>
            </div>
            {!open && (
              <div className="form-group">
                <label className="form-label">Provided by (optional, shown publicly)</label>
                <input className="form-input" type="text" placeholder="e.g. Sharma family"
                  value={sponsorName} onChange={e => setSponsorName(e.target.value)} />
              </div>
            )}
            {meals.some(m => savedRate(m) == null) && (
              <div className="slot-manual-rates">
                <p><AlertTriangle size={13} /> No saved price for {meals.filter(m => savedRate(m) == null).map(m => MEAL_LABEL[m].toLowerCase()).join(', ')}.
                  Enter it here{open ? '' : ' (optional)'}, or save it under Food pricing to skip this next time.</p>
                {meals.filter(m => savedRate(m) == null).map(m => (
                  <label key={m}>
                    <span>{m === 'FULL_DAY' ? 'Whole day' : MEAL_LABEL[m]} — cost per person (₹)</span>
                    <input className="form-input" type="number" min="1" step="0.01" placeholder="e.g. 60"
                      value={manualRates[m] ?? ''} onChange={e => setManualRates(r => ({ ...r, [m]: e.target.value }))} />
                  </label>
                ))}
              </div>
            )}
          </section>

          <details className="slot-extras">
            <summary>Add a menu or a note for donors (optional)</summary>
            <div className="form-group">
              <label className="form-label">Menu</label>
              <input className="form-input" type="text" placeholder="e.g. Dal, rice, sabzi, roti"
                value={menu} onChange={e => setMenu(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Note for donors</label>
              <textarea className="form-input" rows="2" placeholder="e.g. Our regular sponsor for Tuesdays had to cancel."
                value={note} onChange={e => setNote(e.target.value)} />
            </div>
          </details>

          {/* Review: exactly what will be created, before anything is sent. */}
          {plan.length > 0 && (
            <div className="slot-review">
              <div className="slot-review-head">
                <strong>{toPost.length} slot{toPost.length === 1 ? '' : 's'} to post</strong>
                {open && total > 0 && <span>{formatRupees(total)} in total</span>}
              </div>
              <ul>
                {plan.map(p => (
                  <li key={`${p.date}-${p.meal}`} className={p.clash ? 'skip' : ''}>
                    <span><MealIcon meal={p.meal} size={13} /> {dayLabel(p.date)} · {p.meal === 'FULL_DAY' ? 'Whole day' : MEAL_LABEL[p.meal]}</span>
                    <span>{p.clash ? 'Already posted — skipped' : p.amount != null ? formatRupees(p.amount) : open ? 'Price needed' : '—'}</span>
                  </li>
                ))}
              </ul>
              {skipped > 0 && <small>{skipped} already posted for {GROUP_LABEL[group].toLowerCase()} and will be skipped.</small>}
            </div>
          )}

          {error && <div className="alert alert-error">{error}</div>}
          {failures.length > 0 && (
            <div className="alert alert-error">
              <strong>{failures.length} could not be posted:</strong>
              <ul className="slot-failures">
                {failures.map(f => <li key={`${f.date}-${f.meal}`}>{dayLabel(f.date)} · {MEAL_LABEL[f.meal]} — {f.reason}</li>)}
              </ul>
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-full" disabled={submitting || toPost.length === 0}>
            {submitting ? <span className="spinner" />
              : toPost.length ? `Post ${toPost.length} slot${toPost.length === 1 ? '' : 's'}` : 'Nothing new to post'}
          </button>
        </form>
      </div>
    </div>
  );
}

// ── One slot ───────────────────────────────────────────────────────────────────

function SlotDetailModal({ slot, onClose, user, authHeader, onUpdated, onDeleted }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fillOpen, setFillOpen] = useState(false);
  const [sponsorName, setSponsorName] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const state = slotState(slot);

  const run = async (fn) => {
    setBusy(true); setError('');
    try { await fn(); } catch (err) { setError(err.response?.data?.message || 'Could not update this food slot.'); }
    finally { setBusy(false); }
  };
  const base = `/ngos/${user.id}/food-slots/${slot.id}`;
  const fill = () => run(async () => {
    const res = await api.patch(`${base}/fill`, { sponsorName }, { headers: authHeader() });
    onUpdated(res.data); setFillOpen(false);
  });
  const reopen = () => run(async () => onUpdated((await api.patch(`${base}/reopen`, null, { headers: authHeader() })).data));
  const remove = () => run(async () => { await api.delete(base, { headers: authHeader() }); onDeleted(slot.id); });

  return (
    <div className="modal-overlay animate-fade-in" onClick={onClose}>
      <div className="modal-content slot-detail" onClick={e => e.stopPropagation()}>
        <button className="close-btn" onClick={onClose} aria-label="Close">×</button>
        <span className={`slot-detail-state ${state}`}>{STATE_LABEL[state]}</span>
        <h2><MealIcon meal={slot.mealType} size={20} /> {slot.mealType === 'FULL_DAY' ? 'Whole day' : MEAL_LABEL[slot.mealType]}</h2>
        <p className="slot-detail-date">{dayLabel(slot.date)} · {longDayLabel(slot.date)}</p>

        <dl className="slot-detail-facts">
          <div><dt>For</dt><dd>{GROUP_LABEL[slot.beneficiaryGroup]}</dd></div>
          <div><dt>People</dt><dd>{slot.peopleCount}</dd></div>
          <div><dt>Price</dt><dd>{formatRupees(slot.amount)}{slot.costPerPerson != null && <small> ({formatRupees(slot.costPerPerson)} × {slot.peopleCount})</small>}</dd></div>
          {slot.sponsorName && <div><dt>{state === 'paid' ? 'Paid by' : 'Provided by'}</dt><dd>{slot.sponsorName}</dd></div>}
          {slot.menu && <div><dt>Menu</dt><dd>{slot.menu}</dd></div>}
          {slot.note && <div><dt>Note</dt><dd>{slot.note}</dd></div>}
        </dl>

        {error && <div className="alert alert-error">{error}</div>}

        {state === 'paid' && (
          <p className="slot-detail-info">
            <CheckCircle2 size={15} /> A donor paid for this meal on NGO Connect. It is kept as the record of their
            donation, so it can't be changed or deleted.
          </p>
        )}

        {state === 'open' && fillOpen && (
          <div className="slot-fill">
            <label className="form-label">Who is providing this meal? (optional, shown publicly)</label>
            <input className="form-input" type="text" placeholder="e.g. Sharma family" autoFocus
              value={sponsorName} onChange={e => setSponsorName(e.target.value)} />
            <div className="slot-detail-actions">
              <button className="btn btn-primary btn-sm" disabled={busy} onClick={fill}>Confirm covered</button>
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setFillOpen(false)}>Cancel</button>
            </div>
          </div>
        )}

        {state !== 'paid' && !fillOpen && (
          <div className="slot-detail-actions">
            {state === 'open' && (
              <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => setFillOpen(true)}>
                <CheckCircle2 size={15} /> Mark as covered
              </button>
            )}
            {state === 'covered' && slot.amount != null && (
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={reopen}>
                <RotateCcw size={15} /> Reopen for donors
              </button>
            )}
            <button className={`btn btn-sm ${confirmDelete ? 'btn-danger' : 'btn-outline-danger'}`} disabled={busy}
              onClick={() => (confirmDelete ? remove() : setConfirmDelete(true))}>
              <Trash2 size={15} /> {confirmDelete ? 'Click again to delete' : 'Delete'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
