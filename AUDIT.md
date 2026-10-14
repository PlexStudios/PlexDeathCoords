# PlexDeathCoords audit — 2026-10-14

What this repo does: Records each player's latest death and displays or clears it
through a command. The audit assumes one Paper server using a local plugin data
directory, with one record per known player UUID. Shared files between multiple
servers and large-server throughput are outside the supported design and were
not benchmarked.

## Findings fixed

1. **Unrelated YAML values could be lost during saves.** Bukkit's configuration
   conversion removed null entries and treated keys containing dots as paths.
   Added failing preservation tests, then changed storage to safe, plain YAML
   maps using SnakeYAML already supplied by Paper. Null records and unknown
   fields now survive unrelated updates. Clearing a null record also marks the
   removal for persistence. The removed configuration-section/map conversion
   helper is no longer needed. Background serialization does not create or
   serialize Bukkit objects from unknown fields.

2. **Configuration reload performed disk reads on the server thread.** The
   administrator command called the loader synchronously. Reload now reads and
   validates in a Bukkit background task, then swaps the completed settings and
   sends feedback on the server thread. One reload is allowed at a time. Shutdown
   cancels the pending result so a late completion cannot apply settings or send
   a response after disable. Failed loads retain the previous configuration and
   death records. Added tests for delayed completion, duplicate requests, failure
   retention, and changes to display/clear behavior.

3. **Ambiguous configuration could be accepted silently.** Duplicate YAML keys
   were accepted, and null settings could become defaults before validation.
   Configuration now loads as safe plain data and rejects duplicate keys,
   explicit null settings/messages, and wrong types. Missing settings still use
   bundled defaults; existing command names, permission names, placeholders,
   and death-record fields are unchanged.

4. **UUID normalization could overwrite an invalid record.** A valid uppercase
   UUID entry and a lowercase null entry for the same player collided during
   loading. An unrelated player's save then discarded the null entry. Added a
   failing regression for both insertion orders. Storage now retains original
   keys during loading and indexes each player's key variants. Only updating or
   clearing that player consolidates/removes those variants, without scanning
   unrelated players on the server thread. The independent reviewer checked the
   fix and found no remaining issue in that case.

The storage parser's default size limit was also raised to avoid introducing a
new limit on existing death files. A regression test reads a file containing a
four-million-character metadata value. This is a compatibility check, not a
throughput benchmark.

## Review coverage

| File | Result |
| --- | --- |
| `PlexDeathCoords.java` | Death event reads player/world state on the server thread. Cancelled events are ignored. Listener registration occurs only during enable. Reload swaps settings on the server thread; disable cancels pending reload feedback and closes storage. |
| `DeathCommands.java` | Player-only restrictions, use/clear/admin permission checks, alias behavior, usage errors, clearing toggle, reload feedback, and filtered tab completion reviewed and unit tested. |
| `DeathLocation.java` | Canonical UUID parsing, required identity fields, finite coordinates, and non-negative integer timestamps checked. No world loading or lookup is required. |
| `DeathStore.java` | Latest-record replacement, player isolation, case normalization, world identity, malformed/empty/missing files, unknown fields, failed writes, retries, and close/reopen persistence tested. One queued writer; serialization and disk operations run outside the state lock. |
| `Settings.java` | Strict message parsing, escaped dynamic text, precision, independent world/time toggles, clear toggle, default merging, and invalid input tested. Parsing uses local plain-data objects. |
| `plugin.yml` | Parsed by Paper's `PluginDescriptionFile` in tests. Main class, both primary commands, alias, all permission names, and their defaults checked. Permission enforcement lives in the command handler so denial messages remain configurable. |
| `config.yml` | All four options affect behavior. Added configurable `reload-in-progress` message; older configurations receive its bundled default. |
| Build and wrapper | Java 21 toolchain, warnings-as-errors, compile-only Paper dependency, pinned wrapper/checksum, resource version expansion, and JAR contents checked. No new dependency was added or bundled. |
| README, license, changelog, ignore rules | Inspected and updated where behavior changed. |

The record maps intentionally retain saved players, including offline players.
They are persistent data, not a cache of live `Player` or `World` objects. No
extra per-player tasks or listeners are allocated. The writer stops during normal
close, which has an automated lifecycle check. No additional abstraction layer,
database, GUI, or runtime dependency was introduced.

Startup loads data synchronously before registering commands/listeners. Shutdown
waits for storage and performs the final flush to preserve pending data. These
lifecycle operations can take time on a slow filesystem; normal deaths, lookups,
clears, and configuration reload reads perform no disk I/O on the tick thread.
Each storage save still processes the full YAML file, as appropriate for this
small file-based plugin; no throughput claim is made.

## Automated scenarios

| Requested scenario | What was actually tested |
| --- | --- |
| First death | Empty store receives its first record and retrieves it. Actual event dispatch needs a live server. |
| `/lastdeath` without a death | Command handler sends the configured missing-record message. |
| Consecutive deaths | New records replace previous records for the same UUID without affecting another player. |
| Nether/End deaths | Records with independent dimension names and world UUIDs survive close/reopen; output formats saved names without querying loaded worlds. Real dimension travel/death remains manual. |
| Reconnect after death | New test command-sender instances with the same UUID retrieve the saved record after reopening storage. No network reconnect was performed. |
| Restart after death | Store is closed, reopened from the real YAML file, and checked for the latest record. No actual Paper process restart was performed. |
| Clear saved/missing death | Removal and confirmation, repeated clear, persistence, player isolation, permissions, and disabled clearing tested. |
| Console player commands | Player-only response tested. Console administration also tested. |
| Non-admin reload | Denial tested; reload callback is not invoked. |
| Invalid/missing YAML | Missing/empty storage, malformed YAML, wrong structure, invalid records, duplicate keys, null settings, and invalid formatting tested. |
| Configuration reload | Real configuration parsing plus command behavior tested for success/failure, delayed completion, settings retention, new messages, formatting toggles, precision, clearing, and completion suggestions. Live Bukkit scheduler execution remains manual. |

## Validation

`gradlew.bat clean build --console=plain --warning-mode all` passed on JDK 21 with
Gradle 9.1.0. All 45 JUnit tests passed: 22 storage, 10 settings, 13 commands; zero
failures, errors, or skipped tests. The release JAR contains only the five plugin
classes, manifest, plugin/config YAML, and MIT license. Source scanning found no
added `//` comments, TODO implementations, debug printing, or credentials.

## Remaining manual checks and limits

- Start a real Paper server with the JAR, die in each dimension, and run both
  command aliases. Confirm event cancellation with a revival plugin.
- Reconnect and restart the actual server, then verify coordinates and clearing.
- Reload valid/invalid configuration on a running server; confirm main-thread
  scheduler callbacks, one listener registration, and disable during a reload.
- Check runtime logs and behavior on the specific Paper versions being supported.
- Profile if the server has a large historical player file or sustained bursts
  of deaths. No real-world performance, heap, or compatibility benchmark ran.

Forced termination can lose writes still pending in memory. Failed storage keeps
pending data and retries, but cannot guarantee persistence when the filesystem
remains unavailable. Filesystems without atomic replacement use ordinary file
replacement; power-loss behavior was not tested. A running OS file read can
finish after plugin disable, but its cancelled result cannot update settings.
