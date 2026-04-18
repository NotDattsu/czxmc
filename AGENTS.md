# AGENTS.md — CZ x MC Weapons

Contexto del proyecto para agentes de IA. Leé esto antes de tocar cualquier archivo.

---

## Qué es este proyecto

Mod de Minecraft Java para la versión **1.21.1** usando **Fabric**. Agrega armas y habilidades de movimiento al juego. El mod se llama `cz-x-mc-weapons`.

---

## Stack técnico

| Elemento | Valor |
|---|---|
| Minecraft | 1.21.1 |
| Loader | Fabric Loader 0.18.6 |
| Loom | fabric-loom 1.16-SNAPSHOT |
| Mappings | Mojang oficiales (`loom.officialMojangMappings()`) |
| Java | 21 |
| Fabric API | 0.116.10+1.21.1 |
| GeckoLib | 4.7.2 (animaciones de modelos 3D) |
| Trinkets | 3.10.0 (slots de equipamiento extra) |
| Build | Gradle con `splitEnvironmentSourceSets()` |

---

## Estructura del proyecto

```
src/
  main/           → código compartido servidor/cliente
    java/weapons/czxmc/
      CZXMCWeapons.java       ← entrypoint principal (ModInitializer)
      ModItems.java           ← registro de todos los items
      ModEntities.java        ← registro de entidades
      ModParticles.java       ← registro de partículas
      ModSounds.java          ← registro de sonidos
      ModCommands.java        ← comandos
      item/                   ← clases de items
      entity/                 ← entidades
      network/                ← payloads de red (S2C y C2S)
      mixin/                  ← mixins del lado servidor
    resources/
      assets/cz-x-mc-weapons/
        geo/                  ← modelos 3D GeckoLib (.geo.json)
        animations/           ← animaciones GeckoLib (.animation.json)
        textures/             ← texturas
        sounds/               ← archivos de sonido

  client/         → código exclusivo del cliente
    java/weapons/czxmc/client/
      CZXMCWeaponsClient.java ← entrypoint cliente (ClientModInitializer)
      renderer/               ← renderers de entidades e items (GeckoLib)
      particle/               ← partículas personalizadas
      sound/                  ← instancias de sonido con fade
      mixin/                  ← mixins del lado cliente
```

---

## Package base

```
weapons.czxmc
```

Todo el código nuevo va bajo este package. El código cliente va bajo `weapons.czxmc.client`.

---

## Mod ID

```
cz-x-mc-weapons
```

Usado para ResourceLocations: `new ResourceLocation("cz-x-mc-weapons", "nombre")`.  
Con Mojang mappings en 1.21.1 se usa `ResourceLocation.fromNamespaceAndPath("cz-x-mc-weapons", "nombre")`.

---

## Items existentes

### Armas
- **GunItem** — pistola con sistema de upgrades (daño, impulso, balas en el aire, velocidad de recarga)
- **GunUpgradeItem** — items de mejora para la pistola

### Habilidades de movimiento
- **DashItem** — artefacto de dash. Tiene sistema de wall-phase (atravesar paredes) y upgrades propios
- **DashUpgradeItem** — mejoras para el DashItem

### Granadas
- **EchoGrenadeItem** — granada de eco con efecto de flash de pantalla
- **DebugGrenadeItem** — granada de depuración

### Utilidad
- **AnchorItem** — sistema de ancla/gancho (grapple hook)
- **ConnectorItem** — conector para el AnchorItem

---

## Entidades existentes

- **GunBulletEntity** — proyectil de la pistola
- **GrenadeEntity** — granada base
- **EchoGrenadeEntity** — granada de eco (extiende GrenadeEntity)
- **AnchorHookEntity** — garfio del sistema de ancla (tiene estados internos)

---

## Red (networking)

El mod usa el sistema de payloads de Fabric API (`PayloadTypeRegistry`):

| Payload | Dirección | Propósito |
|---|---|---|
| `GunSoundPayload` | S2C | Sonido de disparo con fade |
| `GrenadeFlashPayload` | S2C | Flash de pantalla + sonido de explosión |
| `GrenadeBeforeSoundPayload` | S2C | Sonido previo de echo grenade |
| `WallPhasePayload` | S2C | Efecto visual de noclip (wall-phase) |
| `DashPayload` | C2S | Ejecutar dash desde el cliente |
| `ArtifactReloadPayload` | C2S | Recarga manual del artefacto |

Para agregar un payload nuevo: crearlo en `network/`, registrarlo en `CZXMCWeapons.onInitialize()` (servidor) y en `CZXMCWeaponsClient` si hace falta en el cliente.

---

## Renderizado (GeckoLib)

Los modelos 3D usan GeckoLib 4.7.2. Los archivos van en:
- `assets/cz-x-mc-weapons/geo/*.geo.json` — geometría
- `assets/cz-x-mc-weapons/animations/*.animation.json` — animaciones
- Los renderers están en `src/client/java/.../renderer/`

No usar el sistema de modelos vanilla para items con geometría 3D; siempre usar GeckoLib.

---

## Convenciones importantes

- Los items se registran en `ModItems.registerAll()` — agregar ahí cualquier item nuevo
- Las entidades se registran en `ModEntities.registerAll()`
- Los sonidos en `ModSounds.registerAll()`
- El código que toca el render, partículas o sonidos del cliente va en `src/client/`, nunca en `src/main/`
- Los mixins del servidor van en `src/main/java/.../mixin/` y los del cliente en `src/client/java/.../mixin/`
- Mappings Mojang: los nombres de clases/métodos de Minecraft son los nombres oficiales (ej: `ServerPlayer`, `ServerLevel`, `ItemStack`, etc.)

---

## Cómo compilar

```bash
./gradlew build
```

El jar queda en `build/libs/`. Para correr el cliente de prueba:

```bash
./gradlew runClient
```

---

## Qué NO hacer

- No usar Forge ni NeoForge — este mod es exclusivamente Fabric
- No usar yarn mappings — se usan Mojang mappings
- No poner lógica de render/cliente en `src/main/`
- No cambiar el mod ID ni el package base sin avisar
