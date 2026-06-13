package ae2.integration.modules.theoneprobe;

import ae2.core.localization.LocalizationEnum;

public enum TopText implements LocalizationEnum {
    channels,
    channels_of,
    contains,
    crafting,
    device_missing_channel,
    device_offline,
    device_online,
    locked,
    nested_p2p_tunnel,
    p2p_frequency,
    p2p_input_many_outputs,
    p2p_input_one_output,
    p2p_me_carried_channels,
    p2p_output,
    p2p_output_many_inputs,
    p2p_unlinked,
    quantum_link_dimension,
    quantum_link_dimension_id,
    quantum_link_missing,
    quantum_link_position,
    showing,
    stored_energy,
    unlocked,
    charged,
    suppressed,
    network_booting,
    error_too_many_channels,
    error_controller_conflict,
    beam_former_linked,
    beam_former_unlinked,
    beam_former_target,
    beam_former_beam_hidden,
    crafting_locked_by_redstone_signal,
    crafting_locked_by_lack_of_redstone_signal,
    crafting_locked_until_pulse,
    crafting_locked_until_result,
    debug_forward,
    debug_spin,
    debug_external_node,
    debug_main_node,
    debug_tick_time,
    debug_avg,
    debug_max,
    debug_sum,
    debug_tick_status,
    debug_sleeping,
    debug_alertable,
    debug_awake,
    debug_queued,
    debug_tick_rate,
    debug_last,
    debug_ticks_ago,
    debug_node_exposed,
    netdebug_grid_pivot_pos,
    netdebug_grid_id,
    netdebug_grid_nodes,
    netdebug_grid_cpu_avg_max,
    netdebug_storage,
    netdebug_crafting,
    netdebug_tick,
    netdebug_misc;

    private final String translationKey;

    TopText() {
        var enumName = name();
        if (enumName.startsWith("netdebug_")) {
            this.translationKey = "theoneprobe.ae2.netdebug." + enumName.substring("netdebug_".length());
        } else if (enumName.startsWith("debug_")) {
            this.translationKey = "theoneprobe.ae2.debug." + enumName.substring("debug_".length());
        } else {
            this.translationKey = "theoneprobe.ae2." + enumName;
        }
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
