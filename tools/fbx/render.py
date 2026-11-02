# Blender: renderiza el FBX original (con su animacion) de frente y de costado en varios cuadros
import bpy, sys, math
args = sys.argv[sys.argv.index("--") + 1:]
src, out = args[0], args[1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=src)
sc = bpy.context.scene
img = bpy.data.images.load(args[2])
for o in sc.objects:
    if o.type == "MESH":
        for m in o.data.materials:
            if m is None: continue
            m.use_nodes = True
            nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
            tex = nt.nodes.new("ShaderNodeTexImage"); tex.image = img
            if bsdf: nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
sc.render.engine = "CYCLES"; sc.cycles.samples = 12; sc.cycles.device = "CPU"
sc.render.resolution_x = 360; sc.render.resolution_y = 360
w = bpy.data.worlds.new("w"); w.color = (0.5, 0.55, 0.65); sc.world = w
w.use_nodes = True; w.node_tree.nodes["Background"].inputs[1].default_value = 1.5
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam); sc.camera = cam
sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN")); sc.collection.objects.link(sun); sun.rotation_euler = (0.6, 0.3, 0.5)
import mathutils
mesh = [o for o in sc.objects if o.type == "MESH"][0]
for f in [int(x) for x in args[3].split(",")]:
    sc.frame_set(f)
    dg = bpy.context.evaluated_depsgraph_get()
    ev = mesh.evaluated_get(dg)
    pts = [ev.matrix_world @ v.co for v in ev.data.vertices]
    c = sum(pts, mathutils.Vector()) / len(pts)
    r = max((p - c).length for p in pts)
    for name, ang in (("frente", 0), ("costado", 90)):
        a = math.radians(ang)
        # en Blender el modelo importado queda con z arriba; probamos mirando desde -y (frente) y +x (costado)
        cam.location = c + mathutils.Vector((math.sin(a) * r * 3, -math.cos(a) * r * 3, r * 0.5))
        d = c - cam.location
        cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
        sc.render.filepath = f"{out}/f{f:03d}_{name}.png"
        bpy.ops.render.render(write_still=True)
print("LISTO")
