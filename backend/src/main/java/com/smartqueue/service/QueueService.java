package com.smartqueue.service;

import com.smartqueue.dto.request.TokenRequest;
import com.smartqueue.dto.response.QueueStatusResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.exception.BusinessException;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.*;
import com.smartqueue.model.enums.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueTokenRepository tokenRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;

    @Value("${queue.max-tokens-per-branch:999}")
    private int maxTokensPerBranch;

    @Value("${queue.estimated-service-time-minutes:15}")
    private int defaultServiceTime;

    @Transactional
    public TokenResponse issueToken(TokenRequest request) {
        Branch branch = branchRepository.findWithLockById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", request.getBranchId()));

        ServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found", request.getServiceId()));

        if (!service.getBranch().getId().equals(branch.getId())) {
            log.error("Service {} does not belong to branch {}", service.getId(), branch.getId());
            throw new IllegalArgumentException("Service not available at this branch");
        }

        int waitingCount = tokenRepository.countByBranchIdAndStatus(branch.getId(), TokenStatus.WAITING);
        int activeCounters = counterRepository.countByBranchIdAndStatus(branch.getId(), CounterStatus.OPEN);
        if (activeCounters == 0) {
            activeCounters = 1;
        }

        Double avgTime = tokenRepository.findAverageServiceTime(branch.getId(), LocalDate.now().atStartOfDay());
        int serviceTime = avgTime != null ? avgTime.intValue() : defaultServiceTime;
        int estimatedWait = (waitingCount * serviceTime) / activeCounters;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Integer maxSeq = tokenRepository.findMaxTokenSequence(branch.getId(), startOfDay);
        int nextSeq = maxSeq == null ? 1 : maxSeq + 1;

        if (nextSeq > maxTokensPerBranch) {
            log.warn("Daily token limit ({}) reached for branch {}", maxTokensPerBranch, branch.getId());
            throw new IllegalStateException("Max tokens reached for today");
        }

        String prefix = service.getCode().length() >= 2 ? service.getCode().substring(0, 2) : service.getCode();
        String tokenNumber = prefix.toUpperCase() + String.format("%03d", nextSeq);

        QueueToken token = QueueToken.builder()
                .tokenNumber(tokenNumber)
                .branch(branch)
                .service(service)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .status(TokenStatus.WAITING)
                .priority(request.getPriority())
                .source(request.getSource())
                .positionInQueue(waitingCount + 1)
                .estimatedWaitMinutes(estimatedWait)
                .notes(request.getNotes())
                .issuedAt(LocalDateTime.now())
                .build();

        tokenRepository.save(token);
        log.info("Issued token {} for service {}", token.getTokenNumber(), service.getCode());

        return toResponse(token);
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch missing", branchId));

        // Just fetch the next 50 tokens so we don't blow up memory on busy branches
        List<QueueToken> upcoming = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscIssuedAtAsc(
                branchId, TokenStatus.WAITING);

        List<Counter> counters = counterRepository.findByBranchIdWithDetails(branchId);

        int totalWaiting = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.WAITING);
        int totalServing = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.SERVING);
        int totalCompleted = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.COMPLETED);

        Double avgWait = tokenRepository.findAverageWaitTime(branchId, LocalDate.now().atStartOfDay());

        List<QueueStatusResponse.CounterStatusResponse> counterStatuses = counters.stream().map(c -> {
            var builder = QueueStatusResponse.CounterStatusResponse.builder()
                    .counterId(c.getId())
                    .counterNumber(c.getCounterNumber())
                    .counterName(c.getName())
                    .status(c.getStatus())
                    .serviceNames(c.getServices().stream().map(ServiceEntity::getName).collect(Collectors.toList()));

            if (c.getCurrentToken() != null) {
                builder.currentToken(toResponse(c.getCurrentToken()));
            }
            return builder.build();
        }).collect(Collectors.toList());

        return QueueStatusResponse.builder()
                .branchId(branchId)
                .branchName(branch.getName())
                .totalWaiting(totalWaiting)
                .totalServing(totalServing)
                .totalCompleted(totalCompleted)
                .averageWaitMinutes(avgWait == null ? defaultServiceTime : avgWait.intValue())
                .waitingTokens(upcoming.stream().map(this::toResponse).collect(Collectors.toList()))
                .counters(counterStatuses)
                .build();
    }

    @Transactional(readOnly = true)
    public TokenResponse getTokenStatus(Long tokenId) {
        QueueToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found", tokenId));

        TokenResponse response = toResponse(token);

        if (token.getStatus() == TokenStatus.WAITING) {
            int ahead = tokenRepository.countAheadInQueue(
                    token.getBranch().getId(),
                    token.getPriority().name(),
                    token.getIssuedAt());
            response.setPositionInQueue(ahead + 1);
        }

        return response;
    }

    @Transactional
    public TokenResponse cancelToken(Long tokenId) {
        QueueToken token = tokenRepository.findWithLockById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found", tokenId));

        if (token.getStatus() != TokenStatus.WAITING) {
            log.warn("Cannot cancel token {}, current state is {}", tokenId, token.getStatus());
            throw new IllegalStateException("Only waiting tokens can be cancelled");
        }

        token.setStatus(TokenStatus.CANCELLED);
        token.setCompletedAt(LocalDateTime.now());
        tokenRepository.save(token);

        log.info("Cancelled token {}", token.getTokenNumber());
        return toResponse(token);
    }

    @Transactional
    public TokenResponse callNextToken(Long counterId) {
        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter missing", counterId));

        if (counter.getStatus() != CounterStatus.OPEN) {
            throw new IllegalStateException("Counter is closed");
        }
        if (counter.getCurrentToken() != null) {
            throw new IllegalStateException("Counter is busy");
        }

        Set<Long> allowedServiceIds = counter.getServices().stream()
                .map(ServiceEntity::getId)
                .collect(Collectors.toSet());

        List<QueueToken> candidates = tokenRepository.findNextTokenToCall(
                counter.getBranch().getId(),
                new java.util.ArrayList<>(allowedServiceIds),
                PageRequest.of(0, 10));

        QueueToken nextToken = null;
        for (QueueToken candidate : candidates) {
            QueueToken locked = tokenRepository.findWithLockById(candidate.getId()).orElse(null);
            if (locked != null && locked.getStatus() == TokenStatus.WAITING) {
                nextToken = locked;
                break;
            }
        }

        if (nextToken == null) {
            throw new BusinessException("No waiting customers for available services");
        }

        LocalDateTime now = LocalDateTime.now();
        nextToken.setStatus(TokenStatus.SERVING);
        nextToken.setCounter(counter);
        nextToken.setCalledAt(now);
        nextToken.setServingStartedAt(now);
        tokenRepository.save(nextToken);

        counter.setCurrentToken(nextToken);
        counterRepository.save(counter);

        log.info("Counter {} called token {}", counter.getCounterNumber(), nextToken.getTokenNumber());
        return toResponse(nextToken);
    }

    @Transactional
    public TokenResponse completeService(Long counterId) {
        return finalizeToken(counterId, TokenStatus.COMPLETED);
    }

    @Transactional
    public TokenResponse markNoShow(Long counterId) {
        return finalizeToken(counterId, TokenStatus.NO_SHOW);
    }

    private TokenResponse finalizeToken(Long counterId, TokenStatus status) {
        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        QueueToken activeToken = counter.getCurrentToken();
        if (activeToken == null) {
            log.warn("Attempted to finalize empty counter {}", counterId);
            throw new IllegalStateException("No active token at this counter");
        }

        activeToken.setStatus(status);
        activeToken.setCompletedAt(LocalDateTime.now());
        tokenRepository.save(activeToken);

        counter.setCurrentToken(null);
        counterRepository.save(counter);

        log.debug("Token {} marked as {} at counter {}", activeToken.getTokenNumber(), status,
                counter.getCounterNumber());
        return toResponse(activeToken);
    }

    @Transactional
    public void openCounter(Long counterId, Long userId) {
        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        if (counter.getStatus() == CounterStatus.OPEN) {
            return;
        }

        counter.setStatus(CounterStatus.OPEN);
        counterRepository.save(counter);
        log.info("Counter {} opened by user {}", counter.getCounterNumber(), userId);
    }

    @Transactional
    public void closeCounter(Long counterId) {
        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        if (counter.getCurrentToken() != null) {
            throw new IllegalStateException("Cannot close counter while serving a customer");
        }

        counter.setStatus(CounterStatus.CLOSED);
        counterRepository.save(counter);
        log.info("Counter {} closed", counter.getCounterNumber());
    }

    private TokenResponse toResponse(QueueToken token) {
        if (token == null)
            return null;

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .branchId(token.getBranch().getId())
                .branchName(token.getBranch().getName())
                .serviceId(token.getService().getId())
                .serviceName(token.getService().getName())
                .counterId(token.getCounter() != null ? token.getCounter().getId() : null)
                .counterName(token.getCounter() != null ? token.getCounter().getName() : null)
                .customerName(token.getCustomerName())
                .customerPhone(token.getCustomerPhone())
                .customerEmail(token.getCustomerEmail())
                .status(token.getStatus())
                .priority(token.getPriority())
                .source(token.getSource())
                .positionInQueue(token.getPositionInQueue())
                .issuedAt(token.getIssuedAt())
                .calledAt(token.getCalledAt())
                .servingStartedAt(token.getServingStartedAt())
                .completedAt(token.getCompletedAt())
                .estimatedWaitMinutes(token.getEstimatedWaitMinutes())
                .notes(token.getNotes())
                .build();
    }
}
