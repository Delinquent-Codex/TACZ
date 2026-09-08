"""One-time, asserted migration of the baseline TACZ event declarations/posts.

Uses the verified Forge EventBus 7.0.5 API. Handler body/phase changes are reviewed
separately; this script deliberately does not rewrite cancellation statements.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2] / "src/main/java"
API = ROOT / "com/tacz/guns/api"
for path in [*(API / "event").rglob("*.java"), *(API / "client/event").glob("*.java")]:
    src = path.read_text(encoding="utf-8")
    if "extends Event" not in src:
        continue
    cls = path.stem
    cancellable = "public boolean isCancelable()" in src
    src = src.replace("import net.minecraftforge.eventbus.api.Event;", "import com.tacz.guns.api.event.GunEvent;\nimport net.minecraftforge.eventbus.api.bus.EventBus;")
    src = src.replace("import net.minecraftforge.eventbus.api.Cancelable;\n", "")
    if cancellable or cls == "EntityHurtByGunEvent":
        src = src.replace("import net.minecraftforge.eventbus.api.bus.EventBus;", "import net.minecraftforge.eventbus.api.bus.EventBus;\nimport net.minecraftforge.eventbus.api.bus.CancellableEventBus;\nimport net.minecraftforge.eventbus.api.event.characteristic.Cancellable;")
    src = src.replace("extends Event implements", "extends GunEvent implements")
    src = re.sub(r"    @Override\n    public boolean isCancelable\(\) \{\n        return true;\n    }\n", "", src)
    # This comment described the removed EventBus 6 cancellation override.
    src = src.replace("    /**\n     * 使用注解也可以，但是热重载会导致游戏崩溃\n     */\n", "")
    src = src.replace("    @Cancelable\n", "")
    if cancellable:
        src, n = re.subn(rf"(public class {cls} extends GunEvent implements )", r"\1Cancellable, ", src)
        assert n == 1, path
    bus_type = "CancellableEventBus" if cancellable else "EventBus"
    factory = "cancellableBus" if cancellable else "EventBus.create"
    src, n = re.subn(rf"(public class {cls} [^\n]+\{{)", rf"\1\n    public static final {bus_type}<{cls}> BUS = {factory}({cls}.class);\n", src, count=1)
    assert n == 1, path
    if cls == "EntityHurtByGunEvent":
        src = src.replace("public static class Pre extends EntityHurtByGunEvent {", "public static class Pre extends EntityHurtByGunEvent implements Cancellable {\n        public static final CancellableEventBus<Pre> BUS = cancellableBus(Pre.class);\n")
        src = src.replace("public static class Post extends EntityHurtByGunEvent {", "public static class Post extends EntityHurtByGunEvent {\n        public static final EventBus<Post> BUS = EventBus.create(Post.class);\n")
    if cls in ("RenderLevelBobEvent", "RenderItemInHandBobEvent"):
        for child in ("BobHurt", "BobView"):
            src = src.replace(f"public static class {child} extends {cls} {{", f"public static class {child} extends {cls} {{\n        public static final CancellableEventBus<{child}> BUS = cancellableBus({child}.class);\n")
    path.write_text(src, encoding="utf-8", newline="\n")

for path in (ROOT / "com/tacz/guns/compat/kubejs/events").glob("*.java"):
    src = path.read_text(encoding="utf-8")
    if "import net.minecraftforge.eventbus.api.Event;" in src:
        src = src.replace("import net.minecraftforge.eventbus.api.Event;", "import com.tacz.guns.api.event.GunEvent;")
        src = re.sub(r"\bEvent\b", "GunEvent", src)
        src = src.replace("if (event.isCancelable()) {\n                event.setCanceled(true);", "if (event instanceof net.minecraftforge.eventbus.api.event.characteristic.Cancellable) {\n                event.cancelFromScript();")
        path.write_text(src, encoding="utf-8", newline="\n")

for path in ROOT.rglob("*.java"):
    src = old = path.read_text(encoding="utf-8")
    src = src.replace("import net.minecraftforge.eventbus.api.SubscribeEvent;", "import net.minecraftforge.eventbus.api.listener.SubscribeEvent;")
    src = src.replace("import net.minecraftforge.eventbus.api.EventPriority;", "import net.minecraftforge.eventbus.api.listener.Priority;")
    src = src.replace("EventPriority.", "Priority.")
    src = re.sub(r"bus = Mod.EventBusSubscriber.Bus.MOD, ?", "", src)
    src = re.sub(r", ?bus = Mod.EventBusSubscriber.Bus.MOD", "", src)
    src = src.replace("(bus = Mod.EventBusSubscriber.Bus.MOD)", "")
    src = re.sub(r"MinecraftForge.EVENT_BUS.post\(new ([\w.]+)\(", r"\1.BUS.post(new \1(", src)
    for variable in re.findall(r"MinecraftForge.EVENT_BUS.post\((\w+)\)", src):
        decl = re.search(rf"([\w.]+) {variable} = new ([\w.]+)\(", src)
        assert decl, (path, variable)
        src = src.replace(f"MinecraftForge.EVENT_BUS.post({variable})", f"{decl[2]}.BUS.post({variable})")
    if "MinecraftForge." not in src:
        src = src.replace("import net.minecraftforge.common.MinecraftForge;\n", "")
    if src != old:
        path.write_text(src, encoding="utf-8", newline="\n")
