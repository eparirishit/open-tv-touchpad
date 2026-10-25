import test from 'node:test';
import assert from 'node:assert/strict';
import { createGestures } from '../../web/gestures.js';

function fixture() {
  let time = 0, next = 0;
  const timers = new Map(), sent = [];
  const clock = { now: () => time, setTimeout: (fn, delay) => { timers.set(++next, {fn, at: time + delay}); return next; }, clearTimeout: id => timers.delete(id) };
  const g = createGestures((kind, fields = {}) => sent.push({kind, ...fields}), clock);
  const advance = ms => { time += ms; for (const [id, t] of timers) if (t.at <= time) { timers.delete(id); t.fn(); } };
  const p = (id = 1, x = 0, y = 0, type = 'touch') => ({pointerId: id, clientX: x, clientY: y, pointerType: type, button: 0});
  return {g, sent, advance, p};
}

test('short tap vs exactly 300ms hold, release on up', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p()); advance(299); g.pointerup(p());
  assert.deepEqual(sent, [{kind: 'tap'}]); sent.length = 0;
  g.pointerdown(p()); advance(300);
  assert.deepEqual(sent, [{kind: 'press'}]);
  g.pointerup(p()); assert.deepEqual(sent.at(-1), {kind: 'release'});
});

test('six pixels still permits hold; excursion beyond six permanently cancels tap/hold', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p()); g.pointermove(p(1, 6)); advance(300);
  assert.equal(sent.at(-1).kind, 'press'); g.reset(); sent.length = 0;
  g.pointerdown(p()); g.pointermove(p(1, 7)); g.pointermove(p()); advance(300); g.pointerup(p());
  assert.equal(sent.some(m => ['tap', 'press'].includes(m.kind)), false);
});

test('fractional sensitivity survives quantization; large movement clamps without a replay tail', () => {
  const {g, sent, p} = fixture(); g.setSensitivity(0.5);
  g.pointerdown(p()); g.pointermove(p(1, 1)); assert.equal(sent.length, 0);
  g.pointermove(p(1, 2)); assert.deepEqual(sent.pop(), {kind: 'move', dx: 1, dy: 0});
  g.pointermove(p(1, 10000, -10000)); assert.deepEqual(sent.pop(), {kind: 'move', dx: 256, dy: -256});
  g.pointermove(p(1, 10000, -10000)); assert.equal(sent.length, 0);
  assert.throws(() => g.setSensitivity(NaN)); assert.throws(() => g.setSensitivity(9));
});

test('second finger releases drag before scroll; remaining finger cannot tap', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p()); advance(300); g.pointerdown(p(2));
  g.pointermove(p(2, 0, 50)); g.pointerup(p(2, 0, 50)); g.pointerup(p());
  assert.deepEqual(sent, [{kind:'press'}, {kind:'release'}, {kind:'scroll', dx:0, dy:1}]);
});

test('second finger cancels pending hold and does not resume single finger', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p()); advance(200); g.pointerdown(p(2)); g.pointerup(p(2)); advance(300); g.pointerup(p());
  assert.deepEqual(sent, []);
});

test('mouse hover uses relative deltas; left button drags and cancel releases once', () => {
  const {g, sent, p} = fixture();
  g.pointermove(p(1, 10, 10, 'mouse')); g.pointermove(p(1, 12, 8, 'mouse'));
  g.pointerdown(p(1, 12, 8, 'mouse')); g.pointermove(p(1, 13, 8, 'mouse')); g.pointercancel(); g.pointerup(p(1, 13, 8, 'mouse'));
  assert.deepEqual(sent, [{kind:'move', dx:2, dy:-2}, {kind:'press'}, {kind:'move', dx:1, dy:0}, {kind:'release'}]);
});

test('cancel/reset clears timers, fractions and late events', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p()); g.pointercancel(); advance(1000); g.pointerup(p()); assert.deepEqual(sent, []);
  g.pointerdown(p()); advance(300); g.reset(); g.reset(); assert.deepEqual(sent, [{kind:'press'}, {kind:'release'}]);
});

test('invalid coordinates are ignored and touch tracking stays bounded', () => {
  const {g, sent, advance, p} = fixture();
  g.pointerdown(p(1, Infinity)); advance(400); assert.deepEqual(sent, []);
  g.pointerdown(p()); g.pointerdown(p(2)); g.pointerdown(p(3)); advance(400);
  g.pointerup(p()); g.pointerup(p(2)); g.pointerup(p(3));
  assert.equal(sent.some(m => m.kind === 'tap' || m.kind === 'press'), false);
});

test('late hold after suspension does not start a drag or trailing tap', () => {
  const {g,sent,advance,p}=fixture(); g.pointerdown(p()); advance(4000); g.pointerup(p());
  assert.deepEqual(sent,[]);
});
