package com.smartqueue.cache;

import com.smartqueue.dto.response.QueueStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class QueueCacheService {

    private final Map<String, Object> mockCache = new ConcurrentHashMap<>();

    public void cacheQueueStatus(Long branchId, QueueStatusResponse status) {
        mockCache.put("QUEUE:" + branchId, status);
    }

    public QueueStatusResponse getCachedQueueStatus(Long branchId) {
        Object val = mockCache.get("QUEUE:" + branchId);
        return val instanceof QueueStatusResponse ? (QueueStatusResponse) val : null;
    }

    public void invalidateQueueStatus(Long branchId) {
        mockCache.remove("QUEUE:" + branchId);
    }

    public Long incrementTokenCounter(Long branchId) {
        return null; // DB Fallback
    }

    public void updateCounterState(Long branchId, Long counterId, Map<String, Object> state) {
    }

    public void cacheDashboardStats(Long branchId, Map<String, Object> stats) {
    }

    public boolean isAvailable() {
        return false; // Force fallbacks
    }
}
