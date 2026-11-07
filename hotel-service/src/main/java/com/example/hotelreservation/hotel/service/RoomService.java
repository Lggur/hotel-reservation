package com.example.hotelreservation.hotel.service;

import com.example.hotelreservation.hotel.dto.RoomRequest;
import com.example.hotelreservation.hotel.dto.RoomResponse;
import com.example.hotelreservation.hotel.entity.Hotel;
import com.example.hotelreservation.hotel.entity.Room;
import com.example.hotelreservation.hotel.exception.ConflictException;
import com.example.hotelreservation.hotel.exception.ResourceNotFoundException;
import com.example.hotelreservation.hotel.mapper.RoomMapper;
import com.example.hotelreservation.hotel.repository.HotelRepository;
import com.example.hotelreservation.hotel.repository.RoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponse create(RoomRequest request) {
        Hotel hotel = requireHotel(request.hotelId());
        ensureNumberAvailable(request.hotelId(), request.number(), null);

        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        room.setAvailable(request.available() == null || request.available());

        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> findAll(Long hotelId) {
        List<Room> rooms = hotelId == null
                ? roomRepository.findAllByOrderByIdAsc()
                : roomRepository.findAllByHotelIdOrderByIdAsc(hotelId);

        return rooms.stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return roomMapper.toResponse(requireRoom(id));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = requireRoom(id);
        Hotel hotel = requireHotel(request.hotelId());
        ensureNumberAvailable(request.hotelId(), request.number(), id);

        roomMapper.update(request, room);
        room.setHotel(hotel);
        if (request.available() != null) {
            room.setAvailable(request.available());
        }

        return roomMapper.toResponse(room);
    }

    @Transactional
    public void delete(Long id) {
        roomRepository.delete(requireRoom(id));
    }

    private Room requireRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room " + id + " not found"));
    }

    private Hotel requireHotel(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel " + id + " not found"));
    }

    private void ensureNumberAvailable(Long hotelId, String number, Long excludedRoomId) {
        boolean exists = excludedRoomId == null
                ? roomRepository.existsByHotelIdAndNumberIgnoreCase(hotelId, number)
                : roomRepository.existsByHotelIdAndNumberIgnoreCaseAndIdNot(hotelId, number, excludedRoomId);

        if (exists) {
            throw new ConflictException("Room number " + number + " already exists in hotel " + hotelId);
        }
    }
}
