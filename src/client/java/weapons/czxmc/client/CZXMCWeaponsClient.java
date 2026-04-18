package weapons.czxmc.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;
import weapons.czxmc.ModEntities;
import weapons.czxmc.ModParticles;
import weapons.czxmc.client.GrenadeFlashOverlay;
import weapons.czxmc.client.particle.GunBulletTrailParticle;
import weapons.czxmc.client.renderer.AnchorSystemRenderer;
import weapons.czxmc.client.renderer.ConnectorLineRenderer;
import weapons.czxmc.client.renderer.EchoGrenadeItemRenderer;
import weapons.czxmc.client.renderer.EchoGrenadeRenderer;
import weapons.czxmc.client.renderer.GunBulletRenderer;
import weapons.czxmc.client.renderer.GunRenderer;
import weapons.czxmc.client.sound.DynamicEchoBeforeSoundInstance;
import weapons.czxmc.client.sound.EchoSoundManager;
import weapons.czxmc.client.sound.FadingGunSoundInstance;
import weapons.czxmc.item.GunItem;
import weapons.czxmc.item.ModItems;
import weapons.czxmc.network.ArtifactReloadPayload;
import weapons.czxmc.network.DashPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPosPayload;
import weapons.czxmc.network.GrenadeBeforeSoundVolumePayload;
import weapons.czxmc.network.GrenadeFlashPayload;
import weapons.czxmc.network.GunSoundPayload;
import weapons.czxmc.network.WallPhasePayload;
import weapons.czxmc.sound.ModSounds;

public class CZXMCWeaponsClient implements ClientModInitializer {

    public static final KeyMapping KEY_ARTIFACT_USE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.cz-x-mc-weapons.artifact_use", GLFW.GLFW_KEY_V, "key.categories.cz-x-mc-weapons")
    );

    public static final KeyMapping KEY_ARTIFACT_RELOAD = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.cz-x-mc-weapons.artifact_reload", GLFW.GLFW_KEY_R, "key.categories.cz-x-mc-weapons")
    );

    private static int    wallPhaseTicksLeft = 0;
    private static double wallPhaseVelX      = 0;
    private static double wallPhaseVelY      = 0;
    private static double wallPhaseVelZ      = 0;

    @Override
    public void onInitializeClient() {
        ModParticles.init();
        ParticleFactoryRegistry.getInstance().register(ModParticles.GUN_BULLET_TRAIL, new GunBulletTrailParticle.Factory());

        // GUN_BULLET — dos cubos girando (dust.json) al estilo wind charge
        EntityRendererRegistry.register(ModEntities.GUN_BULLET, GunBulletRenderer::new);

        EntityRendererRegistry.register(ModEntities.GRENADE,      EchoGrenadeRenderer::new);
        EntityRendererRegistry.register(ModEntities.ECHO_GRENADE, EchoGrenadeRenderer::new);

        EchoGrenadeItemRenderer echoItemRenderer = new EchoGrenadeItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.ECHO_GRENADE, echoItemRenderer::renderByItem);

        EntityRendererRegistry.register(ModEntities.ANCHOR_HOOK, AnchorSystemRenderer.HookEntityRenderer::new);

        AnchorSystemRenderer.GunItemRenderer anchorRenderer = new AnchorSystemRenderer.GunItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.ANCHOR, anchorRenderer::renderByItem);

        GunRenderer gunRenderer = new GunRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.GUN, gunRenderer::renderByItem);

        WorldRenderEvents.AFTER_ENTITIES.register(ConnectorLineRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(AnchorSystemRenderer::renderChain);

        ClientPlayNetworking.registerGlobalReceiver(GunSoundPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(payload.soundId());
                if (sound == null) return;
                if (payload.animSpeed() != 1.0f && ModItems.GUN instanceof GunItem gunItem) {
                    gunItem.reloadAnimSpeed = payload.animSpeed();
                }
                Minecraft.getInstance().getSoundManager().play(
                        new FadingGunSoundInstance(sound, payload.volume(), payload.pitch(), payload.holdTicks())
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GrenadeFlashPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    GrenadeFlashOverlay.trigger(
                            payload.intensity(), payload.durationTicks(),
                            payload.r(), payload.g(), payload.b(),
                            payload.soundVolume(), payload.soundPitch()
                    )
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(GrenadeBeforeSoundPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (GrenadeFlashOverlay.isActive()) return;
                if (ModSounds.ECHO_BEFORE == null) return;
                var mc = Minecraft.getInstance();
                if (mc.player == null) return;
                mc.getSoundManager().play(
                        new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                                ModSounds.ECHO_BEFORE.getLocation(),
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                payload.volume(), payload.pitch(),
                                net.minecraft.util.RandomSource.create(),
                                false, 0,
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.Attenuation.NONE,
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true
                        )
                );
            });
        });

        // Reproducir sonido "before" en posición
        ClientPlayNetworking.registerGlobalReceiver(GrenadeBeforeSoundPosPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (GrenadeFlashOverlay.isActive()) return;
                if (ModSounds.ECHO_BEFORE == null) return;
                
                float x = (float) payload.position().x();
                float y = (float) payload.position().y();
                float z = (float) payload.position().z();
                
                // Reducir volumen un 40% para que no sea tan invasivo
                float adjustedVolume = payload.volume() * 0.6f;
                
                EchoSoundManager.playBeforeSound("echo_before", ModSounds.ECHO_BEFORE, 
                        adjustedVolume, payload.pitch(), x, y, z);
            });
        });

        // Actualizar volumen cada tick o detener sonido cuando explota
        ClientPlayNetworking.registerGlobalReceiver(GrenadeBeforeSoundVolumePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.stopSound()) {
                    EchoSoundManager.stopBeforeSound("echo_before");
                } else {
                    // Actualizar volumen con reducción del 40%
                    float adjustedVolume = payload.volume() * 0.6f;
                    EchoSoundManager.updateVolume("echo_before", adjustedVolume);
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> GrenadeFlashOverlay.tick());

        HudRenderCallback.EVENT.register((graphics, tickDelta) ->
                GrenadeFlashOverlay.render(graphics, tickDelta));

        ClientPlayNetworking.registerGlobalReceiver(WallPhasePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var player = context.client().player;
                if (player == null) return;
                player.noPhysics = true;
                player.setDeltaMovement(payload.velX(), payload.velY(), payload.velZ());
                wallPhaseVelX      = payload.velX();
                wallPhaseVelY      = payload.velY();
                wallPhaseVelZ      = payload.velZ();
                wallPhaseTicksLeft = payload.tickDuration();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (wallPhaseTicksLeft <= 0) return;
            var player = client.player;
            if (player == null) {
                wallPhaseTicksLeft = 0;
                return;
            }
            wallPhaseTicksLeft--;
            if (wallPhaseTicksLeft <= 0) {
                player.noPhysics = false;
                wallPhaseVelX = 0;
                wallPhaseVelY = 0;
                wallPhaseVelZ = 0;
            } else {
                player.noPhysics = true;
                player.setDeltaMovement(wallPhaseVelX, wallPhaseVelY, wallPhaseVelZ);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!KEY_ARTIFACT_USE.consumeClick()) return;
            if (client.player == null) return;

            float forward = 0f, strafe = 0f;
            if (client.options.keyUp.isDown())    forward += 1f;
            if (client.options.keyDown.isDown())  forward -= 1f;
            if (client.options.keyRight.isDown()) strafe  += 1f;
            if (client.options.keyLeft.isDown())  strafe  -= 1f;

            boolean hasMovement = Math.abs(forward) > 0.01 || Math.abs(strafe) > 0.01;
            if (hasMovement) {
                float len = (float) Math.sqrt(forward * forward + strafe * strafe);
                forward /= len;
                strafe  /= len;
            }

            ClientPlayNetworking.send(new DashPayload(strafe, forward, hasMovement));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!KEY_ARTIFACT_RELOAD.consumeClick()) return;
            if (client.player == null) return;
            ClientPlayNetworking.send(new ArtifactReloadPayload());
        });
    }
}