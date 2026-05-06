package com.cciradar.server.coe;

import net.minecraft.resources.ResourceLocation;

/**
 * Neutral domain model for a COE vein detected in a chunk.
 * Contains no COE classes — safe to pass outside server/coe/.
 *
 * @param cciResourceKey null if no CCI resource mapping exists for the COE recipe
 */
public record DetectedVein(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        ResourceLocation coeRecipeId,
        String cciResourceKey
) {}
