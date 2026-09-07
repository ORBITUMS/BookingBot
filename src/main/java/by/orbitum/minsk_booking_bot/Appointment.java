package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity // Создаст таблицу appointment в PostgreSQL
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientTelegramId; // ID клиента, который записался
    private String serviceName;    // Название услуги
    private LocalDate bookingDate; // Дата (например, 2026-09-07)
    private String bookingTime;    // Время (например, "15:00")

    // Пустой конструктор для Hibernate
    public Appointment() {}

    // Удобный конструктор
    public Appointment(Long clientTelegramId, String serviceName, LocalDate bookingDate, String bookingTime) {
        this.clientTelegramId = clientTelegramId;
        this.serviceName = serviceName;
        this.bookingDate = bookingDate;
        this.bookingTime = bookingTime;
    }

    // Геттеры
    public Long getId() { return id; }
    public Long getClientTelegramId() { return clientTelegramId; }
    public String getServiceName() { return serviceName; }
    public LocalDate getBookingDate() { return bookingDate; }
    public String getBookingTime() { return bookingTime; }
}
