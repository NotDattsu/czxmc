package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C (servidor → cliente) para activar el wall-phase noclip.
 *
 * El servidor calcula la velocidad y duración necesarias para atravesar la pared,
 * y el cliente activa noPhysics + aplica la velocidad para que el movimiento
 * sea completamente suave (sin luchar contra la predicción de movimiento).
 *
 * @param velX          Velocidad horizontal X a aplicar al cliente
 * @param velY          Velocidad vertical Y a aplicar al cliente
 * @param velZ          Velocidad horizontal Z a aplicar al cliente
 * @param tickDuration  Ticks que debe durar el noPhysics en el cliente
 */
public record WallPhasePayload(
        double velX,
        double velY,
        double velZ,
        int tickDuration
) implements CustomPacketPayload {

    public static final Type<WallPhasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "wall_phase"));

    public static final StreamCodec<FriendlyByteBuf, WallPhasePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, WallPhasePayload::velX,
                    ByteBufCodecs.DOUBLE, WallPhasePayload::velY,
                    ByteBufCodecs.DOUBLE, WallPhasePayload::velZ,
                    ByteBufCodecs.INT,    WallPhasePayload::tickDuration,
                    WallPhasePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
