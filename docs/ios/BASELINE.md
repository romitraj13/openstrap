# Baseline — untouched fork builds and tests green

Card C0. Establishes that the Android side of the fork is green on this server
before any iOS or `shared/` work lands.

- Repository: `https://github.com/romitraj13/openstrap.git` (`origin`), fork of
  `https://github.com/sayed0am/openstrap.git` (`upstream`)
- Branch: `ios`, created from `main`
- Baseline commit (the commit this file's parent is): `19f28656f593f60ea4a51551774dffbf5562e1cc`
  — "Merge pull request #2 from sayed0am/pairing-flow". `git ls-remote upstream main`
  returns the same sha, so the fork's `main` is in sync with upstream at baseline time.
- Recorded: 2026-10-19, Debian 13 (trixie), x86_64

## Command

```sh
source /opt/data/Projects/openstrap/.ios-env.sh
./gradlew :protocol:test :app:testDebugUnitTest :app:assembleDebug
```

Result: `BUILD SUCCESSFUL in 5m 24s`, 51 actionable tasks, exit code 0.
This was the second invocation; the Gradle and Kotlin daemon caches were already
warm from an earlier warm-up run that was cut short by an agent tool timeout
mid-build (it never reached `BUILD SUCCESSFUL`). No source file was changed
between the two runs — `git status` is clean apart from this document.

## Toolchain

| Component | Version | Path |
| --- | --- | --- |
| JDK | Temurin 21.0.12.1+1 LTS | `/opt/data/tools/jdk-21` |
| Gradle (wrapper) | 9.8.1 | `GRADLE_USER_HOME=/opt/data/tools/gradle-home` |
| Kotlin (embedded) | 2.4.10 | — |
| Android SDK | `platforms;android-37.2`, `build-tools;36.0.0`, `platform-tools` 37.0.1 | `/opt/data/tools/android-sdk` |
| Android cmdline-tools | latest (14742923) | `/opt/data/tools/android-sdk/cmdline-tools/latest` |

SDK package set matches `.github/workflows/android.yml` at the baseline commit:
`yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.2" "build-tools;36.0.0"`.

## Tests, per module

Counted from the JUnit XML under `protocol/build/test-results/**/*.xml`
(17 files) and `app/build/test-results/**/*.xml` (20 files). Gradle's `tests`
attribute counts every `testcase`, including skipped ones.

| Module | Suites | Tests | Failed | Errors | Skipped |
| --- | --- | --- | --- | --- | --- |
| `:protocol` | 17 | 111 | 0 | 0 | 1 |
| `:app` (`testDebugUnitTest`) | 20 | 151 | 0 | 0 | 1 |
| total | 37 | 262 | 0 | 0 | 2 |

The two skipped tests are opt-in harnesses guarded by JUnit assumptions, not
failures:

- `org.openstrap.protocol.history.RawDumpReport.report` — skipped unless a raw
  dump fixture is present on disk.
- `org.openstrap.app.data.BacktestTest."replay a database given on the command line"`
  — skipped unless `-Pbacktest=<openstrap.db or backup .zip>` is passed.

Note: the card estimated "about 101 protocol tests". The real count at
`19f2865` is 111; 151 for `:app` matches the estimate.

`assembleDebug` produced `app/build/outputs/apk/debug/app-debug.apk`
(44,662,691 bytes).

## Per-class results

### `:protocol` — `protocol/build/test-results/test`

| Class | Tests | Failed | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `org.openstrap.protocol.ChannelSessionTest` | 5 | 0 | 0 | 0 |
| `org.openstrap.protocol.auth.AuthHandshakeTest` | 3 | 0 | 0 | 0 |
| `org.openstrap.protocol.crypto.B163Test` | 5 | 0 | 0 | 0 |
| `org.openstrap.protocol.firmware.DfuSessionTest` | 8 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.ActivityLayoutTest` | 1 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.ActivityPaddingTest` | 4 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.FetchCodecTest` | 13 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.GdspClientTest` | 6 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.RawDumpReport` | 1 | 0 | 0 | 1 |
| `org.openstrap.protocol.history.RecordParsersTest` | 24 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.VersionReplyTest` | 3 | 0 | 0 | 0 |
| `org.openstrap.protocol.history.WorkoutParsersTest` | 5 | 0 | 0 | 0 |
| `org.openstrap.protocol.services.AlarmsAndSettingsTest` | 9 | 0 | 0 | 0 |
| `org.openstrap.protocol.services.ReadinessCodecTest` | 3 | 0 | 0 | 0 |
| `org.openstrap.protocol.services.RealtimeStepsTest` | 2 | 0 | 0 | 0 |
| `org.openstrap.protocol.services.ServicesTest` | 10 | 0 | 0 | 0 |
| `org.openstrap.protocol.transport.ChunksTest` | 9 | 0 | 0 | 0 |

### `:app` — `app/build/test-results/testDebugUnitTest`

| Class | Tests | Failed | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `org.openstrap.app.data.ActivityTest` | 7 | 0 | 0 | 0 |
| `org.openstrap.app.data.BacktestTest` | 2 | 0 | 0 | 1 |
| `org.openstrap.app.data.BodySignalsTest` | 13 | 0 | 0 | 0 |
| `org.openstrap.app.data.BucketTest` | 1 | 0 | 0 | 0 |
| `org.openstrap.app.data.DayStatTest` | 2 | 0 | 0 | 0 |
| `org.openstrap.app.data.EnergyExpenditureTest` | 5 | 0 | 0 | 0 |
| `org.openstrap.app.data.HistoryTest` | 6 | 0 | 0 | 0 |
| `org.openstrap.app.data.LiveStepsTest` | 4 | 0 | 0 | 0 |
| `org.openstrap.app.data.LoadTest` | 7 | 0 | 0 | 0 |
| `org.openstrap.app.data.RecoveryEngineTest` | 2 | 0 | 0 | 0 |
| `org.openstrap.app.data.RecoveryTest` | 49 | 0 | 0 | 0 |
| `org.openstrap.app.data.SleepHistoryTest` | 7 | 0 | 0 | 0 |
| `org.openstrap.app.data.SleepRegularityTest` | 6 | 0 | 0 | 0 |
| `org.openstrap.app.data.SummariesTest` | 4 | 0 | 0 | 0 |
| `org.openstrap.app.data.Vo2maxTest` | 8 | 0 | 0 | 0 |
| `org.openstrap.app.data.WorkoutsTest` | 4 | 0 | 0 | 0 |
| `org.openstrap.app.data.settings.UnitsTest` | 2 | 0 | 0 | 0 |
| `org.openstrap.app.export.CsvImportTest` | 6 | 0 | 0 | 0 |
| `org.openstrap.app.export.CsvRestoreTest` | 6 | 0 | 0 | 0 |
| `org.openstrap.app.zepp.ZeppCloudTest` | 10 | 0 | 0 | 0 |

## Server-local files (not committed)

- `/opt/data/Projects/openstrap/.ios-env.sh` — exports `JAVA_HOME`, `ANDROID_HOME`,
  `GRADLE_USER_HOME`, `PATH`. Listed in `.git/info/exclude`, so it never reaches a commit.
- `/opt/data/Projects/openstrap/local.properties` — `sdk.dir=/opt/data/tools/android-sdk`
  (already covered by the upstream `.gitignore`).
