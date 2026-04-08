package com.smartqueue.service;

import com.smartqueue.dto.request.AppointmentRequest;
import com.smartqueue.dto.request.TokenRequest;
import com.smartqueue.dto.response.AppointmentResponse;
import com.smartqueue.dto.response.SlotResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.exception.ResourceNotFoundException;
import com.smartqueue.model.*;
import com.smartqueue.model.enums.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final QueueService queueService;

    // Hardcoding business hours is fine for a baseline, but consider pulling from
    // Branch properties
    private static final LocalTime BUSINESS_START = LocalTime.of(8, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(18, 0);

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        // Lock the branch to serialize bookings for the same branch.
        // This solves double-booking race conditions natively without throwing nasty
        // 500 DB errors.
        Branch branch = branchRepository.findWithLockById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", request.getBranchId()));

        ServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found", request.getServiceId()));

        if (!service.getBranch().getId().equals(branch.getId())) {
            log.error("Service {} does not belong to branch {}", service.getId(), branch.getId());
            throw new IllegalArgumentException("Service not available at this branch");
        }

        validateBookingTime(request.getAppointmentDate(), request.getStartTime());

        boolean isSlotTaken = appointmentRepository
                .existsByBranchIdAndServiceIdAndAppointmentDateAndStartTimeAndStatusNot(
                        request.getBranchId(), request.getServiceId(),
                        request.getAppointmentDate(), request.getStartTime(),
                        AppointmentStatus.CANCELLED);

        if (isSlotTaken) {
            log.debug("Booking collision: {} at {} is already taken", request.getAppointmentDate(),
                    request.getStartTime());
            throw new IllegalStateException("Appointment slot is no longer available");
        }

        LocalTime endTime = request.getStartTime().plusMinutes(service.getAvgServiceTimeMinutes());

        Appointment appointment = Appointment.builder()
                .branch(branch)
                .service(service)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .endTime(endTime)
                .status(AppointmentStatus.BOOKED)
                .notes(request.getNotes())
                .build();

        try {
            appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException e) {
            // Safety fallback if DB catches a unique constraint violation from un-managed
            // endpoints
            log.warn("Database constraint caught a double booking attempt", e);
            throw new IllegalStateException("Appointment slot was just taken");
        }

        log.info("Booked appointment {} for {} at {}",
                appointment.getId(), request.getCustomerName(), request.getStartTime());

        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long branchId, Long serviceId, LocalDate date) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found", serviceId));

        int duration = service.getAvgServiceTimeMinutes();
        if (duration <= 0) {
            duration = 15; // fallback to prevent infinite loops
        }

        List<Appointment> existing = appointmentRepository.findActiveAppointments(branchId, serviceId, date);
        Set<LocalTime> bookedTimes = existing.stream()
                .map(Appointment::getStartTime)
                .collect(Collectors.toSet());

        List<SlotResponse> availableSlots = new ArrayList<>();
        LocalTime current = BUSINESS_START;
        LocalTime now = LocalTime.now();
        boolean isToday = date.equals(LocalDate.now());

        // Increment over the business day by standard duration units
        while (!current.plusMinutes(duration).isAfter(BUSINESS_END)) {
            boolean available = !bookedTimes.contains(current);

            // Hide times that have literally already happened today
            if (isToday && current.isBefore(now)) {
                available = false;
            }

            availableSlots.add(SlotResponse.builder()
                    .startTime(current)
                    .endTime(current.plusMinutes(duration))
                    .available(available)
                    .build());

            current = current.plusMinutes(duration);
        }

        return availableSlots;
    }

    @Transactional
    public TokenResponse checkIn(Long appointmentId) {
        // Find with lock so users aren't checking-in an appointment
        // at the exact millisecond an admin is trying to cancel it
        Appointment appointment = appointmentRepository.findWithLockById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found", appointmentId));

        if (appointment.getStatus() != AppointmentStatus.BOOKED &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Appointment cannot be checked in. Status is " + appointment.getStatus());
        }

        // Bridge the appointment over into the live walk-in queue
        TokenRequest tokenRequest = TokenRequest.builder()
                .branchId(appointment.getBranch().getId())
                .serviceId(appointment.getService().getId())
                .customerName(appointment.getCustomerName())
                .customerPhone(appointment.getCustomerPhone())
                .customerEmail(appointment.getCustomerEmail())
                .priority(TokenPriority.HIGH)
                .source(TokenSource.APPOINTMENT)
                .notes("Check-in for Appt #" + appointment.getId())
                .build();

        TokenResponse liveToken = queueService.issueToken(tokenRequest);

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        // Note: You may want to link liveToken entity physically to appointment here if
        // your schema allows
        appointmentRepository.save(appointment);

        log.info("Checked in appointment {} -> Live token {}", appointmentId, liveToken.getTokenNumber());
        return liveToken;
    }

    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findWithLockById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found", appointmentId));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED ||
                appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Appointment is already closed (" + appointment.getStatus() + ")");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        log.info("Cancelled appointment {}", appointmentId);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found", appointmentId));
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByBranchAndDate(Long branchId, LocalDate date) {
        return appointmentRepository.findByBranchIdAndAppointmentDate(branchId, date)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =================================================================================================
    // Internal Utiltites
    // =================================================================================================

    private void validateBookingTime(LocalDate date, LocalTime time) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new IllegalArgumentException("Cannot book standard appointments in the past");
        }

        if (date.equals(today) && time.isBefore(LocalTime.now())) {
            throw new IllegalArgumentException("Cannot book a time slot that has already passed today");
        }

        if (time.isBefore(BUSINESS_START) || time.isAfter(BUSINESS_END)) {
            throw new IllegalArgumentException("Appointment time is outside business hours");
        }
    }

    private AppointmentResponse toResponse(Appointment a) {
        if (a == null)
            return null;

        var builder = AppointmentResponse.builder()
                .id(a.getId())
                .branchId(a.getBranch().getId())
                .branchName(a.getBranch().getName())
                .serviceId(a.getService().getId())
                .serviceName(a.getService().getName())
                .customerName(a.getCustomerName())
                .customerPhone(a.getCustomerPhone())
                .customerEmail(a.getCustomerEmail())
                .appointmentDate(a.getAppointmentDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt());

        if (a.getQueueToken() != null) {
            builder.queueTokenId(a.getQueueToken().getId());
            builder.queueTokenNumber(a.getQueueToken().getTokenNumber());
        }

        return builder.build();
    }
}
