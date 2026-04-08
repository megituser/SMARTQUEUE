package com.smartqueue.config;

import com.smartqueue.model.Branch;
import com.smartqueue.model.Counter;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.User;
import com.smartqueue.model.enums.CounterStatus;
import com.smartqueue.model.enums.UserRole;
import com.smartqueue.repository.BranchRepository;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (branchRepository.count() > 0) {
            log.info("Data already seeded, skipping...");
            return;
        }

        log.info("Seeding demo data...");

        // Create branches
        Branch hospital = branchRepository.save(Branch.builder()
                .name("City General Hospital")
                .code("CGH")
                .address("123 Medical Center Drive, Downtown")
                .phone("+1-555-0100")
                .timezone("America/New_York")
                .build());

        Branch bank = branchRepository.save(Branch.builder()
                .name("National Bank - Main Branch")
                .code("NBMAIN")
                .address("456 Financial Avenue, Business District")
                .phone("+1-555-0200")
                .timezone("America/New_York")
                .build());

        // Create services for hospital
        ServiceEntity generalConsult = serviceRepository.save(ServiceEntity.builder()
                .branch(hospital).name("General Consultation").code("GC")
                .description("General medical consultation").avgServiceTimeMinutes(20).build());
        ServiceEntity labTests = serviceRepository.save(ServiceEntity.builder()
                .branch(hospital).name("Lab Tests").code("LT")
                .description("Blood work and laboratory tests").avgServiceTimeMinutes(10).build());
        ServiceEntity pharmacy = serviceRepository.save(ServiceEntity.builder()
                .branch(hospital).name("Pharmacy").code("PH")
                .description("Prescription pickup").avgServiceTimeMinutes(5).build());

        // Create services for bank
        ServiceEntity accountOpen = serviceRepository.save(ServiceEntity.builder()
                .branch(bank).name("Account Opening").code("AO")
                .description("Open a new bank account").avgServiceTimeMinutes(30).build());
        ServiceEntity cashDeposit = serviceRepository.save(ServiceEntity.builder()
                .branch(bank).name("Cash Deposit/Withdrawal").code("CD")
                .description("Cash transactions").avgServiceTimeMinutes(10).build());
        ServiceEntity loanService = serviceRepository.save(ServiceEntity.builder()
                .branch(bank).name("Loan Services").code("LS")
                .description("Loan applications and inquiries").avgServiceTimeMinutes(45).build());

        // Create counters for hospital
        Counter hCounter1 = createCounter(hospital, 1, "Reception", Set.of(generalConsult, labTests));
        Counter hCounter2 = createCounter(hospital, 2, "Lab Counter", Set.of(labTests));
        Counter hCounter3 = createCounter(hospital, 3, "Pharmacy Counter", Set.of(pharmacy));

        // Create counters for bank
        Counter bCounter1 = createCounter(bank, 1, "General Teller", Set.of(cashDeposit));
        Counter bCounter2 = createCounter(bank, 2, "Account Services", Set.of(accountOpen, loanService));
        Counter bCounter3 = createCounter(bank, 3, "Premium Counter", Set.of(accountOpen, cashDeposit, loanService));

        // Create users
        userRepository.save(User.builder()
                .email("admin@smartqueue.com").passwordHash(passwordEncoder.encode("admin123"))
                .firstName("System").lastName("Admin").role(UserRole.SUPER_ADMIN).build());

        userRepository.save(User.builder()
                .email("hospital.admin@smartqueue.com").passwordHash(passwordEncoder.encode("admin123"))
                .firstName("Dr. Sarah").lastName("Johnson").role(UserRole.BRANCH_ADMIN).branch(hospital).build());

        userRepository.save(User.builder()
                .email("bank.admin@smartqueue.com").passwordHash(passwordEncoder.encode("admin123"))
                .firstName("Michael").lastName("Chen").role(UserRole.BRANCH_ADMIN).branch(bank).build());

        userRepository.save(User.builder()
                .email("staff1@smartqueue.com").passwordHash(passwordEncoder.encode("staff123"))
                .firstName("Emily").lastName("Wilson").role(UserRole.STAFF).branch(hospital).build());

        userRepository.save(User.builder()
                .email("staff2@smartqueue.com").passwordHash(passwordEncoder.encode("staff123"))
                .firstName("James").lastName("Brown").role(UserRole.STAFF).branch(bank).build());

        userRepository.save(User.builder()
                .email("receptionist@smartqueue.com").passwordHash(passwordEncoder.encode("staff123"))
                .firstName("Lisa").lastName("Garcia").role(UserRole.STAFF).branch(hospital).build());

        log.info("Demo data seeded successfully!");
        log.info("=== Login Credentials ===");
        log.info("Super Admin:      admin@smartqueue.com / admin123");
        log.info("Hospital Admin:   hospital.admin@smartqueue.com / admin123");
        log.info("Bank Admin:       bank.admin@smartqueue.com / admin123");
        log.info("Hospital Staff:   staff1@smartqueue.com / staff123");
        log.info("Bank Staff:       staff2@smartqueue.com / staff123");
        log.info("Receptionist:     receptionist@smartqueue.com / staff123");
    }

    private Counter createCounter(Branch branch, int number, String name, Set<ServiceEntity> services) {
        Counter counter = Counter.builder()
                .branch(branch)
                .counterNumber(number)
                .name(name)
                .status(CounterStatus.CLOSED)
                .services(new HashSet<>(services))
                .build();
        return counterRepository.save(counter);
    }
}
