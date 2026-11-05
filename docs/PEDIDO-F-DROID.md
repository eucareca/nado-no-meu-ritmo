# Nado no meu ritmo

Pedido oficial enviado em 05/11/2026: https://gitlab.com/fdroid/rfp/-/work_items/4743 . Status verificado no envio: aberto, aguardando triagem.

A referência técnica abaixo foi preparada antes do envio; a versão efetivamente submetida consta na issue acima.

- Application ID: `br.com.tabinformatica.nadonomeritmo`
- Version: 1.10.0 / versionCode 11
- Source repository: https://github.com/eucareca/nado-no-meu-ritmo
- Release tag: `v1.10.0`
- License: Apache-2.0 (LICENSE and NOTICE in repository)
- Author/maintainer: Tiago Assis Brasil — tiago@tabinformatica.com.br
- Build recipe: `fdroid/br.com.tabinformatica.nadonomeritmo.yml`
- Store metadata, icon, screenshots and changelogs: `fastlane/metadata/android/en-US/` and `pt-BR/`
- Privacy policy: `docs/PRIVACY.md`

## Description

A local swimming diary in Brazilian Portuguese. It records sessions, effort, weight and extra activities; includes editable guided workouts, weekly planning, local reminders, attendance, themes and local photo compositions. Apache-2.0 source, no advertisements, trackers, proprietary runtime SDKs or INTERNET permission. The author requests and consents to inclusion in F-Droid.

Optional integrations use Android framework APIs: read-only Health Connect imports (Android 14+), Calendar Provider access to an app-created weekly series, and encrypted automatic backups to a user-created document through the Storage Access Framework. All are off until user authorization; local diary functionality remains available without them. No own OAuth client, backend or API credentials are required.

The UI suggests Google Calendar/Drive and Mi Fitness/Samsung Health, so the recipe conservatively declares NonFreeNet and NonFreeAdd. Backups also work with other compatible document providers or a local document. Photos are shared only after user action. The APK contains no Google Play Services or Firebase libraries.

## Build

OpenJDK 17, Gradle 8.11.1, AGP 8.9.2, compileSdk 36, Build Tools 35.0.0. Synchronize web assets with `scripts/sync-android-web.sh`, then build the `android/` subdirectory with `gradle assembleRelease`. No signing key is committed; F-Droid can build and sign its own distribution. The production application ID is unchanged; `.preview` is used only for the separate debug test APK.

The GitHub workflow tests local storage, encrypted backups/restoration, workouts, optional health import, schedules/calendar conflicts, themes/photo export and automatic backup bridge with synthetic data. It compiles debug/release and runs lint. Physical-device validation of provider uploads, permissions and integrations is still pending. No claim of independent security audit or guaranteed F-Droid acceptance is made.

The recipe and source are ready for packager review. Please advise of required metadata or build-environment adjustments. The confirmed packaging request is linked at the top; acceptance and publication remain pending review.
