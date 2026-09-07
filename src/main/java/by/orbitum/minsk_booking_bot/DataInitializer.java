package by.orbitum.minsk_booking_bot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final BeautyServiceRepository repository;

    // Спринг сам автоматически передаст сюда наш репозиторий (это называется Dependency Injection)
    public DataInitializer(BeautyServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Проверяем, если в базе еще нет ни одной услуги, то добавляем тестовые
        if (repository.count() == 0) {
            repository.save(new BeautyService("Маникюр + Гель-лак", 40, 120)); // 40 BYN, 120 минут
            repository.save(new BeautyService("Педикюр", 55, 90));            // 55 BYN, 90 минут
            repository.save(new BeautyService("Коррекция бровей", 25, 30));     // 25 BYN, 30 минут
            
            System.out.println(">>> База данных PostgreSQL успешно наполнена бьюти-услугами! <<<");
        }
    }
}
