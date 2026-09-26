"""Verify gunsmith output spill and pickup with every inventory slot occupied."""


RECIPE = 'tacz:attachments/extended_mag_1'
ATTACHMENT = 'tacz:extended_mag_1'


def run_gunsmith_full_scenarios(check, command, client, state, wait, server_number, report):
    evidence = report['gunsmith_full_scenario'] = {}
    before = state('shooter')
    check(before['screen'] == 'null' and before['game_mode'] == 'SURVIVAL'
          and before['inventory_ammo'].get('tacz:762x39') == 58
          and before['inventory_guns'].get('tacz:ak47') == 1,
          'loaded survival gun and reserve survive networking baseline')
    check(client('shooter', 'select_slot', slot=1)['snapshot']['selected_slot'] == 1,
          'shooter selects a free main-hand slot for native workbench placement')
    check('Changed' in command('setblock -2 -60 0 minecraft:stone'),
          'disposable attachment-workbench support block prepared')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.offhand with '
                                       'tacz:workbench_c[minecraft:custom_data={BlockId:"tacz:attachment_workbench"}]'),
          'shipped attachment-workbench item equipped in offhand')
    placed = client('shooter', 'use_block', x=-2, y=-60, z=0, face='UP', hand='OFF_HAND')
    check(placed['use_result'].startswith(('Success[', 'Consume[')),
          'native block use places the attachment workbench')
    wait(lambda: reply if 'tacz:attachment_workbench' in (reply := command('data get block -2 -59 0')) else None,
         'server stores the attachment-workbench BlockId', timeout=10)

    for slot in range(1, 9):
        check('Replaced a slot' in command(f'item replace entity TaczShooter hotbar.{slot} '
                                           'with minecraft:stone 64'),
              f'fill hotbar slot {slot}')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 '
                                       'with minecraft:iron_ingot 64'),
          'full iron stack occupies first ingredient slot')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.2 '
                                       'with minecraft:gold_ingot 64'),
          'full gold stack occupies second ingredient slot')
    for slot in range(3, 27):
        check('Replaced a slot' in command(f'item replace entity TaczShooter inventory.{slot} '
                                           'with minecraft:stone 64'),
              f'fill main inventory slot {slot}')
    full = wait(lambda: v if (v := state('shooter'))['free_slot'] == -1
                and v['inventory_iron'] == 64 and v['inventory_gold'] == 64 else None,
                'client sees full inventory and both full ingredient stacks', timeout=10)
    check('No entity was found' in command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True),
        'no item entity is present before craft')

    opened = client('shooter', 'use_block', x=-2, y=-59, z=0, face='UP', hand='OFF_HAND')
    check(opened['use_result'].startswith(('Success[', 'Consume[')),
          'native block use opens server-backed workbench')
    screen = wait(lambda: v if ('GunSmithTableScreen' in (v := state('shooter'))['screen']
                                and v.get('gunsmith', {}).get('block_id') == 'tacz:attachment_workbench') else None,
                  'attachment workbench screen synchronizes with full inventory', timeout=10)
    check(screen['gunsmith']['filter_by_hand'] is False
          and 'tacz:extended_mag' in screen['gunsmith']['category_ids'],
          'stone main hand leaves attachment category visible')
    selected = client('shooter', 'gunsmith_category_click', category='tacz:extended_mag')['snapshot']['gunsmith']
    check(RECIPE in selected['tab_recipes'], 'shipped extended-magazine recipe appears in category')
    selected = client('shooter', 'gunsmith_select', recipe=RECIPE)['snapshot']['gunsmith']
    check(selected['selected_recipe'] == RECIPE and selected['material_counts'] == [64, 64],
          'native recipe button counts full ingredient stacks')

    client('shooter', 'gunsmith_craft_click')
    consumed = wait(lambda: v if (v := state('shooter'))['inventory_iron'] == 48
                    and v['inventory_gold'] == 60 and v['free_slot'] == -1 else None,
                    'full-inventory craft consumes exact ingredient quantities', timeout=20)
    check(consumed['inventory_attachments'].get(ATTACHMENT, 0) == 0,
          'full inventory cannot pick up the crafted magazine')
    server_number('Inventory[{Slot:10b}].count', 48)
    server_number('Inventory[{Slot:11b}].count', 60)
    ground = wait(lambda: reply if 'tacz:extended_mag_1' in (reply := command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True)) else None,
        'crafted magazine remains as a ground item with full inventory', timeout=10)
    check('count: 1' in ground, 'ground item contains exactly one crafted magazine')
    evidence['full_inventory'] = {'client': consumed, 'ground': ground}

    check('Replaced a slot' in command('item replace entity TaczShooter hotbar.1 with minecraft:air'),
          'server frees one native hotbar slot')
    recovered = wait(lambda: v if (v := state('shooter'))['inventory_attachments'].get(ATTACHMENT) == 1
                     and v['free_slot'] == -1 else None,
                     'ground output enters freed slot while gunsmith menu stays open', timeout=15)
    check('No entity was found' in command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True),
        'ground output disappears after pickup')
    check(ATTACHMENT in command('data get entity TaczShooter Inventory')
          and recovered['inventory_iron'] == 48 and recovered['inventory_gold'] == 60,
          'server and client retain one output and exact residual ingredients')
    check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
          'native gunsmith screen closes after ground-output recovery')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection restores original gun')
    for role in ('shooter', 'observer'):
        value = wait(lambda: v if (v := state(role))['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                     and v['players']['TaczShooter'].get('magazine') == 29 else None,
                     f'{role} sees original gun after full-inventory craft', timeout=10)
        check(value['players']['TaczShooter']['chamber'] is True,
              f'{role} sees original chambered round after craft')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    evidence['before'] = before
    evidence['screen'] = screen
    evidence['recovered'] = recovered
