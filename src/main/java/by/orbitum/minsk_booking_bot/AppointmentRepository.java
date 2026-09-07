package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    // Метод 1: Найдет все записи конкретного клиента из Минска
    List<Appointment> findByClientTelegramId(Long clientTelegramId);

    // Метод 2: Проверит, существует ли в базе запись на эту дату и это время
    boolean existsByBookingDateAndBookingTime(LocalDate bookingDate, String bookingTime);

    List<Appointment> findAllByClientTelegramId(Long clientTelegramId);
}
