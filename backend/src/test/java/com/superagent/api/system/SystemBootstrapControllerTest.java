package com.superagent.api.system;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.common.exception.GlobalExceptionHandler;
import com.superagent.infra.bootstrap.BootstrapCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemBootstrapController.class)
@Import(GlobalExceptionHandler.class)
class SystemBootstrapControllerTest {

    @MockBean
    private BootstrapCatalogService bootstrapCatalogService;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnBootstrapSummary() throws Exception {
        when(bootstrapCatalogService.getSummary()).thenReturn(new BootstrapCatalogService.BootstrapSummary(
                "superagent-backend",
                "/api/v1",
                List.of(new BootstrapCatalogService.ModuleDescriptor("api", "HTTP API")),
                new BootstrapCatalogService.RuntimeDependencySummary(
                        "superagent-documents",
                        "http://localhost:9000",
                        "localhost:9092",
                        "superagent.document.task",
                        false,
                        "gpt-4.1-mini",
                        "text-embedding-3-small",
                        false
                )
        ));

        mockMvc.perform(get("/api/v1/system/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationName").value("superagent-backend"))
                .andExpect(jsonPath("$.data.modules[0].code").value("api"));
    }

    @Test
    void shouldReturnCustomErrorWhenModuleDoesNotExist() throws Exception {
        when(bootstrapCatalogService.getModule("unknown")).thenThrow(new AppException(
                ErrorCode.MODULE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Module not found: unknown"
        ));

        mockMvc.perform(get("/api/v1/system/modules/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void shouldReturnValidationErrorForInvalidModuleName() throws Exception {
        mockMvc.perform(get("/api/v1/system/modules/API"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
