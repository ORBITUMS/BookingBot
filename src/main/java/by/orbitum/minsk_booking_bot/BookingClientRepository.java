package by.orbitum.minsk_booking_bot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// Управляем сущностью BookingClient, у которой ID имеет тип Long (Telegram ID)
public interface BookingClientRepository extends JpaRepository<BookingClient, Long> {
    // Спринг автоматически создаст методы сохранения и поиска
}
