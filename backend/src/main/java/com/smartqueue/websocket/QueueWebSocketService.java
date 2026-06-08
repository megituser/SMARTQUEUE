package com.smartqueue.websocket;

import com.smartqueue.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TYPE_QUEUE_UPDATE = "QUEUE_UPDATE";
    private static final String TYPE_TOKEN_UPDATE = "TOKEN_UPDATE";
    private static final String TYPE_COUNTER_UPDATE = "COUNTER_UPDATE";
    private static final String TYPE_TOKEN_CALLED = "TOKEN_CALLED";

    public void broadcastQueueUpdate(Long branchId) {

        if (branchId == null)
            return;

        try {
            String destination = "/topic/queue/" + branchId;
            Map<String, Object> payload = Map.of(
                    "type", TYPE_QUEUE_UPDATE,
                    "branchId", branchId,
                    "timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(destination, payload);

            log.trace("Broadcasted queue update to {}", destination);

        } catch (Exception e) {
            log.warn("Failed to broadcast queue update for branch {}: {}", branchId, e.getMessage());
        }
    }

    public void broadcastTokenUpdate(TokenResponse token) {
        // Defensive: Ensure we aren't broadcasting hopelessly malformed state
        if (token == null || token.getId() == null)
            return;

        try {
            String tokenDest = "/topic/token/" + token.getId();
            Map<String, Object> tokenPayload = Map.of(
                    "type", TYPE_TOKEN_UPDATE,
                    "token", token,
                    "timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend(tokenDest, tokenPayload);


            if (token.getCounterId() != null && token.getBranchId() != null) {
                String counterDest = String.format("/topic/counter/%d/%d", token.getBranchId(), token.getCounterId());
                Map<String, Object> counterPayload = Map.of(
                        "type", TYPE_COUNTER_UPDATE,
                        "token", token,
                        "timestamp", System.currentTimeMillis());
                messagingTemplate.convertAndSend(counterDest, counterPayload);
            }

        } catch (Exception e) {
            log.warn("Failed to broadcast update for token {}: {}", token.getId(), e.getMessage());
        }
    }

    public void broadcastTokenCalled(TokenResponse token) {
        if (token == null || token.getBranchId() == null)
            return;

        try {
            String destination = "/topic/queue/" + token.getBranchId() + "/called";
            Map<String, Object> payload = Map.of(
                    "type", TYPE_TOKEN_CALLED,
                    "token", token,
                    "timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Broadcasted token-called alert for token {} to branch {}", token.getTokenNumber(),
                    token.getBranchId());

        } catch (Exception e) {
            log.warn("Failed to broadcast token-called alert for branch {}: {}", token.getBranchId(), e.getMessage());
        }
    }
}
