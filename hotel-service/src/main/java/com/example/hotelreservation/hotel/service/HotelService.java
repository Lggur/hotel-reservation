package com.example.hotelreservation.hotel.service;

import com.example.hotelreservation.hotel.dto.HotelRequest;
import com.example.hotelreservation.hotel.dto.HotelResponse;
import com.example.hotelreservation.hotel.entity.Hotel;
import com.example.hotelreservation.hotel.exception.ConflictException;
import com.example.hotelreservation.hotel.exception.ResourceNotFoundException;
import com.example.hotelreservation.hotel.mapper.HotelMapper;
import com.example.hotelreservation.hotel.repository.HotelRepository;
import com.example.hotelreservation.hotel.repository.RoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        return hotelMapper.toResponse(hotel);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<HotelResponse> findAll() {
        return hotelRepository.findAllByOrderByIdAsc().stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public HotelResponse findById(Long id) {
        return hotelMapper.toResponse(requireHotel(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = requireHotel(id);
        hotelMapper.update(request, hotel);
        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        Hotel hotel = requireHotel(id);
        if (roomRepository.existsByHotelId(id)) {
            throw new ConflictException("Hotel " + id + " has rooms and cannot be deleted");
        }
        hotelRepository.delete(hotel);
    }

    private Hotel requireHotel(Long id) {
        return hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel " + id + " not found"));
    }
}
