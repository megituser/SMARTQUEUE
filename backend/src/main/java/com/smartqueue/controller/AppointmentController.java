package com.smartqueue.controller;

import com.smartqueue.dto.request.AppointmentRequest;
import com.smartqueue.dto.response.ApiResponse;
import com.smartqueue.dto.response.AppointmentResponse;
import com.smartqueue.dto.response.SlotResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.service.AppointmentService;
import com.smartqueue.websocket.QueueWebSocketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final QueueWebSocketService webSocketService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody AppointmentRequest request) {

        log.info("Booking appointment: branchId={}, serviceId={}", request.getBranchId(), request.getServiceId());

        AppointmentResponse booked = appointmentService.bookAppointment(request);

        log.info("Appointment booked successfully: appointmentId={}", booked.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment booked", booked));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlots(
            @RequestParam Long branchId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> slots = appointmentService.getAvailableSlots(branchId, serviceId, date);

        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    @PostMapping("/{appointmentId}/check-in")
    public ResponseEntity<ApiResponse<TokenResponse>> checkIn(@PathVariable Long appointmentId) {
        log.info("Check-in requested: appointmentId={}", appointmentId);

        TokenResponse token = appointmentService.checkIn(appointmentId);

        // Push queue update immediately so display boards don't lag behind
        webSocketService.broadcastQueueUpdate(token.getBranchId());

        log.info("Check-in complete: appointmentId={}, branchId={}, queuePosition={}",
                appointmentId, token.getBranchId(), token.getPositionInQueue());

        return ResponseEntity.ok(ApiResponse.success("Checked in successfully", token));
    }

    @PostMapping("/{appointmentId}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable Long appointmentId) {

        log.info("Cancellation requested: appointmentId={}", appointmentId);

        AppointmentResponse cancelled = appointmentService.cancelAppointment(appointmentId);

        log.info("Appointment cancelled: appointmentId={}", appointmentId);

        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled", cancelled));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointment(
            @PathVariable Long appointmentId) {

        AppointmentResponse appointment = appointmentService.getAppointment(appointmentId);

        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // Default to today — callers querying "current" state rarely pass an explicit date
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        List<AppointmentResponse> appointments =
                appointmentService.getAppointmentsByBranchAndDate(branchId, targetDate);

        return ResponseEntity.ok(ApiResponse.success(appointments));
    }
}