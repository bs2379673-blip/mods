# Blender: importa un FBX, informa y lo exporta liviano a .glb con todas sus animaciones.
# uso: blender -b -P convertir.py -- entrada.fbx salida.glb triangulos
import bpy, sys, json
args = sys.argv[sys.argv.index("--") + 1:]
src, dst, target = args[0], args[1], int(args[2])
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=src, automatic_bone_orientation=False, ignore_leaf_bones=True)
info = {"objetos": [], "acciones": []}
tris = 0
meshes = [o for o in bpy.data.objects if o.type == "MESH"]
for o in bpy.data.objects:
    d = {"nombre": o.name, "tipo": o.type, "padre": o.parent.name if o.parent else None}
    if o.type == "MESH":
        o.data.calc_loop_triangles()
        d["tris"] = len(o.data.loop_triangles)
        d["grupos"] = len(o.vertex_groups)
        d["materiales"] = [m.name if m else None for m in o.data.materials]
        d["dims"] = list(o.dimensions)
        tris += d["tris"]
    if o.type == "ARMATURE":
        d["huesos"] = [b.name for b in o.data.bones]
    info["objetos"].append(d)
for a in bpy.data.actions:
    info["acciones"].append({"nombre": a.name, "rango": list(a.frame_range), "curvas": len(a.fcurves)})
info["tris_total"] = tris
info["fps"] = bpy.context.scene.render.fps
# achicar
if tris > target:
    for o in meshes:
        m = o.modifiers.new("dec", "DECIMATE")
        m.ratio = target / tris
        bpy.context.view_layer.objects.active = o
        o.select_set(True)
        bpy.ops.object.modifier_apply(modifier="dec")
        o.select_set(False)
after = 0
for o in meshes:
    o.data.calc_loop_triangles()
    after += len(o.data.loop_triangles)
info["tris_despues"] = after
json.dump(info, open(dst + ".json", "w"), indent=1, ensure_ascii=False)
bpy.ops.export_scene.gltf(filepath=dst, export_format="GLB", export_animations=True, export_animation_mode="ACTIONS",
                          export_skins=True, export_all_influences=False, export_image_format="NONE", export_normals=True,
                          export_texcoords=True, export_apply=False, export_yup=True, export_force_sampling=True)
print("LISTO", json.dumps(info)[:3000])
