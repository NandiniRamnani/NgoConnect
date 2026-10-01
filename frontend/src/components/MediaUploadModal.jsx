import { useEffect, useState } from 'react';
import { ImagePlus, Video, Link2, UploadCloud, X, RefreshCw } from 'lucide-react';
import api from '../api/axios';
import { youTubeId } from '../utils/media';
import './MediaUploadModal.css';

const MAX_PHOTO_MB = 10;   // matches NgoContentService.MAX_PHOTO_BYTES
const MAX_VIDEO_MB = 100;  // matches the servlet's multipart limit
const MAX_CAPTION = 300;

const formatSize = (bytes) => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`);

/**
 * Post a photo or a video to a gallery: drop or pick a file and see it before posting, or paste a
 * video link and see the YouTube preview. Works for both NGOs and donors — they differ only in
 * `mediaBase`, the API path their gallery lives under.
 */
export default function MediaUploadModal({ initialKind = 'photo', mediaBase, authHeader, onClose, onUploaded }) {
  const [kind, setKind] = useState(initialKind);         // 'photo' | 'video'
  const [videoMode, setVideoMode] = useState('upload');  // upload a file, or paste a 'link'
  const [file, setFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [link, setLink] = useState('');
  const [caption, setCaption] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [progress, setProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const usingFile = kind === 'photo' || videoMode === 'upload';
  const maxMb = kind === 'photo' ? MAX_PHOTO_MB : MAX_VIDEO_MB;
  const accept = kind === 'photo' ? 'image/*' : 'video/*';
  const ytId = youTubeId(link.trim());

  // A preview is an object URL pointing at the picked file; release it when the file changes.
  useEffect(() => {
    if (!file) { setPreviewUrl(''); return undefined; }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const switchKind = (next) => {
    if (busy || next === kind) return;
    setKind(next); setFile(null); setError('');
  };

  const pickFile = (picked) => {
    setError('');
    if (!picked) return;
    if (!picked.type.startsWith(kind === 'photo' ? 'image/' : 'video/'))
      return setError(kind === 'photo' ? 'That is not an image. Choose a JPG, PNG or WebP photo.' : 'That is not a video. Choose an MP4, MOV or WebM file.');
    // Checked here too so a 300MB file fails instantly instead of after minutes of uploading.
    if (picked.size > maxMb * 1024 * 1024) return setError(`That file is ${formatSize(picked.size)}. ${kind === 'photo' ? 'Photos' : 'Videos'} must be ${maxMb}MB or smaller.`);
    setFile(picked);
  };

  const onDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    if (!busy) pickFile(e.dataTransfer.files?.[0]);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (usingFile && !file) return setError(kind === 'photo' ? 'Choose a photo first.' : 'Choose a video file first.');
    if (!usingFile) {
      try { new URL(link.trim()); } catch { return setError('Paste a full video link, starting with https://'); }
    }

    setBusy(true); setProgress(0);
    try {
      let res;
      if (usingFile) {
        const fd = new FormData();
        fd.append('file', file);
        if (caption.trim()) fd.append('caption', caption.trim());
        res = await api.post(`${mediaBase}/${kind === 'photo' ? 'photo' : 'video/upload'}`, fd, {
          // Clearing Content-Type lets axios set multipart/form-data with its boundary.
          headers: { ...authHeader(), 'Content-Type': undefined },
          onUploadProgress: (p) => p.total && setProgress(Math.round((p.loaded / p.total) * 100)),
        });
      } else {
        res = await api.post(`${mediaBase}/video`, { videoUrl: link.trim(), caption: caption.trim() }, { headers: authHeader() });
      }
      onUploaded(res.data);
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Upload failed. Please try again.');
    } finally {
      setBusy(false); setProgress(0);
    }
  };

  return (
    <div className="mu-backdrop" onClick={() => !busy && onClose()}>
      <div className="mu-card" role="dialog" aria-modal="true" aria-labelledby="mu-title" onClick={e => e.stopPropagation()}>
        <div className="mu-head">
          <h3 id="mu-title">New post</h3>
          <button type="button" className="mu-close" onClick={onClose} disabled={busy} aria-label="Close"><X size={20} /></button>
        </div>

        <div className="mu-kind" role="tablist">
          <button type="button" role="tab" aria-selected={kind === 'photo'} className={kind === 'photo' ? 'active' : ''} onClick={() => switchKind('photo')}>
            <ImagePlus size={16} /> Photo
          </button>
          <button type="button" role="tab" aria-selected={kind === 'video'} className={kind === 'video' ? 'active' : ''} onClick={() => switchKind('video')}>
            <Video size={16} /> Video
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {kind === 'video' && (
            <div className="mu-mode">
              <label className={videoMode === 'upload' ? 'active' : ''}>
                <input type="radio" name="videoMode" checked={videoMode === 'upload'} onChange={() => { setVideoMode('upload'); setError(''); }} disabled={busy} />
                <UploadCloud size={15} /> Upload a file
              </label>
              <label className={videoMode === 'link' ? 'active' : ''}>
                <input type="radio" name="videoMode" checked={videoMode === 'link'} onChange={() => { setVideoMode('link'); setError(''); }} disabled={busy} />
                <Link2 size={15} /> Paste a link
              </label>
            </div>
          )}

          {usingFile ? (
            file ? (
              <div className="mu-preview">
                {kind === 'photo'
                  ? <img src={previewUrl} alt="Preview" />
                  : <video src={previewUrl} controls muted playsInline />}
                <div className="mu-preview-bar">
                  <span title={file.name}>{file.name}</span>
                  <small>{formatSize(file.size)}</small>
                  <label className={`mu-change ${busy ? 'disabled' : ''}`}>
                    <RefreshCw size={13} /> Change
                    <input type="file" accept={accept} hidden disabled={busy} onChange={e => { pickFile(e.target.files?.[0]); e.target.value = ''; }} />
                  </label>
                </div>
              </div>
            ) : (
              <label
                className={`mu-drop ${dragOver ? 'over' : ''}`}
                onDragOver={e => { e.preventDefault(); setDragOver(true); }}
                onDragLeave={() => setDragOver(false)}
                onDrop={onDrop}
              >
                <span className="mu-drop-icon">{kind === 'photo' ? <ImagePlus size={28} /> : <Video size={28} />}</span>
                <strong>Drag {kind === 'photo' ? 'a photo' : 'a video'} here, or <u>browse</u></strong>
                <small>{kind === 'photo' ? `JPG, PNG or WebP · up to ${MAX_PHOTO_MB}MB` : `MP4, MOV or WebM · up to ${MAX_VIDEO_MB}MB`}</small>
                <input type="file" accept={accept} hidden onChange={e => { pickFile(e.target.files?.[0]); e.target.value = ''; }} />
              </label>
            )
          ) : (
            <div className="mu-link">
              <input className="form-input" type="url" placeholder="https://youtube.com/watch?v=…" value={link}
                onChange={e => setLink(e.target.value)} disabled={busy} autoFocus />
              {ytId ? (
                <div className="mu-preview mu-yt">
                  <img src={`https://i.ytimg.com/vi/${ytId}/hqdefault.jpg`} alt="YouTube preview" />
                  <span>YouTube video — it will play right on the page</span>
                </div>
              ) : (
                <small className="mu-hint">
                  {link.trim()
                    ? 'Not a YouTube link — it will be shown as a “Watch video” button that opens the site.'
                    : 'YouTube links play right on the page. Drive, Instagram and other links open in a new tab.'}
                </small>
              )}
            </div>
          )}

          <div className="mu-caption">
            <label htmlFor="mu-caption-input">Caption <span>(optional)</span></label>
            <textarea id="mu-caption-input" className="form-input" rows="2" maxLength={MAX_CAPTION} disabled={busy}
              placeholder={kind === 'photo' ? 'What is happening in this photo?' : 'What is this video about?'}
              value={caption} onChange={e => setCaption(e.target.value)} />
            <small>{caption.length}/{MAX_CAPTION}</small>
          </div>

          {error && <div className="alert alert-error">{error}</div>}

          {busy && usingFile && (
            <div className="mu-progress" aria-label="Upload progress">
              <div style={{ width: `${progress}%` }} />
              <span>{progress < 100 ? `Uploading… ${progress}%` : kind === 'video' ? 'Processing video…' : 'Finishing…'}</span>
            </div>
          )}

          <div className="mu-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={busy || (usingFile ? !file : !link.trim())}>
              {busy ? <span className="spinner" /> : kind === 'photo' ? 'Post photo' : 'Post video'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
