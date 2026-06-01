package com.superagent.api.system;

import com.superagent.common.api.ApiResponse;
import com.superagent.infra.bootstrap.BootstrapCatalogService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/system")
public class SystemBootstrapController {

    private final BootstrapCatalogService bootstrapCatalogService;

    public SystemBootstrapController(BootstrapCatalogService bootstrapCatalogService) {
        this.bootstrapCatalogService = bootstrapCatalogService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<BootstrapCatalogService.BootstrapSummary> getBootstrapSummary() {
        return ApiResponse.success(bootstrapCatalogService.getSummary());
    }

    @GetMapping("/modules/{module}")
    public ApiResponse<BootstrapCatalogService.ModuleDescriptor> getModule(
            @PathVariable
            @Pattern(regexp = "^[a-z]+$", message = "module must be lowercase letters")
            String module
    ) {
        return ApiResponse.success(bootstrapCatalogService.getModule(module));
    }
}
