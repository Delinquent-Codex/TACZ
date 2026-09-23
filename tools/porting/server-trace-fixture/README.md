# Dedicated diagnostic fixture

This separate Forge 26.2 test mod observes `LivingEntityShoot` return values,
server projectile joins/leaves and server gun-hurt/kill events. Its Mixin records the result after the original
method returns; it does not change a result, packet, ammo, entity or event. The
trace is written to `server-trace.jsonl` when the server begins stopping. A
crash before that event may leave no trace. The fixture is never packaged in
the production TACZ JAR.

Build with Java 25:

```powershell
.\gradlew.bat -p tools/porting/server-trace-fixture jar --console=plain
```

`multiplayer_smoke.py --server-trace --firing-probe` adds this mod to the
otherwise ordinary installed dedicated scenario. Always use a new disposable
root and evidence prefix. Reports label these runs as diagnostic because the
server contains both TACZ and this fixture. The paired clients still contain
the ordinary client fixture. The normal TACZ-only runs 23/24 remain the clean
validation of selected firing mechanics.

With `--firing-probe`, the client fixture also sends two deliberately invalid
relative timestamp packets after the selected firing scenes have finished.
The negative value reaches the server's earlier cooldown check; a distant
future value reaches its network-window check. Neither should change ammo or
create a shot. These synthetic packets validate the diagnostic trace and are
not normal operator actions.

The trace records return values and pre-call timing/heat observations. It
cannot recover the exact reason for a rejection in earlier runs that lacked
this fixture, and it does not provide an observed 1.20.1 baseline comparison.

`multiplayer_smoke.py --server-trace --damage-probe` uses a separate controlled
AK-47 player hit. It checks health, client/server TACZ hurt events, damage
source attribution and ammunition. The diagnostic server mod observes only;
the production projectile and event code remain unchanged.

Headshot, lethal follow-up and armor probes use the same read-only trace.
The kill event records its attacker and direct bullet; the hurt event records
whether the normal and piercing sources carry the target `bypasses_armor` tag.
Clean-server repetitions omit this mod entirely and retain client/RCON checks.
