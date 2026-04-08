package com.smartqueue.model;

import com.smartqueue.model.enums.CounterStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "counters", indexes = {
        @Index(name = "idx_counters_branch_id", columnList = "branch_id"),
        @Index(name = "idx_counters_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_counters_branch_number", columnNames = { "branch_id", "counter_number" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Counter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @NotNull
    @Min(1)
    @Column(name = "counter_number", nullable = false)
    private Integer counterNumber;

    @Size(max = 100)
    @Column(length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CounterStatus status = CounterStatus.CLOSED;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_token_id")
    private QueueToken currentToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToMany
    @JoinTable(name = "counter_services", joinColumns = @JoinColumn(name = "counter_id"), inverseJoinColumns = @JoinColumn(name = "service_id"))
    @Builder.Default
    private Set<ServiceEntity> services = new HashSet<>();

    public boolean isOpen() {
        return status == CounterStatus.OPEN;
    }

    public boolean isBusy() {
        return currentToken != null;
    }

    public boolean canServe(ServiceEntity service) {
        return isOpen() && services.contains(service);
    }

    public void assignToken(QueueToken token) {
        this.currentToken = token;
    }

    public void clearToken() {
        this.currentToken = null;
    }

    public boolean handles(ServiceEntity service) {
        return services != null && services.contains(service);
    }
}