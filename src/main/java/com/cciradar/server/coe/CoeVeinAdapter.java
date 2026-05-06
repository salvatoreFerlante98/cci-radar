package com.cciradar.server.coe;

import com.mojang.logging.LogUtils;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Wraps OreDataAttachment.getData() and translates the result into our neutral DetectedVein model.
 * All COE API references are confined to this class.
 */
public final class CoeVeinAdapter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Detects a COE vein in the given already-loaded chunk.
     * Returns empty if the chunk has no vein or on any error.
     *
     * Safe to call server-side only; OreDataAttachment.getData() throws if called client-side.
     */
    public static Optional<DetectedVein> detect(ServerLevel level, LevelChunk chunk) {
        try {
            OreData data = OreDataAttachment.getData(chunk);
            ResourceLocation recipeId = data.getRecipeId();
            if (recipeId == null) {
                return Optional.empty();
            }

            Optional<String> cciKey = CoeRecipeResolver.resolve(recipeId);
            if (cciKey.isEmpty()) {
                LOGGER.warn("[CCI Radar] Unknown COE vein recipe id: {} at chunk [{}, {}] in {} — no CCI resource mapping found",
                        recipeId, chunk.getPos().x, chunk.getPos().z, level.dimension().location());
            } else {
                LOGGER.debug("[CCI Radar] Mapped COE vein {} -> {} at chunk [{}, {}]",
                        recipeId, cciKey.get(), chunk.getPos().x, chunk.getPos().z);
            }

            return Optional.of(new DetectedVein(
                    level.dimension().location(),
                    chunk.getPos().x,
                    chunk.getPos().z,
                    recipeId,
                    cciKey.orElse(null)
            ));
        } catch (Exception e) {
            LOGGER.error("[CCI Radar] Error detecting COE vein at chunk [{}, {}]: {}",
                    chunk.getPos().x, chunk.getPos().z, e.getMessage());
            return Optional.empty();
        }
    }
}
