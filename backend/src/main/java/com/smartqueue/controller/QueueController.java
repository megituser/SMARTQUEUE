package com.smartqueue.controller;

import com.smartqueue.dto.request.TokenRequest;
import com.smartqueue.dto.response.ApiResponse;
import com.smartqueue.dto.response.QueueStatusResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.service.QueueService;
import com.smartqueue.websocket.QueueWebSocketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final QueueWebSocketService webSocketService;

    @PostMapping("/tokens")
    public ResponseEntity<ApiResponse<TokenResponse>> issueToken(@Valid @RequestBody TokenRequest request) {
        TokenResponse token = queueService.issueToken(request);
        webSocketService.broadcastQueueUpdate(request.getBranchId());
        webSocketService.broadcastTokenUpdate(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Token issued", token));
    }

    @GetMapping("/tokens/{id}")
    public ResponseEntity<ApiResponse<TokenResponse>> getTokenStatus(@PathVariable Long id) {
        TokenResponse token = queueService.getTokenStatus(id);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @GetMapping("/branch/{branchId}/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getQueueStatus(@PathVariable Long branchId) {
        QueueStatusResponse status = queueService.getQueueStatus(branchId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/tokens/{id}/cancel")
    public ResponseEntity<ApiResponse<TokenResponse>> cancelToken(@PathVariable Long id) {
        TokenResponse token = queueService.cancelToken(id);
        webSocketService.broadcastQueueUpdate(token.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Token cancelled", token));
    }
}
