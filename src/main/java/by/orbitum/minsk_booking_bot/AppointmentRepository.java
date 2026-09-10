package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    // 1. Ищем будущие записи конкретного клиента для экрана «Мои записи»
    List<Appointment> findAllByClientTelegramIdAndBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(Long clientTelegramId, LocalDate date);

    // 2. Проверяем, занято ли конкретное окошко (для скрытия кнопок времени)
    boolean existsByBookingDateAndBookingTime(LocalDate bookingDate, String bookingTime);

    // 3. Считаем лимит будущих записей на одну услугу (защита от спама)
    long countByClientTelegramIdAndServiceNameAndBookingDateGreaterThanEqual(Long clientTelegramId, String serviceName, LocalDate date);

    // 4. Панель директора: расписание от сегодняшнего дня и выше по порядку
    List<Appointment> findAllByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(LocalDate date);

    // 5. Будильник: ищем записи на конкретный час, которые еще не получили напоминание
    List<Appointment> findAllByBookingDateAndBookingTimeAndNotifiedFalse(LocalDate date, String time);
}
