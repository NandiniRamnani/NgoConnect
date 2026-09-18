import './Avatar.css';

/**
 * One component for every profile picture in the app — navbar, NGO cards, detail pages, dashboard.
 *
 * It renders whichever of three things the account actually has:
 *
 *   1. An uploaded photo    src = "https://res.cloudinary.com/..."
 *   2. A chosen illustration src = "preset:leaf"
 *   3. Nothing at all        src = null  ->  initials on a colour derived from the name
 *
 * Having one component decide this is the reason the fallback can be good. When each page wrote
 * its own `{ngo.ngoName?.[0]}` there was nowhere to put the logic for "what if there's a photo?",
 * so every page ignored photos and every avatar was a bare letter on the same green circle.
 */

/**
 * The bundled illustrations, drawn as inline SVG rather than loaded from an avatar CDN.
 *
 * Three reasons not to use a service like DiceBear or Gravatar: the page keeps working offline and
 * on a slow connection, nothing about your users is sent to a third party just to draw a circle,
 * and there is no external host that can rate-limit, change its output, or disappear and take
 * everyone's avatar with it. Each one is a few hundred bytes and ships with the app.
 *
 * `bg` is a two-stop gradient and `art` is the glyph drawn on top, on a 0 0 48 48 canvas.
 */
const PRESETS = {
  leaf:     { bg: ['#16a34a', '#065f46'], art: <path d="M34 14c0 11-8 19-19 20 0-11 8-19 19-20z" fill="#fff" opacity=".92"/> },
  sprout:   { bg: ['#22c55e', '#15803d'], art: <g fill="#fff" opacity=".92"><path d="M24 34V20"/><path d="M24 22c-6 0-9-4-9-8 5 0 9 3 9 8zM24 24c6 0 9-5 9-9-5 0-9 4-9 9z"/><rect x="22.5" y="22" width="3" height="14" rx="1.5"/></g> },
  sun:      { bg: ['#f59e0b', '#b45309'], art: <g fill="#fff" opacity=".92"><circle cx="24" cy="24" r="8"/><g stroke="#fff" strokeWidth="2.5" strokeLinecap="round"><path d="M24 8v4M24 36v4M8 24h4M36 24h4M13 13l3 3M32 32l3 3M35 13l-3 3M16 32l-3 3"/></g></g> },
  wave:     { bg: ['#0ea5e9', '#0369a1'], art: <g fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity=".92"><path d="M10 20c4-4 8-4 12 0s8 4 12 0"/><path d="M10 28c4-4 8-4 12 0s8 4 12 0"/></g> },
  mountain: { bg: ['#64748b', '#1e293b'], art: <g fill="#fff" opacity=".92"><path d="M8 34l10-16 6 9 5-7 11 14z"/><circle cx="32" cy="14" r="3.5"/></g> },
  bloom:    { bg: ['#ec4899', '#9d174d'], art: <g fill="#fff" opacity=".92"><circle cx="24" cy="16" r="5"/><circle cx="24" cy="32" r="5"/><circle cx="16" cy="24" r="5"/><circle cx="32" cy="24" r="5"/><circle cx="24" cy="24" r="4.5" fill="#fde68a"/></g> },
  star:     { bg: ['#8b5cf6', '#5b21b6'], art: <path d="M24 9l4.6 9.8 10.4 1.5-7.5 7.6 1.8 10.9L24 33.6l-9.3 5.2 1.8-10.9L9 20.3l10.4-1.5z" fill="#fff" opacity=".92"/> },
  globe:    { bg: ['#14b8a6', '#0f766e'], art: <g fill="none" stroke="#fff" strokeWidth="2.5" opacity=".92"><circle cx="24" cy="24" r="14"/><ellipse cx="24" cy="24" rx="6" ry="14"/><path d="M10 24h28"/></g> },
};

export const PRESET_NAMES = Object.keys(PRESETS);

/**
 * A fixed palette for initials, chosen so white text stays readable on every one of them.
 * Everyone being the same green made a list of NGOs look like one repeated placeholder; giving
 * each name its own colour makes them distinguishable at a glance even before you read them.
 */
const INITIAL_COLORS = [
  ['#16a34a', '#065f46'], ['#0ea5e9', '#0c4a6e'], ['#8b5cf6', '#4c1d95'],
  ['#f59e0b', '#92400e'], ['#ec4899', '#831843'], ['#14b8a6', '#115e59'],
  ['#ef4444', '#7f1d1d'], ['#6366f1', '#312e81'],
];

/**
 * Pick a colour from the name, deterministically.
 *
 * The same name must always produce the same colour — an NGO whose badge changed hue on every
 * page load would read as a glitch. So instead of anything random, the characters are folded into
 * a number and that number picks a slot. Multiplying by 31 and letting it overflow is the standard
 * cheap string hash; the exact constant does not matter, only that different names spread out.
 *
 * `Math.abs` because the multiplication can overflow into negatives, and a negative index would
 * return undefined.
 */
function colorFor(name) {
  let hash = 0;
  for (let i = 0; i < (name || '').length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0;
  return INITIAL_COLORS[Math.abs(hash) % INITIAL_COLORS.length];
}

/**
 * "Hope Foundation" -> "HF", "Asha" -> "A", "" -> "?".
 *
 * Two letters where they exist because one letter collides constantly — in a list of NGOs, half of
 * them start with the same letter and every badge looks identical.
 */
function initialsFor(name) {
  const words = (name || '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0][0].toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

/**
 * @param src     avatarUrl / logoUrl from the backend: an https URL, "preset:name", or null
 * @param name    used for the initials and their colour when there is no picture
 * @param size    diameter in pixels
 * @param ring    draw the soft outer ring (used in the navbar, off for dense lists)
 * @param variant 'account' (default): photo -> preset -> initials, as described above.
 *                'person': always a plain human silhouette, ignoring `src` entirely.
 *                Used for individual users, who have no photo/logo of their own to show
 *                and would otherwise get the same colour-and-initials badge an NGO gets.
 */
export default function Avatar({ src, name, size = 40, ring = false, className = '', variant = 'account' }) {
  const style = { width: size, height: size, fontSize: Math.round(size * 0.38) };
  const classes = `avatar ${ring ? 'avatar-ring' : ''} ${className}`;

  // 0. No account picture to show at all — a generic silhouette, not initials.
  if (variant === 'person') {
    return (
      <span className={`${classes} avatar-person`} style={style} title={name}>
        <svg viewBox="0 0 24 24" width="60%" height="60%" fill="currentColor" aria-label={name || 'User'}>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 20c0-4.4 3.6-8 8-8s8 3.6 8 8v1H4v-1z" />
        </svg>
      </span>
    );
  }

  // 1. An illustration the user picked.
  if (src && src.startsWith('preset:')) {
    const preset = PRESETS[src.slice(7)];
    if (preset) {
      return (
        <span className={classes} style={style} title={name}>
          <svg viewBox="0 0 48 48" width={size} height={size} aria-label={name}>
            <defs>
              {/* The gradient id must be unique per preset, or two different avatars on the same
                  page would both use whichever definition the browser saw first. */}
              <linearGradient id={`av-${src.slice(7)}`} x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor={preset.bg[0]} />
                <stop offset="100%" stopColor={preset.bg[1]} />
              </linearGradient>
            </defs>
            <circle cx="24" cy="24" r="24" fill={`url(#av-${src.slice(7)})`} />
            {preset.art}
          </svg>
        </span>
      );
    }
    // An unknown preset name (renamed or removed since it was saved) falls through to initials
    // rather than rendering an empty hole.
  }

  // 2. A real uploaded photo.
  if (src && (src.startsWith('http://') || src.startsWith('https://'))) {
    return (
      <span className={classes} style={style}>
        <img
          src={src}
          alt={name || 'Profile picture'}
          className="avatar-img"
          loading="lazy"
          /* If the image 404s — deleted from Cloudinary, offline, blocked — hide it so the
             coloured circle underneath shows through instead of a broken-image icon. */
          onError={(e) => { e.currentTarget.style.display = 'none'; }}
        />
      </span>
    );
  }

  // 3. Nothing set: initials on their own colour.
  const [from, to] = colorFor(name);
  return (
    <span
      className={classes}
      style={{ ...style, background: `linear-gradient(135deg, ${from}, ${to})` }}
      title={name}
    >
      <span className="avatar-initials">{initialsFor(name)}</span>
    </span>
  );
}
