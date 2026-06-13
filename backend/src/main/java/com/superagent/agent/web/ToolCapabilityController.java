package com.superagent.agent.web;

import com.superagent.agent.domain.ToolCapabilityItem;
import com.superagent.agent.service.ToolCapabilityService;
import com.superagent.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/tools")
public class ToolCapabilityController {

    private final ToolCapabilityService toolCapabilityService;

    public ToolCapabilityController(ToolCapabilityService toolCapabilityService) {
        this.toolCapabilityService = toolCapabilityService;
    }

    @GetMapping("/capabilities")
    public ApiResponse<ToolCapabilityResponse> listCapabilities() {
        return ApiResponse.success(new ToolCapabilityResponse(toolCapabilityService.listCapabilities()));
    }

    public record ToolCapabilityResponse(List<ToolCapabilityItem> tools) {
    }
}
