package by.orbitum.minsk_booking_bot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity // Это слово говорит Спрингу: "Создай из этого класса таблицу в базе данных!"
public class BeautyService {

    @Id // Это главный уникальный номер (ID) услуги в базе
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Номера будут идти по порядку: 1, 2, 3... automatically
    private Long id;

    private String name;        // Название услуги (например, "Мужская стрижка")
    private Integer price;      // Цена в белорусских рублях (BYN)
    private Integer duration;   // Длительность услуги в минутах (например, 40)

    // Пустой конструктор нужен для базы данных
    public BeautyService() {}

    // Конструктор для удобного создания услуг в коде
    public BeautyService(String name, Integer price, Integer duration) {
        this.name = name;
        this.price = price;
        this.duration = duration;
    }

    // Геттеры (чтобы программа могла читать данные из полей)
    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getPrice() { return price; }
    public Integer getDuration() { return duration; }
}
