package com.example.studyroom.repository;

import com.example.studyroom.domain.Reservation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaReservationRepository implements ReservationRepository{

    private final SpringDataReservationRepository delegate;


    public JpaReservationRepository(SpringDataReservationRepository delegate){
        this.delegate = delegate;
    }

    @Override
    public Reservation save(Reservation reservation){
        return delegate.save(reservation);
    }

    @Override
    public Optional<Reservation> findById(Long id){
        return delegate.findById(id);
    }

    @Override
    public List<Reservation> findAll(){
        return delegate.findAll();
    }

    @Override
    public List<Reservation> findAllWithMember(){
        return delegate.findAllWithMember();
    }

    @Override
    public List<Reservation> findAllWithMemberOrNull(){
        return delegate.findAllWithMemberOrNull();
    }

    @Override
    public List<Reservation> findOverlapping(String roomName, LocalDateTime startAt, LocalDateTime endAt){
        return delegate.findOverlapping(roomName, startAt, endAt);
    }
}
