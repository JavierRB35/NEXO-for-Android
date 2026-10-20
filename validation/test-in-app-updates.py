from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
java=(r/'app/src/main/java/com/jruiz/nexo/updates/NexoUpdateManager.java').read_text()
job=(r/'app/src/main/java/com/jruiz/nexo/updates/NexoUpdateJob.java').read_text()
provider=(r/'app/src/main/java/com/jruiz/nexo/updates/NexoUpdateApkProvider.java').read_text()
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
activity=(r/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
html=(r/'app/src/main/assets/index.html').read_text()
script=(r/'app/src/main/assets/app.js').read_text()
workflow=(r/'.github/workflows/android-release.yml').read_text()
publisher=(r/'.github/workflows/publish-public-updates.yml').read_text()
for token in [
 'NexoUpdateManager.fetchRelease()', 'getContentLengthLong()',
 'BuildConfig.VERSION_CODE','apkSha256','apkSize',
 'MessageDigest.getInstance("SHA-256")','hasOfficialSigner(pkg)',
 'isInstalledSignatureOfficial(activity)','PackageManager.GET_SIGNING_CERTIFICATES',
 'REQUEST_INSTALL_PACKAGES','FLAG_GRANT_READ_URI_PERMISSION'
]:
 assert token in '\n'.join((java,job,manifest)),token
assert 'android:name=".updates.NexoUpdateJob"' in manifest
assert 'android:name=".updates.NexoUpdateApkProvider"' in manifest
assert 'android:grantUriPermissions="true"' in manifest
assert 'ParcelFileDescriptor.MODE_READ_ONLY' in provider
assert 'NexoUpdateJob.schedule(this)' in activity
assert '@JavascriptInterface public void checkForUpdates()' in activity
assert '@JavascriptInterface public String getUpdateStatus()' in activity
assert 'onUpdateStatusChanged(raw)' in script
assert 'id="checkUpdatesSettings"' in html and 'id="checkUpdatesAbout"' in html
assert 'uses: ./.github/workflows/publish-public-updates.yml' in workflow
assert 'NEXO_UPDATE_PUBLISH_TOKEN' in publisher
assert 'gh release create' in publisher and 'sha256' in publisher.lower()
assert 'JavierRB35/nexo-android-updates/' in java
assert not re.search(r'(ghp_|github_pat_)',java)
print('Contrato de actualizaciones de Nexo: OK')
