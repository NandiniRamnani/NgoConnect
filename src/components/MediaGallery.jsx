import { useCallback, useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, X, Trash2, Images } from 'lucide-react';
import MediaView, { MediaThumb } from './MediaView';
import './MediaGallery.css';

const formatDate = (iso) => {
  if (!iso) return '';
  try { return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }); }
  catch { return ''; }
};

/**
 * A photo & video gallery: filter tabs, a tidy grid of same-sized thumbnails, and a lightbox that
 * plays the item full size with prev/next. Pass `onDelete` to show a remove button on each tile
 * (the owner's dashboard); leave it out for the read-only public page.
 */
export default function MediaGallery({ items, onDelete, emptyText = 'No photos or videos yet.', emptyAction }) {
  const [filter, setFilter] = useState('ALL');
  const [openIndex, setOpenIndex] = useState(null);

  const photos = items.filter(m => m.mediaType === 'IMAGE').length;
  const videos = items.length - photos;
  const shown = filter === 'ALL' ? items : items.filter(m => (filter === 'IMAGE' ? m.mediaType === 'IMAGE' : m.mediaType !== 'IMAGE'));
  const current = openIndex != null ? shown[openIndex] : null;

  const close = useCallback(() => setOpenIndex(null), []);
  const step = useCallback((dir) => setOpenIndex(i => (i == null ? i : (i + dir + shown.length) % shown.length)), [shown.length]);

  useEffect(() => {
    if (openIndex == null) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') close();
      else if (e.key === 'ArrowRight') step(1);
      else if (e.key === 'ArrowLeft') step(-1);
    };
    // Stop the page behind the lightbox from scrolling while it is open.
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKey);
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prevOverflow; };
  }, [openIndex, close, step]);

  // An item deleted while the lightbox shows it must not leave the lightbox pointing past the end.
  if (openIndex != null && openIndex >= shown.length) setOpenIndex(shown.length ? shown.length - 1 : null);

  if (items.length === 0) {
    return (
      <div className="mg-empty">
        <Images size={36} strokeWidth={1.4} />
        <p>{emptyText}</p>
        {emptyAction}
      </div>
    );
  }

  return (
    <div className="mg">
      {photos > 0 && videos > 0 && (
        <div className="mg-tabs" role="tablist">
          {[['ALL', 'All', items.length], ['IMAGE', 'Photos', photos], ['VIDEO', 'Videos', videos]].map(([key, label, n]) => (
            <button key={key} role="tab" aria-selected={filter === key} className={filter === key ? 'active' : ''} onClick={() => setFilter(key)}>
              {label} <span>{n}</span>
            </button>
          ))}
        </div>
      )}

      <div className="mg-grid">
        {shown.map((item, i) => (
          <div key={item.id} className="mg-tile">
            <button type="button" className="mg-tile-open" onClick={() => setOpenIndex(i)}
              aria-label={`Open ${item.mediaType === 'IMAGE' ? 'photo' : 'video'}${item.caption ? `: ${item.caption}` : ''}`}>
              <MediaThumb item={item} />
              {item.caption && <span className="mg-tile-caption">{item.caption}</span>}
            </button>
            {onDelete && (
              <button type="button" className="mg-tile-delete" onClick={() => onDelete(item)} aria-label="Remove" title="Remove">
                <Trash2 size={15} />
              </button>
            )}
          </div>
        ))}
      </div>

      {current && (
        <div className="mg-lightbox" role="dialog" aria-modal="true" onClick={close}>
          <div className="mg-lightbox-top" onClick={e => e.stopPropagation()}>
            <span>{openIndex + 1} / {shown.length}</span>
            <button type="button" onClick={close} aria-label="Close"><X size={22} /></button>
          </div>
          {shown.length > 1 && (
            <>
              <button type="button" className="mg-nav prev" onClick={e => { e.stopPropagation(); step(-1); }} aria-label="Previous"><ChevronLeft size={28} /></button>
              <button type="button" className="mg-nav next" onClick={e => { e.stopPropagation(); step(1); }} aria-label="Next"><ChevronRight size={28} /></button>
            </>
          )}
          <div className="mg-stage" onClick={e => e.stopPropagation()}>
            {/* key forces a fresh player per item, so the previous video stops playing. */}
            <MediaView key={current.id} item={current} autoPlay />
          </div>
          {(current.caption || current.createdAt) && (
            <div className="mg-lightbox-caption" onClick={e => e.stopPropagation()}>
              {current.caption && <p>{current.caption}</p>}
              {current.createdAt && <small>{formatDate(current.createdAt)}</small>}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
