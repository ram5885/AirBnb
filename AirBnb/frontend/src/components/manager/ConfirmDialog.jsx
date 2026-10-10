import { useEffect, useRef } from 'react';

/** Modal confirmation for destructive actions. Escape or the backdrop cancels; focus starts on Cancel. */
export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirm',
  cancelLabel = 'Keep',
  danger = true,
  busy = false,
  onConfirm,
  onCancel,
}) {
  const cancelRef = useRef(null);
  const previousFocus = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    previousFocus.current = document.activeElement;
    cancelRef.current?.focus();
    function onKey(e) {
      if (e.key === 'Escape' && !busy) onCancel();
    }
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previousFocus.current?.focus?.();
    };
  }, [open, busy, onCancel]);

  if (!open) return null;

  return (
    <div className="m-dialog__backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
      <div className="m-dialog" role="alertdialog" aria-modal="true" aria-labelledby="m-dialog-title">
        <h2 id="m-dialog-title">{title}</h2>
        <div className="m-dialog__body">{children}</div>
        <div className="m-dialog__actions">
          <button type="button" className="m-btn m-btn--secondary" ref={cancelRef} onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`m-btn ${danger ? 'm-btn--danger' : 'm-btn--primary'}`}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? 'Please wait…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
