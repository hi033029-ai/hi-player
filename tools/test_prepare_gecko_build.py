import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare_gecko_build.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

class PatchTests(unittest.TestCase):
    def test_gecko_libvlc_dependency_generated(self):
        app = Path(__file__).resolve().parents[1] / "app/build.gradle.kts"
        assert app.read_text().count('implementation("org.videolan.android:libvlc-all:3.7.7")') == 1

    def test_context_checked_replacement(self):
        patch = "diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -1,2 +1,2 @@\n one\n-two\n+three\n"
        original = {"a": "one\ntwo\n"}
        self.assertEqual("one\nthree\n", prepare.apply_patch(original, patch)["a"])
        self.assertEqual("one\ntwo\n", original["a"])
    def test_changed_context_fails(self):
        patch = "diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -1 +1 @@\n-old\n+new\n"
        with self.assertRaises(ValueError):
            prepare.apply_patch({"a": "changed\n"}, patch)
    def test_replacement_requires_unique_context(self):
        with self.assertRaises(ValueError):
            prepare.replace_once("a a", "a", "b", "marker")
        with self.assertRaises(ValueError):
            prepare.replace_once("x", "a", "b", "marker")

if __name__ == "__main__":
    unittest.main()
