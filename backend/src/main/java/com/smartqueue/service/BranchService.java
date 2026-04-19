package com.smartqueue.service;

import com.smartqueue.dto.request.BranchRequest;
import com.smartqueue.dto.request.CounterRequest;
import com.smartqueue.dto.request.ServiceRequest;
import com.smartqueue.dto.response.*;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.*;
import com.smartqueue.model.enums.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

        private final BranchRepository branchRepository;
        private final ServiceRepository serviceRepository;
        private final CounterRepository counterRepository;
        private final QueueTokenRepository tokenRepository;
        private final AppointmentRepository appointmentRepository;

        // =================================================================================================
        // Branch Management
        // =================================================================================================

        @Transactional
        public BranchResponse createBranch(BranchRequest request) {
                String cleanCode = normalizeCode(request.getCode());

                if (branchRepository.existsByCode(cleanCode)) {
                        log.debug("Branch creation blocked: Code {} is already in use", cleanCode);
                        throw new IllegalStateException("Branch code is already registered");
                }

                Branch branch = Branch.builder()
                                .name(request.getName() != null ? request.getName().trim() : "Unnamed Branch")
                                .code(cleanCode)
                                .address(request.getAddress())
                                .phone(request.getPhone())
                                .timezone(StringUtils.hasText(request.getTimezone()) ? request.getTimezone() : "UTC")
                                .isActive(true)
                                .build();

                try {
                        branch = branchRepository.save(branch);
                } catch (DataIntegrityViolationException e) {
                        log.warn("Concurrent branch creation caught for code {}", cleanCode);
                        throw new IllegalStateException("Branch code is already registered");
                }

                log.info("Created new branch: {} ({})", branch.getName(), branch.getCode());
                return toResponse(branch);
        }

        @Transactional(readOnly = true)
        public List<BranchResponse> getAllBranches() {
                return branchRepository.findByIsActiveTrue()
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public BranchResponse getBranch(Long branchId) {
                Branch branch = branchRepository.findById(branchId)
                                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", branchId));
                return toResponse(branch);
        }

        @Transactional
        public BranchResponse updateBranch(Long branchId, BranchRequest request) {
                // Lock branch to prevent competing admin updates permanently overriding each
                // other
                Branch branch = branchRepository.findWithLockById(branchId)
                                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", branchId));

                if (StringUtils.hasText(request.getName()))
                        branch.setName(request.getName().trim());
                if (StringUtils.hasText(request.getAddress()))
                        branch.setAddress(request.getAddress().trim());
                if (StringUtils.hasText(request.getPhone()))
                        branch.setPhone(request.getPhone().trim());
                if (StringUtils.hasText(request.getTimezone()))
                        branch.setTimezone(request.getTimezone().trim());

                branchRepository.save(branch);
                log.info("Updated branch configuration: {}", branch.getCode());
                return toResponse(branch);
        }

        // =================================================================================================
        // Service Management
        // =================================================================================================

        @Transactional
        public ServiceResponse createService(ServiceRequest request) {
                Branch branch = branchRepository.findById(request.getBranchId())
                                .orElseThrow(() -> new ResourceNotFoundException("Branch not found",
                                                request.getBranchId()));

                String cleanCode = normalizeCode(request.getCode());

                if (serviceRepository.existsByBranchIdAndCode(branch.getId(), cleanCode)) {
                        throw new IllegalStateException(
                                        String.format("Service code '%s' already exists in this branch", cleanCode));
                }

                ServiceEntity service = ServiceEntity.builder()
                                .branch(branch)
                                .name(request.getName() != null ? request.getName().trim() : "Unnamed Service")
                                .code(cleanCode)
                                .description(request.getDescription())
                                // Defensive check to prevent nasty division by zero bugs later in estimated
                                // wait-time calculations
                                .avgServiceTimeMinutes(request.getAvgServiceTimeMinutes() != null
                                                && request.getAvgServiceTimeMinutes() > 0
                                                                ? request.getAvgServiceTimeMinutes()
                                                                : 15)
                                .isActive(true)
                                .build();

                try {
                        service = serviceRepository.save(service);
                } catch (DataIntegrityViolationException e) {
                        log.warn("Database unique constraint caught concurrent service creation for code {}",
                                        cleanCode);
                        throw new IllegalStateException("Service code already exists in this branch");
                }

                log.info("Created service {} ({}) for branch {}", service.getName(), service.getCode(),
                                branch.getCode());
                return toResponse(service);
        }

        @Transactional(readOnly = true)
        public List<ServiceResponse> getServicesByBranch(Long branchId) {
                return serviceRepository.findByBranchIdAndIsActiveTrue(branchId)
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        // =================================================================================================
        // Counter Management
        // =================================================================================================

        @Transactional
        public void createCounter(CounterRequest request) {
                Branch branch = branchRepository.findById(request.getBranchId())
                                .orElseThrow(() -> new ResourceNotFoundException("Branch not found",
                                                request.getBranchId()));

                Counter counter = Counter.builder()
                                .branch(branch)
                                .counterNumber(request.getCounterNumber())
                                .name(StringUtils.hasText(request.getName()) ? request.getName().trim()
                                                : "Counter " + request.getCounterNumber())
                                .status(CounterStatus.CLOSED)
                                .build();

                if (request.getServiceIds() != null && !request.getServiceIds().isEmpty()) {
                        List<ServiceEntity> requestedServices = serviceRepository.findAllById(request.getServiceIds());

                        // Defensive: ensure all requested services were actually found in the database
                        if (requestedServices.size() != request.getServiceIds().size()) {
                                throw new IllegalArgumentException("One or more provided service IDs do not exist");
                        }

                        // Defensive: protect against cross-branch mapping where a counter serves
                        // services from another branch
                        boolean hasExternalServices = requestedServices.stream()
                                        .anyMatch(s -> !s.getBranch().getId().equals(branch.getId()));

                        if (hasExternalServices) {
                                log.error("Security/Integrity blocked: Attempted to map external branch services to counter in branch {}",
                                                branch.getId());
                                throw new IllegalArgumentException(
                                                "Cannot assign services belonging to a different branch");
                        }

                        counter.setServices(new HashSet<>(requestedServices));
                }

                try {
                        counterRepository.save(counter);
                } catch (DataIntegrityViolationException e) {
                        log.warn("Duplicate counter number {} caught via DB constraint", request.getCounterNumber());
                        throw new IllegalStateException("Counter number already exists for this branch");
                }

                log.info("Created counter {} for branch {}", counter.getCounterNumber(), branch.getCode());
        }

        @Transactional(readOnly = true)
        public List<QueueStatusResponse.CounterStatusResponse> getCountersByBranch(Long branchId) {
                // Optimize: Find by branch with details (assuming `@EntityGraph` or `JOIN
                // FETCH`)
                // to avoid N+1 query floods on getServices() which scales poorly with more
                // counters
                return counterRepository.findByBranchIdWithDetails(branchId).stream()
                                .map(c -> {
                                        var builder = QueueStatusResponse.CounterStatusResponse.builder()
                                                        .counterId(c.getId())
                                                        .counterNumber(c.getCounterNumber())
                                                        .counterName(c.getName())
                                                        .status(c.getStatus())
                                                        .serviceNames(c.getServices().stream()
                                                                        .map(ServiceEntity::getName)
                                                                        .collect(Collectors.toList()));

                                        if (c.getCurrentToken() != null) {
                                                QueueToken token = c.getCurrentToken();
                                                builder.currentToken(TokenResponse.builder()
                                                                .id(token.getId())
                                                                .tokenNumber(token.getTokenNumber())
                                                                .branchId(token.getBranch().getId())
                                                                .branchName(token.getBranch().getName())
                                                                .serviceId(token.getService().getId())
                                                                .serviceName(token.getService().getName())
                                                                .counterId(c.getId())
                                                                .counterName(c.getName())
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
                                                                .build());
                                        }

                                        return builder.build();
                                })
                                .collect(Collectors.toList());
        }

        // =================================================================================================
        // Dashboard & Analytics
        // =================================================================================================

        @Transactional(readOnly = true)
        public DashboardResponse getDashboard(Long branchId) {
                Branch branch = branchRepository.findById(branchId)
                                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", branchId));

                LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
                LocalDate today = LocalDate.now();

                // DEV NOTE: This executes 12 separate count queries. This is fine for
                // low/medium load.
                // If the system scales significantly, these should be aggregated natively in
                // the Repository
                // using a query like: SELECT status, COUNT(*) FROM queue_tokens GROUP BY status
                int totalToday = tokenRepository.countTodayTokens(branchId, startOfDay);
                int waiting = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.WAITING);
                int serving = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.SERVING);
                int completed = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.COMPLETED);
                int noShow = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.NO_SHOW);
                int cancelled = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.CANCELLED);

                Double avgWait = tokenRepository.findAverageWaitTime(branchId, startOfDay);
                Double avgService = tokenRepository.findAverageServiceTime(branchId, startOfDay);

                int activeCounters = counterRepository.countByBranchIdAndStatus(branchId, CounterStatus.OPEN);

                // Optimisation Idea: Pulling down standard entity objects is heavy just for a
                // count.
                // We use size() here assuming counters per branch is extremely small (e.g. < 20
                // per branch).
                // We would use a native `countByBranchId` in repository if that scale expands.
                int totalCounters = counterRepository.findByBranchId(branchId).size();

                int appointmentsToday = appointmentRepository.countByBranchIdAndAppointmentDateAndStatusIn(
                                branchId, today, List.of(
                                                AppointmentStatus.BOOKED,
                                                AppointmentStatus.CONFIRMED,
                                                AppointmentStatus.CHECKED_IN,
                                                AppointmentStatus.COMPLETED));

                int checkedIn = appointmentRepository.countByBranchIdAndAppointmentDateAndStatusIn(
                                branchId, today, List.of(AppointmentStatus.CHECKED_IN));

                return DashboardResponse.builder()
                                .branchId(branchId)
                                .branchName(branch.getName())
                                .totalTokensToday(totalToday)
                                .currentWaiting(waiting)
                                .currentServing(serving)
                                .completedToday(completed)
                                .noShowToday(noShow)
                                .cancelledToday(cancelled)
                                .averageWaitMinutes(avgWait != null ? avgWait : 0.0)
                                .averageServiceMinutes(avgService != null ? avgService : 0.0)
                                .activeCounters(activeCounters)
                                .totalCounters(totalCounters)
                                .appointmentsToday(appointmentsToday)
                                .appointmentsCheckedIn(checkedIn)
                                .lastUpdated(LocalDateTime.now())
                                .build();
        }

        // =================================================================================================
        // Internal Utilities
        // =================================================================================================

        private String normalizeCode(String code) {
                if (!StringUtils.hasText(code)) {
                        throw new IllegalArgumentException("Code parameter cannot be empty");
                }
                // Drastically prevents code duplication bugs (e.g. "HQ" vs "hq ")
                return code.trim().toUpperCase();
        }

        private BranchResponse toResponse(Branch b) {
                if (b == null)
                        return null;

                return BranchResponse.builder()
                                .id(b.getId())
                                .name(b.getName())
                                .code(b.getCode())
                                .address(b.getAddress())
                                .phone(b.getPhone())
                                .timezone(b.getTimezone())
                                .isActive(Boolean.TRUE.equals(b.getIsActive()))
                                .build();
        }

        private ServiceResponse toResponse(ServiceEntity s) {
                if (s == null)
                        return null;

                return ServiceResponse.builder()
                                .id(s.getId())
                                .branchId(s.getBranch().getId())
                                .name(s.getName())
                                .code(s.getCode())
                                .description(s.getDescription())
                                .avgServiceTimeMinutes(
                                                s.getAvgServiceTimeMinutes() > 0 ? s.getAvgServiceTimeMinutes()
                                                                : 15)
                                .isActive(s.isActive())
                                .build();
        }
}
