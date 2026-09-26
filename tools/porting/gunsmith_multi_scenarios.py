"""Craft two-material attachment and ammo recipes at their shipped workbenches."""
import re
import time


ATTACHMENT_RECIPE = 'tacz:attachments/extended_mag_1'
AMMO_RECIPE = 'tacz:ammo/762x39'
ATTACHMENT = 'tacz:extended_mag_1'
AMMO = 'tacz:762x39'


def run_gunsmith_multi_scenarios(check, command, client, state, wait, server_number, report):
    evidence = report['gunsmith_multi_scenario'] = {}
    before = {role: state(role) for role in ('shooter', 'observer')}
    check(before['shooter']['screen'] == 'null' and before['shooter']['game_mode'] == 'SURVIVAL'
          and before['shooter']['inventory_ammo'].get(AMMO) == 58
          and before['shooter']['inventory_guns'].get('tacz:ak47') == 1,
          'survival AK-47 and 58 rifle rounds remain after networking baseline')
    check(client('shooter', 'select_slot', slot=1)['snapshot']['selected_slot'] == 1,
          'empty native hotbar hand exposes unfiltered gunsmith recipes')
    check('Changed' in command('setblock -2 -60 0 minecraft:stone'),
          'disposable attachment-workbench support block prepared')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.offhand with '
                                       'tacz:workbench_c[minecraft:custom_data={BlockId:"tacz:attachment_workbench"}]'),
          'shipped attachment-workbench item equipped in offhand')
    placed = client('shooter', 'use_block', x=-2, y=-60, z=0, face='UP', hand='OFF_HAND')
    check(placed['use_result'].startswith(('Success[', 'Consume[')),
          'native block use places the vertical attachment workbench')
    wait(lambda: reply if 'tacz:attachment_workbench' in (reply := command('data get block -2 -59 0')) else None,
         'attachment workbench stores shipped BlockId on server', timeout=10)
    table = {'x': -2, 'block_id': 'tacz:attachment_workbench'}

    def open_table():
        opened = client('shooter', 'use_block', x=table['x'], y=-59, z=0, face='UP', hand='OFF_HAND')
        check(opened['use_result'].startswith(('Success[', 'Consume[')),
              'native block use opens server-backed gunsmith menu')
        return wait(lambda: v if ('GunSmithTableScreen' in (v := state('shooter'))['screen']
                                  and v.get('gunsmith', {}).get('menu_id', 0) > 0
                                  and v['gunsmith']['block_id'] == table['block_id']) else None,
                    'gunsmith menu and screen synchronized', timeout=10)

    def close_table():
        check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
              'native gunsmith screen closes')

    def select(category, recipe):
        value = state('shooter')
        check(category in value['gunsmith']['category_ids'], f'shipped category {category} is listed')
        selected = client('shooter', 'gunsmith_category_click', category=category)['snapshot']['gunsmith']
        check(selected['selected_type'] == category and recipe in selected['tab_recipes'],
              f'native category page and tab expose {recipe}')
        chosen = client('shooter', 'gunsmith_select', recipe=recipe)['snapshot']['gunsmith']
        check(chosen['selected_recipe'] == recipe, f'native recipe button selects {recipe}')
        return chosen

    def materials(iron=0, gold=0, copper=0, gunpowder=0):
        def matched():
            v = state('shooter')
            return v if (v['inventory_iron'] == iron and v['inventory_gold'] == gold
                         and v['inventory_copper'] == copper and v['inventory_gunpowder'] == gunpowder) else None
        return wait(matched, 'client inventory synchronizes exact gunsmith materials', timeout=10)

    def ammo_count(expected):
        reply = command('clear TaczShooter tacz:ammo 0')
        match = re.search(r'Found (\d+) matching item\(s\)', reply)
        check(match is not None and int(match[1]) == expected,
              f'server holds exactly {expected} TACZ rifle rounds')

    screen = open_table()
    check(state('observer')['screen'] == 'null', 'observer remains in world while table is open')
    selected = select('tacz:extended_mag', ATTACHMENT_RECIPE)
    check(selected['material_counts'] == [0, 0], 'extended magazine initially lacks both ingredients')
    client('shooter', 'gunsmith_craft_click')
    client('shooter', 'craft_packet', recipe=ATTACHMENT_RECIPE, menu_delta=0)
    time.sleep(0.5)
    check(state('shooter')['inventory_attachments'].get(ATTACHMENT, 0) == 0,
          'UI and direct packet cannot craft the attachment without materials')
    close_table()

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with minecraft:iron_ingot 16'),
          'server equips first exact attachment ingredient')
    materials(iron=16)
    open_table()
    selected = select('tacz:extended_mag', ATTACHMENT_RECIPE)
    check(selected['material_counts'] == [16, 0], 'client identifies missing gold ingredient')
    client('shooter', 'gunsmith_craft_click')
    client('shooter', 'craft_packet', recipe=ATTACHMENT_RECIPE, menu_delta=0)
    time.sleep(0.5)
    check(state('shooter')['inventory_attachments'].get(ATTACHMENT, 0) == 0
          and state('shooter')['inventory_iron'] == 16,
          'partial attachment ingredients cannot consume iron or create output')
    close_table()

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.2 with minecraft:gold_ingot 4'),
          'server equips second exact attachment ingredient')
    materials(iron=16, gold=4)
    open_table()
    selected = select('tacz:extended_mag', ATTACHMENT_RECIPE)
    check(selected['material_counts'] == [16, 4], 'client counts both exact attachment ingredients')
    client('shooter', 'gunsmith_craft_click')
    crafted = wait(lambda: v if (v := state('shooter'))['inventory_attachments'].get(ATTACHMENT) == 1
                   and v['inventory_iron'] == 0 and v['inventory_gold'] == 0 else None,
                   'survival craft consumes iron/gold and returns one shipped magazine', timeout=20)
    check(ATTACHMENT in command('data get entity TaczShooter Inventory')
          and 'minecraft:gold_ingot' not in command('data get entity TaczShooter Inventory'),
          'server inventory owns one crafted attachment and no residual gold')
    client('shooter', 'gunsmith_craft_click')
    time.sleep(0.5)
    check(state('shooter')['inventory_attachments'].get(ATTACHMENT) == 1,
          'repeat click without inputs cannot duplicate magazine')
    evidence['attachment'] = {'selected': selected, 'crafted': crafted}
    close_table()
    check(client('shooter', 'select_slot', slot=2)['snapshot']['selected_slot'] == 2,
          'empty native hotbar hand clears the crafted attachment filter')

    check('Changed' in command('setblock -1 -60 0 minecraft:stone'),
          'disposable ammo-workbench support block prepared beside attachment workbench')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.offhand with '
                                       'tacz:workbench_a[minecraft:custom_data={BlockId:"tacz:ammo_workbench"}]'),
          'shipped ammo-workbench item equipped in offhand')
    placed = client('shooter', 'use_block', x=-1, y=-60, z=0, face='UP', hand='OFF_HAND')
    check(placed['use_result'].startswith(('Success[', 'Consume[')),
          'native block use places separate ammo workbench')
    wait(lambda: reply if 'tacz:ammo_workbench' in (reply := command('data get block -1 -59 0')) else None,
         'ammo workbench stores shipped BlockId on server', timeout=10)
    table.update(x=-1, block_id='tacz:ammo_workbench')

    ammo_screen = open_table()
    check(ammo_screen['gunsmith']['filter_by_hand'] is False,
          'empty hand leaves shipped ammo categories visible')
    selected = select('tacz:ifp_rifle_cartridges', AMMO_RECIPE)
    check(selected['material_counts'] == [0, 0], 'rifle-ammo recipe initially lacks both ingredients')
    client('shooter', 'gunsmith_craft_click')
    client('shooter', 'craft_packet', recipe=AMMO_RECIPE, menu_delta=0)
    time.sleep(0.5)
    check(state('shooter')['inventory_ammo'].get(AMMO) == 58,
          'UI and direct packet cannot craft rifle ammo without inputs')
    close_table()

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.3 with minecraft:copper_ingot 15'),
          'server equips first exact ammo ingredient')
    materials(copper=15)
    open_table()
    selected = select('tacz:ifp_rifle_cartridges', AMMO_RECIPE)
    check(selected['material_counts'] == [15, 0], 'client identifies missing gunpowder ingredient')
    client('shooter', 'gunsmith_craft_click')
    client('shooter', 'craft_packet', recipe=AMMO_RECIPE, menu_delta=0)
    time.sleep(0.5)
    check(state('shooter')['inventory_ammo'].get(AMMO) == 58
          and state('shooter')['inventory_copper'] == 15,
          'partial ammo ingredients cannot consume copper or create rounds')
    close_table()

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.4 with minecraft:gunpowder 3'),
          'server equips second exact ammo ingredient')
    materials(copper=15, gunpowder=3)
    open_table()
    selected = select('tacz:ifp_rifle_cartridges', AMMO_RECIPE)
    check(selected['material_counts'] == [15, 3], 'client counts both exact ammo ingredients')
    client('shooter', 'gunsmith_craft_click')
    crafted = wait(lambda: v if (v := state('shooter'))['inventory_ammo'].get(AMMO) == 93
                   and v['inventory_copper'] == 0 and v['inventory_gunpowder'] == 0 else None,
                   'survival craft returns exactly 35 more rifle rounds', timeout=20)
    ammo_count(93)
    client('shooter', 'gunsmith_craft_click')
    time.sleep(0.5)
    check(state('shooter')['inventory_ammo'].get(AMMO) == 93,
          'repeat ammo craft without inputs cannot duplicate rounds')
    evidence['ammo'] = {'selected': selected, 'crafted': crafted}
    close_table()

    check(state('shooter')['inventory_attachments'].get(ATTACHMENT) == 1
          and state('shooter')['inventory_guns'].get('tacz:ak47') == 1,
          'both crafted outputs coexist with the original AK-47')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection restores original AK-47')
    for role in ('shooter', 'observer'):
        value = wait(lambda: v if (v := state(role))['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                     and v['players']['TaczShooter'].get('magazine') == 29 else None,
                     f'{role} sees original AK-47 after craft', timeout=10)
        check(value['players']['TaczShooter']['chamber'] is True,
              f'{role} keeps original chambered round unchanged')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    evidence['before'] = before
    evidence['screen'] = screen
