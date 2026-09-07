package by.orbitum.minsk_booking_bot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// Мы наследуемся от JpaRepository и говорим, что управляем сущностью BeautyService, у которой ID имеет тип Long
public interface BeautyServiceRepository extends JpaRepository<BeautyService, Long> {
    // Весь базовый функционал (сохранить, удалить, найти все) Спринг создаст за нас автоматически!
}
