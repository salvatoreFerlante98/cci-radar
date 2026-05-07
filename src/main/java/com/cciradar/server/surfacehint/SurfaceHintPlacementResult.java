package com.cciradar.server.surfacehint;

public record SurfaceHintPlacementResult(
        String  pebbleBlockId,         // "cci_radar:coal_pebbles" or "none"
        boolean alreadyPlaced,         // previously placed ≥1 pebble
        int     previousFailedAttempts,// how many prior 0-result attempts
        int     attempted,             // positions tried this call
        int     placed,                // positions where a pebble was placed
        String  reason                 // human-readable status
) {
    public static SurfaceHintPlacementResult ofNoBlock(String pebbleId) {
        return new SurfaceHintPlacementResult(pebbleId, false, 0, 0, 0, "no pebble block for resource");
    }

    public static SurfaceHintPlacementResult ofAlreadyPlaced(String pebbleId, int prevFailed) {
        return new SurfaceHintPlacementResult(pebbleId, true, prevFailed, 0, 0, "already placed");
    }

    public static SurfaceHintPlacementResult ofMaxRetries(String pebbleId, int prevFailed, int retryLimit) {
        return new SurfaceHintPlacementResult(pebbleId, false, prevFailed, 0, 0,
                "max retries exceeded (" + prevFailed + "/" + retryLimit + ")");
    }

    public static SurfaceHintPlacementResult ofChunkNotLoaded(String pebbleId, int prevFailed) {
        return new SurfaceHintPlacementResult(pebbleId, false, prevFailed, 0, 0, "chunk not loaded");
    }

    public static SurfaceHintPlacementResult ofSuccess(String pebbleId, int prevFailed, int attempted, int placed) {
        String reason = placed > 0 ? "ok" : "no valid positions found";
        return new SurfaceHintPlacementResult(pebbleId, false, prevFailed, attempted, placed, reason);
    }

    public static SurfaceHintPlacementResult ofDisabled(String pebbleId) {
        return new SurfaceHintPlacementResult(pebbleId, false, 0, 0, 0, "surface_hints_enabled=false");
    }
}
