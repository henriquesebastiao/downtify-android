# Weekly DJ copy editor: bounded UI regression check

Opening a weekly set must not start playback, prepare audio, or save a copy.
Saving requires an explicit action and the user's chosen name. The original
five shelf tests retain those assertions.

## Reproduction and correction, 14 November 2026

The copy editor passed outside a dialog at the existing phone-size qualifier
`w411dp-h914dp`. The same editor inside a default-width `AlertDialog` failed
with `AppNotIdleException`. The original shelf scenario had previously exhausted
the 512 MiB test heap in Robolectric's retained trace sections.

This matches the width-dependent dialog/text-field measurement loop documented
in [Robolectric issue 8460](https://github.com/robolectric/robolectric/issues/8460).
The dialog now uses `DialogProperties(usePlatformDefaultWidth = false)` so its
Material content sizing does not compete with the platform window width.
The existing phone qualifier, input actions, assertions, clock behavior, and
memory limits remain unchanged. No test is skipped and tracing is not disabled.

Two small tests exercise the real copy form both standalone and in the corrected
dialog. Together with the five original shelf cases, all seven passed: zero
failures, errors, or skipped tests. The standalone default-width baseline passed;
the minimal default-width dialog baseline failed before the correction.

Compilation and tests ran as separate guarded invocations, after formatting:

```sh
./gradlew --offline :app:compileDebugUnitTestKotlin --console=plain
./gradlew --offline :app:testDebugUnitTest \
  --tests com.henriquesebastiao.downtify.feature.musicai.WeeklyDjShelfTest \
  --tests com.henriquesebastiao.downtify.feature.musicai.WeeklyDjCopyFormTest \
  --console=plain
```

The successful test invocation took 31.479 seconds; its complete process-group
memory peak was 1,601,343,488 bytes. This is a test-run measurement, not Android
device memory, playback latency, or a cold-build benchmark. The test worker heap
remained 512 MiB and the group limit remained 4 GiB.

## Remaining work

Production cards now navigate to the [weekly DJ playlist page](weekly-dj-playlist.md).
The embedded dialog and its regression checks remain covered. Visual review on
a phone, APK/R8/Lint release gates, Android Auto, audio quality, and long-running
playback qualification remain separate work. Passing these source checks does
not qualify those features or imply a new APK has been delivered.
