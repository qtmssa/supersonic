package com.tencent.supersonic.headless.server.sync.superset;

import com.tencent.supersonic.common.pojo.DataEvent;
import com.tencent.supersonic.common.pojo.DataItem;
import com.tencent.supersonic.common.pojo.enums.TypeEnums;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SupersetSyncListener {

    private final SupersetSyncService supersetSyncService;

    public SupersetSyncListener(SupersetSyncService supersetSyncService) {
        this.supersetSyncService = supersetSyncService;
    }

    @Async("eventExecutor")
    @EventListener
    public void onSupersetSyncEvent(SupersetSyncEvent event) {
        if (event == null) {
            return;
        }
        if (event.getSyncType() == SupersetSyncType.DATABASE) {
            supersetSyncService.triggerDatabaseSync(event.getResourceIds(),
                    SupersetSyncTrigger.EVENT);
            return;
        }
        if (event.getSyncType() == SupersetSyncType.DATASET) {
            supersetSyncService.triggerDatasetSync(event.getResourceIds(),
                    SupersetSyncTrigger.EVENT);
        }
    }

    @Async("eventExecutor")
    @EventListener
    public void onModelEvent(DataEvent event) {
        if (event == null || event.getDataItems() == null) {
            return;
        }
        Set<Long> modelIds = event.getDataItems().stream()
                .filter(item -> item != null && TypeEnums.MODEL.equals(item.getType()))
                .map(DataItem::getId).filter(StringUtils::isNumeric).map(Long::valueOf)
                .collect(Collectors.toSet());
        if (modelIds.isEmpty()) {
            return;
        }
        log.debug("superset dataset sync triggered by model event, modelIds={}", modelIds);
        supersetSyncService.triggerDatasetSyncByModelIds(modelIds, SupersetSyncTrigger.EVENT);
    }
}
