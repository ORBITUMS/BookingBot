package by.orbitum.minsk_booking_bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class BookingBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private final TelegramClient telegramClient;
    private final String botToken;
    private final BeautyServiceRepository serviceRepository;
    private final BookingClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;
        // Читаем настройки времени из файла application.properties
    @Value("${business.work.start}")
    private int workStartHour;

    @Value("${business.work.end}")
    private int workEndHour;


    public BookingBot(@Value("${bot.token}") String botToken, 
                      BeautyServiceRepository serviceRepository,
                      BookingClientRepository clientRepository,
                    AppointmentRepository appointmentRepository) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.serviceRepository = serviceRepository;
        this.clientRepository = clientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            
            // 1. Обработка текстовых команд
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (messageText.equals("/start")) {
                    registerClient(update.getMessage().getFrom());
                    sendWelcomeMessage(chatId);
                }
            } 
            
            // 2. Обработка нажатий на инлайн-кнопки
            else if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();

                if (callbackData.equals("button_book")) {
                    handleBookingClick(chatId, messageId);
                } else if (callbackData.equals("button_my_bookings")) {
                    handleMyBookingsClick(chatId, messageId);
                } else if (callbackData.equals("button_main_menu")) {
                    // Возврат в главное меню через РЕДАКТИРОВАНИЕ сообщения
                    handleMainMenuClick(chatId, messageId);
                } else if (callbackData.startsWith("service_")) {
                    // Пользователь выбрал конкретную услугу!
                    String serviceId = callbackData.split("_")[1];
                    handleServiceSelectClick(chatId, messageId, serviceId);
                } else if (callbackData.startsWith("date_")) {
                    String selectedDate = callbackData.replace("date_", "");
                    handleDateSelectClick(chatId, messageId, selectedDate);
                } 
                // 💥 ДОБАВЛЯЕМ ВОТ ЭТОТ БЛОК:
                else if (callbackData.startsWith("time_")) {
                    // Разделяем строку, чтобы узнать выбранную дату и время
                    String[] parts = callbackData.split("_");
                    String date = parts[1];
                    String time = parts[2];
                    handleFinalConfirmationClick(chatId, messageId, date, time);
                }

            }
        }
    }

    // Метод регистрации клиента вынесли отдельно, чтобы не загромождать consume
    private void registerClient(User tgUser) {
        long telegramId = tgUser.getId();
        if (!clientRepository.existsById(telegramId)) {
            BookingClient newClient = new BookingClient(
                telegramId, 
                tgUser.getFirstName(), 
                tgUser.getLastName(), 
                tgUser.getUserName()
            );
            clientRepository.save(newClient);
            System.out.println(">>> Зарегистрирован новый клиент из Минска: " + tgUser.getFirstName() + " <<<");
        }
    }

    // Первое приветственное сообщение (новое)
    private void sendWelcomeMessage(long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Привет! Добро пожаловать в сервис записи для бизнеса в Минске. Что хочешь сделать?")
                .build();
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Редактирование сообщения для возврата в главное меню
    private void handleMainMenuClick(long chatId, int messageId) {
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Привет! Добро пожаловать в сервис записи для бизнеса в Минске. Что хочешь сделать?")
                .build();
        editMessage.setReplyMarkup(createMainMenuKeyboard());

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Вспомогательный метод для создания кнопок главного меню
    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardButton buttonBook = InlineKeyboardButton.builder()
                .text("📅 Записаться на услугу")
                .callbackData("button_book")
                .build();

        InlineKeyboardButton buttonMyBookings = InlineKeyboardButton.builder()
                .text("🗂 Мои записи")
                .callbackData("button_my_bookings")
                .build();

        InlineKeyboardRow row = new InlineKeyboardRow(buttonBook, buttonMyBookings);
        return new InlineKeyboardMarkup(List.of(row));
    }

    // Экран выбора услуг
    private void handleBookingClick(long chatId, int messageId) {
        List<BeautyService> services = serviceRepository.findAll();
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();

        for (BeautyService service : services) {
            String buttonText = service.getName() + " — " + service.getPrice() + " BYN";
            String callbackData = "service_" + service.getId();

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData(callbackData)
                    .build();

            rows.add(new InlineKeyboardRow(button));
        }

        // Добавляем в самый низ кнопку Назад
        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Назад в меню")
                .callbackData("button_main_menu")
                .build();
        rows.add(new InlineKeyboardRow(buttonBack));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Отлично! Выберите бьюти-услугу из нашего прайс-листа: ✨")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Экран "Мои записи" с кнопкой Назад
    private void handleMyBookingsClick(long chatId, int messageId) {
        // 1. Ищем в базе данных все записи этого пользователя по его chatId (он равен Telegram ID)
        List<Appointment> myAppointments = appointmentRepository.findAllByClientTelegramId(chatId);

        String responseText;

        // 2. Проверяем, нашли ли мы что-нибудь
        if (myAppointments.isEmpty()) {
            responseText = "У вас пока нет активных записей. 🗂\nЧтобы записаться, выберите соответствующий пункт в меню.";
        } else {
            // Если записи есть, собираем их в один красивый текст
            StringBuilder sb = new StringBuilder("📋 Ваши активные записи:\n\n");
            
            for (int i = 0; i < myAppointments.size(); i++) {
                Appointment app = myAppointments.get(i);
                sb.append(i + 1).append(". ✨ ").append(app.getServiceName()).append("\n")
                  .append("📅 Дата: ").append(app.getBookingDate()).append("\n")
                  .append("⏰ Время: ").append(app.getBookingTime()).append("\n")
                  .append("-------------------------\n");
            }
            
            responseText = sb.toString();
        }

        // 3. Создаем кнопку возврата в главное меню
        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Назад в меню")
                .callbackData("button_main_menu")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

        // 4. Редактируем старое сообщение, выводя список записей
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(responseText)
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    // НОВЫЙ ЭКРАН: Выбор Даты после выбора услуги
    private void handleServiceSelectClick(long chatId, int messageId, String serviceId) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM (EE)");

        // Генерируем даты на 3 дня вперед (Сегодня, Завтра, Послезавтра)
        for (int i = 0; i < 3; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String prefix = (i == 0) ? "Сегодня " : (i == 1) ? "Завтра " : "";
            String buttonText = prefix + date.format(formatter);
            String callbackData = "date_" + date.toString(); // Например "date_2026-09-06"

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData(callbackData)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }

        // Кнопка назад к выбору услуг
        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 К услугам")
                .callbackData("button_book")
                .build();
                        rows.add(new InlineKeyboardRow(buttonBack));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Принято! Теперь выберите удобный день для визита: 📅")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            // Отправляем отредактированное сообщение в Telegram
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // НОВЫЙ ЭКРАН: Подтверждение даты (заглушка для следующего шага)
    private void handleDateSelectClick(long chatId, int messageId, String selectedDate) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDate localDate = LocalDate.parse(selectedDate); // Переводим текст даты в объект

        // Генерируем окошки по нашему графику
        for (int hour = workStartHour; hour < workEndHour; hour++) {
            String timeText = String.format("%02d:00", hour);
            
            // 💥 ПРОВЕРКА: Проверяем в PostgreSQL, не занято ли это время кем-то другим?
            boolean isBusy = appointmentRepository.existsByBookingDateAndBookingTime(localDate, timeText);
            
            // Если время ЗАНЯТО, мы просто пропускаем этот час и не создаем для него кнопку!
            if (isBusy) {
                continue; 
            }
            
            String callbackData = "time_" + selectedDate + "_" + timeText;

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text("⏰ " + timeText)
                    .callbackData(callbackData)
                    .build();
            
            rows.add(new InlineKeyboardRow(button));
        }

        // Если все окошки разобрали, выведем текст об этом
        String messageText = "Вы выбрали дату: " + selectedDate + " 📅\nТеперь выберите свободное время для записи:";
        if (rows.isEmpty()) {
            messageText = "Извините, на дату " + selectedDate + " все окошки уже заняты! 😭";
        }

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Выбрать другую дату")
                .callbackData("button_book")
                .build();
        rows.add(new InlineKeyboardRow(buttonBack));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(messageText)
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleFinalConfirmationClick(long chatId, int messageId, String date, String time) {
        // 1. Создаем объект записи и сохраняем его в PostgreSQL!
        // Переводим строку даты обратно в объект LocalDate
        LocalDate localDate = LocalDate.parse(date); 
        
        // Для MVP пока напишем просто "Бьюти-услуга" (на следующих шагах научим бота передавать имя услуги через callback)
        Appointment newAppointment = new Appointment(chatId, "Маникюр + Гель-лак", localDate, time);
        appointmentRepository.save(newAppointment);
        
        System.out.println(">>> Реальная запись сохранена в PostgreSQL на " + date + " в " + time + " <<<");

        // 2. Выводим текст успешного завершения на экран
        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 В главное меню")
                .callbackData("button_main_menu")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🎉 Поздравляем! Вы успешно записаны!\n\n📅 Дата: " + date + "\n⏰ Время: " + time + "\n\nДанные железно сохранены в базу данных PostgreSQL! 📋")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
    

