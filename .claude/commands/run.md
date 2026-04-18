# Cobalt Pair-and-Run

You are driving an end-to-end Cobalt login test. Cobalt is a Java WhatsApp Web reimplementation; you will write/run a Cobalt test that authenticates to WhatsApp's servers via pairing code, and you will drive the user's physical Android phone through the WhatsApp app to enter that code.

The user invokes this command as: `/cobalt-pair-and-run <short description of what the test should do>`

Your output is: a working Cobalt test that logs in successfully, runs the described logic, disconnects cleanly, and prints meaningful results. Do NOT write a plan document. Do NOT commit anything. Just make the test run.

## Preconditions

Before touching anything else, verify all of these. If any fails, stop and tell the user:

1. **MCP server reachable.** `curl -s -o /dev/null -w "%{http_code}" http://localhost:8787/mcp` returns `200`. If not, start it: `cd tooling/web-mcp-server-new && node dist/index.js &`, wait 3 seconds, re-check.
2. **Android device connected.** Call `mcp__whatsapp__web_live_adb_list_devices`. At least one entry with `state=="device"` must exist. If `unauthorized` or none, tell the user to accept the RSA prompt or plug the phone in.
3. **WhatsApp app staged.** The adb tool tolerates several entry states (home, linked-devices screen, code-input screen), but biometric/PIN lock-screens block automation. If an earlier run blocked on `biometric_required` or `pin_required`, tell the user to unlock the device and keep it unlocked.
4. **Phone number is known.** The user must supply a phone number (E.164 digits, no `+`) either in their prompt or when you ask. Do not guess.

## Execution Plan

### Phase 1: Prepare the Cobalt test file

The canonical example is `modules/lib/src/test/java/com/github/auties00/cobalt/example/WebQrLoginExample.java` — a single-file Java 25 source (`void main()`, no class, no package). Author your test in the same style, in the same directory, named after the user's goal (e.g. `SendMessagePairingExample.java`).

Your test MUST:

- Use `.unregistered(phoneNumber, pairingCodeHandler)` (NOT `.unregistered(QrCode.toTerminal())`).
- The `PairingCode` handler MUST print the code with this exact prefix so you can parse it from stdout:
  ```java
  code -> System.out.println("COBALT_PAIRING_CODE=" + code)
  ```
- Print `COBALT_LOGGED_IN` from the `addLoggedInListener` callback so you can detect auth success.
- Print `COBALT_DISCONNECTED=<reason>` from `addDisconnectedListener` so you can detect teardown.
- Print `COBALT_DONE` from the bottom of `main()` after the test logic finishes (if your test does something finite — e.g., send a message and exit). Omit this if the test is supposed to stay connected.
- Implement the logic described in the user's prompt. Keep it minimal.

Skeleton:

```java
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

void main() throws IOException {
    long phoneNumber = <DIGITS>L;
    WhatsAppClient.builder()
            .webClient(WhatsAppStoreFactory.inMemory())
            .createConnection()
            .unregistered(phoneNumber, code -> System.out.println("COBALT_PAIRING_CODE=" + code))
            .addLoggedInListener(api -> System.out.println("COBALT_LOGGED_IN"))
            .addDisconnectedListener((_, reason) -> System.out.println("COBALT_DISCONNECTED=" + reason))
            // ... user's test logic wired as listeners or follow-up calls
            .connect()
            .waitForDisconnection();
    System.out.println("COBALT_DONE");
}
```

### Phase 2: Launch the test in the background

Use `mvn` from the repo root to compile and run, redirecting stdout+stderr to a log file you can tail. Pick a unique log path per run (e.g. `/tmp/cobalt-pair-<epoch>.log`).

```bash
mvn -q -pl modules/lib -am test-compile && \
  mvn -q -pl modules/lib exec:java \
    -Dexec.mainClass="<ExampleClassName>" \
    -Dexec.classpathScope=test \
    > /tmp/cobalt-pair-<epoch>.log 2>&1
```

Run this with `Bash(run_in_background=true)`. Save the background shell id; you need it to poll output and eventually kill it if needed.

If the user's Maven layout doesn't support `exec:java` on JEP 512 sources, fall back to compiling with `javac --enable-preview --release 25` and running with `java --enable-preview --source 25 <path>.java`, still piped into the log file.

### Phase 3: Extract the pairing code

Poll the log file until `COBALT_PAIRING_CODE=` appears, with a 90-second deadline. Use `Grep` on the file (not tail in a sleep loop — just re-run Grep).

```
grep 'COBALT_PAIRING_CODE=' /tmp/cobalt-pair-<epoch>.log
```

Parse the value after `=`. It will look like `ABCD-EFGH` or `ABCDEFGH`.

If the deadline expires with no code:
- Check the log for stack traces (`Grep` for `Exception`). If Cobalt crashed on startup, report the error and stop — the user has a code bug.
- If the log is empty, the Maven command likely failed to compile. Re-run the compile step in the foreground so you can see the error.

### Phase 4: Enter the code on the phone via MCP

Call `mcp__whatsapp__web_live_adb_enter_pairing_code` with:
- `code`: the value from Phase 3 (the tool strips dashes/whitespace internally — passing the raw form is fine).
- `serial`: omit unless the user has multiple devices; otherwise pass the specific one from Phase 1.
- `packageName`: omit (defaults to `com.whatsapp`).
- `stepTimeoutMs`: omit for standard devices; pass `20000` if the user flagged a slow device.

Inspect `result.success` and `result.blockedReason`:

| `blockedReason`                | What it means                                       | Action                                                                      |
|--------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| `null` + `success=true`        | Code typed and Enter pressed                        | Proceed to Phase 5                                                          |
| `no_device`                    | No device surfaced to adb                           | Tell user to reconnect phone. Stop.                                         |
| `biometric_required`           | Fingerprint/face-unlock prompt                      | Tell user to complete biometric, then retry this phase.                     |
| `pin_required`                 | WhatsApp PIN or app-lock PIN                        | Tell user to enter PIN, then retry this phase.                              |
| `max_linked_devices_reached`   | Even after auto-disconnect, 5 slots still full      | Tell user to remove a linked device manually. Stop.                         |
| `linked_devices_not_found`     | Could not navigate to Linked Devices screen         | Retry once. If still failing, check if the app is in a foreign language.    |
| `link_device_button_not_found` | Linked Devices screen reached, button not detected  | Retry once. Otherwise ask user to open "Link a device" manually, then retry.|
| `code_input_not_found`         | Phone-link screen didn't appear                     | Retry once. Pairing window may have expired — restart test if so.           |

Inspect `result.details` for the step-by-step log if you need to reason about failure. Do not retry indefinitely — two attempts max per phase, then stop and hand back to the user with the details.

If the code expired before you got there, the Cobalt side will eventually log an error or hang. In that case: kill the background shell, delete the code file, and restart from Phase 2. Cobalt will emit a fresh code.

### Phase 5: Wait for login

After the phone enters the code, the WhatsApp server routes a notification through WA Web's Noise channel to the Cobalt client. You detect this by polling the log for `COBALT_LOGGED_IN`, deadline 60 seconds.

If it does not appear:
- Check log for `COBALT_DISCONNECTED=`. A disconnect with reason `LOGGED_OUT` or `BANNED` means server rejected — stop and report.
- Check log for Signal/crypto exceptions (`WhatsAppMessageException`, `BadMac`). Report root cause and stop.

### Phase 6: Run the user's logic

The test is now logged in. What happens next depends on what the user asked for:

- **Send-and-exit tests** (e.g. "send a message"): wait for `COBALT_DONE` with a reasonable deadline (30s default, longer if the prompt implies bulk work). Then collect any application output between `COBALT_LOGGED_IN` and `COBALT_DONE` and report it.
- **Observe-and-stay tests** (e.g. "log incoming messages for 60 seconds"): sleep for the stated duration using `Bash(run_in_background=false)`, then proceed to cleanup.

### Phase 7: Cleanup

Always do this, even on success:

1. If the background shell is still running, kill it. Cobalt's `waitForDisconnection()` blocks the process until the socket closes, so there is almost always a running process to kill.
2. Do NOT call `mcp__whatsapp__web_live_stop_session` — you never started one. The MCP's `web_live_*` session is independent of the `web_live_adb_*` adb path you used.
3. Summarize: pairing code used, time to login, what the test did, any errors.

## Rules

- Never print the pairing code anywhere except as part of the parsed value you send to MCP. Do not include it in your user-facing summary.
- Never commit the test file unless the user explicitly asks. Leave it in the example directory for inspection.
- If Cobalt's stdout contains uncaught exceptions at any phase, surface the root cause immediately — do not try to work around it. Cobalt bugs are more valuable than pairing bugs.
- Never retry the full flow more than twice. If the second attempt fails, hand back to the user with the logs.
- Do not use `web_live_start_session` / `web_live_login_with_phone_number` at any point. They drive the MCP's own WA Web browser; you want Cobalt to be the only client talking to WhatsApp's servers.
