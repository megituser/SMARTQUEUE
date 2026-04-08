package com.smartqueue.service;

import com.smartqueue.dto.request.TokenRequest;
import com.smartqueue.dto.response.TokenResponse;
import com.smartqueue.exception.BusinessException;
import com.smartqueue.exception.ResourceNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private QueueTokenRepository tokenRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private CounterRepository counterRepository;

    @InjectMocks private QueueService queueService;

    private Branch testBranch;
    private ServiceEntity testService;
    private Counter testCounter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "maxTokensPerBranch", 999);
        ReflectionTestUtils.setField(queueService, "defaultServiceTime", 15);

        testBranch = Branch.builder().id(1L).name("Test Hospital").code("TH").build();
        testService = ServiceEntity.builder().id(1L).branch(testBranch).name("General").code("GC").avgServiceTimeMinutes(15).build();
        testCounter = Counter.builder().id(1L).branch(testBranch).counterNumber(1).name("Counter 1")
                .status(CounterStatus.OPEN).services(Set.of(testService)).build();
    }

    @Nested
    @DisplayName("Token Issuance")
    class IssueToken {

        @Test
        @DisplayName("Should issue token with correct format")
        void shouldIssueToken() {
            TokenRequest request = TokenRequest.builder()
                    .branchId(1L).serviceId(1L)
                    .customerName("John Doe").customerPhone("+1234567890")
                    .priority(TokenPriority.NORMAL).source(TokenSource.WALK_IN)
                    .build();

            when(branchRepository.findWithLockById(1L)).thenReturn(Optional.of(testBranch));
            when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
            when(tokenRepository.findMaxTokenSequence(eq(1L), any())).thenReturn(null);
            when(tokenRepository.countByBranchIdAndStatus(1L, TokenStatus.WAITING)).thenReturn(0);
            when(counterRepository.countByBranchIdAndStatus(1L, CounterStatus.OPEN)).thenReturn(1);
            when(tokenRepository.findAverageServiceTime(eq(1L), any())).thenReturn(15.0);
            when(tokenRepository.save(any(QueueToken.class))).thenAnswer(inv -> {
                QueueToken t = inv.getArgument(0);
                t.setId(1L);
                t.setIssuedAt(LocalDateTime.now());
                return t;
            });

            TokenResponse result = queueService.issueToken(request);

            assertThat(result).isNotNull();
            assertThat(result.getTokenNumber()).startsWith("GC");
            assertThat(result.getStatus()).isEqualTo(TokenStatus.WAITING);
            assertThat(result.getCustomerName()).isEqualTo("John Doe");
            verify(tokenRepository).save(any(QueueToken.class));
        }

        @Test
        @DisplayName("Should throw when branch not found")
        void shouldThrowWhenBranchNotFound() {
            TokenRequest request = TokenRequest.builder().branchId(99L).serviceId(1L).build();
            when(branchRepository.findWithLockById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> queueService.issueToken(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when service doesn't belong to branch")
        void shouldValidateServiceBranch() {
            Branch otherBranch = Branch.builder().id(2L).name("Other").code("OT").build();
            ServiceEntity otherService = ServiceEntity.builder().id(2L).branch(otherBranch).name("Other").code("OT").build();

            TokenRequest request = TokenRequest.builder().branchId(1L).serviceId(2L).build();
            when(branchRepository.findWithLockById(1L)).thenReturn(Optional.of(testBranch));
            when(serviceRepository.findById(2L)).thenReturn(Optional.of(otherService));

            assertThatThrownBy(() -> queueService.issueToken(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not available at this branch");
        }
    }

    @Nested
    @DisplayName("Call Next Token")
    class CallNext {

        @Test
        @DisplayName("Should call next token and update counter")
        void shouldCallNextToken() {
            QueueToken waitingToken = QueueToken.builder()
                    .id(1L).tokenNumber("GC001").branch(testBranch).service(testService)
                    .status(TokenStatus.WAITING).priority(TokenPriority.NORMAL)
                    .issuedAt(LocalDateTime.now())
                    .build();

            when(counterRepository.findWithLockById(1L)).thenReturn(Optional.of(testCounter));
            when(tokenRepository.findNextTokenToCall(any(), any(), any())).thenReturn(List.of(waitingToken));
            when(tokenRepository.findWithLockById(1L)).thenReturn(Optional.of(waitingToken));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TokenResponse result = queueService.callNextToken(1L);

            assertThat(result.getStatus()).isEqualTo(TokenStatus.SERVING);
            assertThat(result.getTokenNumber()).isEqualTo("GC001");
            verify(tokenRepository).save(any());
            verify(counterRepository).save(any());
        }

        @Test
        @DisplayName("Should throw when counter not open")
        void shouldThrowWhenCounterClosed() {
            testCounter.setStatus(CounterStatus.CLOSED);
            when(counterRepository.findWithLockById(1L)).thenReturn(Optional.of(testCounter));

            assertThatThrownBy(() -> queueService.callNextToken(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("Should throw when counter already has active token")
        void shouldThrowWhenCounterBusy() {
            testCounter.setCurrentToken(QueueToken.builder().id(99L).build());
            when(counterRepository.findWithLockById(1L)).thenReturn(Optional.of(testCounter));

            assertThatThrownBy(() -> queueService.callNextToken(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("busy");
        }

        @Test
        @DisplayName("Should throw when no waiting tokens")
        void shouldThrowWhenNoWaiting() {
            when(counterRepository.findWithLockById(1L)).thenReturn(Optional.of(testCounter));
            when(tokenRepository.findNextTokenToCall(any(), any(), any())).thenReturn(List.of());

            assertThatThrownBy(() -> queueService.callNextToken(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No waiting customers");
        }
    }

    @Nested
    @DisplayName("Complete Service")
    class CompleteService {

        @Test
        @DisplayName("Should complete service and clear counter")
        void shouldCompleteService() {
            QueueToken servingToken = QueueToken.builder()
                    .id(1L).tokenNumber("GC001").branch(testBranch).service(testService)
                    .status(TokenStatus.SERVING).counter(testCounter)
                    .build();
            testCounter.setCurrentToken(servingToken);

            when(counterRepository.findWithLockById(1L)).thenReturn(Optional.of(testCounter));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TokenResponse result = queueService.completeService(1L);

            assertThat(result.getStatus()).isEqualTo(TokenStatus.COMPLETED);
            verify(counterRepository).save(argThat(c -> c.getCurrentToken() == null));
        }
    }

    @Nested
    @DisplayName("Cancel Token")
    class CancelToken {

        @Test
        @DisplayName("Should cancel waiting token")
        void shouldCancelToken() {
            QueueToken waitingToken = QueueToken.builder()
                    .id(1L).tokenNumber("GC001").branch(testBranch).service(testService)
                    .status(TokenStatus.WAITING)
                    .build();

            when(tokenRepository.findWithLockById(1L)).thenReturn(Optional.of(waitingToken));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TokenResponse result = queueService.cancelToken(1L);
            assertThat(result.getStatus()).isEqualTo(TokenStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should reject cancellation of non-waiting token")
        void shouldRejectNonWaiting() {
            QueueToken servingToken = QueueToken.builder()
                    .id(1L).status(TokenStatus.SERVING).build();
            when(tokenRepository.findWithLockById(1L)).thenReturn(Optional.of(servingToken));

            assertThatThrownBy(() -> queueService.cancelToken(1L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
