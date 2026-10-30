import re, sys, pathlib
root, pkg = sys.argv[1], sys.argv[2]
for p in pathlib.Path(root).rglob('*.java'):
    if '/compat/' in str(p):
        continue
    s = o = p.read_text()
    s = re.sub(r'net\.fabricmc\.[a-z0-9_.]*\.([A-Z]\w*)', pkg + r'.\1', s)
    if 'FabricClientCommandSource' in s:
        s = s.replace(pkg + '.FabricClientCommandSource', 'net.minecraft.commands.CommandSourceStack')
        s = s.replace('FabricClientCommandSource', 'CommandSourceStack')
        s = s.replace('.getSource().sendFeedback(', '.getSource().sendSystemMessage(')
        s = s.replace('.getSource().sendError(', '.getSource().sendFailure(')
    if s != o:
        p.write_text(s)
