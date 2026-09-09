package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    List<Appointment> findAllByClientTelegramId(Long clientTelegramId);

    boolean existsByBookingDateAndBookingTime(LocalDate bookingDate, String bookingTime);

    // 💥 Подсчитать, сколько раз этот клиент записался на услугу с конкретным именем
    long countByClientTelegramIdAndServiceName(Long clientTelegramId, String serviceName);

    // 💥 Вытащить ВСЕ записи из базы и автоматически отсортировать их: сначала по Дате, а внутри даты — по Времени!
    List<Appointment> findAllByOrderByBookingDateAscBookingTimeAsc();
}
