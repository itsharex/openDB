package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.ConnectionProfileRequest;
import com.opendb.dto.ConnectionProfileResponse;
import com.opendb.dto.ConnectionRequest;
import com.opendb.dto.ConnectionResponse;
import com.opendb.service.ConnectionProfileService;
import com.opendb.service.ConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ConnectionProfileController {

    private final ConnectionProfileService profileService;
    private final ConnectionService connectionService;

    @GetMapping
    public ApiResponse<List<ConnectionProfileResponse>> list() {
        return ApiResponse.ok(profileService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectionProfileResponse> get(@PathVariable String id) {
        return ApiResponse.ok(profileService.get(id));
    }

    @PostMapping("/upsert")
    public ApiResponse<ConnectionProfileResponse> upsert(@Valid @RequestBody ConnectionProfileRequest request) {
        return ApiResponse.ok("Profile saved", profileService.upsert(request));
    }

    @PostMapping
    public ApiResponse<ConnectionProfileResponse> create(@Valid @RequestBody ConnectionProfileRequest request) {
        return ApiResponse.ok("Profile saved", profileService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConnectionProfileResponse> update(@PathVariable String id,
                                                         @Valid @RequestBody ConnectionProfileRequest request) {
        return ApiResponse.ok("Profile updated", profileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        profileService.delete(id);
        return ApiResponse.ok("Profile deleted", null);
    }

    @PostMapping("/{id}/connect")
    public ApiResponse<ConnectionResponse> connect(@PathVariable String id) {
        return ApiResponse.ok("Connected", connectionService.connectProfile(id));
    }
}
