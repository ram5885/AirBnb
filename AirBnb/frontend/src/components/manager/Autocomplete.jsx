import { useEffect, useId, useMemo, useRef, useState } from 'react';

const MAX_SUGGESTIONS = 50;

/**
 * Accessible combobox restricted to the given options: typing filters suggestions, and the
 * value only changes when an option is picked (or the typed text exactly matches one on blur).
 * Free text that matches nothing never becomes the selected value.
 */
export default function Autocomplete({
  id,
  options,
  value,
  onSelect,
  placeholder,
  disabled,
  loading,
  loadError,
  emptyText = 'No matches',
  invalid,
  describedBy,
}) {
  const [text, setText] = useState(value || '');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const listId = useId();
  const listRef = useRef(null);
  const clearedByTyping = useRef(false);

  // Keep the input in sync when the parent changes or clears the selection, but don't wipe
  // what the user is typing when the clear came from their own edit.
  useEffect(() => {
    if (!value && clearedByTyping.current) {
      clearedByTyping.current = false;
      return;
    }
    setText(value || '');
  }, [value]);

  const matches = useMemo(() => {
    const q = text.trim().toLowerCase();
    if (!q || q === (value || '').toLowerCase()) return options.slice(0, MAX_SUGGESTIONS);
    const starts = [];
    const contains = [];
    for (const o of options) {
      const l = o.toLowerCase();
      if (l.startsWith(q)) starts.push(o);
      else if (l.includes(q)) contains.push(o);
    }
    return [...starts, ...contains].slice(0, MAX_SUGGESTIONS);
  }, [options, text, value]);

  useEffect(() => {
    setActive(0);
  }, [text, open]);

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView?.({ block: 'nearest' });
  }, [active]);

  function choose(option) {
    setText(option);
    setOpen(false);
    onSelect(option);
  }

  function handleChange(e) {
    setText(e.target.value);
    setOpen(true);
    if (value) {
      // typing invalidates the previous selection until a new pick
      clearedByTyping.current = true;
      onSelect(null);
    }
  }

  function handleBlur() {
    setOpen(false);
    if (!value && text.trim()) {
      const exact = options.find((o) => o.toLowerCase() === text.trim().toLowerCase());
      if (exact) choose(exact);
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) setOpen(true);
      else setActive((i) => Math.min(i + 1, matches.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      if (open && matches[active]) {
        e.preventDefault();
        choose(matches[active]);
      }
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  const showList = open && !disabled;
  return (
    <div className="m-combobox">
      <input
        id={id}
        type="text"
        role="combobox"
        autoComplete="off"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listId}
        aria-activedescendant={showList && matches[active] ? `${listId}-${active}` : undefined}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        value={text}
        placeholder={placeholder}
        disabled={disabled}
        onChange={handleChange}
        onFocus={() => setOpen(true)}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
      />
      {showList && (
        <ul className="m-combobox__list" id={listId} role="listbox" ref={listRef}>
          {loading && <li className="m-combobox__info">Loading…</li>}
          {!loading && loadError && <li className="m-combobox__info m-combobox__info--error">{loadError}</li>}
          {!loading && !loadError && matches.length === 0 && <li className="m-combobox__info">{emptyText}</li>}
          {!loading &&
            !loadError &&
            matches.map((o, i) => (
              <li
                key={o}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className={i === active ? 'is-active' : ''}
                // mousedown (not click) so the choice lands before the input's blur closes the list
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(o);
                }}
                onMouseEnter={() => setActive(i)}
              >
                {o}
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
