package weapons.czxmc.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapons.czxmc.item.DashItem;

/**
 * Intercepta el manejo de movimiento del servidor durante una sesión de wall-phase.
 *
 * El problema que resuelve:
 *   Cuando el cliente activa noPhysics y se mueve dentro de una pared, envía
 *   ServerboundMovePlayerPackets con posiciones dentro de bloques sólidos.
 *   El servidor normalmente valida que ese movimiento sea posible, y si no lo es,
 *   envía ClientboundPlayerPositionPacket corrigiendo la posición del jugador
 *   (lo que se experimenta como un teleport de vuelta).
 *
 * La solución:
 *   Cuando hay una sesión de wall-phase activa para el jugador, en lugar de
 *   ejecutar la lógica normal de validación, simplemente actualizamos la posición
 *   del jugador en el servidor para que coincida con lo que reporta el cliente.
 *   Esto es lo mismo que hace Minecraft con el modo espectador: acepta la posición
 *   sin cuestionar si es físicamente válida.
 *
 *   Sin este mixin, el servidor sigue enviando paquetes de corrección de posición
 *   que sobreescriben el movimiento suave del cliente, causando el rubber-banding
 *   o efecto de teleport que se ve al intentar atravesar la pared.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * Inyecta al inicio del handler de movimiento.
     * Si hay una sesión de wall-phase activa, acepta la posición del cliente
     * directamente y cancela el procesamiento normal (que incluiría la validación
     * y el posible envío de un paquete de corrección).
     */
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void onHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Solo intervenir si este jugador tiene una sesión de wall-phase activa
        if (!DashItem.hasActiveWallPhase(player)) return;

        // Leer la posición que el cliente reporta, usando la posición actual
        // del servidor como fallback si el paquete no incluye posición
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yRot = packet.getYRot(player.getYRot());
        float xRot = packet.getXRot(player.getXRot());

        // Actualizar la posición del servidor para que coincida con el cliente.
        // setPos() mueve la entidad sin enviar ningún paquete de corrección,
        // así que el cliente puede seguir moviéndose libremente.
        player.setPos(x, y, z);
        player.setYRot(yRot);
        player.setXRot(xRot);

        // Cancelar la lógica normal de handleMovePlayer (que incluye la validación
        // de si el movimiento es físicamente posible y el envío de correcciones)
        ci.cancel();
    }
}
