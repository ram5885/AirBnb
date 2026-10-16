import { useState } from 'react';

/** Public hotel/room photo (plain <img>; the photo endpoints need no login) with a placeholder fallback. */
export default function PublicImage({ src, alt, className, title }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) {
    return (
      <div className={`m-img-placeholder ${className || ''}`} role="img" aria-label={alt}>
        <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor" aria-hidden="true">
          <path d="M12 3L2 12h3v8h6v-6h2v6h6v-8h3L12 3z" />
        </svg>
      </div>
    );
  }
  return <img src={src} alt={alt} title={title} className={className} loading="lazy" onError={() => setFailed(true)} />;
}
