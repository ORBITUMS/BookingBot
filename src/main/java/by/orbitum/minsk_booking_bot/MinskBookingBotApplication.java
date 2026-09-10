package by.orbitum.minsk_booking_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // Импортируем

@SpringBootApplication
@EnableScheduling // 💥 ВКЛЮЧАЕМ ПЛАНИРОВЩИК ТУТ!
public class MinskBookingBotApplication {
	public static void main(String[] args) {
		SpringApplication.run(MinskBookingBotApplication.class, args);
	}
}
