package com.vclient.mod.cosmetics;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Makes the local player's launcher-supplied skin (Ely.by accounts) show up even when vanilla's own lookup
 * failed, and layers the equipped cosmetic cape on top.
 *
 * Vanilla fetches the local profile once at start-up and never retries, so a single failed request leaves
 * Steve/Alex for the whole session. The launcher resolves the skin itself and passes it as
 * {@code -Dvclient.skin.url / .model / .uuid} (and {@code -Dvclient.cape.url}); this class downloads it once,
 * caches it on disk, registers it as a texture and swaps it into the local player's {@link SkinTextures}
 * whenever vanilla produced the default skin. A skin vanilla did load is never replaced.
 */
public final class SkinFix {
    private static final String SKIN_URL = System.getProperty("vclient.skin.url", "").strip();
    private static final String CAPE_URL = System.getProperty("vclient.cape.url", "").strip();
    private static final String MODEL = System.getProperty("vclient.skin.model", "default").strip();
    private static final String LAUNCHER_UUID = System.getProperty("vclient.skin.uuid", "").strip();

    private static volatile Identifier skinTexture;
    private static volatile Identifier capeTexture;
    private static volatile int loadedVersion;
    private static boolean loggedVanillaSkin;
    private static boolean loggedApplied;

    private static SkinTextures lastInput;
    private static SkinTextures lastOutput;
    private static int lastStateVersion = -1;
    private static int lastSkinVersion = -1;
    private static int lastCatalogRevision = -1;

    private SkinFix() {}

    private static volatile String skinStatus = "not supplied by the launcher";
    private static volatile String capeStatus = "not supplied by the launcher";

    public static void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        String user = client != null && client.getSession() != null ? client.getSession().getUsername() : "?";
        VClientMod.LOGGER.info("[Skin] Signed in as {} (launcher skin uuid: {}).", user,
                LAUNCHER_UUID.isEmpty() ? "none - not an Ely.by launch" : LAUNCHER_UUID);
        if (SKIN_URL.isEmpty() && CAPE_URL.isEmpty()) {
            VClientMod.LOGGER.info("[Skin] The launcher did not supply a skin, so the game's own lookup is used.");
            return;
        }
        VClientMod.LOGGER.info("[Skin] Launcher supplied a skin for {} (model {}): {}", LAUNCHER_UUID, MODEL, SKIN_URL);
        if (!SKIN_URL.isEmpty()) load(SKIN_URL, true);
        if (!CAPE_URL.isEmpty()) load(CAPE_URL, false);
    }

    /** One line for the menu and the diagnostics: where the launcher-supplied skin and cape stand. */
    public static String status() {
        return "skin: " + skinStatus + "; cape: " + capeStatus;
    }

    public static boolean skinFailed() {
        return skinStatus.startsWith("failed");
    }

    private static void setStatus(boolean skin, String value) {
        if (skin) skinStatus = value;
        else capeStatus = value;
    }

    /**
     * Downloads one launcher-supplied texture without ever blocking a pool thread (chaining on the download
     * future), so the skin and the cape load side by side and a failure is reported instead of hanging.
     */
    private static void load(String address, boolean skin) {
        String label = skin ? "skin" : "cape";
        URI uri;
        try {
            uri = URI.create(address);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IOException("only https texture addresses are accepted");
            }
        } catch (IOException | IllegalArgumentException e) {
            setStatus(skin, "failed: invalid address (" + e.getMessage() + ")");
            VClientMod.LOGGER.warn("[Skin] The launcher {} address {} is not usable: {}", label, address, e.getMessage());
            return;
        }
        Path cache = CosmeticNet.directory().getParent().resolve("skin-cache")
                .resolve(CosmeticNet.sha256(address.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".png");
        setStatus(skin, "downloading");
        VClientMod.LOGGER.info("[Skin] Fetching {} texture {} (cache {}).", label, uri, cache);
        CosmeticNet.download(uri, "", cache).whenComplete((bytes, error) -> {
            if (error != null) {
                String reason = CosmeticNet.describe(error);
                setStatus(skin, "failed: " + reason);
                VClientMod.LOGGER.warn("[Skin] Could not download the {} texture {}: {}", label, uri, reason);
                return;
            }
            VClientMod.LOGGER.info("[Skin] {} download finished ({} bytes).", label, bytes.length);
            try {
                NativeImage image = skin ? processSkin(NativeImage.read(bytes)) : NativeImage.read(bytes);
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    try {
                        Identifier id = Identifier.of("vclient", "launcher/" + label + "/" + Integer.toHexString(address.hashCode()));
                        client.getTextureManager().registerTexture(id,
                                new NativeImageBackedTexture(() -> "vclient launcher " + label, image));
                        if (skin) skinTexture = id;
                        else capeTexture = id;
                        loadedVersion++;
                        setStatus(skin, "ready");
                        VClientMod.LOGGER.info("[Skin] {} texture registered as {}.", label, id);
                    } catch (RuntimeException e) {
                        setStatus(skin, "failed: could not create the GPU texture (" + e.getMessage() + ")");
                        CosmeticNet.logError("Registering the " + label + " texture", e);
                    }
                });
            } catch (IOException | RuntimeException e) {
                setStatus(skin, "failed: the file is not a usable " + label + " image (" + e.getMessage() + ")");
                CosmeticNet.logError("Decoding the " + label + " texture from " + uri, e);
            }
        });
    }

    /**
     * Returns the textures to use for the profile {@code id}. For anyone but the local player, or when there
     * is nothing to change, this is {@code original} itself.
     */
    public static SkinTextures apply(UUID id, SkinTextures original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isLocalPlayer(client, id)) return original;
        int state = CosmeticState.version();
        if (original == lastInput && state == lastStateVersion && loadedVersion == lastSkinVersion
                && CosmeticCatalog.revision() == lastCatalogRevision) {
            return lastOutput;
        }
        SkinTextures result = compute(original);
        lastInput = original;
        lastOutput = result;
        lastStateVersion = state;
        lastSkinVersion = loadedVersion;
        lastCatalogRevision = CosmeticCatalog.revision();
        return result;
    }

    private static boolean loggedIdentity;

    /**
     * Whether {@code id} is the local player. On a single-player world it is the account's own UUID, but on an
     * offline-mode ("cracked") server the server gives you a different UUID derived from your name, so the
     * player entity's UUID and the signed-in name are checked as well.
     */
    private static boolean isLocalPlayer(MinecraftClient client, UUID id) {
        if (client == null || client.getSession() == null) return false;
        boolean local = id.equals(client.getSession().getUuidOrNull())
                || (client.player != null && id.equals(client.player.getUuid()));
        if (local && !loggedIdentity && !id.equals(client.getSession().getUuidOrNull())) {
            loggedIdentity = true;
            VClientMod.LOGGER.info("[Skin] The server uses a different UUID for you ({}) than your account ({}); "
                    + "applying the launcher skin to it.", id, client.getSession().getUuidOrNull());
        }
        return local;
    }

    private static SkinTextures compute(SkinTextures original) {
        AssetInfo.TextureAsset body = original.comp_1626();
        AssetInfo.TextureAsset cape = original.comp_1627();
        PlayerSkinType model = original.comp_1629();
        boolean changed = false;

        boolean vanillaIsDefault = body.comp_3627().getPath().startsWith("textures/");
        if (vanillaIsDefault && skinTexture != null) {
            body = new AssetInfo.TextureAssetInfo(skinTexture, skinTexture);
            model = PlayerSkinType.byModelMetadata(MODEL);
            changed = true;
            if (!loggedApplied) {
                loggedApplied = true;
                VClientMod.LOGGER.info("[Skin] Vanilla fell back to the default skin; applying the launcher skin {}.",
                        skinTexture);
            }
        } else if (!vanillaIsDefault && !loggedVanillaSkin) {
            loggedVanillaSkin = true;
            VClientMod.LOGGER.info("[Skin] Vanilla loaded the skin itself ({}); leaving it alone.", body.comp_3627());
        }

        Cosmetic equipped = CosmeticState.effective(CosmeticSlot.CAPE);
        Identifier cosmeticCape = equipped == null ? null : CosmeticTextures.current(equipped);
        if (cosmeticCape != null) {
            cape = new AssetInfo.TextureAssetInfo(cosmeticCape, cosmeticCape);
            changed = true;
        } else if (cape == null && capeTexture != null && equipped == null) {
            cape = new AssetInfo.TextureAssetInfo(capeTexture, capeTexture);
            changed = true;
        }
        return changed ? new SkinTextures(body, cape, original.comp_1628(), model, original.comp_1630()) : original;
    }

    // ---- skin image clean-up, same rules as vanilla's player skin loader -----------------------------

    private static NativeImage processSkin(NativeImage source) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            source.close();
            throw new IOException("unsupported skin size " + width + "x" + height);
        }
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < height; y++) for (int x = 0; x < 64; x++) image.setColorArgb(x, y, source.getColorArgb(x, y));
        source.close();
        if (height == 32) {
            copyMirrored(image, 4, 16, 16, 32, 4, 4);
            copyMirrored(image, 8, 16, 16, 32, 4, 4);
            copyMirrored(image, 0, 20, 24, 32, 4, 12);
            copyMirrored(image, 4, 20, 16, 32, 4, 12);
            copyMirrored(image, 8, 20, 8, 32, 4, 12);
            copyMirrored(image, 12, 20, 16, 32, 4, 12);
            copyMirrored(image, 44, 16, -8, 32, 4, 4);
            copyMirrored(image, 48, 16, -8, 32, 4, 4);
            copyMirrored(image, 40, 20, 0, 32, 4, 12);
            copyMirrored(image, 44, 20, -8, 32, 4, 12);
            copyMirrored(image, 48, 20, -16, 32, 4, 12);
            copyMirrored(image, 52, 20, -8, 32, 4, 12);
        }
        opaque(image, 0, 0, 32, 16);
        opaque(image, 0, 16, 64, 16);
        opaque(image, 16, 48, 32, 16);
        return image;
    }

    private static void copyMirrored(NativeImage image, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setColorArgb(sx + dx + (w - 1 - x), sy + dy + y, image.getColorArgb(sx + x, sy + y));
            }
        }
    }

    private static void opaque(NativeImage image, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) image.setColorArgb(x, y, image.getColorArgb(x, y) | 0xFF000000);
        }
    }
}
