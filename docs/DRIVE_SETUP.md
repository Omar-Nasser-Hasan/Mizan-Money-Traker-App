# Drive setup

Google Drive backup is planned for M6 and is intentionally not configured in M0.

## P13 configuration required

Supply a Google Cloud project and Android OAuth client matching `app.mizan` and the signing certificate. Configure `drive.appdata` consent in production. The repository has no Drive authorization/uploader implementation; the settings row explains this and keeps encrypted local backup/share available. Do not present Drive as connected or claim uploads succeeded.
