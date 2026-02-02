package com.tencent.supersonic.headless.server.sync.superset;

import lombok.Data;

@Data
public class SupersetSyncStats {

    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int failed;

    public void incTotal() {
        total += 1;
    }

    public void incCreated() {
        created += 1;
    }

    public void incUpdated() {
        updated += 1;
    }

    public void incSkipped() {
        skipped += 1;
    }

    public void incFailed() {
        failed += 1;
    }
}
