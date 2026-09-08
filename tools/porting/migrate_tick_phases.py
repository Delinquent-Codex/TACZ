"""Apply the audited 1.20.1 -> EventBus 7 tick phase mapping once."""
from pathlib import Path

root = Path(__file__).resolve().parents[2] / "src/main/java/com/tacz/guns"

def edit(file, replacements):
    path = root / file
    src = path.read_text(encoding="utf-8")
    for before, after in replacements:
        assert src.count(before) == 1, (file, before, src.count(before))
        src = src.replace(before, after)
    path.write_text(src, encoding="utf-8", newline="\n")

def both(file, method, event):
    signature = f"public static void {method}(TickEvent.{event} event)"
    wrappers = "    // The baseline handles both phases; retain its scheduling frequency.\n"
    for phase in ("Pre", "Post"):
        wrappers += f"    @SubscribeEvent\n    public static void {method}(TickEvent.{event}.{phase} event) {{\n        {method}((TickEvent.{event}) event);\n    }}\n\n"
    edit(file, [("    @SubscribeEvent\n    " + signature, wrappers + "    " + signature)])

for file, method, event in [
    ("event/ServerTickEvent.java", "onServerTick", "ServerTickEvent"),
    ("client/event/InventoryEvent.java", "onPlayerChangeSelect", "ClientTickEvent"),
    ("client/event/TickAnimationEvent.java", "tickAnimation", "ClientTickEvent"),
    ("client/event/RenderCrosshairEvent.java", "onRenderTick", "RenderTickEvent"),
    ("client/animation/screen/RefitTransform.java", "tickInterpolation", "RenderTickEvent"),
    ("client/input/AimKey.java", "onAimHoldingPreInput", "ClientTickEvent"),
]:
    both(file, method, event)

edit("client/event/InventoryEvent.java", [("event.phase == TickEvent.Phase.END", "event instanceof TickEvent.ClientTickEvent.Post")])
edit("event/SyncedEntityDataEvent.java", [
    ("onServerTick(TickEvent.ServerTickEvent event)", "onServerTick(TickEvent.ServerTickEvent.Post event)"),
    ("        if (event.side != LogicalSide.SERVER) {\n            return;\n        }\n", ""),
    ("        if (event.phase != TickEvent.Phase.END) {\n            return;\n        }\n", ""),
])
for file, method in [("client/sound/SoundPlayManager.java", "onClientTick"), ("client/input/AimKey.java", "cancelAim")]:
    edit(file, [
        (f"{method}(TickEvent.ClientTickEvent event)", f"{method}(TickEvent.ClientTickEvent.Post event)"),
        ("        if (event.phase != TickEvent.Phase.END) {\n            return;\n        }\n", ""),
    ])
edit("client/event/TickAnimationEvent.java", [
    ("tickAnimation(TickEvent.RenderTickEvent event)", "tickAnimation(TickEvent.RenderTickEvent.Pre event)"),
    ("        if (event.phase == TickEvent.Phase.END) {\n            return;\n        }\n", ""),
    ("event.renderTickTime", "event.timer().getGameTimeDeltaPartialTick(false)"),
])
edit("client/input/ShootKey.java", [
    ("autoShoot(TickEvent.ClientTickEvent event)", "autoShoot(TickEvent.ClientTickEvent.Post event)"),
    ("event.phase != TickEvent.Phase.END || !isInGame()", "!isInGame()"),
])
edit("client/input/ReloadKey.java", [
    ("autoReload(TickEvent.PlayerTickEvent event)", "autoReload(TickEvent.PlayerTickEvent.Pre event)"),
    ("event.phase != TickEvent.Phase.START || event.side != LogicalSide.CLIENT", "event.side() != LogicalSide.CLIENT"),
])
