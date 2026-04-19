package com.smartqueue.service;

import com.smartqueue.dto.request.TokenRequest;
import com.smartqueue.dto.response.QueueStatusResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.exception.BadRequestException;
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

    // ================= ISSUE TOKEN =================
    @Transactional
    public TokenResponse issueToken(TokenRequest request) {

        log.info("Issuing token for branchId={}, serviceId={}",
                request.getBranchId(), request.getServiceId());

        Branch branch = branchRepository.findWithLockById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", request.getBranchId()));

        ServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found", request.getServiceId()));

        if (!service.getBranch().getId().equals(branch.getId())) {
            throw new BadRequestException("Service not available at this branch");
        }

        int waitingCount = tokenRepository.countByBranchIdAndStatus(branch.getId(), TokenStatus.WAITING);

        int activeCounters = counterRepository.countByBranchIdAndStatus(branch.getId(), CounterStatus.OPEN);
        if (activeCounters == 0) activeCounters = 1;

        Double avgTime = tokenRepository.findAverageServiceTime(branch.getId(), LocalDate.now().atStartOfDay());
        int serviceTime = avgTime != null ? avgTime.intValue() : defaultServiceTime;

        int estimatedWait = (waitingCount * serviceTime) / activeCounters;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Integer maxSeq = tokenRepository.findMaxTokenSequence(branch.getId(), startOfDay);
        int nextSeq = maxSeq == null ? 1 : maxSeq + 1;

        if (nextSeq > maxTokensPerBranch) {
            throw new BadRequestException("Max tokens reached for today");
        }

        String prefix = service.getCode().length() >= 2
                ? service.getCode().substring(0, 2)
                : service.getCode();

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

        log.info("Token issued: {}", tokenNumber);

        return toResponse(token);
    }

    // ================= QUEUE STATUS =================
    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(Long branchId) {

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch missing", branchId));

        List<QueueToken> waiting = tokenRepository
                .findByBranchIdAndStatusOrderByPriorityAscIssuedAtAsc(branchId, TokenStatus.WAITING);

        List<Counter> counters = counterRepository.findByBranchIdWithDetails(branchId);

        int totalWaiting = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.WAITING);
        int totalServing = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.SERVING);
        int totalCompleted = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.COMPLETED);

        Double avgWait = tokenRepository.findAverageWaitTime(branchId, LocalDate.now().atStartOfDay());

        return QueueStatusResponse.builder()
                .branchId(branchId)
                .branchName(branch.getName())
                .totalWaiting(totalWaiting)
                .totalServing(totalServing)
                .totalCompleted(totalCompleted)
                .averageWaitMinutes(avgWait == null ? defaultServiceTime : avgWait.intValue())
                .waitingTokens(waiting.stream().map(this::toResponse).collect(Collectors.toList()))
                .counters(mapCounters(counters))
                .build();
    }

    // ================= GET TOKEN =================
    @Transactional(readOnly = true)
    public TokenResponse getTokenStatus(Long tokenId) {

        QueueToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found", tokenId));

        TokenResponse response = toResponse(token);

        if (token.getStatus() == TokenStatus.WAITING) {
            int ahead = tokenRepository.countAheadInQueue(
                    token.getBranch().getId(),
                    token.getPriority(),
                    token.getIssuedAt());

            response.setPositionInQueue(ahead + 1);
        }

        return response;
    }

    // ================= CANCEL =================
    @Transactional
    public TokenResponse cancelToken(Long tokenId) {

        QueueToken token = tokenRepository.findWithLockById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found", tokenId));

        if (token.getStatus() != TokenStatus.WAITING) {
            throw new BadRequestException("Only waiting tokens can be cancelled");
        }

        token.setStatus(TokenStatus.CANCELLED);
        token.setCompletedAt(LocalDateTime.now());

        tokenRepository.save(token);

        return toResponse(token);
    }

    // ================= CALL NEXT =================
    @Transactional
    public TokenResponse callNextToken(Long counterId) {

        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        if (counter.getStatus() != CounterStatus.OPEN) {
            throw new BadRequestException("Counter is closed");
        }

        if (counter.getCurrentToken() != null) {
            throw new BadRequestException("Counter is already serving a token");
        }

        Set<Long> allowedServices = counter.getServices().stream()
                .map(ServiceEntity::getId)
                .collect(Collectors.toSet());

        List<QueueToken> candidates = tokenRepository.findNextTokenToCall(
                counter.getBranch().getId(),
                List.copyOf(allowedServices),
                PageRequest.of(0, 10)
        );

        QueueToken next = candidates.stream()
                .map(c -> tokenRepository.findWithLockById(c.getId()).orElse(null))
                .filter(t -> t != null && t.getStatus() == TokenStatus.WAITING)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No waiting customers"));

        LocalDateTime now = LocalDateTime.now();

        next.setStatus(TokenStatus.SERVING);
        next.setCounter(counter);
        next.setCalledAt(now);
        next.setServingStartedAt(now);

        tokenRepository.save(next);

        counter.setCurrentToken(next);
        counterRepository.save(counter);

        log.info("Counter {} called token {}", counter.getCounterNumber(), next.getTokenNumber());

        return toResponse(next);
    }

    // ================= COMPLETE =================
    @Transactional
    public TokenResponse completeService(Long counterId) {
        return finalizeToken(counterId, TokenStatus.COMPLETED);
    }

    // ================= NO SHOW =================
    @Transactional
    public TokenResponse markNoShow(Long counterId) {
        return finalizeToken(counterId, TokenStatus.NO_SHOW);
    }

    private TokenResponse finalizeToken(Long counterId, TokenStatus status) {

        Counter counter = counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        QueueToken active = counter.getCurrentToken();

        if (active == null) {
            throw new BadRequestException("No active token at this counter");
        }

        active.setStatus(status);
        active.setCompletedAt(LocalDateTime.now());

        tokenRepository.save(active);

        counter.setCurrentToken(null);
        counterRepository.save(counter);

        return toResponse(active);
    }

    // ================= MAPPERS =================
    private List<QueueStatusResponse.CounterStatusResponse> mapCounters(List<Counter> counters) {
        return counters.stream().map(c -> {
            var builder = QueueStatusResponse.CounterStatusResponse.builder()
                    .counterId(c.getId())
                    .counterNumber(c.getCounterNumber())
                    .counterName(c.getName())
                    .status(c.getStatus())
                    .serviceNames(c.getServices().stream()
                            .map(ServiceEntity::getName)
                            .collect(Collectors.toList()));

            if (c.getCurrentToken() != null) {
                builder.currentToken(toResponse(c.getCurrentToken()));
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    private TokenResponse toResponse(QueueToken token) {
        if (token == null) return null;

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

    @Transactional
    public void openCounter(Long counterId) {

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        if (counter.getStatus() == CounterStatus.OPEN) {
            throw new BadRequestException("Counter is already open");
        }

        counter.setStatus(CounterStatus.OPEN);
        counterRepository.save(counter);

        log.info("Counter opened: {}", counterId);
    }

    @Transactional
    public void closeCounter(Long counterId) {

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found", counterId));

        if (counter.getStatus() == CounterStatus.CLOSED) {
            throw new BadRequestException("Counter is already closed");
        }

        if (counter.getCurrentToken() != null) {
            throw new BadRequestException("Cannot close counter while serving a customer");
        }

        counter.setStatus(CounterStatus.CLOSED);
        counterRepository.save(counter);

        log.info("Counter closed: {}", counterId);
    }
}