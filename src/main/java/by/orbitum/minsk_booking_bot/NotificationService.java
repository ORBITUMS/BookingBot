package by.orbitum.minsk_booking_bot;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class NotificationService {

    private final AppointmentRepository appointmentRepository;
    private final TelegramClient telegramClient;

    @Value("${business.notification.before.hours}")
    private int notificationBeforeHours;

    // Конструктор внедряет репозиторий и создает сетевого клиента для отправки
    public NotificationService(AppointmentRepository appointmentRepository, @Value("${bot.token}") String botToken) {
        this.appointmentRepository = appointmentRepository;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    // 💥 БУДИЛЬНИК: Срабатывает каждые 15 минут (время указано в миллисекундах: 15 * 60 * 1000)
    @Scheduled(fixedRate = 900000)
    public void sendReminders() {
        // 1. Берем текущее время на сервере и прибавляем к нему часы лимита (например, +2 часа)
        LocalDateTime targetDateTime = LocalDateTime.now().plusHours(notificationBeforeHours);
        
        // 2. Форматируем отдельно дату и отдельно время для запроса в PostgreSQL
        java.time.LocalDate targetDate = targetDateTime.toLocalDate();
        String targetTimeStr = String.format("%02d:%02d", targetDateTime.getHour(), (targetDateTime.getMinute() / 15) * 15); 
        // Округляем минуты до пятнадцатиминутных шагов (00, 15, 30, 45) для точного совпадения

        // 3. Вытаскиваем из PostgreSQL всех счастливчиков, кому пора слать напоминание
        List<Appointment> appointmentsToNotify = appointmentRepository
                .findAllByBookingDateAndBookingTimeAndNotifiedFalse(targetDate, targetTimeStr);

        for (Appointment app : appointmentsToNotify) {
            
            String text = "⏰ НАПОМИНАНИЕ О ЗАПИСИ! ✨\n\n" +
                          "Уважаемый клиент, вы записаны на процедуру:\n" +
                          "🏷 «" + app.getServiceName() + "»\n" +
                          "📅 Когда: Сегодня, " + app.getBookingTime() + "\n\n" +
                          "Мастер очень ждет вас в Минске! Если ваши планы изменились, пожалуйста, отмените визит через меню бота. 🙏";

            SendMessage message = SendMessage.builder()
                    .chatId(app.getClientTelegramId())
                    .text(text)
                    .build();

            try {
                // 4. Шлем сообщение в Telegram
                telegramClient.execute(message);
                
                // 💥 БЕЗОПАСНОСТЬ: Меняем флаг строго ПОСЛЕ успешной отправки!
                app.setNotified(true);
                appointmentRepository.save(app);
                
                System.out.println(">>> Напоминание успешно отправлено клиенту ID " + app.getClientTelegramId() + " <<<");
                
            } catch (Exception e) {
                // Если юзер заблокировал бота, все равно ставим true, чтобы не спамить ошибками в консоль
                app.setNotified(true);
                appointmentRepository.save(app);
                System.out.println("Не удалось отправить напоминание пользователю: " + e.getMessage());
            }
        }
    }
}
