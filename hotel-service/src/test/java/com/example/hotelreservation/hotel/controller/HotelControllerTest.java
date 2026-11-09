package com.example.hotelreservation.hotel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotelreservation.hotel.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "security.jwt.secret=" + TestJwt.SECRET,
        })
@AutoConfigureMockMvc
@Transactional
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createHotelAsAdmin() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"Grand Hotel","address":"Main street 1"}
            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Grand Hotel"))
                .andExpect(jsonPath("$.address").value("Main street 1"));
    }

    @Test
    void createHotelAsUserIsForbidden() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"Grand Hotel","address":"Main street 1"}
            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createHotelWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"Grand Hotel","address":"Main street 1"}
            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createHotelWithBlankNameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"","address":"Main street 1"}
            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void listHotelsAsUser() throws Exception {
        createHotel("First", "Address 1");
        createHotel("Second", "Address 2");

        mockMvc.perform(get("/api/hotels").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void getHotelNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateHotelAsAdmin() throws Exception {
        long hotelId = createHotel("Old name", "Old address");

        mockMvc.perform(put("/api/hotels/{id}", hotelId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"New name","address":"New address"}
            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hotelId))
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.address").value("New address"));
    }

    @Test
    void deleteHotelAsAdmin() throws Exception {
        long hotelId = createHotel("To delete", "Address");

        mockMvc.perform(delete("/api/hotels/{id}", hotelId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hotels/{id}", hotelId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHotelWithRoomsIsConflict() throws Exception {
        long hotelId = createHotel("Hotel with rooms", "Address");

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"hotelId":%d,"number":"101"}
            """.formatted(hotelId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/hotels/{id}", hotelId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isConflict());
    }

    @Test
    void openApiEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private long createHotel(String name, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"name":"%s","address":"%s"}
            """.formatted(name, address)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
    }
}
