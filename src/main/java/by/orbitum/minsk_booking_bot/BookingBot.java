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

    @Value("${business.admin.ids}")
    private List<Long> adminIds;

    @Value("${business.max.bookings.per.service}")
    private int maxBookingsPerService;




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
            
            // 1. Обработка текстовых команд (например, /start)
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (messageText.equals("/start")) {
                    registerClient(update.getMessage().getFrom());
                    sendWelcomeMessage(chatId);
                }
            } 
            
            // 2. Обработка нажатий на ВСЕ инлайн-кнопки
            else if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();

                if (callbackData.equals("button_book")) {
                    handleBookingClick(chatId, messageId);
                } else if (callbackData.equals("button_my_bookings")) {
                    handleMyBookingsClick(chatId, messageId);
                } else if (callbackData.equals("button_main_menu")) {
                    handleMainMenuClick(chatId, messageId);
                } else if (callbackData.equals("button_admin_panel")) {
                    // Ловушка для админки директора салона
                    handleAdminPanelClick(chatId, messageId);
                } else if (callbackData.startsWith("service_")) {
                    String serviceId = callbackData.split("_")[1];
                    handleServiceSelectClick(chatId, messageId, serviceId);
                } else if (callbackData.startsWith("date_")) {
                    // Строка выглядит так: "date_1_2026-09-09"
                    String[] parts = callbackData.split("_");
                    String serviceId = parts[1];
                    String selectedDate = parts[2];
                    handleDateSelectClick(chatId, messageId, serviceId, selectedDate);
                } else if (callbackData.startsWith("time_")) {
                    // Строка выглядит так: "time_1_2026-09-09_14:00"
                    String[] parts = callbackData.split("_");
                    String serviceId = parts[1];
                    String date = parts[2];
                    String time = parts[3];
                    handleFinalConfirmationClick(chatId, messageId, serviceId, date, time);
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
        
        // Передаем chatId в метод создания клавиатуры
        message.setReplyMarkup(createMainMenuKeyboard(chatId));

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleMainMenuClick(long chatId, int messageId) {
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Привет! Добро пожаловать в сервис записи для бизнеса в Минске. Что хочешь сделать?")
                .build();
        
        // Передаем chatId в метод создания клавиатуры
        editMessage.setReplyMarkup(createMainMenuKeyboard(chatId));

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Вспомогательный метод для создания кнопок главного меню
        private InlineKeyboardMarkup createMainMenuKeyboard(long chatId) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();

        // Эти кнопки видят ВСЕ пользователи
        InlineKeyboardButton buttonBook = InlineKeyboardButton.builder()
                .text("📅 Записаться на услугу")
                .callbackData("button_book")
                .build();

        InlineKeyboardButton buttonMyBookings = InlineKeyboardButton.builder()
                .text("🗂 Мои записи")
                .callbackData("button_my_bookings")
                .build();

        rows.add(new InlineKeyboardRow(buttonBook, buttonMyBookings));

        // 🔥 МАГИЯ АДМИНКИ: Если ТГ ID пользователя записан в настройках, добавляем кнопку админа!
        if (adminIds.contains(chatId)) {
            InlineKeyboardButton buttonAdmin = InlineKeyboardButton.builder()
                    .text("⚙️ Панель директора")
                    .callbackData("button_admin_panel")
                    .build();
            rows.add(new InlineKeyboardRow(buttonAdmin));
        }

        return new InlineKeyboardMarkup(rows);
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
        // Находим выбранную услугу, чтобы узнать её имя
        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Услуга";

        // 💥 ПРОВЕРКА ЛИМИТА: Считаем в PostgreSQL, сколько активных записей у этого chatId на эту услугу
        long currentBookingsCount = appointmentRepository.countByClientTelegramIdAndServiceName(chatId, serviceName);

        // Если клиент уперся в лимит из application.properties
        if (currentBookingsCount >= maxBookingsPerService) {
            InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                    .text("🔙 К выбору услуг")
                    .callbackData("button_book")
                    .build();
            InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("⚠️ Ограничение записи!\n\nВы не можете сделать более " + maxBookingsPerService + " записей на услугу:\n«" + serviceName + "».\n\nПожалуйста, отмените старые визиты или выберите другую услугу. 🙏")
                    .build();
            editMessage.setReplyMarkup(markup);

            try {
                telegramClient.execute(editMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return; // Останавливаем метод, дальше к выбору дат не пускаем!
        }

        // Если лимит не превышен — идет наш стандартный код выбора дат:
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM (EE)");

        for (int i = 0; i < 3; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String prefix = (i == 0) ? "Сегодня " : (i == 1) ? "Завтра " : "";
            String buttonText = prefix + date.format(formatter);
            String callbackData = "date_" + serviceId + "_" + date.toString(); 

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData(callbackData)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }

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
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    // НОВЫЙ ЭКРАН: Подтверждение даты (заглушка для следующего шага)
         // 💥 ИЗМЕНЯЕМ АРГУМЕНТЫ: теперь метод принимает еще и serviceId
    private void handleDateSelectClick(long chatId, int messageId, String serviceId, String selectedDate) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDate localDate = LocalDate.parse(selectedDate);

        // Находим выбранную услугу в базе по ID, чтобы узнать её реальное имя ("Коррекция бровей" и т.д.)
        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Услуга";

        for (int hour = workStartHour; hour < workEndHour; hour++) {
            String timeText = String.format("%02d:00", hour);
            
            boolean isTimeBusy = appointmentRepository.existsByBookingDateAndBookingTime(localDate, timeText);
            if (isTimeBusy) {
                continue; 
            }

            // 💥 ИЗМЕНЯЕМ ТУТ: зашиваем в callback всё вместе: "time_IDуслуги_Дата_Время"
            String callbackData = "time_" + serviceId + "_" + selectedDate + "_" + timeText;

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text("⏰ " + timeText)
                    .callbackData(callbackData)
                    .build();
            
            rows.add(new InlineKeyboardRow(button));
        }

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Выбрать другую дату")
                .callbackData("button_book")
                .build();
        rows.add(new InlineKeyboardRow(buttonBack));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        // Сделали заголовок еще круче — теперь пишется конкретная услуга!
        String headerText = "Выбранная услуга: 🏷 " + serviceName + "\n" +
                            "Выбранная дата: 📅 " + selectedDate + "\n" +
                            "───────────────────\n" +
                            "✨ НИЖЕ СВЕЖИЕ И СВОБОДНЫЕ ОКОШКИ:\n" +
                            "Пожалуйста, выберите удобное время:";

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(headerText)
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



        // 💥 ИЗМЕНЯЕМ АРГУМЕНТЫ: метод теперь принимает и serviceId
    private void handleFinalConfirmationClick(long chatId, int messageId, String serviceId, String date, String time) {
        LocalDate localDate = LocalDate.parse(date); 
        
        // Находим услугу в базе, чтобы сохранить её настоящее имя
        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Бьюти-услуга";

        // 💥 СОХРАНЯЕМ НАСТОЯЩЕЕ НАЗВАНИЕ УСЛУГИ В БАЗУ ДАННЫХ!
        Appointment newAppointment = new Appointment(chatId, serviceName, localDate, time);
        appointmentRepository.save(newAppointment);
        
        System.out.println(">>> Реальная запись [" + serviceName + "] сохранена в PostgreSQL на " + date + " в " + time + " <<<");

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 В главное меню")
                .callbackData("button_main_menu")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🎉 Поздравляем! Вы успешно записаны!\n\n🏷 Услуга: " + serviceName + "\n📅 Дата: " + date + "\n⏰ Время: " + time + "\n\nДанные железно сохранены в базу данных PostgreSQL! 📋")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


            private void handleAdminPanelClick(long chatId, int messageId) {
        if (!adminIds.contains(chatId)) {
            return;
        }

        // 💥 ИЗМЕНЯЕМ ТУТ: Вытаскиваем все записи, но уже ИДЕАЛЬНО ОРТСОЛТИРОВАННЫЕ по дате и времени!
        List<Appointment> allAppointments = appointmentRepository.findAllByOrderByBookingDateAscBookingTimeAsc();
        String responseText;

        if (allAppointments.isEmpty()) {
            responseText = "⚙️ Панель директора\n\nВ салоне пока нет ни одной записи клиентов. 🤷‍♂️";
        } else {
            StringBuilder sb = new StringBuilder("⚙️ Панель директора\n\n📌 Расписание всех записей (по порядку): ✨\n\n");
            
            // Форматируем красивый вывод даты
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            
            for (int i = 0; i < allAppointments.size(); i++) {
                Appointment app = allAppointments.get(i);
                sb.append(i + 1).append(". 📅 ").append(app.getBookingDate().format(dateFormatter))
                  .append(" в ").append(app.getBookingTime()).append("\n")
                  .append("   🏷 Услуга: ").append(app.getServiceName()).append("\n")
                  .append("   👤 Клиент ID: ").append(app.getClientTelegramId()).append("\n")
                  .append("─────────────────────────\n");
            }
            responseText = sb.toString();
        }

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Назад в меню")
                .callbackData("button_main_menu")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

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
}
    

