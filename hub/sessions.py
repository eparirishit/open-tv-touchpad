"""Local enrollment and opaque sessions; native approval never accepts bearers.

Use one instance per hub boot. Native calls must arrive through the owner's
message queue, never a browser administration route. The server owns cookies,
Origin/CSRF enforcement for authenticated requests, and socket/lease fencing.
"""

from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import secrets
import sqlite3
import stat
import threading
import time
import unicodedata


class EnrollmentRejected(ValueError):
    """A fixed, non-secret enrollment rejection code."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


class SessionRejected(ValueError):
    """A fixed, non-secret authentication rejection code."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


@dataclass(repr=False)
class _Pending:
    token_hash: str
    csrf: str
    label: str
    code: str
    expiry: float
    approved: bool = False


def _check_private(path, *, directory=False):
    info = path.lstat()
    expected_type = stat.S_ISDIR if directory else stat.S_ISREG
    expected_mode = 0o700 if directory else 0o600
    if (not expected_type(info.st_mode) or info.st_uid != os.getuid()
            or stat.S_IMODE(info.st_mode) != expected_mode
            or (not directory and info.st_nlink != 1)):
        raise PermissionError("session storage must be private and owned")
    return info


def _connect(db_path):
    if any(parent.is_symlink() for parent in db_path.absolute().parents):
        raise PermissionError("session storage ancestors must not be symlinks")
    parent = db_path.parent
    try:
        parent.mkdir(mode=0o700)
    except FileExistsError:
        pass
    _check_private(parent, directory=True)
    for suffix in ("", "-journal", "-wal", "-shm"):
        try:
            _check_private(Path(str(db_path) + suffix))
        except FileNotFoundError:
            pass
    try:
        fd = os.open(db_path, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    except FileExistsError:
        fd = os.open(db_path, os.O_RDWR | os.O_NOFOLLOW)
    try:
        info = _check_private(db_path)
        opened = os.fstat(fd)
        if (info.st_dev, info.st_ino) != (opened.st_dev, opened.st_ino):
            raise PermissionError("session storage changed while opening")
        db = sqlite3.connect(db_path, check_same_thread=False)
    finally:
        os.close(fd)
    try:
        # DELETE keeps transient journals beside the private database, not in WAL.
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("""CREATE TABLE IF NOT EXISTS sessions (
            session_id TEXT PRIMARY KEY,
            token_hash TEXT NOT NULL UNIQUE,
            label TEXT NOT NULL,
            expiry REAL NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1))
        )""")
        db.commit()
    except BaseException:
        db.close()
        raise
    return db


def _credential(value):
    return isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{43}", value) is not None


def _digest(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


class Sessions:
    def __init__(self, db_path: Path, *, clock=time.time):
        self._db = _connect(Path(db_path))
        self._clock = clock
        self._lock = threading.RLock()
        self._closed = False
        self._window_expiry = 0
        self._attempts = 0
        self._pending = {}
        self._codes = set()
        self._csrf = {}

    @contextmanager
    def _operation(self):
        with self._lock:
            if self._closed:
                raise RuntimeError("sessions store is closed")
            yield

    def _clear_pending(self):
        self._pending.clear()
        self._codes.clear()
        self._window_expiry = 0

    def _expire_pending(self):
        if self._clock() >= self._window_expiry:
            self._clear_pending()

    def open_window(self) -> None:
        """Native-only action; reopening discards previous pending approvals."""
        with self._operation():
            self._clear_pending()
            self._attempts = 0
            self._window_expiry = self._clock() + 120

    def _active_count(self):
        return self._db.execute(
            "SELECT COUNT(*) FROM sessions WHERE revoked = 0 AND expiry > ?",
            (self._clock(),),
        ).fetchone()[0]

    def request(self, label: str) -> tuple[str, str, str]:
        with self._operation():
            self._expire_pending()
            if not self._window_expiry:
                raise EnrollmentRejected("window_closed")
            if self._attempts >= 4:
                raise EnrollmentRejected("window_limit")
            self._attempts += 1
            if (not isinstance(label, str) or not 1 <= len(label) <= 32
                    or not label.strip() or any(c in "<>" or unicodedata.category(c).startswith("C")
                                               for c in label)):
                raise EnrollmentRejected("invalid_label")
            if self._active_count() >= 8:
                raise EnrollmentRejected("controller_limit")
            token, csrf, pending_id = (secrets.token_urlsafe(32) for _ in range(3))
            number = secrets.randbelow(1000000)
            code = f"{number:06d}"
            while code in self._codes:
                number = (number + 1) % 1000000
                code = f"{number:06d}"
            self._codes.add(code)
            self._pending[pending_id] = _Pending(
                _digest(token), csrf, label, code, self._window_expiry,
            )
            return token, csrf, code

    def list_pending(self) -> list[dict]:
        with self._operation():
            self._expire_pending()
            return [{"pending_id": pending_id, "label": pending.label,
                     "code": pending.code, "expiry": pending.expiry}
                    for pending_id, pending in self._pending.items()]

    def _native_pending(self, pending_id):
        self._expire_pending()
        if not _credential(pending_id) or pending_id not in self._pending:
            raise EnrollmentRejected("invalid_pending")
        return self._pending[pending_id]

    def approve(self, pending_id: str) -> None:
        with self._operation():
            self._native_pending(pending_id).approved = True

    def cancel(self, pending_id: str) -> None:
        with self._operation():
            self._native_pending(pending_id)
            del self._pending[pending_id]

    def complete(self, pending_token: str, csrf: str) -> str | None:
        with self._operation():
            self._expire_pending()
            if not _credential(pending_token) or not _credential(csrf):
                raise EnrollmentRejected("invalid_pending")
            token_hash = _digest(pending_token)
            for pending_id, pending in self._pending.items():
                if secrets.compare_digest(token_hash, pending.token_hash):
                    break
            else:
                raise EnrollmentRejected("invalid_pending")
            if not secrets.compare_digest(csrf, pending.csrf):
                raise EnrollmentRejected("invalid_pending")
            if not pending.approved:
                return None
            # Serialize capacity + insert across connections; consume only after commit.
            with self._db:
                self._db.execute("BEGIN IMMEDIATE")
                if self._clock() >= pending.expiry:
                    raise EnrollmentRejected("window_closed")
                if self._active_count() >= 8:
                    raise EnrollmentRejected("controller_limit")
                token = secrets.token_urlsafe(32)
                session_id = secrets.token_urlsafe(32)
                self._db.execute(
                    "INSERT INTO sessions (session_id, token_hash, label, expiry) VALUES (?, ?, ?, ?)",
                    (session_id, _digest(token), pending.label, self._clock() + 30 * 24 * 60 * 60),
                )
            del self._pending[pending_id]
            return token

    def _check_session(self, row):
        if row is None:
            raise SessionRejected("invalid_session")
        session_id, expiry, revoked = row
        if self._clock() >= expiry:
            self._csrf.pop(session_id, None)
            raise SessionRejected("expired")
        if revoked:
            self._csrf.pop(session_id, None)
            raise SessionRejected("revoked")
        return session_id

    def authenticate(self, token: str) -> str:
        with self._operation():
            if not _credential(token):
                raise SessionRejected("invalid_session")
            return self._check_session(self._db.execute(
                "SELECT session_id, expiry, revoked FROM sessions WHERE token_hash = ?",
                (_digest(token),),
            ).fetchone())

    def csrf(self, session_id: str) -> str:
        with self._operation():
            if not _credential(session_id):
                raise SessionRejected("invalid_session")
            self._check_session(self._db.execute(
                "SELECT session_id, expiry, revoked FROM sessions WHERE session_id = ?",
                (session_id,),
            ).fetchone())
            if session_id not in self._csrf:
                self._csrf[session_id] = secrets.token_urlsafe(32)
            return self._csrf[session_id]

    def list_sessions(self) -> list[dict]:
        with self._operation():
            return [{"id": session_id, "label": label, "expires": expiry}
                    for session_id, label, expiry in self._db.execute(
                        "SELECT session_id, label, expiry FROM sessions WHERE revoked = 0 AND expiry > ? "
                        "ORDER BY rowid", (self._clock(),),
                    )]

    def revoke(self, session_id: str) -> None:
        with self._operation():
            if not _credential(session_id):
                raise SessionRejected("invalid_session")
            with self._db:
                self._db.execute("UPDATE sessions SET revoked = 1 WHERE session_id = ?", (session_id,))
            self._csrf.pop(session_id, None)

    def revoke_all(self) -> None:
        with self._operation():
            with self._db:
                self._db.execute("UPDATE sessions SET revoked = 1 WHERE revoked = 0")
            self._csrf.clear()
            self._clear_pending()

    def expire(self) -> list[str]:
        """Return newly expired IDs for server fencing, even after failed admission."""
        with self._operation():
            self._expire_pending()
            with self._db:
                self._db.execute("BEGIN IMMEDIATE")
                expired = [row[0] for row in self._db.execute(
                    "SELECT session_id FROM sessions WHERE revoked = 0 AND expiry <= ?",
                    (self._clock(),),
                )]
                self._db.executemany("UPDATE sessions SET revoked = 1 WHERE session_id = ?",
                                     [(session_id,) for session_id in expired])
            for session_id in expired:
                self._csrf.pop(session_id, None)
            return expired

    def close(self) -> None:
        with self._lock:
            if not self._closed:
                self._db.close()
                self._closed = True
                self._csrf.clear()
                self._clear_pending()

    def __enter__(self):
        with self._operation():
            return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()
