# Quick diagnostics and private reports

Open Settings → About → Diagnostics, then choose **Run quick checks**.
Nothing runs until that button is pressed. The checks do not replace, start,
stop or edit a playback queue.

- UI: ten main-thread scheduling samples, 100 ms apart. A sampled delay above
  250 ms is shown as SLOW. This is a short observation, not audio qualification.
- Storage: write/read/checksum a temporary 1 MiB file in the app cache, then
  remove only that temporary file. The selected music folder is not modified.
- Server: probe the currently paired server's information endpoint. No library
  scan, download or DJ session is requested. Each check has a five-second
  coroutine timeout; blocking operating-system I/O may take longer to return.

The report records elapsed times, Java/native heap estimates, process PSS and
CPU time, including CPU/wall time during the checks. These measurements are
not interchangeable or a total-memory cap. A bounded snapshot of the app's
main/playback/dispatcher threads adds technical frames without their names.
On Android 11+, available historical exits for this app add technical stack
frames, including available ANR traces. Missing OS traces are reported as such.

**Export diagnostic report** opens Android's document picker. The user selects
the destination and decides whether to send the resulting text file. There is
no automatic upload, telemetry endpoint or additional broad storage permission.

The report omits account fields, authentication material, server addresses,
track titles, exception descriptions and arbitrary thread names. Only technical
Java frames from this app's process dump are retained, with restricted source
locations. Trace input, frame count and final report length are bounded; the
final report is limited to 64 Ki characters. Review an exported file before
sharing it. Physical listening and real-time playback remain separate tests.
