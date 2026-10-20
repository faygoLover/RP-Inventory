package faygolover.rpinventory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpinventory.compat.CuriosCompat;
import faygolover.rpinventory.limits.Limits;
import faygolover.rpinventory.limits.LimitsCalc;
import faygolover.rpinventory.limits.Personal;
import faygolover.rpinventory.server.InvServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/** {@code /rpinv}: show — итог и из чего сложился; set — личная поправка ГМа; reset; curios — типы слотов Curios. */
final class InvCommand {
    private InvCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        var players = Commands.argument("players", EntityArgument.players());
        for (Part p : Part.values()) {
            players.then(Commands.literal(Personal.key(p))
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, p.size))
                            .executes(c -> setPart(c, p))));
        }
        players.then(Commands.literal("curios")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                CuriosCompat.types(c.getSource().getPlayerOrException()).stream().map(CuriosCompat.TypeInfo::type), b))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 64))
                                .executes(InvCommand::setCurios))));
        set.then(players);

        d.register(Commands.literal("rpinv")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("show")
                        .executes(c -> show(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> show(c, EntityArgument.getPlayer(c, "player")))))
                .then(set)
                .then(Commands.literal("reset")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(InvCommand::reset)))
                .then(Commands.literal("curios")
                        .executes(c -> curios(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> curios(c, EntityArgument.getPlayer(c, "player"))))));
    }

    private static int show(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        LimitsCalc.Result r = LimitsCalc.explain(player);
        Limits l = r.limits();
        CommandSourceStack s = c.getSource();
        s.sendSuccess(() -> Component.translatable("rpinventory.show.head", player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        if (l.exempt()) {
            s.sendSuccess(() -> Component.translatable("rpinventory.show.exempt"), false);
            return 1;
        }
        for (Part p : Part.values()) {
            MutableComponent line = Component.translatable("rpinventory.part." + Personal.key(p))
                    .append(": " + l.capacity(p) + "/" + p.size);
            if (l.partFrozen(p)) line.append(" ").append(Component.translatable("rpinventory.show.frozen").withStyle(ChatFormatting.AQUA));
            s.sendSuccess(() -> line, false);
        }
        l.curios().forEach((type, rule) -> s.sendSuccess(() -> Component.literal("curios:" + type + " "
                + (rule.add() != 0 ? (rule.add() > 0 ? "+" : "") + rule.add() + " " : "")
                + (rule.limit() >= 0 ? "≤ " + rule.limit() : "")), false));
        if (!r.notes().isEmpty()) {
            s.sendSuccess(() -> Component.translatable("rpinventory.show.sources").withStyle(ChatFormatting.GRAY), false);
            for (LimitsCalc.Note n : r.notes()) {
                s.sendSuccess(() -> Component.literal("  " + n.source() + ": " + n.what()).withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static int setPart(CommandContext<CommandSourceStack> c, Part part) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        int value = IntegerArgumentType.getInteger(c, "value");
        for (ServerPlayer p : players) {
            Personal.setPart(p, part, value);
            InvServer.refresh(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("rpinventory.set.done",
                Component.translatable("rpinventory.part." + Personal.key(part)), value, players.size()), true);
        return players.size();
    }

    private static int setCurios(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        String type = StringArgumentType.getString(c, "type");
        int value = IntegerArgumentType.getInteger(c, "value");
        for (ServerPlayer p : players) {
            Personal.setCurios(p, type, value);
            InvServer.refresh(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("rpinventory.set.done",
                Component.literal("curios:" + type), value, players.size()), true);
        return players.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer p : players) {
            Personal.reset(p);
            InvServer.refresh(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("rpinventory.reset.done", players.size()), true);
        return players.size();
    }

    private static int curios(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        var types = CuriosCompat.types(player);
        CommandSourceStack s = c.getSource();
        if (types.isEmpty()) {
            s.sendFailure(Component.translatable("rpinventory.curios.none"));
            return 0;
        }
        s.sendSuccess(() -> Component.translatable("rpinventory.curios.head", player.getDisplayName(), types.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (CuriosCompat.TypeInfo t : types) {
            Limits.CuriosRule r = t.rule();
            String rule = (r.add() != 0 ? (r.add() > 0 ? " +" : " ") + r.add() : "") + (r.limit() >= 0 ? " ≤ " + r.limit() : "");
            s.sendSuccess(() -> Component.literal(t.type() + ": " + t.slots()).append(
                    Component.literal(rule).withStyle(ChatFormatting.YELLOW)), false);
        }
        return types.size();
    }
}
