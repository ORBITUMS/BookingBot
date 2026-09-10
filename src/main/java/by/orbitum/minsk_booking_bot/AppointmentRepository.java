package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    List<Appointment> findAllByClientTelegramIdAndBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(Long clientTelegramId, LocalDate date);

    boolean existsByBookingDateAndBookingTime(LocalDate bookingDate, String bookingTime);

    long countByClientTelegramIdAndServiceNameAndBookingDateGreaterThanEqual(Long clientTelegramId, String serviceName, LocalDate date);

    List<Appointment> findAllByOrderByBookingDateAscBookingTimeAsc();

    // 💥 НОВЫЙ МЕТОД ДЛЯ АДМИНА: Находит все записи, начиная с указанной даты (сортировка от ранних к поздним)
    List<Appointment> findAllByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(LocalDate date);
}
