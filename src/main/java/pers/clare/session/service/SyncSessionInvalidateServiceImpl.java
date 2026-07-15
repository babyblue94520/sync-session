package pers.clare.session.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import pers.clare.session.constant.EventType;
import pers.clare.session.event.SyncSessionEventService;
import pers.clare.session.store.SyncSessionStore;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class SyncSessionInvalidateServiceImpl<T extends SyncSession> implements SyncSessionInvalidateService {

    public static final String SPLIT = "\n";

    @Setter(onMethod_ = {@Autowired})
    protected SyncSessionStore<T> store;

    @Setter(onMethod_ = {@Autowired(required = false)})
    protected SyncSessionEventService sessionEventService;


    public void invalidate(String id) {
        if (id == null) return;
        int count = store.delete(id);
        if (count > 0) {
            if (sessionEventService == null) return;
            sessionEventService.send(EventType.INVALIDATE + SPLIT + id);
        }
    }

    public void invalidateByUsername(String username, String... excludeSessionIds) {
        if (username == null) return;
        Set<String> excludeIds = toExcludeIds(excludeSessionIds);
        Collection<SyncSessionId> invalidated = store.findAllByUsername(username);
        for (SyncSessionId sessionId : invalidated) {
            String id = sessionId.getId();
            if (excludeIds.contains(id)) continue;
            invalidate(id);
        }
    }

    private Set<String> toExcludeIds(String... excludeSessionIds) {
        if (excludeSessionIds == null || excludeSessionIds.length == 0) return Collections.emptySet();
        Set<String> excludeIds = new HashSet<>(excludeSessionIds.length);
        for (String excludeSessionId : excludeSessionIds) {
            if (excludeSessionId == null) continue;
            excludeIds.add(excludeSessionId);
        }
        return excludeIds.isEmpty() ? Collections.emptySet() : excludeIds;
    }
}
