"""Force the second split output to fail and verify atomic gunsmith refund."""
import re
import time


RECIPE = 'tacz:ammo/22wmr'
AMMO = 'tacz:22wmr'


def run_gunsmith_refund_scenarios(check, command, client, state, wait, server_number, report):
    evidence = report['gunsmith_refund_scenario'] = {}
    before = state('shooter')
    check(before['screen'] == 'null' and before['game_mode'] == 'SURVIVAL'
          and before['inventory_ammo'].get('tacz:762x39') == 58
          and before['inventory_guns'].get('tacz:ak47') == 1,
          'loaded survival gun and reserve survive networking baseline')
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
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 '
                                       'with minecraft:copper_ingot 10'),
          'server equips exact copper ingredient')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.2 '
                                       'with minecraft:gunpowder 2'),
          'server equips exact gunpowder ingredient')
    wait(lambda: v if (v := state('shooter'))['inventory_copper'] == 10
         and v['inventory_gunpowder'] == 2 else None,
         'client sees exact ingredients before opening table', timeout=10)

    opened = client('shooter', 'use_block', x=-1, y=-59, z=0, face='UP', hand='OFF_HAND')
    check(opened['use_result'].startswith(('Success[', 'Consume[')),
          'native block use opens server-backed ammo workbench')
    screen = wait(lambda: v if ('GunSmithTableScreen' in (v := state('shooter'))['screen']
                                and v.get('gunsmith', {}).get('block_id') == 'tacz:ammo_workbench') else None,
                  'ammo workbench menu and screen synchronize', timeout=10)
    check(screen['gunsmith']['filter_by_hand'] is False
          and 'tacz:pd_cartridges' in screen['gunsmith']['category_ids'],
          'pack-defined pistol-cartridge category is visible')
    category = client('shooter', 'gunsmith_category_click', category='tacz:pd_cartridges')['snapshot']['gunsmith']
    check(RECIPE in category['tab_recipes'], 'native category button exposes shipped recipe')
    selected = client('shooter', 'gunsmith_select', recipe=RECIPE)['snapshot']['gunsmith']
    check(selected['selected_recipe'] == RECIPE and selected['material_counts'] == [10, 2],
          'native recipe button counts exact materials for split output')
    check('No entity was found' in command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True),
        'no ground item exists before the fault-injected craft')

    client('shooter', 'gunsmith_craft_click')
    time.sleep(0.6)
    refunded = state('shooter')
    check(refunded['inventory_copper'] == 10 and refunded['inventory_gunpowder'] == 2
          and refunded['inventory_ammo'].get(AMMO, 0) == 0,
          'failed second output restores both ingredients and yields no ammo')
    server_number('Inventory[{Slot:10b}].count', 10)
    server_number('Inventory[{Slot:11b}].count', 2)
    check('No entity was found' in command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True),
        'rollback discards the accepted first output entity')
    evidence['refunded'] = refunded

    client('shooter', 'gunsmith_craft_click')
    recovered = wait(lambda: v if (v := state('shooter'))['inventory_ammo'].get(AMMO) == 100
                     and v['inventory_copper'] == 0 and v['inventory_gunpowder'] == 0 else None,
                     'retry after one-shot fault crafts 100 rounds exactly once', timeout=20)
    reply = command('clear TaczShooter tacz:ammo 0')
    match = re.search(r'Found (\d+) matching item\(s\)', reply)
    check(match is not None and int(match[1]) == 158,
          'server holds only original 58 rifle rounds plus retried 100-round output')
    first = command('data get entity TaczShooter Inventory[{Slot:1b}]')
    second = command('data get entity TaczShooter Inventory[{Slot:2b}]')
    counts = sorted(int(match.group(1)) for item in (first, second)
                    if AMMO in item for match in [re.search(r'count: (\d+)\b', item)] if match)
    check(counts == [36, 64], 'retry output occupies legal 64- and 36-round stacks')
    check(client('shooter', 'gunsmith_close')['snapshot']['screen'] == 'null',
          'native gunsmith screen closes after retry')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection restores original gun')
    for role in ('shooter', 'observer'):
        value = wait(lambda: v if (v := state(role))['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                     and v['players']['TaczShooter'].get('magazine') == 29 else None,
                     f'{role} sees original gun after rollback and retry', timeout=10)
        check(value['players']['TaczShooter']['chamber'] is True,
              f'{role} sees original chambered round')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    evidence['before'] = before
    evidence['screen'] = screen
    evidence['retry'] = recovered
