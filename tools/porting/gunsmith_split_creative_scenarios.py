"""Exercise shipped 100-round output splitting and ingredient-free creative craft."""
import re
import time


RECIPE = 'tacz:ammo/22wmr'
AMMO = 'tacz:22wmr'


def run_gunsmith_split_creative_scenarios(check, command, client, state, wait, server_number, report):
    evidence = report['gunsmith_split_creative_scenario'] = {}
    before = state('shooter')
    check(before['screen'] == 'null' and before['game_mode'] == 'SURVIVAL'
          and before['inventory_ammo'].get('tacz:762x39') == 58
          and before['inventory_guns'].get('tacz:ak47') == 1,
          'survival networking baseline retains loaded gun and rifle reserve')
    check(client('shooter', 'select_slot', slot=1)['snapshot']['selected_slot'] == 1,
          'shooter selects empty native hotbar hand')
    check('Changed' in command('setblock -1 -60 0 minecraft:stone'),
          'disposable ammo-workbench support block prepared')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.offhand with '
                                       'tacz:workbench_a[minecraft:custom_data={BlockId:"tacz:ammo_workbench"}]'),
          'shipped ammo-workbench item equipped in offhand')
    placed = client('shooter', 'use_block', x=-1, y=-60, z=0, face='UP', hand='OFF_HAND')
    check(placed['use_result'].startswith(('Success[', 'Consume[')),
          'native block use places shipped ammo workbench')
    wait(lambda: reply if 'tacz:ammo_workbench' in (reply := command('data get block -1 -59 0')) else None,
         'server stores shipped ammo-workbench BlockId', timeout=10)

    def open_table():
        opened = client('shooter', 'use_block', x=-1, y=-59, z=0, face='UP', hand='OFF_HAND')
        check(opened['use_result'].startswith(('Success[', 'Consume[')),
              'native block use opens server-backed ammo workbench')
        return wait(lambda: v if ('GunSmithTableScreen' in (v := state('shooter'))['screen']
                                  and v.get('gunsmith', {}).get('block_id') == 'tacz:ammo_workbench') else None,
                    'ammo workbench screen and menu synchronize', timeout=10)

    def select_recipe():
        shown = state('shooter')['gunsmith']
        check(shown['filter_by_hand'] is False and 'tacz:pd_cartridges' in shown['category_ids'],
              'pack-defined pistol-caliber category appears without hand filtering')
        category = client('shooter', 'gunsmith_category_click', category='tacz:pd_cartridges')['snapshot']['gunsmith']
        check(category['selected_type'] == 'tacz:pd_cartridges' and RECIPE in category['tab_recipes'],
              'native category button exposes shipped 100-round recipe')
        selected = client('shooter', 'gunsmith_select', recipe=RECIPE)['snapshot']['gunsmith']
        check(selected['selected_recipe'] == RECIPE,
              'native recipe button selects shipped 22wmr output')
        return selected

    def server_ammo(expected):
        reply = command('clear TaczShooter tacz:ammo 0')
        match = re.search(r'Found (\d+) matching item\(s\)', reply)
        check(match is not None and int(match[1]) == expected,
              f'server holds exactly {expected} total TACZ rounds')

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 '
                                       'with minecraft:copper_ingot 10'),
          'server equips exact copper ingredient for 100-round recipe')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.2 '
                                       'with minecraft:gunpowder 2'),
          'server equips exact gunpowder ingredient for 100-round recipe')
    wait(lambda: v if (v := state('shooter'))['inventory_copper'] == 10
         and v['inventory_gunpowder'] == 2 else None,
         'client sees exact survival materials before opening table', timeout=10)
    open_table()
    selected = select_recipe()
    check(selected['material_counts'] == [10, 2],
          'client counts both survival ingredients')

    client('shooter', 'craft_packet', recipe='tacz:gun/glock_17', menu_delta=0)
    time.sleep(0.4)
    check(state('shooter')['inventory_guns'] == {'tacz:ak47': 1}
          and state('shooter')['inventory_copper'] == 10,
          'survival forged gun-recipe packet leaves gun and ammo ingredients unchanged')
    client('shooter', 'gunsmith_craft_click')
    crafted = wait(lambda: v if (v := state('shooter'))['inventory_ammo'].get(AMMO) == 100
                   and v['inventory_copper'] == 0 and v['inventory_gunpowder'] == 0 else None,
                   'survival craft consumes both ingredients and returns 100 rounds', timeout=20)
    server_ammo(158)
    first = command('data get entity TaczShooter Inventory[{Slot:1b}]')
    second = command('data get entity TaczShooter Inventory[{Slot:2b}]')
    counts = sorted(int(match.group(1)) for reply in (first, second)
                    if AMMO in reply for match in [re.search(r'count: (\d+)\b', reply)] if match)
    check(counts == [36, 64], '100-round output occupies legal 64- and 36-round item stacks')
    evidence['survival'] = {'client': crafted, 'stacks': [first, second]}
    check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
          'native gunsmith screen closes before game-mode transition')

    check('Set' in command('gamemode creative TaczShooter'),
          'server changes shooter to native creative mode')
    wait(lambda: v if (v := state('shooter'))['game_mode'] == 'CREATIVE' else None,
         'client receives creative game mode', timeout=10)
    check(client('shooter', 'select_slot', slot=3)['snapshot']['selected_slot'] == 3,
          'empty hotbar hand prevents ammo by-hand filter')
    open_table()
    selected = select_recipe()
    check(selected['material_counts'] == [0, 0],
          'creative recipe still reports zero physical ingredients')
    client('shooter', 'craft_packet', recipe='tacz:gun/glock_17', menu_delta=0)
    time.sleep(0.4)
    check(state('shooter')['inventory_guns'] == {'tacz:ak47': 1},
          'creative mode cannot bypass ammo-workbench gun-recipe filter')
    client('shooter', 'gunsmith_craft_click')
    creative = wait(lambda: v if (v := state('shooter'))['inventory_ammo'].get(AMMO) == 200 else None,
                    'visible creative craft adds 100 rounds without materials', timeout=20)
    check(creative['inventory_copper'] == 0 and creative['inventory_gunpowder'] == 0,
          'creative output leaves physical ingredients absent')
    server_ammo(258)
    evidence['creative'] = creative
    check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
          'native creative gunsmith screen closes')
    check('Set' in command('gamemode survival TaczShooter'),
          'server restores shooter to survival mode')
    wait(lambda: v if (v := state('shooter'))['game_mode'] == 'SURVIVAL' else None,
         'client receives restored survival game mode', timeout=10)
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection restores original gun')
    for role in ('shooter', 'observer'):
        value = wait(lambda: v if (v := state(role))['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                     and v['players']['TaczShooter'].get('magazine') == 29 else None,
                     f'{role} sees original gun after survival and creative crafts', timeout=10)
        check(value['players']['TaczShooter']['chamber'] is True,
              f'{role} sees original chambered round after both crafts')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    evidence['before'] = before
