import { useEffect, useId, useMemo, useRef } from 'react';
import AuthImage from './AuthImage.jsx';

export const MAX_PHOTOS = 10;
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Multi-photo picker. `existing` are already-saved photos (removable on edit), `files` are
 * newly selected File objects. Client-side checks mirror the backend (type, size, count);
 * the backend re-validates the actual file content.
 */
export default function PhotoPicker({ existing = [], files, onFilesChange, onRemoveExisting, onError, disabled }) {
  const inputId = useId();
  const inputRef = useRef(null);

  const previews = useMemo(() => files.map((f) => ({ file: f, url: URL.createObjectURL(f) })), [files]);
  useEffect(() => () => previews.forEach((p) => URL.revokeObjectURL(p.url)), [previews]);

  const total = existing.length + files.length;

  function handleSelect(e) {
    const selected = Array.from(e.target.files || []);
    e.target.value = ''; // allow re-selecting the same file after removing it
    const accepted = [];
    const problems = [];
    for (const f of selected) {
      if (!ACCEPTED.includes(f.type)) problems.push(`${f.name}: only JPEG, PNG or WebP images are allowed`);
      else if (f.size === 0) problems.push(`${f.name}: file is empty`);
      else if (f.size > MAX_PHOTO_BYTES) problems.push(`${f.name}: larger than 5 MB`);
      else accepted.push(f);
    }
    const room = MAX_PHOTOS - total;
    if (accepted.length > room) {
      problems.push(`You can add at most ${MAX_PHOTOS} photos in total`);
      accepted.splice(room);
    }
    onError(problems.join('. '));
    if (accepted.length) onFilesChange([...files, ...accepted]);
  }

  return (
    <div className="m-photos">
      <div className="m-photos__grid">
        {existing.map((p) => (
          <figure key={`e-${p.id}`} className="m-photos__item">
            <AuthImage src={p.url} alt={p.fileName} />
            <button
              type="button"
              className="m-photos__remove"
              onClick={() => onRemoveExisting(p.id)}
              disabled={disabled}
              aria-label={`Remove ${p.fileName}`}
            >
              ×
            </button>
          </figure>
        ))}
        {previews.map((p, i) => (
          <figure key={`n-${i}-${p.file.name}`} className="m-photos__item m-photos__item--new">
            <img src={p.url} alt={`Preview of ${p.file.name}`} />
            <figcaption>New</figcaption>
            <button
              type="button"
              className="m-photos__remove"
              onClick={() => onFilesChange(files.filter((_, j) => j !== i))}
              disabled={disabled}
              aria-label={`Remove ${p.file.name}`}
            >
              ×
            </button>
          </figure>
        ))}
        {total < MAX_PHOTOS && (
          <label htmlFor={inputId} className={`m-photos__add ${disabled ? 'is-disabled' : ''}`}>
            <span aria-hidden="true">+</span>
            <span>Add photos</span>
          </label>
        )}
      </div>
      <input
        id={inputId}
        ref={inputRef}
        className="visually-hidden"
        type="file"
        accept={ACCEPTED.join(',')}
        multiple
        onChange={handleSelect}
        disabled={disabled}
      />
      <p className="m-field__hint">
        JPEG, PNG or WebP, up to 5 MB each, {MAX_PHOTOS} photos max ({total}/{MAX_PHOTOS}).
      </p>
    </div>
  );
}
