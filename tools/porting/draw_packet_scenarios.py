"""Exercise repeated client draw packets against a selected gun's live state."""


def run_draw_packet_scenarios(check, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['players']['TaczShooter']['magazine'] == 29 for role in roles)
          and before['shooter']['reserve_slot_count'] == 58,
          'draw packet probe starts with magazine29 and reserve58')
    client('shooter', 'reload')

    def active_reload():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players']['TaczShooter']['reloading'] for role in roles) else None

    active = wait(active_reload, 'active synchronized reload before repeated draw packets', timeout=10)
    client('shooter', 'draw_packet')
    client('shooter', 'draw_packet')

    def completed_reload():
        values = {role: state(role) for role in roles}
        return values if (all(values[role]['players']['TaczShooter']['magazine'] == 30
                              and not values[role]['players']['TaczShooter']['reloading'] for role in roles)
                          and values['shooter']['reserve_slot_count'] == 57) else None

    reloaded = wait(completed_reload, 'active reload survives repeated draw packets', timeout=15)
    for role in roles:
        check(reloaded[role]['reloads'].get('TaczShooter', 0) ==
              before[role]['reloads'].get('TaczShooter', 0) + 1,
              role + ' observes one reload after repeated draw packets')
        check(reloaded[role]['shots'].get('TaczShooter', 0) ==
              before[role]['shots'].get('TaczShooter', 0)
              and reloaded[role]['projectiles'] == before[role]['projectiles'],
              role + ' sees no draw-induced shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 30)
    server_number('Inventory[{Slot:9b}].count', 57)

    def ready():
        value = state('shooter')
        return value if (value['draw_cooldown'] == 0 and value['shoot_cooldown'] == 0
                         and not value['state_locked']) else None

    wait(ready, 'first post-reload shot ready', timeout=15)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'first repeat-fire shot accepted')

    def first_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players']['TaczShooter']['magazine'] == 29
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             reloaded[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == reloaded[role]['projectiles'] + 1
                             for role in roles) else None

    fired = wait(first_shot, 'first post-reload shot synchronized', timeout=15)
    client('shooter', 'draw_packet')
    wait(ready, 'second repeat-fire shot ready', timeout=15)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'second repeat-fire shot accepted')

    def second_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players']['TaczShooter']['magazine'] == 28
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             fired[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == fired[role]['projectiles'] + 1
                             for role in roles) else None

    fired_twice = wait(second_shot, 'second shot survives repeated draw packet', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 57)
    report['draw_packet_scenario'] = {'before': before, 'active_reload': active,
                                      'reloaded': reloaded, 'first_shot': fired,
                                      'second_shot': fired_twice}
