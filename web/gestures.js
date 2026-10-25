const clamp = (n, limit) => Math.max(-limit, Math.min(limit, n));
const valid = p => Number.isFinite(p.clientX) && Number.isFinite(p.clientY) && Number.isSafeInteger(p.pointerId);

export function createGestures(send, clock = {
  now: () => performance.now(), setTimeout: (fn, ms) => setTimeout(fn, ms), clearTimeout: id => clearTimeout(id),
}) {
  const points = new Map();
  let timer = null, dragging = false, multi = false, mouse = null, sensitivity = 1;
  let fractionX = 0, fractionY = 0, scrollX = 0, scrollY = 0;
  const clearHold = () => { if (timer !== null) clock.clearTimeout(timer); timer = null; };
  const release = () => { if (dragging) { dragging = false; send('release', {}); } };
  function move(dx, dy) {
    fractionX = clamp(fractionX + dx * sensitivity, 256);
    fractionY = clamp(fractionY + dy * sensitivity, 256);
    const x = Math.trunc(fractionX), y = Math.trunc(fractionY);
    fractionX -= x; fractionY -= y;
    if (x || y) send('move', {dx: x || 0, dy: y || 0});
  }
  const center = () => {
    const pair = [...points.values()];
    return {x: (pair[0].x + pair[1].x) / 2, y: (pair[0].y + pair[1].y) / 2};
  };
  function reset() {
    clearHold(); release(); points.clear(); mouse = null; multi = false;
    fractionX = fractionY = scrollX = scrollY = 0;
  }
  function pointerdown(p) {
    if (!valid(p) || (p.pointerType === 'mouse' && p.button !== 0)) return;
    if (p.pointerType === 'mouse') {
      mouse = {x: p.clientX, y: p.clientY};
      if (!dragging) { dragging = true; send('press', {}); }
      return;
    }
    if (points.has(p.pointerId)) return;
    if (points.size >= 2) { clearHold(); release(); multi = true; return; }
    points.set(p.pointerId, {x: p.clientX, y: p.clientY, startX: p.clientX, startY: p.clientY, at: clock.now(), moved: false});
    if (points.size === 2) { clearHold(); release(); multi = true; return; }
    if (multi) return;
    timer = clock.setTimeout(() => {
      timer = null;
      if ([...points.values()].some(point => clock.now() - point.at >= 3000)) { reset(); return; }
      if (points.size === 1 && !multi && ![...points.values()][0].moved) { dragging = true; send('press', {}); }
    }, 300);
  }
  function pointermove(p) {
    if (!valid(p)) return;
    if (p.pointerType === 'mouse') {
      if (mouse) move(p.clientX - mouse.x, p.clientY - mouse.y);
      mouse = {x: p.clientX, y: p.clientY}; return;
    }
    const point = points.get(p.pointerId);
    if (!point) return;
    const before = points.size === 2 ? center() : null;
    const dx = p.clientX - point.x, dy = p.clientY - point.y;
    point.x = p.clientX; point.y = p.clientY;
    if (Math.hypot(point.x - point.startX, point.y - point.startY) > 6) { point.moved = true; clearHold(); }
    if (multi) {
      if (!before) return;
      const after = center();
      scrollX = clamp(scrollX + after.x - before.x, 20); scrollY = clamp(scrollY + after.y - before.y, 20);
      const x = Math.trunc(scrollX / 20), y = Math.trunc(scrollY / 20);
      scrollX -= x * 20; scrollY -= y * 20;
      if (x || y) send('scroll', {dx: x || 0, dy: y || 0});
    } else move(dx, dy);
  }
  function pointerup(p) {
    if (!valid(p)) { reset(); return; }
    if (p.pointerType === 'mouse') { release(); return; }
    const point = points.get(p.pointerId);
    if (!point) return;
    clearHold();
    const tap = !multi && !dragging && !point.moved && clock.now() - point.at < 300 && Math.hypot(p.clientX - point.startX, p.clientY - point.startY) <= 6;
    release(); points.delete(p.pointerId);
    if (tap) send('tap', {});
    if (!points.size) { multi = false; fractionX = fractionY = scrollX = scrollY = 0; }
  }
  return {pointerdown, pointermove, pointerup, pointercancel: reset, reset,
    get active() { return dragging || points.size > 0; },
    setSensitivity(value) {
      if (!Number.isFinite(value) || value < 0.25 || value > 4) throw new RangeError('Sensitivity must be 0.25–4');
      sensitivity = value; fractionX = fractionY = 0;
    },
  };
}
