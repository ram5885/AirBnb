import { useEffect, useRef, useState } from 'react';

/**
 * Horizontally scrolling row with a heading and previous/next arrows (disabled at either end, hidden when
 * everything fits). `children` are the <li> items; `variant` sets how many fit per screen width.
 */
export default function Carousel({ id, title, subtitle, titleAction, variant = 'cities', children }) {
  const rowRef = useRef(null);
  const [scroll, setScroll] = useState({ start: true, end: true });

  function updateArrows() {
    const el = rowRef.current;
    if (!el) return;
    setScroll({ start: el.scrollLeft <= 1, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 1 });
  }

  useEffect(() => {
    updateArrows();
    window.addEventListener('resize', updateArrows);
    return () => window.removeEventListener('resize', updateArrows);
  }, [children]);

  function page(direction) {
    const el = rowRef.current;
    el?.scrollBy({ left: direction * el.clientWidth * 0.9, behavior: 'smooth' });
  }

  const scrollable = !(scroll.start && scroll.end);
  return (
    <section className="carousel" aria-labelledby={`${id}-title`}>
      <div className="carousel__header">
        <div>
          <div className="carousel__title">
            <h2 id={`${id}-title`}>{title}</h2>
            {titleAction}
          </div>
          {subtitle && <p className="carousel__subtitle">{subtitle}</p>}
        </div>
        {scrollable && (
          <div className="carousel__arrows">
            <button type="button" onClick={() => page(-1)} disabled={scroll.start} aria-label={`Scroll ${title} back`}>
              <svg viewBox="0 0 32 32" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="4" aria-hidden="true">
                <path d="M20 28L8 16 20 4" />
              </svg>
            </button>
            <button type="button" onClick={() => page(1)} disabled={scroll.end} aria-label={`Scroll ${title} forward`}>
              <svg viewBox="0 0 32 32" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="4" aria-hidden="true">
                <path d="M12 4l12 12-12 12" />
              </svg>
            </button>
          </div>
        )}
      </div>
      <ul className={`carousel__row carousel__row--${variant}`} ref={rowRef} onScroll={updateArrows}>
        {children}
      </ul>
    </section>
  );
}
