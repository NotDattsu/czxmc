package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete C2S (cliente → servidor) para activar el dash.
 *
 * El cliente detecta la tecla de dash, lee las teclas WASD activas,
 * calcula la dirección y manda este paquete.
 *
 * @param dirX  componente X de la dirección de movimiento (-1 a 1)
 * @param dirZ  componente Z de la dirección de movimiento (-1 a 1)
 * @param useMovement  true = usar WASD, false = usar la mirada del jugador
 */
public record DashPayload(
        float dirX,
        float dirZ,
        boolean useMovement
) implements CustomPacketPayload {

    public static final Type<DashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "dash"));

    public static final StreamCodec<FriendlyByteBuf, DashPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,   DashPayload::dirX,
                    ByteBufCodecs.FLOAT,   DashPayload::dirZ,
                    ByteBufCodecs.BOOL,    DashPayload::useMovement,
                    DashPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
