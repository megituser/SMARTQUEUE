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

    // ✅ ISSUE TOKEN
    @PostMapping("/tokens")
    public ResponseEntity<ApiResponse<TokenResponse>> issueToken(@Valid @RequestBody TokenRequest request) {
        TokenResponse token = queueService.issueToken(request);
        webSocketService.broadcastQueueUpdate(request.getBranchId());
        webSocketService.broadcastTokenUpdate(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Token issued", token));
    }

    // ✅ GET TOKEN
    @GetMapping("/tokens/{id}")
    public ResponseEntity<ApiResponse<TokenResponse>> getTokenStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(queueService.getTokenStatus(id)));
    }

    // ✅ QUEUE STATUS
    @GetMapping("/branch/{branchId}/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getQueueStatus(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(queueService.getQueueStatus(branchId)));
    }

    // ✅ CANCEL TOKEN
    @PostMapping("/tokens/{id}/cancel")
    public ResponseEntity<ApiResponse<TokenResponse>> cancelToken(@PathVariable Long id) {
        TokenResponse token = queueService.cancelToken(id);
        webSocketService.broadcastQueueUpdate(token.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Token cancelled", token));
    }

    // 🔥 CALL NEXT (CRITICAL)
    @PostMapping("/counters/{counterId}/call-next")
    public ResponseEntity<ApiResponse<TokenResponse>> callNext(@PathVariable Long counterId) {
        TokenResponse token = queueService.callNextToken(counterId);
        webSocketService.broadcastQueueUpdate(token.getBranchId());
        webSocketService.broadcastTokenUpdate(token);
        return ResponseEntity.ok(ApiResponse.success("Token called", token));
    }

    // 🔥 COMPLETE SERVICE
    @PostMapping("/counters/{counterId}/complete")
    public ResponseEntity<ApiResponse<TokenResponse>> complete(@PathVariable Long counterId) {
        TokenResponse token = queueService.completeService(counterId);
        webSocketService.broadcastQueueUpdate(token.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Service completed", token));
    }

    // 🔥 NO SHOW
    @PostMapping("/counters/{counterId}/no-show")
    public ResponseEntity<ApiResponse<TokenResponse>> noShow(@PathVariable Long counterId) {
        TokenResponse token = queueService.markNoShow(counterId);
        webSocketService.broadcastQueueUpdate(token.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show", token));
    }
}