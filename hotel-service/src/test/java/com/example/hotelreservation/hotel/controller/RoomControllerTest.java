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
    "eureka.client.enabled=false", "security.jwt.secret=" + TestJwt.SECRET,
  }
)
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void createRoomAsAdmin() throws Exception {
    long hotelId = createHotel("Hotel", "Address");

    mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":"101"}
            """.formatted(hotelId)
          )
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isNumber())
      .andExpect(jsonPath("$.hotelId").value(hotelId))
      .andExpect(jsonPath("$.hotelName").value("Hotel"))
      .andExpect(jsonPath("$.number").value("101"))
      .andExpect(jsonPath("$.available").value(true))
      .andExpect(jsonPath("$.timesBooked").value(0));
  }

  @Test
  void createRoomAsUserIsForbidden() throws Exception {
    long hotelId = createHotel("Hotel", "Address");

    mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":"101"}
            """.formatted(hotelId)
          )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void createRoomForMissingHotelIsNotFound() throws Exception {
    mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":999999,"number":"101"}
            """
          )
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void createRoomWithBlankNumberIsBadRequest() throws Exception {
    long hotelId = createHotel("Hotel", "Address");

    mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":""}
            """.formatted(hotelId)
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors.number").exists());
  }

  @Test
  void createDuplicateRoomNumberIsConflict() throws Exception {
    long hotelId = createHotel("Hotel", "Address");
    createRoom(hotelId, "101A");

    mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":"101a"}
            """.formatted(hotelId)
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void listRoomsFilteredByHotel() throws Exception {
    long firstHotelId = createHotel("First", "Address 1");
    long secondHotelId = createHotel("Second", "Address 2");
    createRoom(firstHotelId, "101");
    createRoom(secondHotelId, "201");

    mockMvc
      .perform(
        get("/api/rooms")
          .param("hotelId", String.valueOf(firstHotelId))
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user()))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].hotelId").value(firstHotelId))
      .andExpect(jsonPath("$[0].number").value("101"));
  }

  @Test
  void updateRoomAsAdmin() throws Exception {
    long hotelId = createHotel("Hotel", "Address");
    long roomId = createRoom(hotelId, "101");

    mockMvc
      .perform(
        put("/api/rooms/{id}", roomId)
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":"102","available":false}
            """.formatted(hotelId)
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.number").value("102"))
      .andExpect(jsonPath("$.available").value(false));
  }

  @Test
  void deleteRoomAsAdmin() throws Exception {
    long hotelId = createHotel("Hotel", "Address");
    long roomId = createRoom(hotelId, "101");

    mockMvc
      .perform(
        delete("/api/rooms/{id}", roomId).header(
          HttpHeaders.AUTHORIZATION,
          TestJwt.bearer(TestJwt.admin())
        )
      )
      .andExpect(status().isNoContent());

    mockMvc
      .perform(
        get("/api/rooms/{id}", roomId).header(
          HttpHeaders.AUTHORIZATION,
          TestJwt.bearer(TestJwt.user())
        )
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void getRoomNotFound() throws Exception {
    mockMvc
      .perform(
        get("/api/rooms/{id}", 999_999).header(
          HttpHeaders.AUTHORIZATION,
          TestJwt.bearer(TestJwt.user())
        )
      )
      .andExpect(status().isNotFound());
  }

  private long createHotel(String name, String address) throws Exception {
    MvcResult result = mockMvc
      .perform(
        post("/api/hotels")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"name":"%s","address":"%s"}
            """.formatted(name, address)
          )
      )
      .andExpect(status().isCreated())
      .andReturn();

    return objectMapper
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asLong();
  }

  private long createRoom(long hotelId, String number) throws Exception {
    MvcResult result = mockMvc
      .perform(
        post("/api/rooms")
          .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"hotelId":%d,"number":"%s"}
            """.formatted(hotelId, number)
          )
      )
      .andExpect(status().isCreated())
      .andReturn();

    return objectMapper
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asLong();
  }
}
