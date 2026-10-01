/**
 * Pull the video id out of the YouTube URL shapes people actually paste:
 * youtube.com/watch?v=ID, youtu.be/ID, youtube.com/shorts/ID, youtube.com/embed/ID.
 */
export function youTubeId(url) {
  try {
    const u = new URL(url);
    const host = u.hostname.replace(/^www\.|^m\./, '');
    if (host === 'youtu.be') return u.pathname.slice(1) || null;
    if (host === 'youtube.com') {
      if (u.searchParams.get('v')) return u.searchParams.get('v');
      const match = u.pathname.match(/^\/(shorts|embed|live)\/([^/?]+)/);
      return match ? match[2] : null;
    }
  } catch { /* not a URL — fall through to a plain link */ }
  return null;
}

/** "drive.google.com" from a full link, for labelling videos that can only open on their own site. */
export const hostOf = (url) => { try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return 'external site'; } };
