package weapons.czxmc;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import weapons.czxmc.item.GunItem;

import java.util.UUID;

/**
 * Comandos del mod CZ x MC Weapons.
 *
 * ── Comandos disponibles ───────────────────────────────────────────────────
 *
 *   /gundebug
 *     Activa o desactiva el modo debug de la pistola para el jugador que
 *     ejecuta el comando. Cuando está activo, cada disparo muestra en el
 *     action bar la condición de propulsión aplicada, el multiplicador
 *     resultante, las balas cargadas y los disparos en el aire usados.
 *
 *     Permiso requerido: nivel 0 (cualquier jugador).
 */
public class ModCommands {

    public static void registerAll() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // ── /gundebug ──────────────────────────────────────────────────────
            dispatcher.register(
                Commands.literal("gundebug")
                    .requires(source -> source.hasPermission(0)) // cualquier jugador
                    .executes(ModCommands::toggleGunDebug)
            );
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Handlers
    // ══════════════════════════════════════════════════════════════════════════

    private static int toggleGunDebug(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();

            if (GunItem.debugPlayers.contains(uuid)) {
                GunItem.debugPlayers.remove(uuid);
                ctx.getSource().sendSuccess(
                    () -> Component.literal("⚙ Gun Debug ")
                              .append(Component.literal("OFF").withStyle(ChatFormatting.RED)),
                    false
                );
            } else {
                GunItem.debugPlayers.add(uuid);
                ctx.getSource().sendSuccess(
                    () -> Component.literal("⚙ Gun Debug ")
                              .append(Component.literal("ON").withStyle(ChatFormatting.GREEN))
                              .append(Component.literal(" — la info aparece en el action bar al disparar.")
                                  .withStyle(ChatFormatting.GRAY)),
                    false
                );
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                Component.literal("Este comando solo puede ejecutarlo un jugador.")
            );
            return 0;
        }
    }
}
