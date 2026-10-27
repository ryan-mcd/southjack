"""Focused privacy and manifest regression tests; Android lint covers the SDK APIs."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class SecurityPolicy(unittest.TestCase):
    def test_call_logs_use_fixed_messages(self):
        for path in (ROOT / 'app/src/main').rglob('*.kt'):
            source = path.read_text()
            calls = re.findall(r'Log\.[vdiew]\s*\((.*?)\)\s*', source, re.S)
            for call in calls:
                self.assertRegex(call.strip(), r'^TAG,\s*"[^"$]*"$', str(path))

    def test_notification_hides_destination(self):
        source = (ROOT / 'app/src/main/java/com/southjack/bridge/RedirectionService.kt').read_text()
        texts = re.findall(r'\.setContentText\((.*?)\)', source)
        self.assertTrue(texts)
        for text in texts:
            self.assertRegex(text, r'^"[^"$]*"$')
        self.assertIn('.setVisibility(Notification.VISIBILITY_PRIVATE)', source)

    def test_service_is_signature_protected(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        service = manifest.find('./application/service')
        self.assertEqual(service.get(ANDROID + 'permission'),
                         'android.permission.BIND_CALL_REDIRECTION_SERVICE')

    def test_private_app_data_and_transport(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        app = manifest.find('application')
        self.assertEqual(app.get(ANDROID + 'allowBackup'), 'false')
        self.assertEqual(app.get(ANDROID + 'fullBackupContent'), 'false')
        self.assertEqual(app.get(ANDROID + 'dataExtractionRules'), '@xml/data_extraction_rules')
        self.assertEqual(app.get(ANDROID + 'usesCleartextTraffic'), 'false')

    def test_google_voice_intent_is_scoped_and_immutable(self):
        source = (ROOT / 'app/src/main/java/com/southjack/bridge/RedirectionService.kt').read_text()
        self.assertIn('setPackage(Settings.GOOGLE_VOICE_PKG)', source)
        self.assertIn('PendingIntent.FLAG_IMMUTABLE', source)
        self.assertNotIn('PendingIntent.FLAG_MUTABLE', source)

    def test_unused_privileged_permission_is_absent(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        permissions = {item.get(ANDROID + 'name') for item in manifest.findall('uses-permission')}
        self.assertNotIn('android.permission.MANAGE_OWN_CALLS', permissions)


if __name__ == '__main__':
    unittest.main()
