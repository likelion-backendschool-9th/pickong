package com.pickong.place.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.place.application.dto.PlaceCluster;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.dto.PlaceMapResult;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
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
    @DisplayName("bbox가 없으면 전체를 돌려주지 않고 400")
    void requiresBounds() throws Exception {
        for (String bbox : new String[] {null, " "}) {
            var request = get("/places").param("category", "CAFE").param("cluster", "false");
            if (bbox != null) {
                request.param("bbox", bbox);
            }
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        verifyNoInteractions(places);
    }

    @Test
    @DisplayName("bbox는 서경, 남위, 동경, 북위 순서로 전달하고 묶음을 돌려준다")
    void bounds() throws Exception {
        PlaceMapQuery query = new PlaceMapQuery(126.0, 37.0, 127.0, 38.0, null);
        given(places.findMap(query)).willReturn(new PlaceMapResult(
                List.of(item()), List.of(new PlaceCluster(37.5, 126.5, 12, 126.4, 37.4, 126.6, 37.6))));

        mockMvc.perform(get("/places").param("bbox", "126,37,127,38"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].id").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.places[0].lat").value(37.5628))
                .andExpect(jsonPath("$.clusters[0].lat").value(37.5))
                .andExpect(jsonPath("$.clusters[0].lng").value(126.5))
                .andExpect(jsonPath("$.clusters[0].count").value(12))
                .andExpect(jsonPath("$.clusters[0].bounds.west").value(126.4))
                .andExpect(jsonPath("$.clusters[0].bounds.north").value(37.6));
    }

    @Test
    @DisplayName("카테고리를 전달한다")
    void category() throws Exception {
        PlaceMapQuery query = new PlaceMapQuery(126.0, 37.0, 127.0, 38.0, "CAFE");
        given(places.findMap(query)).willReturn(new PlaceMapResult(List.of(), List.of()));

        mockMvc.perform(get("/places").param("bbox", "126,37,127,38").param("category", "CAFE"))
                .andExpect(status().isOk());
        verify(places).findMap(query);
    }

    @Test
    @DisplayName("cluster=false면 묶지 않은 목록을 돌려준다")
    void withoutCluster() throws Exception {
        given(places.findInBounds(new PlaceMapQuery(126.0, 37.0, 127.0, 38.0, null))).willReturn(List.of(item()));

        mockMvc.perform(get("/places").param("bbox", "126,37,127,38").param("cluster", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].id").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.clusters").isEmpty());
    }

    @Test
    @DisplayName("bbox에 NaN이나 Infinity가 있으면 400")
    void notFiniteBounds() throws Exception {
        for (String bbox : List.of("NaN,37,127,38", "126,37,Infinity,38")) {
            mockMvc.perform(get("/places").param("bbox", bbox))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    @DisplayName("bbox 형식이 아니면 400")
    void invalidBounds() throws Exception {
        mockMvc.perform(get("/places").param("bbox", "126,37"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private static PlaceMapItem item() {
        return new PlaceMapItem(PUBLIC_ID, "책방", "서울", 37.5628, 126.9254, "SHOP", "CUSTOM");
    }
}
