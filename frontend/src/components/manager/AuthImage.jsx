import { useEffect, useState } from 'react';
import { fetchImage } from '../../api.js';

/**
 * Photos are served by authenticated endpoints, which a plain <img src> can't call with
 * a Bearer token, so the bytes are fetched and shown through an object URL.
 */
export default function AuthImage({ src, alt, className }) {
  const [url, setUrl] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!src) return undefined;
    let objectUrl = null;
    let cancelled = false;
    setFailed(false);
    fetchImage(src)
      .then((u) => {
        objectUrl = u;
        if (cancelled) URL.revokeObjectURL(u);
        else setUrl(u);
      })
      .catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [src]);

  if (!src || failed) {
    return (
      <div className={`m-img-placeholder ${className || ''}`} role="img" aria-label={alt}>
        <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" aria-hidden="true">
          <path d="M21 19V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z" />
        </svg>
      </div>
    );
  }
  if (!url) return <div className={`m-img-placeholder m-img-placeholder--loading ${className || ''}`} aria-busy="true" />;
  return <img src={url} alt={alt} className={className} />;
}
