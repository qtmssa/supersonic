package com.tencent.supersonic.headless.server.sync.superset;

import lombok.Data;

@Data
public class SupersetSyncResult {

    private boolean success;
    private String message;
    private long durationMs;
    private SupersetSyncStats stats = new SupersetSyncStats();

    public static SupersetSyncResult success(String message, long durationMs,
            SupersetSyncStats stats) {
        SupersetSyncResult result = new SupersetSyncResult();
        result.success = true;
        result.message = message;
        result.durationMs = durationMs;
        if (stats != null) {
            result.stats = stats;
        }
        return result;
    }

    public static SupersetSyncResult failure(String message, long durationMs,
            SupersetSyncStats stats) {
        SupersetSyncResult result = new SupersetSyncResult();
        result.success = false;
        result.message = message;
        result.durationMs = durationMs;
        if (stats != null) {
            result.stats = stats;
        }
        return result;
    }
}
