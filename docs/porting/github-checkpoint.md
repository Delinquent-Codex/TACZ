# GitHub recovery checkpoint — 2026-09-29

The user requested this checkpoint before deleting the local project and moving
to another project. The Forge 26.2 port remains **incomplete**. Read `status.md`,
`feature-parity.md` and `verification.md` before resuming. The new observer-side
third-person capture helper is syntax-checked but has no runtime result.

Source, full commit history, original port prompt, source snapshots, reports,
logs and first-person PNG evidence are on branch `port/forge-26.2` of
[Delinquent-Codex/TACZ](https://github.com/Delinquent-Codex/TACZ/tree/port/forge-26.2).
The baseline branch `1.20.1` remains at
`b43eb84c38e9768d8e73c8b14f0b845669704b38`.

The [published archival prerelease](https://github.com/Delinquent-Codex/TACZ/releases/tag/forge-26.2-checkpoint-2026-09-29)
uses tag `forge-26.2-checkpoint-2026-09-29`. All eight asset sizes and SHA-256
hashes were verified against GitHub. Public downloads of the candidate and Git
bundle match; a fresh clone of the downloaded bundle passes Git integrity checks
and restores source commit `6e26ee27290d59ccd2f644bc4cf0ca3e5135cc0a`.
The branch contains the final upload receipt at
`evidence/github-checkpoint-upload-2026-09-29.json`. Its assets are:

| Asset | Contents |
| --- | --- |
| `tacz-forge-26.2-checkpoint.bundle` | Complete local Git refs/history at the checkpoint; can be cloned without the original local repository |
| `tacz-26.2-1.1.8-hotfix-port.1.jar` | Latest installed/tested production candidate, SHA-256 `b112cabd821c68400077b0c2f85e5c11eab362a5df9b1006a69f10b202d40946` |
| `tacz-26.2-1.1.8-hotfix-port.1-sources.jar` | Current generated source artifact |
| `tacz-26.2-1.1.8-hotfix-port.1-slim.jar` | Intermediate build output; use the full candidate JAR for the documented installation |
| `tacz-port-binaries.zip` | Nine available distinct TACZ/fixture/build binaries, including the initial and current target candidates and untouched baseline outputs; includes a manifest mapping recorded installation paths to binary hashes |
| `tacz-port-runtime-state.zip` | 1,834 available mutable test-world, control, configuration, log and reference-metadata files, with per-file hashes |
| `checkpoint.json` and `SHA256SUMS.txt` | Source revision and SHA-256/size inventory for the recovery assets |

The local directories for multiplayer runs 1–169 had already been removed before
this backup started. Their reports, logs, recorded hashes and archived harness
source remain in Git. Their raw worlds and 53 original binary versions are
unavailable; the binary archive identifies those missing hashes explicitly.
Runs 170/171 and the remaining prepared client/server worlds are preserved.
The pre-pruning audit is historical evidence of 159 checked attempts and 4,325
production inputs, not a promise that every removed installation can now be
reverified. The two retained installations were audited again before upload.

To resume, clone `port/forge-26.2` from GitHub or clone the bundle and select that
branch. Install the pinned Java 25 and Forge 26.2-65.1.0 toolchain described in
`dependencies.md`. Use Java 17 for the separate untouched 1.20.1 baseline.
Recreate official game profiles with their original downloads; the controller
scripts and evidence record the dependency paths and hashes.

The runtime-state ZIP uses `project/` and `reference/` prefixes. Restore
`project/` into the cloned checkout and `reference/` into a sibling
`TACZ-port-reference` directory when those local worlds/configs are needed.
The binary ZIP stores JARs at `sha256/<digest>.jar`; its manifest gives their
original names and installed locations. RCON passwords were replaced with
`CHANGE_ME_ON_RESTORE`; set fresh local passwords before starting a server.

JDK downloads, Gradle caches, Minecraft/Forge libraries and game assets, native
libraries, extracted dependency sources and repeated exported default packs
are reproducible dependencies. They are excluded from the recovery assets.
The default gun pack can be exported from the matching archived TACZ JAR.
The GitHub checkpoint preserves the available project work; it does not upload
the hundreds of gigabytes reported for repeated local game installations.

Code and asset attribution/license notices remain in the repository and JARs.
This is an archival prerelease, with all outstanding full-port and distribution
gates still visible. It is not a completed or approved end-user release.
