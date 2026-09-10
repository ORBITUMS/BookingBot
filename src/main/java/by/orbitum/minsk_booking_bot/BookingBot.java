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

    @Value("${business.work.start}")
    private int workStartHour;

    @Value("${business.work.end}")
    private int workEndHour;

    @Value("${business.admin.ids}")
    private List<Long> adminIds;

    @Value("${business.max.bookings.per.service}")
    private int maxBookingsPerService;

    @Value("${business.work.days}")
    private List<Integer> workDays;





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
            
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (messageText.equals("/start")) {
                    registerClient(update.getMessage().getFrom());
                    sendWelcomeMessage(chatId);
                }
            } 
            
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
                } else if (callbackData.startsWith("cancel_")) {
                    String appointmentId = callbackData.replace("cancel_", "");
                    handleCancelBookingClick(chatId, messageId, appointmentId);
                }


            }
        }
    }

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
            System.out.println(">>> Зарегистрирован новый клиент из Минска: " + tgUser.getFirstName());
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Привет! Добро пожаловать в сервис записи в Минске. Что хочешь сделать?")
                .build();
        
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
                .text("Привет! Добро пожаловать в сервис записи в Минске. Что хочешь сделать?")
                .build();
        
        editMessage.setReplyMarkup(createMainMenuKeyboard(chatId));

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

        private InlineKeyboardMarkup createMainMenuKeyboard(long chatId) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();

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

        private void handleMyBookingsClick(long chatId, int messageId) {
        List<Appointment> myAppointments = appointmentRepository.findAllByClientTelegramIdAndBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(chatId, LocalDate.now());

        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        String responseText;

        if (myAppointments.isEmpty()) {
            responseText = "У вас пока нет активных будущих записей. 🗂";
        } else {
            StringBuilder sb = new StringBuilder("📋 Ваши активные записи:\n\n");
            for (int i = 0; i < myAppointments.size(); i++) {
                Appointment app = myAppointments.get(i);
                sb.append("Запись №").append(i + 1).append("\n")
                  .append("✨ ").append(app.getServiceName()).append("\n")
                  .append("📅 Дата: ").append(app.getBookingDate()).append("\n")
                  .append("⏰ Время: ").append(app.getBookingTime()).append("\n")
                  .append("─────────────────────────\n");

                // Создаем кнопку отмены КОНКРЕТНО для этой записи
                InlineKeyboardButton buttonCancel = InlineKeyboardButton.builder()
                        .text("❌ Отменить запись №" + (i + 1))
                        .callbackData("cancel_" + app.getId()) // Зашиваем ID записи из PostgreSQL
                        .build();
                rows.add(new InlineKeyboardRow(buttonCancel));
            }
            responseText = sb.toString();
        }

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 Назад в меню")
                .callbackData("button_main_menu")
                .build();
        rows.add(new InlineKeyboardRow(buttonBack));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

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

    private void handleServiceSelectClick(long chatId, int messageId, String serviceId) {
        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Услуга";

        long currentBookingsCount = appointmentRepository.countByClientTelegramIdAndServiceNameAndBookingDateGreaterThanEqual(chatId, serviceName, LocalDate.now());

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
            return;
        }

        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM (EE)");

        for (int i = 0; i < 5; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            
            int dayOfWeekNumber = date.getDayOfWeek().getValue();
            
            int isWorkingDay = workDays.get(dayOfWeekNumber - 1);

            if (isWorkingDay == 0) {
                continue; 
            }

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
                .text("Принято! Выберите удобный день (бот показывает только рабочие дни салона): 📅")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

       private void handleDateSelectClick(long chatId, int messageId, String serviceId, String selectedDate) {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDate localDate = LocalDate.parse(selectedDate);

        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Услуга";

        java.time.LocalTime currentTime = java.time.LocalTime.now();
        boolean isToday = localDate.equals(LocalDate.now());

        for (int hour = workStartHour; hour < workEndHour; hour++) {
            String timeText = String.format("%02d:00", hour);
            
            if (isToday) {
                if (hour <= currentTime.getHour()) {
                    continue; 
                }
            }

            boolean isTimeBusy = appointmentRepository.existsByBookingDateAndBookingTime(localDate, timeText);
            if (isTimeBusy) {
                continue; 
            }

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

    private void handleFinalConfirmationClick(long chatId, int messageId, String serviceId, String date, String time) {
        LocalDate localDate = LocalDate.parse(date); 
        
        BeautyService selectedService = serviceRepository.findById(Long.parseLong(serviceId)).orElse(null);
        String serviceName = (selectedService != null) ? selectedService.getName() : "Бьюти-услуга";

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

        List<Appointment> allAppointments = appointmentRepository.findAllByBookingDateGreaterThanEqualOrderByBookingDateAscBookingTimeAsc(LocalDate.now());
        String responseText;

        if (allAppointments.isEmpty()) {
            responseText = "⚙️ Панель директора\n\nВ салоне пока нет активных записей на сегодня и будущие дни. 🤷‍♂️";
        } else {
            StringBuilder sb = new StringBuilder("⚙️ Панель директора\n\n📌 Актуальное расписание (прошлые дни скрыты): ✨\n\n");
            
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            
            for (int i = 0; i < allAppointments.size(); i++) {
                Appointment app = allAppointments.get(i);

                String todayLabel = app.getBookingDate().equals(LocalDate.now()) ? " [СЕГОДНЯ]" : "";

                sb.append(i + 1).append(". 📅 ").append(app.getBookingDate().format(dateFormatter)).append(todayLabel)
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


        private void handleCancelBookingClick(long chatId, int messageId, String appointmentIdStr) {
        Long appointmentId = Long.parseLong(appointmentIdStr);
        
        // Достаем запись из PostgreSQL
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);

        if (appointment == null) {
            return;
        }

        //  Проверяем, принадлежит ли эта запись тому, кто нажал на кнопку
        if (!appointment.getClientTelegramId().equals(chatId)) {
            System.out.println("⚠️ Попытка взлома! Пользователь " + chatId + " пытался удалить чужую запись №" + appointmentId);
            return;
        }

        // Проверяем, не является ли запись вчерашней/прошлой
        if (appointment.getBookingDate().isBefore(LocalDate.now())) {
            System.out.println("⚠️ Попытка удалить старую запись из прошлого!");
            return; 
        }

        appointmentRepository.deleteById(appointmentId);
        System.out.println(">>> Запись №" + appointmentId + " успешно удалена из PostgreSQL кликом клиента <<<");

        InlineKeyboardButton buttonBack = InlineKeyboardButton.builder()
                .text("🔙 В главное меню")
                .callbackData("button_main_menu")
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(buttonBack);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🗑 Запись успешно отменена!\n\nЭто время снова свободно в календаре для других клиентов. 🔄")
                .build();
        editMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
    

