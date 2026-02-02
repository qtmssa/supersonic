package com.tencent.supersonic.headless.server.sync.superset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SupersetSyncScheduler {

    private final SupersetSyncService supersetSyncService;
    private final SupersetSyncProperties properties;

    public SupersetSyncScheduler(SupersetSyncService supersetSyncService,
            SupersetSyncProperties properties) {
        this.supersetSyncService = supersetSyncService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${s2.superset.sync.interval-ms:3600000}")
    public void scheduleSync() {
        if (!properties.isEnabled() || !properties.getSync().isEnabled()) {
            return;
        }
        log.debug("superset scheduled sync triggered");
        supersetSyncService.triggerFullSync(SupersetSyncTrigger.SCHEDULED);
    }
}
