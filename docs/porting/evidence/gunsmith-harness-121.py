"""Place and open a native gunsmith table, then exercise survival crafting."""
import re
import time


RECIPE = 'tacz:gun/glock_17'


def run_gunsmith_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld'
              and before[role]['screen'] == 'null' for role in roles),
          'gunsmith probe begins with both installed clients in game')
    check(before['shooter']['game_mode'] == 'SURVIVAL'
          and before['shooter']['inventory_ammo'].get('tacz:762x39') == 58
          and before['shooter']['inventory_guns'].get('tacz:ak47') == 1,
          'survival shooter begins with one AK-47 and 58 existing rifle rounds')
    check(client('shooter', 'select_slot', slot=1)['snapshot']['selected_slot'] == 1,
          'empty hotbar hand avoids the inherited by-hand recipe filter')
    check('Changed' in command('setblock -2 -60 0 minecraft:stone'),
          'disposable support block prepared for native table placement')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.offhand '
                                       'with tacz:gun_smith_table'),
          'server equips an actual default gunsmith-table item in the offhand')
    placed = client('shooter', 'use_block', x=-2, y=-60, z=0, face='UP', hand='OFF_HAND')
    check(placed['use_result'].startswith(('Success[', 'Consume[')),
          'native client block-use places the default gunsmith table')
    wait(lambda: value if 'tacz:gun_smith_table' in
         (value := command('data get block -2 -59 0')) else None,
         'server has placed default table block entity', timeout=15)
    opened = client('shooter', 'use_block', x=-2, y=-59, z=0, face='UP', hand='OFF_HAND')
    check(opened['use_result'].startswith(('Success[', 'Consume[')),
          'native client block-use opens the server-backed table')

    def screen_ready():
        value = state('shooter')
        return value if ('GunSmithTableScreen' in value['screen']
                         and value.get('gunsmith', {}).get('block_id') == 'tacz:gun_smith_table'
                         and value['gunsmith']['menu_id'] > 0
                         and RECIPE in value['gunsmith']['tab_recipes']) else None

    screen = wait(screen_ready, 'installed gunsmith screen has synced pistol recipe', timeout=10)
    check(state('observer')['screen'] == 'null', 'observer remains in world while table opens')
    check(client('shooter', 'gunsmith_select', recipe=RECIPE)['snapshot']['gunsmith']['selected_recipe'] == RECIPE,
          'native recipe-list button selects the Glock recipe')
    client('shooter', 'gunsmith_craft_click')
    time.sleep(1)
    denied = state('shooter')
    check(denied['inventory_guns'].get('tacz:glock_17', 0) == 0
          and denied['gunsmith']['selected_recipe'] == RECIPE,
          'craft button with no materials creates no Glock')
    server_number('Inventory[{Slot:9b}].count', 58)
    client('shooter', 'craft_packet', recipe=RECIPE, menu_delta=0)
    time.sleep(1)
    check(state('shooter')['inventory_guns'].get('tacz:glock_17', 0) == 0,
          'server rejects a valid recipe packet without materials')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 '
                                       'with minecraft:iron_ingot 16'),
          'server equips the Glock recipe exact iron requirement')
    server_number('Inventory[{Slot:10b}].count', 16)
    client('shooter', 'craft_packet', recipe=RECIPE, menu_delta=1)
    time.sleep(1)
    check(state('shooter')['inventory_guns'].get('tacz:glock_17', 0) == 0,
          'server rejects a craft packet for a stale menu id')
    server_number('Inventory[{Slot:10b}].count', 16)
    check(client('shooter', 'gunsmith_select', recipe=RECIPE)['snapshot']['gunsmith']['selected_recipe'] == RECIPE,
          'native recipe button refreshes client material counts')
    client('shooter', 'gunsmith_craft_click')

    def crafted():
        value = state('shooter')
        return value if value['inventory_guns'].get('tacz:glock_17') == 1 else None

    final = wait(crafted, 'native craft consumes inputs and returns one Glock', timeout=20)
    count_reply = command('clear TaczShooter tacz:modern_kinetic_gun 0')
    match = re.search(r'\b(\d+)\b', count_reply)
    check(match is not None and int(match[1]) == 2,
          'read-only server inventory count has original AK-47 and crafted Glock')
    inventory = command('data get entity TaczShooter Inventory')
    check('minecraft:iron_ingot' not in inventory and 'tacz:glock_17' in inventory,
          'successful survival craft consumes exact iron stack and retains Glock ID')
    client('shooter', 'gunsmith_craft_click')
    time.sleep(1)
    check(state('shooter')['inventory_guns'].get('tacz:glock_17') == 1,
          'second craft click without inputs creates no duplicate output')
    check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
          'native gunsmith close returns client to game')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['players']['TaczShooter']['magazine'] == 29,
          'original loaded AK-47 remains usable after Glock craft')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    check(state('observer')['screen'] == 'null', 'observer remains in game after craft')
    report['gunsmith_scenario'] = {'before': before, 'screen': screen, 'denied': denied,
                                    'crafted': final, 'server_ammo_query': count_reply}
