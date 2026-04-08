package com.smartqueue.service;

import com.smartqueue.dto.request.AppointmentRequest;
import com.smartqueue.dto.response.AppointmentResponse;
import com.smartqueue.dto.response.SlotResponse;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.exception.BusinessException;
import com.smartqueue.exception.DuplicateResourceException;
import com.smartqueue.model.*;
import com.smartqueue.model.enums.*;
import com.smartqueue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private QueueService queueService;

    @InjectMocks private AppointmentService appointmentService;

    private Branch testBranch;
    private ServiceEntity testService;

    @BeforeEach
    void setUp() {
        testBranch = Branch.builder().id(1L).name("Test Hospital").code("TH").build();
        testService = ServiceEntity.builder().id(1L).branch(testBranch).name("General").code("GC").avgServiceTimeMinutes(15).build();
    }

    @Nested
    @DisplayName("Book Appointment")
    class BookAppointment {

        @Test
        @DisplayName("Should book appointment successfully")
        void shouldBookAppointment() {
            LocalDate futureDate = LocalDate.now().plusDays(1);
            AppointmentRequest request = AppointmentRequest.builder()
                    .branchId(1L).serviceId(1L)
                    .customerName("Jane Doe").customerPhone("+1234567890")
                    .appointmentDate(futureDate).startTime(LocalTime.of(10, 0))
                    .build();

            when(branchRepository.findWithLockById(1L)).thenReturn(Optional.of(testBranch));
            when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
            when(appointmentRepository.existsByBranchIdAndServiceIdAndAppointmentDateAndStartTimeAndStatusNot(
                    any(), any(), any(), any(), any())).thenReturn(false);
            when(appointmentRepository.save(any())).thenAnswer(inv -> {
                Appointment a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

            AppointmentResponse result = appointmentService.bookAppointment(request);

            assertThat(result).isNotNull();
            assertThat(result.getCustomerName()).isEqualTo("Jane Doe");
            assertThat(result.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        }

        @Test
        @DisplayName("Should prevent double-booking")
        void shouldPreventDoubleBooking() {
            LocalDate futureDate = LocalDate.now().plusDays(1);
            AppointmentRequest request = AppointmentRequest.builder()
                    .branchId(1L).serviceId(1L)
                    .customerName("Jane").appointmentDate(futureDate).startTime(LocalTime.of(10, 0))
                    .build();

            when(branchRepository.findWithLockById(1L)).thenReturn(Optional.of(testBranch));
            when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
            when(appointmentRepository.existsByBranchIdAndServiceIdAndAppointmentDateAndStartTimeAndStatusNot(
                    any(), any(), any(), any(), any())).thenReturn(true);

            assertThatThrownBy(() -> appointmentService.bookAppointment(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no longer available");
        }

        @Test
        @DisplayName("Should reject past date appointments")
        void shouldRejectPastDate() {
            AppointmentRequest request = AppointmentRequest.builder()
                    .branchId(1L).serviceId(1L)
                    .customerName("Jane").appointmentDate(LocalDate.now().minusDays(1)).startTime(LocalTime.of(10, 0))
                    .build();

            when(branchRepository.findWithLockById(1L)).thenReturn(Optional.of(testBranch));
            when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));

            assertThatThrownBy(() -> appointmentService.bookAppointment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past");
        }
    }

    @Nested
    @DisplayName("Check-In")
    class CheckIn {

        @Test
        @DisplayName("Should convert appointment to queue token")
        void shouldCheckIn() {
            Appointment appointment = Appointment.builder()
                    .id(1L).branch(testBranch).service(testService)
                    .customerName("Jane").status(AppointmentStatus.BOOKED)
                    .build();

            TokenResponse mockToken = TokenResponse.builder()
                    .id(1L).tokenNumber("GC001").branchId(1L).branchName("Test")
                    .status(TokenStatus.WAITING).priority(TokenPriority.HIGH)
                    .build();

            when(appointmentRepository.findWithLockById(1L)).thenReturn(Optional.of(appointment));
            when(queueService.issueToken(any())).thenReturn(mockToken);
            when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TokenResponse result = appointmentService.checkIn(1L);

            assertThat(result.getPriority()).isEqualTo(TokenPriority.HIGH);
            verify(appointmentRepository).save(argThat(a -> a.getStatus() == AppointmentStatus.CHECKED_IN));
        }
    }

    @Nested
    @DisplayName("Get Available Slots")
    class GetSlots {

        @Test
        @DisplayName("Should return slots with availability")
        void shouldReturnSlots() {
            when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
            when(appointmentRepository.findActiveAppointments(1L, 1L, LocalDate.now().plusDays(1)))
                    .thenReturn(List.of());

            List<SlotResponse> slots = appointmentService.getAvailableSlots(1L, 1L, LocalDate.now().plusDays(1));

            assertThat(slots).isNotEmpty();
            assertThat(slots.stream().filter(SlotResponse::isAvailable).count()).isGreaterThan(0);
        }
    }
}
