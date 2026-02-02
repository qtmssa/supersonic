package com.tencent.supersonic.headless.server.sync.superset;

import com.tencent.supersonic.common.pojo.enums.EventType;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Set;

public class SupersetSyncEvent extends ApplicationEvent {

    private final SupersetSyncType syncType;
    private final EventType eventType;
    private final Set<Long> resourceIds;

    public SupersetSyncEvent(Object source, SupersetSyncType syncType, EventType eventType,
            Set<Long> resourceIds) {
        super(source);
        this.syncType = syncType;
        this.eventType = eventType;
        this.resourceIds = resourceIds == null ? Collections.emptySet() : resourceIds;
    }

    public SupersetSyncType getSyncType() {
        return syncType;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Set<Long> getResourceIds() {
        return resourceIds;
    }
}
