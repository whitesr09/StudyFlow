# StudyFlow permanent APK signing

The private recovery ZIP is provided separately. Keep it private and backed up. Never upload its contents to a public repository or send them in a chat message.

One-time phone setup:
1. Open https://github.com/whitesr09/StudyFlow/settings/secrets/actions
2. Add repository secret STUDYFLOW_KEYSTORE_BASE64 using the entire text in STUDYFLOW_KEYSTORE_BASE64.txt.
3. Add repository secret STUDYFLOW_KEYSTORE_PASSWORD using the text in STUDYFLOW_KEYSTORE_PASSWORD.txt.
4. Once the updated Android workflow is available, open Actions > Build StudyFlow APK > Run workflow, select the updated branch and run it.
5. Download StudyFlow-0.7.0-signed-release after all checks pass. Future versions must use this same key and a higher version code.

The existing 0.6 debug key was not retained by its build workflow. This new key cannot guarantee an in-place update over that APK. Recover the previous key if available; otherwise preserve/export existing study files and data before any migration. Do not uninstall the old app before preserving data.

Alias: studyflow-release. Format: PKCS12. Application ID: app.studyflow.
The repository pins the public certificate SHA-256 and fails if a different key is supplied. No secrets belong in source control.
