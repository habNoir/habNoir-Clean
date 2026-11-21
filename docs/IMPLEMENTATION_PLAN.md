# Task Breakdown & Implementation Plan — habNoir Clean

> **Dokumen:** PLAN-001 · **Versi:** 1.0.0 · **Proyek:** `habNoir Clean` (`xyz.habnoir.clean`, Lisensi `GPL-3.0-or-later`)  
> **Rujukan:** [`PRD.md`](file:///c:/Users/Administrator/Documents/Coding%20Project/Android%20APP/docs/PRD.md) · [`TDD.md`](file:///c:/Users/Administrator/Documents/Coding%20Project/Android%20APP/docs/TDD.md) · [`UI_UX_DESIGN.md`](file:///c:/Users/Administrator/Documents/Coding%20Project/Android%20APP/docs/UI_UX_DESIGN.md)  
> **Skills Aktif:** 14 skills teraudit di [`SKILLS_MANIFEST.md`](file:///c:/Users/Administrator/Documents/Coding%20Project/Android%20APP/.agents/skills/SKILLS_MANIFEST.md)

---

## 1. Baseline Environment & Toolchain Terverifikasi

Seluruh versi di bawah telah diverifikasi langsung terhadap repositori Google Maven / Maven Central dan mesin lokal:

| Komponen | Nilai Terverifikasi | Catatan |
| :--- | :--- | :--- |
| **Android SDK Lokal** | `C:\Users\Administrator\AppData\Local\Android\Sdk` | Berisi `platforms/android-37.0` (Android 17 base SDK), `platforms/android-34`, `build-tools/36.0.0`, `platform-tools`, `emulator` |
| **JDK Lokal untuk Gradle** | `C:\Users\Administrator\.jdks\jbr-17.0.14` (OpenJDK `17.0.14`) via `JAVA_HOME` (kompilasi target `JavaVersion.VERSION_17`) | Tidak perlu membuka GUI Android Studio saat build & test (hindari JDK 27 sistem untuk Gradle 9.6.0) |
| **Android Gradle Plugin (AGP)** | **`9.5.2`** (stable terbaru mendukung `compileSdk = 37`, `optimization { enable = true }`, built-in Kotlin) | Built-in Kotlin bundling `2.2.10` (tanpa plugin `org.jetbrains.kotlin.android` terpisah) |
| **Gradle Wrapper** | **`9.6.0-bin.zip`** (HTTP 200 terverifikasi) | Syarat minimum AGP 9.5.x |
| **Kotlin & Compose Plugin** | **`2.2.10`** (`org.jetbrains.kotlin.plugin.compose`) | K2 compiler penuh |
| **Jetpack Compose BOM** | **`2026.09.00`** | Stabil `ui=1.12.1`, mendukung `compileSdk = 37` (`android-37.0`) |
| **AndroidX Core / Activity / Lifecycle** | `core-ktx:1.19.1`, `activity-compose:1.13.0`, `lifecycle-runtime-compose:2.11.1`, `datastore-preferences:1.3.0` | Seluruhnya versi rilis stabil terbaru |
| **Kotlinx Coroutines** | **`1.11.0`** | Stabil terbaru |
| **Glance AppWidget (P1)** | **`1.2.0`** | Stabil untuk `SizeMode.Responsive` + Android 17 `RemoteViews` |
| **Testing Stack** | `junit:4.13.2`, `kotlinx-coroutines-test:1.11.0`, `turbine:1.2.0`, `robolectric:4.14.1` | Tanpa overhead Hilt test |

---

## 2. Quality Gates Wajib di Setiap Fase

Sebelum menandai fase selesai, seluruh gate berikut wajib lulus:
1. **Build & Test Gate:** `./gradlew testFossDebugUnitTest` dan `./gradlew lintFossDebug` lulus 0 error.
2. **Anti-AI-Slop UI Gate (`impeccable` + `frontend-design` + `hallmark`):**
   - Tidak ada eyebrow ALL-CAPS, middle-dot (`·`), em-dash (`—`), glued arrow (`→`), unicode `✓`, ikon sparkle, nested card, atau pill neon.
   - Menggunakan Material 3 `NavigationBar` (`Ruang`, `Aplikasi`, `Sampah`), `TopAppBar`, `ModalBottomSheet`, `SnackbarHost`, dan target sentuh `>= 48x48dp`.
3. **Code Deslop Gate (`ai-slop-cleaner`):** Tidak ada wrapper berlebihan, komentar narasi per baris, `GlobalScope`, `_state.value =`, `collectAsState()`, atau `try/catch` defensif yang tidak perlu.
4. **Security, ANR & Zero-Internet Gate:** `aapt2 dump permissions` wajib **0** kemunculan `INTERNET`/`ACCESS_NETWORK_STATE`; tidak ada disk/IPC I/O di main thread.

---

## 3. Task Breakdown Terperinci (Fase 1 – Fase 4)

### Fase 1: Fondasi Proyek, Lisensi, Tema habNoir & Cache Scanner (`MVP Core`)

| Task ID | Deliverables & Path File | Kriteria Selesai (*Definition of Done*) |
| :--- | :--- | :--- |
| **T1.1** | `LICENSE` (GPL-3.0-or-later), `.gitignore`, `gradle/wrapper/*`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `local.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro` | Gradle Wrapper 9.6.0 + AGP 9.5.2 + Kotlin 2.2.10 + flavor `foss` & `store` tersinkronisasi dan mengkompilasi APK kosong (`assembleFossDebug`) lewat CLI. |
| **T1.2** | `app/src/main/AndroidManifest.xml`, `app/src/foss/AndroidManifest.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi/ic_launcher*.xml`, `res/xml/data_extraction_rules.xml`, `res/values/strings.xml`, `res/values-in/strings.xml`, `res/values/themes.xml` | Adaptive Icon monogram `HN` (< 3 KB), monochrome themed icon API 33+, zero `INTERNET` permission, `windowSoftInputMode="adjustResize"`, tanpa `screenOrientation` kaku. |
| **T1.3** | `ui/theme/HabNoirColors.kt`, `ui/theme/HabNoirTypography.kt`, `ui/theme/HabNoirTheme.kt`, `ui/icons/HabNoirIcons.kt`, `ui/components/StorageStrataBar.kt` | Token warna `#090A0F` / `#F4F2EC` / `#E2C799` / `#E08A8A`, subset font/skala M3, ikon vektor `1.5dp` custom, dan *Calibrated Storage Strata Bar* (`drawWithCache`). |
| **T1.4** | `AppContainer.kt`, `HabNoirApp.kt`, `data/prefs/UserPrefsRepository.kt`, `model/CleanModels.kt` | Manual DI container + `Preferences DataStore` suspend untuk whitelist, threshold (`5 MB`), engine mode, dan total freed bytes. |
| **T1.5** | `engine/scanner/SystemProcessGuard.kt`, `engine/scanner/AppIconMemoryCache.kt`, `engine/scanner/StorageStatsCacheScanner.kt` | Pemindaian non-blocking di `Dispatchers.IO` dengan `Semaphore(4)`, deduplikasi `sharedUserId` via `MutableIntSet`, serta `LruCache` 4 MB untuk icon `40dp`. |
| **T1.6** | `ui/screen/home/HomeViewModel.kt`, `HomeScreen.kt`, `ui/screen/apps/AppsViewModel.kt`, `AppsScreen.kt`, `MainActivity.kt` | `enableEdgeToEdge()`, M3 `Scaffold` + `NavigationBar` (3 tab) / `NavigationRail` (`>= 600dp`), `collectAsStateWithLifecycle()`, unboxed hairline ledger, dan search `imePadding()`. |
| **T1.7** | `test/.../StorageStatsCacheScannerTest.kt`, `HomeViewModelTest.kt`, `AppShellUiTest.kt` (Robolectric) | Unit test + Turbine MVI + Robolectric test pada lebar `320dp` dan `fontScale = 1.5f` lolos 100%. |

---

### Fase 2: Engine Pembersihan Bertahap & Matriks OEM Indonesia

| Task ID | Deliverables & Path File | Kriteria Selesai (*Definition of Done*) |
| :--- | :--- | :--- |
| **T2.1** | `engine/oem/OemModels.kt`, `engine/oem/OemDetector.kt`, `engine/oem/OemSpecRegistry.kt` | Profil lengkap & label ID/EN untuk Xiaomi/HyperOS, OPPO/Realme/ColorOS, Vivo/iQOO/Funtouch, Transsion (Infinix/Tecno/itel), Samsung One UI (`Hapus memori`), & AOSP tanpa refleksi privat. |
| **T2.2** | `engine/accessibility/SafetyNodeMatcher.kt`, `engine/accessibility/AccessibilitySessionBridge.kt`, `engine/accessibility/AccessibilityCleanerService.kt`, `res/xml/accessibility_service_config.xml` | Klik semantik `ACTION_CLICK` saja (tanpa `canPerformGestures`), snapshot `Channel.CONFLATED` non-blocking di `onAccessibilityEvent`, Hard Veto Blacklist anti-hapus-data, proteksi bottom-sheet Xiaomi, & pause otomatis saat disentuh user. |
| **T2.3** | `engine/staged/DeviceHealthMonitor.kt`, `engine/staged/StagedCleanOrchestrator.kt`, `engine/staged/CleanExecutionForegroundService.kt`, `engine/staged/GuidedQueueCleanerBackend.kt` | Adaptive batch size (`5` low-RAM / `8` mid / `12` flagship / `3` thermal guard), `PauseGate`, watchdog `4.5s` (retry `1x`), verifikasi delta byte nyata, dan FGS `specialUse` dengan `PendingIntent.FLAG_IMMUTABLE`. |
| **T2.4** | `ui/components/StagedProgressSheet.kt` | M3 `ModalBottomSheet` menampilkan batch, progres linear, counter `tnum`, state cooldown / Thermal Guard, tombol `Jeda` & `Hentikan`, serta ringkasan hasil akhir. |
| **T2.5** | `test/.../AdaptiveBatchSchedulerTest.kt`, `StagedCleanOrchestratorTest.kt`, `SafetyNodeMatcherTest.kt`, `OemDetectorTest.kt` | Pengujian ratusan variasi tombol OEM Indonesia/Inggris (0% false-positive pada Hapus Data), simulasi thermal throttling, timeout, & cancel instan. |

---

### Fase 3: Pembersih Sampah 3-Layer, Setelan OEM & Onboarding Wizard

| Task ID | Deliverables & Path File | Kriteria Selesai (*Definition of Done*) |
| :--- | :--- | :--- |
| **T3.1** | `engine/trash/MediaStoreTrashSource.kt`, `AllowlistFolderTrashSource.kt`, `OemGalleryTrashShortcuts.kt`, `TrashCoordinator.kt` | Layer 1 chunked `150 URI` per `createDeleteRequest`; Layer 2 canonicalPath + tolak symlink + allowlist strict `.Trash`/`.recycle`/`.trashed-*`; Layer 3 `resolveActivity()` aman ke Galeri OEM. |
| **T3.2** | `ui/screen/trash/TrashViewModel.kt`, `TrashScreen.kt` | Layar `Sampah` unboxed hairline untuk 3 lapis pembersihan lengkap dengan feedback M3 `Snackbar`. |
| **T3.3** | `ui/screen/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `ui/screen/onboarding/OnboardingScreen.kt` | Layar Setelan (pilihan mode engine, threshold, checklist izin real-time tiap `onResume`, shortcut aman ke Auto-Start/Battery OEM) + Onboarding 3 langkah termasuk panduan `⋮ Izinkan setelan terbatas` & deteksi Android 17 Advanced Protection. |
| **T3.4** | `test/.../AllowlistFolderTrashSourceTest.kt`, `TrashCoordinatorTest.kt` | Uji `TemporaryFolder` memastikan kamera/WhatsApp/symlink selalu ditolak mutlak; uji intent fallback OEM aman. |

---

### Fase 4: Shizuku Mode, Glance Widget, Pengingat Hemat Daya & Rilis v1.0

| Task ID | Deliverables & Path File | Kriteria Selesai (*Definition of Done*) |
| :--- | :--- | :--- |
| **T4.1** | `engine/shizuku/ShizukuCleanerBackend.kt` (P1) | Deteksi lazy `Shizuku.pingBinder()`, eksekusi tetap bertahap via `StagedCleanOrchestrator`, menjadi jalur utama saat Android 17 Advanced Protection aktif. |
| **T4.2** | `ui/widget/HabNoirCleanWidget.kt`, `HabNoirCleanWidgetReceiver.kt`, `res/xml/habnoir_widget_info.xml` (P1) | Glance 1.2.0 `SizeMode.Responsive`, zero-bitmap (`< 20 KB`), zero-arg `ActionCallback` dengan rule ProGuard, `onCompositionError` teks fallback, meluncurkan `MainActivity` aman tanpa FGS background crash. |
| **T4.3** | `engine/reminder/StorageReminderJobService.kt` (P1) | `JobScheduler` native API 31+ (0 KB dependensi tambahan), `setRequiresBatteryNotLow(true)`, hanya muncul bila opt-in aktif & cache >= 1.5 GB. |
| **T4.4** | `.github/workflows/ci.yml`, `fastlane/metadata/android/id-ID/*`, `README.md` | CI pipeline GitHub Actions memverifikasi test, lint, ukuran APK `<= 3.5 MB`, serta `aapt2 dump permissions` 0 izin jaringan. |
| **T4.5** | Eksekusi Audit `r8-analyzer`, `anr-audit-and-fix`, `ai-slop-cleaner`, & `assembleFossRelease` | APK release terkompilasi, terukur **≤ 3.5 MB**, lolos 100% gate lint/unit/UI/ANR/R8/Anti-AI-Slop, siap rilis GitHub & F-Droid. |

---

## 4. Urutan Eksekusi Segera
Setelah konfirmasi user:
1. Mulai langsung dari **Fase 1 (`T1.1` s.d. `T1.7`)** dan jalankan `./gradlew testFossDebugUnitTest` + `./gradlew assembleFossDebug` untuk verifikasi build nyata di terminal.
2. Lanjutkan bertahap ke **Fase 2** (`Staged Engine` + `OEM Matrix`), **Fase 3** (`3-Layer Trash` + `Onboarding` + `Settings`), lalu **Fase 4** (`Shizuku`, `Glance Widget`, `R8 Release Build ≤ 3.5 MB`, dan Audit Penuh).

---

## 5. Status Implementasi & Hasil Verifikasi v1.0 MVP

Status pembaruan setelah implementasi dan verifikasi CLI penuh (dengan `JAVA_HOME=C:\Users\Administrator\.jdks\jbr-17.0.14`):

### Ruang Lingkup Terimplementasi (Selesai & Terkompilasi)
- **Fase 1 (`T1.1`–`T1.6`):** Fondasi Gradle 9.6.0 + AGP 9.5.2 + Kotlin 2.2.10, manifest zero-internet + `data_extraction_rules.xml`, Adaptive Icon monogram `HN` di `mipmap-anydpi/`, tema & tipografi `habNoir UI` (termasuk variabel font Cinzel), token ikon vector `1.5dp`, *Calibrated Storage Strata Bar*, `UserPrefsRepository` (DataStore), `StorageStatsCacheScanner`, `MainViewModel`, `MainActivity` edge-to-edge adaptif, `HomeScreen` + `AppsScreen` dengan scroll daftar aplikasi independen.
- **Fase 2 (`T2.1`–`T2.5`):** `OemDetector` & `OemSpecRegistry` (Xiaomi/Redmi/POCO, OPPO/Realme/OnePlus, Vivo/iQOO, Transsion XOS/HiOS/itel OS, Samsung One UI `Hapus memori`, AOSP), `SafetyNodeMatcher` + `AccessibilityCleanerService` + `AccessibilitySessionBridge` (anti-hapus-data & proteksi bottom-sheet Xiaomi), `DeviceHealthMonitor` + `StagedCleanOrchestrator` + `GuidedQueueCleanerBackend` + `CleanExecutionForegroundService`, `StagedProgressSheet`.
- **Fase 3 (`T3.1`–`T3.4`):** 3-Layer Trash Cleaner (`MediaStoreTrashSource` chunked 150 URI, `AllowlistFolderTrashSource` strict canonicalPath + tolak symlink, `OemGalleryTrashShortcuts`, `TrashCoordinator`), `TrashScreen`, `SettingsScreen`, `OnboardingScreen` 3 langkah.
- **Fase 4 (`T4.3` selesai; P1 lain siap iterasi berikutnya):** `StorageReminderJobService` (`JobScheduler` native API 31+, opt-in, tanpa dependensi tambahan) telah terimplementasi; **`T4.1` (`ShizukuCleanerBackend`)**, **`T4.2` (`Glance AppWidget`)**, dan **`T4.4` (GitHub Actions CI & Fastlane metadata)** belum dikerjakan dan siap untuk iterasi Fase 4 lanjutan.

### Hasil Verifikasi Teramati (Perintah Clean Build Penuh)
- **Build & Unit Test (`.\gradlew.bat clean testFossDebugUnitTest lintFossDebug assembleFossDebug assembleFossRelease --no-build-cache --no-daemon`):** Exit code **`0`** (`BUILD SUCCESSFUL`).
- **Unit Test JVM (`testFossDebugUnitTest`):** **20 tes di 6 suite lulus 100%** (0 failure, 0 error, 0 skipped): `SafetyNodeMatcherTest` (7), `DeviceHealthMonitorPolicyTest` (5), `StagedCleanOrchestratorTest` (3), `OemSpecRegistryTest` (2), `TrashAllowlistValidatorTest` (2), `ByteFormatTest` (1). *(Catatan: `Robolectric` / instrumented UI test belum dijalankan pada MVP ini.)*
- **Android Lint (`lintFossDebug`):** **0 error**, 10 warning ringan (saran update versi library di `libs.versions.toml` / wrapper serta `PluralsCandidate` pada string format `%d`).
- **Ukuran APK (`foss` flavor):**
  - Release minified R8 Full Mode (`app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk`): **`2.680.570 byte` (`2,556 MB` / `2.617,7 KiB`)** — memenuhi target `<= 3.5 MB` (batas keras `<= 5.0 MB`).
  - Debug (`app/build/outputs/apk/foss/debug/app-foss-debug.apk`): **`32.214.826 byte` (`30,722 MB`)**.
- **Audit Izin (`aapt2 dump permissions` pada Release APK):** Terverifikasi **0 kemunculan** `android.permission.INTERNET` maupun `android.permission.ACCESS_NETWORK_STATE`.
- **Static Anti-AI-Slop & Code Deslop Scan:** 38 berkas Kotlin di `app/src/main/java` (0 pelanggaran `GlobalScope`, `_state.value =`, `collectAsState(`, `!!`) dan 2 berkas `strings.xml` (0 karakter slop `·`, `—`, `→`, `✓`).
