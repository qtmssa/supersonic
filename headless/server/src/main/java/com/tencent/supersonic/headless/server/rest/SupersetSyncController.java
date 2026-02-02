package com.tencent.supersonic.headless.server.rest;

import com.tencent.supersonic.headless.server.sync.superset.SupersetSyncResult;
import com.tencent.supersonic.headless.server.sync.superset.SupersetSyncService;
import com.tencent.supersonic.headless.server.sync.superset.SupersetSyncTrigger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/semantic/superset")
public class SupersetSyncController {

    private final SupersetSyncService supersetSyncService;

    public SupersetSyncController(SupersetSyncService supersetSyncService) {
        this.supersetSyncService = supersetSyncService;
    }

    @PostMapping("/sync/databases")
    public SupersetSyncResult syncDatabases() {
        return supersetSyncService.triggerDatabaseSync(Collections.emptySet(),
                SupersetSyncTrigger.MANUAL);
    }

    @PostMapping("/sync/datasets")
    public SupersetSyncResult syncDatasets() {
        return supersetSyncService.triggerDatasetSync(Collections.emptySet(),
                SupersetSyncTrigger.MANUAL);
    }
}
