package com.smartqueue.controller;

import com.smartqueue.dto.request.BranchRequest;
import com.smartqueue.dto.request.ServiceRequest;
import com.smartqueue.dto.response.ApiResponse;
import com.smartqueue.dto.response.BranchResponse;
import com.smartqueue.dto.response.DashboardResponse;
import com.smartqueue.dto.response.ServiceResponse;
import com.smartqueue.service.BranchService;
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
@RequestMapping("/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
            @Valid @RequestBody BranchRequest request) {

        log.info("Creating branch: name={}", request.getName());

        BranchResponse branch = branchService.createBranch(request);

        log.info("Branch created: branchId={}", branch.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Branch created", branch));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getAllBranches() {
        List<BranchResponse> branches = branchService.getAllBranches();

        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    @GetMapping("/{branchId}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable Long branchId) {
        BranchResponse branch = branchService.getBranch(branchId);

        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    @PutMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable Long branchId,
            @Valid @RequestBody BranchRequest request) {

        log.info("Updating branch: branchId={}", branchId);

        BranchResponse branch = branchService.updateBranch(branchId, request);

        return ResponseEntity.ok(ApiResponse.success("Branch updated", branch));
    }

    @PostMapping("/{branchId}/services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @PathVariable Long branchId,
            @Valid @RequestBody ServiceRequest request) {

        log.info("Creating service: branchId={}, serviceType={}", branchId, request.getCode());

        // branchId lives on the path; the DTO shouldn't require callers to repeat it in the body
        request.setBranchId(branchId);

        ServiceResponse service = branchService.createService(request);

        log.info("Service created: branchId={}, serviceId={}", branchId, service.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service created", service));
    }

    @GetMapping("/{branchId}/services")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServices(
            @PathVariable Long branchId) {

        List<ServiceResponse> services = branchService.getServicesByBranch(branchId);

        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @GetMapping("/{branchId}/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @PathVariable Long branchId) {

        DashboardResponse dashboard = branchService.getDashboard(branchId);

        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }
}