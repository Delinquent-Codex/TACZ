"""One-time migration to inspected Forge 26.2 SimpleChannel/context APIs."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2] / "src/main/java/com/tacz/guns"
path = root / "network/NetworkHandler.java"
src = path.read_text(encoding="utf-8-sig")
src = src.replace("import net.minecraftforge.network.HandshakeHandler;", "import net.minecraftforge.network.ChannelBuilder;\nimport net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;\nimport net.minecraftforge.eventbus.api.listener.Priority;")
src = src.replace("import net.minecraftforge.network.simple.SimpleChannel;", "import net.minecraftforge.network.SimpleChannel;")
for unused in ["net.minecraftforge.network.NetworkRegistry", "org.apache.commons.lang3.tuple.Pair", "javax.annotation.Nullable", "java.lang.reflect.Constructor", "java.util.List", "java.util.Optional", "java.util.function.Function"]:
    src = src.replace(f"import {unused};\n", "")
src = src.replace('private static final String VERSION = "1.0.5";', '// Target-only protocol: exact version required on both ends.\n    public static final int VERSION = 262001;')
src, n = re.subn(r'NetworkRegistry.newSimpleChannel\((Identifier.fromNamespaceAndPath\(GunMod.MOD_ID, "\w+"\)),\s*\(\) -> VERSION, it -> it.equals\(VERSION\), it -> it.equals\(VERSION\)\)', r'ChannelBuilder.named(\1).networkProtocolVersion(VERSION).simpleChannel()', src)
assert n == 2
src = src.replace("    private static final AtomicInteger HANDSHAKE_ID_COUNT = new AtomicInteger(1);\n", "")
src, n = re.subn(r'CHANNEL.registerMessage\(ID_COUNT.getAndIncrement\(\), (\w+)\.class, \1::encode, \1::decode, \1::handle,\s*Optional.of\((NetworkDirection.\w+)\)\);', r'CHANNEL.messageBuilder(\1.class, ID_COUNT.getAndIncrement(), \2)\n                .encoder(\1::encode).decoder(\1::decode).consumerNetworkThread(\1::handle).add();', src)
assert n == 31, n
src = src.replace("        registerAcknowledge();\n        registerHandshakeMessage(ServerMessageSyncedEntityDataMapping.class, null);", """        CHANNEL.build();
        registerConfigurationMessages();
        GatherLoginConfigurationTasksEvent.BUS.addListener(Priority.LOWEST,
                event -> event.addTask(new MappingConfigurationTask()));""")
start = src.index("    public static void registerAcknowledge()")
end = src.index("    public static void sendToClientPlayer(")
src = src[:start] + """    private static void registerConfigurationMessages() {
        HANDSHAKE_CHANNEL.messageBuilder(Acknowledge.class, 1, NetworkDirection.CONFIGURATION_TO_SERVER)
                .encoder(Acknowledge::encode).decoder(Acknowledge::decode).consumerNetworkThread(Acknowledge::handle).add();
        HANDSHAKE_CHANNEL.messageBuilder(ServerMessageSyncedEntityDataMapping.class, 2, NetworkDirection.CONFIGURATION_TO_CLIENT)
                .encoder(ServerMessageSyncedEntityDataMapping::encode).decoder(ServerMessageSyncedEntityDataMapping::decode)
                .consumerNetworkThread(ServerMessageSyncedEntityDataMapping::handle).add();
        HANDSHAKE_CHANNEL.build();
    }

    public static void sendToServer(Object message) {
        CHANNEL.send(message, PacketDistributor.SERVER.noArg());
    }

""" + src[end:]
path.write_text(src, encoding="utf-8", newline="\n")

for path in (root / "network").rglob("*.java"):
    src = old = path.read_text(encoding="utf-8-sig")
    if "NetworkEvent" in src:
        src = src.replace("import net.minecraftforge.network.NetworkEvent;", "import net.minecraftforge.event.network.CustomPayloadEvent;")
        src = src.replace("Supplier<NetworkEvent.Context> contextSupplier", "CustomPayloadEvent.Context context")
        src = src.replace("        NetworkEvent.Context context = contextSupplier.get();\n", "")
        src = src.replace("Supplier<NetworkEvent.Context> supplier", "CustomPayloadEvent.Context context")
        src = src.replace("context.getDirection().getReceptionSide().isServer()", "context.isServerSide()")
        src = src.replace("context.getDirection().getReceptionSide().isClient()", "context.isClientSide()")
        src = src.replace("context.getNetworkManager()", "context.getConnection()")
        if "Supplier<" not in src:
            src = src.replace("import java.util.function.Supplier;\n", "")
    if "buf.readItem()" in src:
        src = src.replace("FriendlyByteBuf", "RegistryFriendlyByteBuf")
        src = src.replace("buf.readItem()", "ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)")
        src = re.sub(r"buf.writeItem\(([^;]+)\);", r"ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, \1);", src)
        src = src.replace("buf.writeItemStack(message.gun, true);", "ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, message.gun);")
    if src != old:
        path.write_text(src, encoding="utf-8", newline="\n")

for path in root.rglob("*.java"):
    src = old = path.read_text(encoding="utf-8-sig")
    src = src.replace("NetworkHandler.CHANNEL.sendToServer(", "NetworkHandler.sendToServer(")
    src = src.replace(".with(() -> ", ".with(")
    # Swap the two arguments of the old target-first send calls. New message-first
    # calls already start with a message and must be left alone.
    pattern = re.compile(r"\b(?:NetworkHandler\.)?CHANNEL.send\(PacketDistributor\.")
    for match in reversed(list(pattern.finditer(src))):
        begin = src.index("(", match.start()) + 1
        depth, comma, end = 0, None, None
        for i in range(begin, len(src)):
            char = src[i]
            if char == "(": depth += 1
            elif char == ")":
                if depth == 0:
                    end = i
                    break
                depth -= 1
            elif char == "," and depth == 0:
                assert comma is None, (path, src[begin:i])
                comma = i
        assert comma is not None and end is not None, path
        src = src[:begin] + src[comma + 1:end].strip() + ", " + src[begin:comma] + src[end:]
    if src != old:
        path.write_text(src, encoding="utf-8", newline="\n")
