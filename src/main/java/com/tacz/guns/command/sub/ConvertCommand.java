package com.tacz.guns.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.resource.PackConvertor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.fml.loading.FMLLoader;

public class ConvertCommand {
    private static final String CONVERT_NAME = "convert";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> reload = Commands.literal(CONVERT_NAME);
        reload.executes(ConvertCommand::convert);
        return reload;
    }

    // Client-only API; caller-side isolation replaces obsolete Forge member stripping.
    private static void convertClient(CommandSourceStack source) { PackConvertor.convert(source); }

    private static int convert(CommandContext<CommandSourceStack> context) {
        if (FMLLoader.getDist().isClient()) convertClient(context.getSource());
        return Command.SINGLE_SUCCESS;
    }
}
