import re, sys, pathlib
root, pkg = sys.argv[1], sys.argv[2]
for p in pathlib.Path(root).rglob('*.java'):
    if '/compat/' in str(p):
        continue
    s = o = p.read_text()
    s = re.sub(r'net\.fabricmc\.[a-z0-9_.]*\.([A-Z]\w*)', pkg + r'.\1', s)
    s = s.replace('net.minecraft.network.protocol.common.custom.CustomPacketPayload', pkg + '.CustomPacketPayload')
    s = s.replace('net.minecraft.network.codec.StreamCodec', pkg + '.StreamCodec')
    s = s.replace('net.minecraft.network.codec.ByteBufCodecs', pkg + '.ByteBufCodecs')
    s = s.replace('net.minecraft.client.DeltaTracker', pkg + '.DeltaTracker')
    s = s.replace('RegistryFriendlyByteBuf', 'FriendlyByteBuf')
    s = s.replace('.getTimer().getGameTimeDeltaPartialTick(true)', '.getPartialTick()')
    s = s.replace('.getTimer().getGameTimeDeltaPartialTick(false)', '.getPartialTick()')
    s = re.sub(r'ResourceLocation\.(fromNamespaceAndPath|parse|withDefaultNamespace)\(', 'new ResourceLocation(', s)
    if 'FabricClientCommandSource' in s:
        s = s.replace(pkg + '.FabricClientCommandSource', 'net.minecraft.commands.CommandSourceStack')
        s = s.replace('FabricClientCommandSource', 'CommandSourceStack')
        s = s.replace('.getSource().sendFeedback(', '.getSource().sendSystemMessage(')
        s = s.replace('.getSource().sendError(', '.getSource().sendFailure(')
    if s != o:
        p.write_text(s)
