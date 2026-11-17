"""Malformed Supabase tokens fail before any network call."""

import unittest

from server import verify_supabase_jwt


class SupabaseJwtTest(unittest.TestCase):
    def test_malformed_token(self):
        with self.assertRaises(ValueError):
            verify_supabase_jwt("not-a-jwt")


if __name__ == "__main__":
    unittest.main()
