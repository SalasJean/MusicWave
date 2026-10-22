package com.salas.musicwave.api.settings;

import com.salas.musicwave.api.download.LocalOwnerProvider;
import com.salas.musicwave.application.settings.UserSettings;
import com.salas.musicwave.application.settings.UserSettingsRepository;
import com.salas.musicwave.application.settings.UserSettingsService;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void readsCurrentOwnerSettings() throws Exception {
        UserSettingsRepository repository = mock(UserSettingsRepository.class);
        when(repository.getOrCreate(any(), any())).thenReturn(new UserSettings(5, "192K"));

        mvc(repository).perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConcurrency").value(5))
                .andExpect(jsonPath("$.audioQuality").value("192K"));
    }

    @Test
    void updatesAndNormalizesSettings() throws Exception {
        UserSettingsRepository repository = mock(UserSettingsRepository.class);
        when(repository.update(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        mvc(repository).perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrency\":3,\"audioQuality\":\"256k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConcurrency").value(3))
                .andExpect(jsonPath("$.audioQuality").value("256K"));
    }

    private MockMvc mvc(UserSettingsRepository repository) {
        UserSettingsService service = new UserSettingsService(repository, Clock.systemUTC());
        return MockMvcBuilders.standaloneSetup(new SettingsController(
                service, new LocalOwnerProvider(OWNER_ID, "local-user"))).build();
    }
}
