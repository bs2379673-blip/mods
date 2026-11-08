# Tabla: sprite de 1.20.2+ (textures/gui/sprites/<nombre>.png) -> lugar en las hojas de 1.20.1.
# Uso: python3 sprites.py <salida.json>   (genera el recurso que lee el mod)
import json, sys
T = {}
def put(name, atlas, x, y, w, h): T.setdefault(name, []).append([atlas, x, y, w, h])
I = 'gui/icons.png'; W = 'gui/widgets.png'
# corazones
types = [('', 52, 61, 70, 79), ('poisoned', 88, 97, 106, 115), ('withered', 124, 133, 142, 151)]
for hc, y in (('', 0), ('hardcore_', 45)):
    for t, fu, ha, fb, hb in types:
        pre = (t + '_' if t else '') + hc
        put('hud/heart/' + pre + 'full', I, fu, y, 9, 9)
        put('hud/heart/' + pre + 'half', I, ha, y, 9, 9)
        put('hud/heart/' + pre + 'full_blinking', I, fb, y, 9, 9)
        put('hud/heart/' + pre + 'half_blinking', I, hb, y, 9, 9)
    for t, fu, ha in (('absorbing', 160, 169), ('frozen', 178, 187)):
        put('hud/heart/' + t + '_' + hc + 'full', I, fu, y, 9, 9)
        put('hud/heart/' + t + '_' + hc + 'half', I, ha, y, 9, 9)
    c = 'hud/heart/container' + ('_hardcore' if hc else '')
    put(c, I, 16, y, 9, 9); put(c + '_blinking', I, 25, y, 9, 9)
put('hud/heart/vehicle_container', I, 52, 9, 9, 9)
put('hud/heart/vehicle_full', I, 88, 9, 9, 9)
put('hud/heart/vehicle_half', I, 97, 9, 9, 9)
put('hud/armor_empty', I, 16, 9, 9, 9); put('hud/armor_half', I, 25, 9, 9, 9); put('hud/armor_full', I, 34, 9, 9, 9)
put('hud/air', I, 16, 18, 9, 9); put('hud/air_bursting', I, 25, 18, 9, 9)
put('hud/food_empty', I, 16, 27, 9, 9); put('hud/food_full', I, 52, 27, 9, 9); put('hud/food_half', I, 61, 27, 9, 9)
put('hud/food_empty_hunger', I, 133, 27, 9, 9); put('hud/food_full_hunger', I, 88, 27, 9, 9); put('hud/food_half_hunger', I, 97, 27, 9, 9)
put('hud/crosshair', I, 0, 0, 15, 15)
put('hud/crosshair_attack_indicator_background', I, 36, 94, 16, 4)
put('hud/crosshair_attack_indicator_progress', I, 52, 94, 16, 4)
put('hud/crosshair_attack_indicator_full', I, 68, 94, 16, 16)
put('hud/hotbar_attack_indicator_background', I, 0, 94, 18, 18)
put('hud/hotbar_attack_indicator_progress', I, 18, 94, 18, 18)
put('hud/experience_bar_background', I, 0, 64, 182, 5); put('hud/experience_bar_progress', I, 0, 69, 182, 5)
put('hud/jump_bar_cooldown', I, 0, 74, 182, 5); put('hud/jump_bar_background', I, 0, 84, 182, 5); put('hud/jump_bar_progress', I, 0, 89, 182, 5)
put('hud/hotbar', W, 0, 0, 182, 22); put('hud/hotbar_selection', W, 0, 22, 24, 23)
put('hud/hotbar_offhand_left', W, 24, 22, 29, 24); put('hud/hotbar_offhand_right', W, 53, 22, 29, 24)
put('widget/button_disabled', W, 0, 46, 200, 20); put('widget/button', W, 0, 66, 200, 20); put('widget/button_highlighted', W, 0, 86, 200, 20)
put('widget/slider', 'gui/slider.png', 0, 0, 200, 20); put('widget/slider_highlighted', 'gui/slider.png', 0, 20, 200, 20)
put('widget/slider_handle', 'gui/slider.png', 0, 40, 8, 20); put('widget/slider_handle_highlighted', 'gui/slider.png', 0, 60, 8, 20)
put('widget/checkbox', 'gui/checkbox.png', 0, 0, 20, 20); put('widget/checkbox_highlighted', 'gui/checkbox.png', 20, 0, 20, 20)
put('widget/checkbox_selected', 'gui/checkbox.png', 0, 20, 20, 20); put('widget/checkbox_selected_highlighted', 'gui/checkbox.png', 20, 20, 20, 20)
put('widget/tab_selected', 'gui/tab_button.png', 0, 0, 130, 24); put('widget/tab_selected_highlighted', 'gui/tab_button.png', 0, 24, 130, 24)
put('widget/tab', 'gui/tab_button.png', 0, 48, 130, 24); put('widget/tab_highlighted', 'gui/tab_button.png', 0, 72, 130, 24)
put('hud/effect_background', 'gui/container/inventory.png', 141, 166, 24, 24)
put('hud/effect_background_ambient', 'gui/container/inventory.png', 165, 166, 24, 24)
put('container/inventory/effect_background', 'gui/container/inventory.png', 0, 166, 120, 32)
put('container/inventory/effect_background', 'gui/container/inventory.png', 0, 198, 32, 32)
cols = ['pink', 'blue', 'red', 'green', 'yellow', 'purple', 'white']
for i, c in enumerate(cols):
    put('boss_bar/' + c + '_background', 'gui/bars.png', 0, i * 10, 182, 5)
    put('boss_bar/' + c + '_progress', 'gui/bars.png', 0, i * 10 + 5, 182, 5)
for i, n in enumerate([6, 10, 12, 20]):
    put('boss_bar/notched_%d_background' % n, 'gui/bars.png', 0, 80 + i * 10, 182, 5)
    put('boss_bar/notched_%d_progress' % n, 'gui/bars.png', 0, 85 + i * 10, 182, 5)
if __name__ == '__main__':
    json.dump(T, open(sys.argv[1], 'w'), indent=0)
    print(len(T), 'sprites')
