import hashlib
from contextlib import closing
import os
from pathlib import Path
import sqlite3
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

from hub.sessions import EnrollmentRejected, SessionRejected, Sessions


class SessionsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name).resolve() / "state" / "sessions.sqlite3"
        self.now = 1000.0
        self.sessions = self.new_store()

    def new_store(self):
        store = Sessions(self.path, clock=lambda: self.now)
        self.addCleanup(store.close)
        return store

    def enroll(self, store=None, label="Phone"):
        store = store or self.sessions
        store.open_window()
        pending, csrf, code = store.request(label)
        native = store.list_pending()[0]
        store.approve(native["pending_id"])
        token = store.complete(pending, csrf)
        self.assertIsInstance(token, str)
        return token, store.authenticate(token)

    def test_only_exact_native_approval_can_complete_once(self):
        s = self.sessions
        s.open_window()
        token, csrf, code = s.request("Phone")
        native = s.list_pending()[0]
        self.assertEqual(set(native), {"pending_id", "label", "code", "expiry"})
        self.assertEqual(native["label"], "Phone")
        self.assertEqual(native["code"], code)
        self.assertEqual(native["expiry"], 1120)
        self.assertRegex(code, r"^[0-9]{6}$")
        self.assertNotEqual(native["pending_id"], token)
        self.assertIsNone(s.complete(token, csrf))
        with self.assertRaises(EnrollmentRejected):
            s.approve(token)
        s.approve(native["pending_id"])
        with self.assertRaises(EnrollmentRejected):
            s.complete(native["pending_id"], csrf)
        with self.assertRaises(EnrollmentRejected):
            s.complete(token, "wrong")
        session = s.complete(token, csrf)
        self.assertEqual(len(session), 43)  # 32 random bytes, unpadded URL base64.
        session_id = s.authenticate(session)
        self.assertNotEqual(session_id, session)
        self.assertEqual(s.list_sessions(), [{
            "id": session_id, "label": "Phone", "expires": 2593000,
        }])
        self.assertEqual(s.list_pending(), [])
        with self.assertRaises(EnrollmentRejected):
            s.complete(token, csrf)

    def test_copied_label_code_or_csrf_does_not_transfer_approval(self):
        s = self.sessions
        s.open_window()
        first, first_csrf, first_code = s.request("Same name")
        second, second_csrf, second_code = s.request("Same name")
        self.assertNotEqual(first_code, second_code)
        s.approve(s.list_pending()[0]["pending_id"])
        self.assertIsNone(s.complete(second, second_csrf))
        for token, csrf in [(first, second_csrf), (second, first_csrf),
                            (first_code, first_csrf)]:
            with self.assertRaises(EnrollmentRejected):
                s.complete(token, csrf)
        self.assertIsInstance(s.complete(first, first_csrf), str)

    def test_closed_window_and_exact_deadline_reject_enrollment(self):
        s = self.sessions
        with self.assertRaises(EnrollmentRejected):
            s.request("Phone")
        s.open_window()
        token, csrf, _ = s.request("Phone")
        native_id = s.list_pending()[0]["pending_id"]
        s.approve(native_id)
        self.now = 1120
        self.assertEqual(s.list_pending(), [])
        for action in [lambda: s.request("New"), lambda: s.approve(native_id),
                       lambda: s.complete(token, csrf)]:
            with self.assertRaises(EnrollmentRejected):
                action()

    def test_four_attempts_remain_spent_after_cancel_until_native_reopen(self):
        s = self.sessions
        s.open_window()
        for _ in range(4):
            token, csrf, _ = s.request("Phone")
            native_id = s.list_pending()[0]["pending_id"]
            s.cancel(native_id)
            with self.assertRaises(EnrollmentRejected):
                s.complete(token, csrf)
        with self.assertRaises(EnrollmentRejected):
            s.request("Fifth")
        s.open_window()
        self.assertEqual(len(s.request("Fresh")), 3)

    def test_four_live_pending_and_unique_codes_even_on_random_collision(self):
        s = self.sessions
        s.open_window()
        with patch("hub.sessions.secrets.randbelow", return_value=7):
            codes = [s.request("Phone")[2] for _ in range(4)]
        self.assertEqual(len(set(codes)), 4)
        self.assertEqual(len(s.list_pending()), 4)
        with self.assertRaises(EnrollmentRejected):
            s.request("Fifth")

    def test_reopen_discards_previous_pending_and_approval(self):
        s = self.sessions
        s.open_window()
        token, csrf, _ = s.request("Phone")
        native_id = s.list_pending()[0]["pending_id"]
        s.approve(native_id)
        s.open_window()
        for action in [lambda: s.complete(token, csrf), lambda: s.approve(native_id)]:
            with self.assertRaises(EnrollmentRejected):
                action()

    def test_labels_are_bounded_plain_text_without_markup_or_controls(self):
        s = self.sessions
        for label in [None, 5, "", " \t", "x" * 33, "<b>Phone</b>",
                      "a\nb", "a\x00b", "a\x7fb", "a\u202eb", "a\ud800b"]:
            with self.subTest(label=repr(label)):
                s.open_window()
                with self.assertRaises(EnrollmentRejected):
                    s.request(label)
                self.assertEqual(s.list_pending(), [])
        for label in ["x" * 32, "Rishit’s phone", "Phone & tablet", "📱"]:
            s.open_window()
            s.request(label)
            self.assertEqual(s.list_pending()[0]["label"], label)

    def test_invalid_requests_also_spend_window_budget(self):
        s = self.sessions
        s.open_window()
        for _ in range(4):
            with self.assertRaises(EnrollmentRejected):
                s.request("")
        with self.assertRaises(EnrollmentRejected):
            s.request("Valid")

    def test_cancel_requires_native_id_and_cancels_approved_pending(self):
        s = self.sessions
        s.open_window()
        token, csrf, _ = s.request("Phone")
        with self.assertRaises(EnrollmentRejected):
            s.cancel(token)
        native_id = s.list_pending()[0]["pending_id"]
        s.approve(native_id)
        s.cancel(native_id)
        with self.assertRaises(EnrollmentRejected):
            s.complete(token, csrf)

    def test_concurrent_completion_consumes_approval_once(self):
        s = self.sessions
        s.open_window()
        token, csrf, _ = s.request("Phone")
        s.approve(s.list_pending()[0]["pending_id"])
        barrier = threading.Barrier(8)

        def complete(_):
            barrier.wait()
            try:
                return s.complete(token, csrf)
            except EnrollmentRejected:
                return None

        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(complete, range(8)))
        self.assertEqual(sum(isinstance(result, str) for result in results), 1)
        self.assertEqual(len(s.list_sessions()), 1)

    def test_capacity_is_atomic_across_two_database_connections(self):
        s = self.sessions
        for _ in range(7):
            self.enroll()
        other = self.new_store()
        attempts = []
        for store in [s, other]:
            store.open_window()
            token, csrf, _ = store.request("Final slot")
            store.approve(store.list_pending()[0]["pending_id"])
            attempts.append((store, token, csrf))
        barrier = threading.Barrier(2)

        def complete(attempt):
            store, token, csrf = attempt
            barrier.wait()
            try:
                return store.complete(token, csrf)
            except EnrollmentRejected:
                return None

        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(complete, attempts))
        self.assertEqual(sum(isinstance(result, str) for result in results), 1)
        self.assertEqual(len(s.list_sessions()), 8)

    def test_failed_database_write_does_not_consume_approval(self):
        s = self.sessions
        s.open_window()
        token, csrf, _ = s.request("Phone")
        s.approve(s.list_pending()[0]["pending_id"])
        with closing(sqlite3.connect(self.path)) as db:
            with db:
                db.execute("CREATE TRIGGER fail_insert BEFORE INSERT ON sessions "
                           "BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try:
            with self.assertRaises(sqlite3.IntegrityError):
                s.complete(token, csrf)
            self.assertEqual(s.list_sessions(), [])
        finally:
            with closing(sqlite3.connect(self.path)) as db:
                with db:
                    db.execute("DROP TRIGGER fail_insert")
        self.assertIsInstance(s.complete(token, csrf), str)

    def test_eight_controllers_lost_response_and_revoke_recovery(self):
        s = self.sessions
        sessions = [self.enroll() for _ in range(8)]
        s.open_window()
        with self.assertRaises(EnrollmentRejected):
            s.request("Ninth")
        s.revoke(sessions[0][1])
        self.enroll(label="Replacement")
        self.assertEqual(len(s.list_sessions()), 8)

    def test_only_hashes_persist_and_pending_state_is_lost_on_restart(self):
        s = self.sessions
        session_token, session_id = self.enroll()
        session_csrf = s.csrf(session_id)
        s.open_window()
        pending, pending_csrf, code = s.request("Pending secret label")
        native_id = s.list_pending()[0]["pending_id"]
        s.approve(native_id)
        s.close()
        raw = self.path.read_bytes()
        self.assertIn(hashlib.sha256(session_token.encode()).hexdigest().encode(), raw)
        for secret in [session_token, session_csrf, pending, pending_csrf,
                       native_id, "Pending secret label"]:
            self.assertNotIn(secret.encode(), raw)
        # A six-digit code can occur by chance inside a persisted hash.
        with closing(sqlite3.connect(self.path)) as db:
            values = [value for row in db.execute("SELECT * FROM sessions") for value in row]
        self.assertNotIn(code, values)
        reloaded = self.new_store()
        self.assertEqual(reloaded.authenticate(session_token), session_id)
        self.assertNotEqual(reloaded.csrf(session_id), session_csrf)
        self.assertEqual(reloaded.list_pending(), [])
        with self.assertRaises(EnrollmentRejected):
            reloaded.complete(pending, pending_csrf)
        with self.assertRaises(EnrollmentRejected):
            reloaded.request("Closed after restart")

    def test_csrf_is_stable_for_tabs_and_distinct_per_session(self):
        _, first_id = self.enroll()
        _, second_id = self.enroll()
        first = self.sessions.csrf(first_id)
        self.assertEqual(first, self.sessions.csrf(first_id))
        self.assertNotEqual(first, self.sessions.csrf(second_id))
        with self.assertRaises(SessionRejected):
            self.sessions.csrf("unknown")

    def test_absolute_expiry_at_boundary_and_timer_fences_once(self):
        s = self.sessions
        token, session_id = self.enroll()
        s.csrf(session_id)
        self.now = 2592999
        self.assertEqual(s.authenticate(token), session_id)
        self.assertEqual(s.expire(), [])
        self.now = 2593000
        for action in [lambda: s.authenticate(token), lambda: s.csrf(session_id)]:
            with self.assertRaises(SessionRejected) as error:
                action()
            self.assertEqual(error.exception.code, "expired")
        self.assertEqual(s.list_sessions(), [])
        self.assertEqual(s.expire(), [session_id])
        self.assertEqual(s.expire(), [])
        self.enroll(label="After expiry")

    def test_revocation_persists_and_all_revocation_cancels_enrollment(self):
        s = self.sessions
        first, first_id = self.enroll()
        second, second_id = self.enroll()
        s.csrf(first_id)
        s.revoke(first_id)
        s.revoke(first_id)
        with self.assertRaises(SessionRejected) as error:
            s.authenticate(first)
        self.assertEqual(error.exception.code, "revoked")
        with self.assertRaises(SessionRejected):
            s.csrf(first_id)
        self.assertEqual(s.authenticate(second), second_id)
        s.open_window()
        pending, csrf, _ = s.request("Waiting")
        s.approve(s.list_pending()[0]["pending_id"])
        s.revoke_all()
        with self.assertRaises(EnrollmentRejected):
            s.complete(pending, csrf)
        s.close()
        reloaded = self.new_store()
        for token in [first, second]:
            with self.assertRaises(SessionRejected):
                reloaded.authenticate(token)
        self.assertEqual(reloaded.list_sessions(), [])

    def test_invalid_credentials_have_typed_rejections_without_secret_echo(self):
        s = self.sessions
        token, session_id = self.enroll()
        for invalid in [None, [], 5, "", "x" * 10000, "💣", session_id]:
            with self.subTest(kind=type(invalid).__name__):
                with self.assertRaises(SessionRejected):
                    s.authenticate(invalid)
        with self.assertRaises(SessionRejected) as error:
            s.authenticate("private-bearer-value")
        self.assertNotIn("private-bearer-value", str(error.exception))
        self.assertNotIn(token, repr(s))

    def test_owner_only_database_parent_and_context_manager_close(self):
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.path.parent.stat().st_mode & 0o777, 0o700)
        with self.new_store() as s:
            token, _ = self.enroll(s)
        s.close()
        with self.assertRaises((RuntimeError, sqlite3.ProgrammingError)):
            s.authenticate(token)

    def test_rejects_insecure_existing_paths_without_chmod(self):
        self.sessions.close()
        for path, mode in [(self.path, 0o644), (self.path.parent, 0o755)]:
            with self.subTest(path=path.name):
                original = path.stat().st_mode & 0o777
                path.chmod(mode)
                try:
                    with self.assertRaises(PermissionError):
                        self.new_store()
                    self.assertEqual(path.stat().st_mode & 0o777, mode)
                finally:
                    path.chmod(original)

    def test_rejects_symlinked_database_parent_and_sqlite_sidecar(self):
        self.sessions.close()
        target = self.path.with_name("target")
        self.path.rename(target)
        self.path.symlink_to(target)
        with self.assertRaises(PermissionError):
            self.new_store()
        self.path.unlink()
        target.rename(self.path)
        directory = self.path.parent
        moved = directory.with_name("moved")
        directory.rename(moved)
        directory.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(PermissionError):
            self.new_store()
        directory.unlink()
        moved.rename(directory)
        journal = Path(str(self.path) + "-journal")
        journal.symlink_to(self.path)
        with self.assertRaises(PermissionError):
            self.new_store()

    def test_rejects_hardlinked_database(self):
        self.sessions.close()
        os.link(self.path, self.path.with_name("alias"))
        with self.assertRaises(PermissionError):
            self.new_store()

    def test_rejects_symlink_in_ancestor_not_just_immediate_parent(self):
        self.sessions.close()
        alias = self.path.parent.parent / "ancestor-alias"
        alias.symlink_to(self.path.parent.parent, target_is_directory=True)
        with self.assertRaises(PermissionError):
            with Sessions(alias / "state" / self.path.name):
                pass


if __name__ == "__main__":
    unittest.main()
