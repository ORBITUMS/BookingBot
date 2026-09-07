package by.orbitum.minsk_booking_bot;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity // Говорим Спрингу создать таблицу booking_client в PostgreSQL
public class BookingClient {

    @Id // В качестве главного ID мы возьмем уникальный ID пользователя из самого Telegram!
    private Long telegramId;

    private String firstName;   // Имя из Телеграма
    private String lastName;    // Фамилия из Телеграма
    private String username;    // Юзернейм (например, @ivan_minsk)
    private LocalDateTime registeredAt; // Дата и время, когда клиент впервые зашел в бота

    // Пустой конструктор для Hibernate
    public BookingClient() {}

    // Удобный конструктор для создания клиента в коде
    public BookingClient(Long telegramId, String firstName, String lastName, String username) {
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.registeredAt = LocalDateTime.now(); // Время регистрации запишется автоматически
    }

    // Геттеры
    public Long getTelegramId() { return telegramId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getUsername() { return username; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
}
