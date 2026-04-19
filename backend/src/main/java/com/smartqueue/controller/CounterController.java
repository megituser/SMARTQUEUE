package com.smartqueue.controller;

import com.smartqueue.dto.request.CounterRequest;
import com.smartqueue.dto.response.ApiResponse;
import com.smartqueue.dto.response.QueueStatusResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.service.BranchService;
import com.smartqueue.service.QueueService;
import com.smartqueue.websocket.QueueWebSocketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/counters")
@RequiredArgsConstructor
public class CounterController {

    private final QueueService queueService;
    private final BranchService branchService;
    private final QueueWebSocketService webSocketService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> createCounter(@Valid @RequestBody CounterRequest request) {
        log.info("Creating counter: branchId={}, label={}", request.getBranchId(), request.getName());

        branchService.createCounter(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Counter created", null));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse.CounterStatusResponse>>> getCounters(
            @PathVariable Long branchId) {

        List<QueueStatusResponse.CounterStatusResponse> counters = branchService.getCountersByBranch(branchId);

        return ResponseEntity.ok(ApiResponse.success(counters));
    }

    @PostMapping("/{counterId}/open")
    public ResponseEntity<ApiResponse<Void>> openCounter(@PathVariable Long counterId) {

        log.info("Opening counter: {}", counterId);

        queueService.openCounter(counterId);

        return ResponseEntity.ok(ApiResponse.success("Counter opened", null));
    }

    @PostMapping("/{counterId}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> closeCounter(@PathVariable Long counterId) {
        log.info("Closing counter: counterId={}", counterId);

        queueService.closeCounter(counterId);

        return ResponseEntity.ok(ApiResponse.success("Counter closed", null));
    }

    @PostMapping("/{counterId}/next")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<TokenResponse>> callNext(@PathVariable Long counterId) {
        log.info("Calling next token: counterId={}", counterId);

        TokenResponse token = queueService.callNextToken(counterId);

        broadcastQueueAndToken(token);

        log.info("Next token called: counterId={}, tokenId={}, branchId={}",
                counterId, token.getId(), token.getBranchId());

        return ResponseEntity.ok(ApiResponse.success("Next token called", token));
    }

    @PostMapping("/{counterId}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<TokenResponse>> completeService(@PathVariable Long counterId) {
        log.info("Completing service: counterId={}", counterId);

        TokenResponse token = queueService.completeService(counterId);

        broadcastQueueAndToken(token);

        log.info("Service completed: counterId={}, tokenId={}", counterId, token.getId());

        return ResponseEntity.ok(ApiResponse.success("Service completed", token));
    }

    @PostMapping("/{counterId}/no-show")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<TokenResponse>> markNoShow(@PathVariable Long counterId) {
        log.info("Marking no-show: counterId={}", counterId);

        TokenResponse token = queueService.markNoShow(counterId);

        // Token update skipped intentionally — no-show doesn't advance the counter,
        // so only the queue position snapshot needs refreshing on display boards
        broadcastQueueOnly(token);

        log.info("No-show marked: counterId={}, tokenId={}", counterId, token.getId());

        return ResponseEntity.ok(ApiResponse.success("Token marked as no-show", token));
    }

    // --- private helpers ---

    private void broadcastQueueAndToken(TokenResponse token) {
        try {
            webSocketService.broadcastQueueUpdate(token.getBranchId());
            webSocketService.broadcastTokenUpdate(token);
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: branchId={}, tokenId={} — {}",
                    token.getBranchId(), token.getId(), e.getMessage());
        }
    }

    private void broadcastQueueOnly(TokenResponse token) {
        try {
            webSocketService.broadcastQueueUpdate(token.getBranchId());
        } catch (Exception e) {
            log.warn("Queue broadcast failed: branchId={}, tokenId={} — {}",
                    token.getBranchId(), token.getId(), e.getMessage());
        }
    }
}