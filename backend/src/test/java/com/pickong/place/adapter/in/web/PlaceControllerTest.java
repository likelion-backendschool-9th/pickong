package com.pickong.place.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.vo.PlaceSourceType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    private static final UUID PUBLIC_ID = UUID.fromString("6f1e8c2a-1b4d-4e7a-9c3d-0a1b2c3d4e5f");

    @Mock
    PlaceQueryUseCase places;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaceController(places))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .build();
    }

    @Test
    @DisplayName("공개 목록은 places의 공개 id만 돌려준다")
    void listsPublicPlaces() throws Exception {
        given(places.findPublic()).willReturn(List.of(place()));

        mockMvc.perform(get("/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("책방"))
                .andExpect(jsonPath("$[0].lat").value(37.5628))
                .andExpect(jsonPath("$[0].lng").value(126.9254))
                .andExpect(jsonPath("$[0].sourceType").value("CUSTOM"))
                .andExpect(jsonPath("$[0].visibility").doesNotExist());
    }

    @Test
    @DisplayName("bbox는 서경, 남위, 동경, 북위 순서로 전달한다")
    void bounds() throws Exception {
        given(places.findInBounds(126.0, 37.0, 127.0, 38.0)).willReturn(List.of());

        mockMvc.perform(get("/places").param("bbox", "126,37,127,38"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(places).findInBounds(126.0, 37.0, 127.0, 38.0);
    }

    @Test
    @DisplayName("bbox 형식이 아니면 400")
    void invalidBounds() throws Exception {
        mockMvc.perform(get("/places").param("bbox", "126,37"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private static Place place() {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 24, 5, 0, 0, 0, ZoneOffset.UTC);
        return Place.builder()
                .id(9L)
                .publicId(PUBLIC_ID)
                .name("책방")
                .address("서울")
                .location(new GeoPoint(37.5628, 126.9254))
                .category("SHOP")
                .sourceType(PlaceSourceType.CUSTOM)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
