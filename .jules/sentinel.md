# Sentinel's Journal

## 2026-02-19 - Unintended Exported Activity
**Vulnerability:** `VideoEditingActivity` was exported (`android:exported="true"`) in `AndroidManifest.xml` without an intent filter. This allowed any application on the device to launch this internal activity directly, bypassing the `MainActivity` and potentially leading to unexpected states or crashes if the activity expects specific initialization parameters (like `VIDEO_URI` extra). While the activity handles null URIs gracefully, exporting internal components increases the attack surface unnecessarily.

**Learning:** Developers often copy-paste activity declarations or rely on IDE defaults which might set `exported="true"`. In this case, it might have been intended for testing or leftover from a different configuration. Explicitly defining export status is crucial, especially for activities that are not entry points.

**Prevention:** Always set `android:exported="false"` for activities that are not intended to be launched by other apps or the system (via implicit intents). Use tools like `lint` to catch these issues, although `lint` allows exported activities without intent filters (as they are valid but often insecure/unintended). Review `AndroidManifest.xml` carefully during security audits.
