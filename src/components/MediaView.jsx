import { Film, Play } from 'lucide-react';
import { youTubeId, hostOf } from '../utils/media';
import './MediaView.css';

/**
 * The full player for one gallery item, sized by its container. Photos render as images; uploaded
 * video files (they have a cloudinaryId) play in a native <video>; YouTube links embed; any other
 * link (Drive, Instagram…) opens in a new tab, since those sites refuse to be embedded.
 */
export default function MediaView({ item, autoPlay = false }) {
  if (item.mediaType === 'IMAGE') {
    return <img className="media-view media-view-image" src={item.mediaUrl} alt={item.caption || 'Photo'} />;
  }

  if (item.cloudinaryId) {
    return <video className="media-view" src={item.mediaUrl} controls autoPlay={autoPlay} playsInline preload="metadata" />;
  }

  const ytId = youTubeId(item.mediaUrl);
  if (ytId) {
    return (
      <iframe
        className="media-view media-view-frame"
        src={`https://www.youtube-nocookie.com/embed/${ytId}${autoPlay ? '?autoplay=1' : ''}`}
        title={item.caption || 'Video'}
        allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
        allowFullScreen
      />
    );
  }

  return (
    <a className="media-view media-view-link" href={item.mediaUrl} target="_blank" rel="noreferrer">
      <Film size={40} strokeWidth={1.5} />
      Watch on {hostOf(item.mediaUrl)} ↗
    </a>
  );
}

/**
 * A still preview for the gallery grid — never a live player, so a grid of twenty videos does not
 * load twenty players. Uploaded videos show their first frame; YouTube shows its own thumbnail.
 */
export function MediaThumb({ item }) {
  if (item.mediaType === 'IMAGE') {
    return <img className="media-thumb" src={item.mediaUrl} alt={item.caption || 'Photo'} loading="lazy" />;
  }
  const ytId = youTubeId(item.mediaUrl);
  return (
    <>
      {item.cloudinaryId ? (
        // "#t=0.5" makes the browser paint a frame from half a second in, instead of a black box.
        <video className="media-thumb" src={`${item.mediaUrl}#t=0.5`} preload="metadata" muted playsInline tabIndex={-1} />
      ) : ytId ? (
        <img className="media-thumb" src={`https://i.ytimg.com/vi/${ytId}/hqdefault.jpg`} alt={item.caption || 'Video'} loading="lazy" />
      ) : (
        <div className="media-thumb media-thumb-link"><Film size={30} strokeWidth={1.5} /><span>{hostOf(item.mediaUrl)}</span></div>
      )}
      <span className="media-play" aria-hidden="true"><Play size={20} fill="currentColor" /></span>
    </>
  );
}
