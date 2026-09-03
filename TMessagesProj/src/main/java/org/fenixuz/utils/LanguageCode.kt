package org.fenixuz.utils

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog


object LanguageCode {
    var languageCode = "en"

    var languages = ArrayList<Language>()
    var titlesLanguages = ArrayList<TitleLanguages>()

    private var sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private var editor = sharedPreferences.edit()

    private var titlesInitialized = false

    private fun checkTitlesInitialized() {
        if (titlesInitialized) return
        titlesInitialized = true
        initTitles()
    }

    private fun  initTitles() {
        titlesLanguages.add(
            TitleLanguages(
                0,
                "Speak something...",
                "Biror nima gapiring...",
                "Скажи что-нибудь..."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                1,
                "Novagram settings",
                "Novagram Sozlamalar",
                "Novagram Настройки"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                2,
                "2FA password",
                "Ikki bosqichli tasdiqlash paroli",
                "Пароль двухфакторной аутентификации"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                3,
                "enter 2FA password",
                "Ikki bosqichli tasdiqlash parolini kiriting",
                "Введите пароль двухфакторной аутентификации\n"
            )
        )
        titlesLanguages.add(TitleLanguages(4, "Next", "Keyingi", "Далее"))
        titlesLanguages.add(
            TitleLanguages(
                5,
                "Set up a 2FA security key to recover passwords when forgotten",
                "Parol esdan chiqqanda ularni qayta tiklash uchun 2FA xavfsizlik kalitini o'rnating",
                "Установите ключ безопасности 2FA для восстановления паролей в случае их утери"
            )
        )
        titlesLanguages.add(TitleLanguages(6, "change", "O'zgartirish", "Изменить"))
        titlesLanguages.add(TitleLanguages(7, "save", "Saqlash", "Сохранить"))
        titlesLanguages.add(TitleLanguages(8, "password", "Parol", "Пароль"))
        titlesLanguages.add(
            TitleLanguages(
                9,
                "Change password",
                "Parolni o'zgartirish",
                "Изменить пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                10,
                "Remove password",
                "Parolni o'chirish",
                "Удалить пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                11,
                "Enter current password",
                "Joriy parolni kiriting",
                "Введите текущий пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                12,
                "Enter the access code for User1! Note: This password is only valid for User1",
                "User1 uchun kirish kodini kiriting! Eslatma: Ushbu parol faqat User1 uchun amal qiladi",
                "Введите код доступа для User1! Примечание: этот пароль действителен только для User1"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                13,
                "Enter new password",
                "Yangi parolni kiriting",
                "Введите новый пароль"
            )
        )
        titlesLanguages.add(TitleLanguages(14, "Select language", "Tilni tanlang", "Выберите язык"))
        titlesLanguages.add(
            TitleLanguages(
                15,
                "Save all messages",
                "Barcha xabarlarni saqlash",
                "Сохранить все сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                16,
                "Messages deleted from both sides are also saved by me. (Deleted from both sides)",
                "2 tarafdan o'chirilgan xabarlar ham menda saqlanadi. (2-tarafdan o'chib ketadi)",
                "Сообщения, удалённые с обеих сторон, также сохраняются у меня. (Удаляются с обеих сторон)"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                17,
                "Save messages deleted by me",
                "Men o'chirgan xabarlarni saqlash",
                "Сохранять сообщения, удаленные мной"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                18,
                "Meaning, my messages are deleted from both sides but remain saved on my side.",
                "Ya'ni, meni xabarlarim 2-tarafdan o'chib ketadi, ammo o'zimda saqlanib qoladi.",
                "То есть, мои сообщения удаляются с обеих сторон, но остаются сохраненными у меня."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                19,
                "Save deleted messages",
                "O'chirilgan xabarlarni saqlash",
                "Сохранять удалённые сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                20,
                "Save messages that are deleted from both sides on my end.",
                "2-tarafdan o'chirilgan xabarlarni menda saqlash.",
                "Сохранять у себя сообщения, удалённые с обеих сторон."
            )
        )
        titlesLanguages.add(TitleLanguages(21, "As usual", "Odatdagidek", "Как обычно"))
        titlesLanguages.add(
            TitleLanguages(
                22,
                "Works the same as the original Telegram.",
                "Rasmiy Telegram bilan bir xil ishlaydi",
                "Работает так же, как оригинальный Telegram.\n"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                23,
                "Save edited messages",
                "Tahrirlangan xabarlarni saqlash",
                "Сохранить отредактированные сообщения"
            )
        )
        titlesLanguages.add(TitleLanguages(24, "", "", ""))
        titlesLanguages.add(TitleLanguages(25, "Ghost on", "Ghost yoqilgan", "Гост-режим включен"))
        titlesLanguages.add(
            TitleLanguages(
                26,
                "Ghost off",
                "Ghost o'chirilgan",
                "Гост-режим выключен"
            )
        )
        titlesLanguages.add(TitleLanguages(27, "Ghost", "Ghost", "Гост"))
        titlesLanguages.add(
            TitleLanguages(
                28,
                "Lock chats",
                "Yozishmalarni qulflash",
                "Блокировка чатов"
            )
        )
        titlesLanguages.add(TitleLanguages(29, "Language menu", "Til menyusi", "Меню языков"))
        titlesLanguages.add(
            TitleLanguages(
                30,
                "Save edited messages",
                "Tahrirlangan xabarlarni saqlash",
                "Сохранить отредактированные сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                31,
                "Save deleted messages",
                "O'chirilgan xabarlarni saqlash",
                "Сохранить удалённые сообщения"
            )
        )
        titlesLanguages.add(TitleLanguages(32, "Ghost mode", "Ghost rejimi", "Режим призрака"))
        titlesLanguages.add(TitleLanguages(33, "Common menu", "Umumiy menyusi", "Общее меню"))
        titlesLanguages.add(TitleLanguages(34, "Select language", "Tilni tanlang", "Выберите язык"))
        titlesLanguages.add(
            TitleLanguages(
                35,
                "Password deleted!",
                "Parol o'chirildi!",
                "Пароль удален!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                36,
                "Password wrong!",
                "Parol xato!",
                "Неверный пароль!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                37,
                "Password changed!",
                "Parol o'zgartirildi!",
                "Пароль изменен!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                38,
                "Password created!",
                "Parol yaratildi!",
                "Пароль создан!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                39,
                "do You want to discard?",
                "bekor qilmoqchimisiz?",
                "Вы хотите отказаться?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                40,
                "do You want to save changes?",
                "o'zgarishlarni saqlamoqchimisiz?",
                "Вы хотите сохранить изменения?"
            )
        )
        titlesLanguages.add(TitleLanguages(41, "copied", "nusxalandi", "скопировано"))
        titlesLanguages.add(
            TitleLanguages(
                42,
                "create a two-step password first",
                "avval ikki bosqichli parol yarating",
                "сначала создайте двухэтапный пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                43,
                "If you don't remember your password, enter your two-step password",
                "Agar parolingizni eslamasangiz, ikki bosqichli parolni kiriting",
                "Если вы не помните свой пароль, введите двухэтапный пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                44,
                "Artificial intelligence",
                "Suniy intelekt",
                "Искусственный интеллект"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                45,
                "Novagram AI BOT",
                "Novagram AI BOT",
                "Novagram AI BOT"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                46,
                "Novagram pic generator BOT",
                "Novagram pic generator BOT",
                "Генератор изображений Novagram BOT"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                47,
                "Novagram Business",
                "Novagram Business",
                "Novagram Business"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                48,
                "Create a chat bot",
                "Chat bot yaratish",
                "Создать чат-бота"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                49,
                "Download stories",
                "Hikoyalarni yuklab olish",
                "Скачать истории"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                50,
                "Story download off",
                "Hikoya yuklash o'chirilgan",
                "Скачивание историй выключено"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                51,
                "Story download on",
                "Hikoya yuklash yoqilgan",
                "Скачивание историй включено"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                52,
                "enter post url...",
                "post url-ni kiriting...",
                "введите URL-адрес поста..."
            )
        )
        titlesLanguages.add(TitleLanguages(53, "Download", "Yuklash olish", "Скачать"))
        titlesLanguages.add(
            TitleLanguages(
                54,
                "Enter any story link",
                "Istalgan hikoya havolasini kiriting",
                "введите любую ссылку на историю"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                55,
                "put a clock on the profile",
                "profilga soat qo'yish",
                "поставь часы в профиль"
            )
        )
        titlesLanguages.add(TitleLanguages(56, "clock on", "soatni yoqish", "включить часы"))
        titlesLanguages.add(TitleLanguages(57, "clock off", "soatni o'chirish", "выключить часы"))
        titlesLanguages.add(TitleLanguages(58, "changed", "o'zgartirildi", "изменено"))
        titlesLanguages.add(TitleLanguages(59, "More", "Batafsil", "Более"))
        titlesLanguages.add(
            TitleLanguages(
                60,
                "Protection from strangers",
                "Begonadan himoya",
                "Защита от незнакомцев"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                61,
                "Enable protection from strangers",
                "Begonadan himoyani yoqish",
                "Включить защиту от посторонних"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                62,
                "Disable protection from strangers",
                "Begonadan himoyani o'chirish",
                "Отключить защиту от посторонних"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                63,
                "Download Stories",
                "Hikoyani yuklab olish",
                "Скачать истории"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                64,
                "Contact us to recover lost chats!",
                "Yo'qotilgan suhbatlarni qaytarish uchun biz bilan bog'laning!",
                "Свяжитесь с нами, чтобы восстановить потерянные чаты!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                65,
                "enter password",
                "parolni kiriting",
                "введите пароль"
            )
        )
        titlesLanguages.add(TitleLanguages(66, "Restart", "Qaytarish", "Перезапуск"))
        titlesLanguages.add(TitleLanguages(67, "Contact", "Kontakt", "Контакт"))
        titlesLanguages.add(
            TitleLanguages(
                68,
                "If you enable this function, the \"Save edited messages\" and \"Save deleted messages\" features will automatically be activated, and they cannot be turned off until you restart this function.",
                "Agar bu funksiyani yoqsangiz avtomatik ravishda \"Save edited messages\" va \"Save deleted messages\" funksiyalari yonadi va ularni o'chirib bo'lmaydi toki siz bu funksiyani RESTART qilmaguningizcha",
                "Если вы включите эту функцию, автоматически активируются функции \"Сохранение отредактированных сообщений\" и \"Сохранение удаленных сообщений\", и их нельзя будет отключить до тех пор, пока вы не перезапустите эту функцию."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                69,
                "To modify the deleted messages function, you need to restart the \"Protection from strangers\" function first.",
                "O'chirilgan xabarlar funksiyasini o'zgartirish uchun oldin \"Begonadan himoya\" funksiyasini RESTART qilishingiz kerak",
                "Чтобы изменить функцию удаления сообщений, вам необходимо сначала перезапустить функцию \"Защита от незнакомцев\"."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                70,
                "To modify the edited messages function, you need to restart the \"Protection from strangers\" function first.",
                "O'zgartirilgan xabarlar funksiyasini o'zgartirish uchun oldin \"Begonadan himoya\" funksiyasini RESTART qilishingiz kerak",
                "Чтобы изменить функцию изменённых сообщений, вам необходимо сначала перезапустить функцию \"Защита от незнакомцев\"."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                71,
                "\uD83E\uDD2B No one will know that you are online, and your read messages will remain a secret.\n\uD83E\uDD2A At least .... Let him die for free!",
                "\uD83E\uDD2B Online bo'lishingizni hech kim bilmaydi , o'qilgan xabarlaringiz ham sir bo'lib qoladi. \n\uD83E\uDD2AKamiga .... O'lsin teppa tekin !",
                "\uD83E\uDD2B Никто не узнает, что вы онлайн, а ваши прочитанные сообщения останутся в секрете.\n\uD83E\uDD2A Хотя бы.... Пусть умрет бесплатно!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                72,
                // The menu entry is R.string.WebHistory ("History") in ChatActivity's message options;
                // name it, or the instruction stops halfway and the user is left looking for it.
                "Keep the earlier versions of messages that someone edits. To read them, press and hold the message and choose History.",
                "Kimdir tahrirlagan xabarlarning oldingi variantlarini saqlab qoladi. O'qish uchun xabarni bosib turing va «Tarix» ni tanlang.",
                "Сохраняет прежние версии сообщений, которые кто-то отредактировал. Чтобы их прочитать, нажмите и удерживайте сообщение и выберите «История»."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                73,
                "\uD83D\uDD0F Your data will no longer be lost, even if your conversation partner wants to erase it.\n\uD83D\uDE01 Don't worry, everything is under my control...",
                "\uD83D\uDD0F Ma'lumotlaringiz endi yo'qolmaydi, hatto suhbatdoshingiz ma'lumotlarni yo'qotishni xohlaganda ham.\n\n\uD83D\uDE01 Qo'rqmanglar hammasi nazoratim ostida.....",
                "\uD83D\uDD0F Ваши данные теперь не пропадут, даже если ваш собеседник захочет их удалить.\n\uD83D\uDE01 Не волнуйтесь, всё под моим контролем..."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                74,
                "Download any story quickly and easily, completely free!",
                "Har qanday hikoyani tez va oson, mutlaqo bepul yuklab oling!",
                "Скачайте любую историю быстро и легко, совершенно бесплатно!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                75,
                "Terms of Use for Novagram Premium",
                "Novagram Premiumdan Foydalashish shartlari .",
                "Условия использования Novagram Premium"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                76,
                "FIRST have read and agree.",
                "O'qib chiqdim va roziman",
                "Я прочитал и согласен."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                77,
                "Important information",
                "Muhim ma'lumot",
                "Важная информация"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                78,
                "Please read the Terms of Use for Novagram Premium before giving your consent.",
                "Iltimos rozilik bildirishdan oldin Novagram Premiumdan Foydalashish shartlarini o'qib chiqing",
                "Пожалуйста, прочитайте Условия использования Novagram Premium перед тем, как дать согласие."
            )
        )
        titlesLanguages.add(TitleLanguages(79, "Subscription", "Obuna", "Подписка"))
        titlesLanguages.add(TitleLanguages(80, "Cancel", "Bekor qilish", "Отмена"))
        titlesLanguages.add(
            TitleLanguages(
                81,
                "Enter your Two-Step Verification password.",
                "Ikki bosqichli tekshiruv parolingizni kiriting.",
                "Введите пароль двухэтапной аутентификации."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                82,
                "This window is waiting for the auto payment agreement to be approved by the banks of Uzbekistan.",
                "Ushbu oyna O'zbekiston banklari tarafidan avto to'lov shartnomasi tasdiqlanishini kutmoqda.",
                "В этом окне ожидается одобрение договора автоплатежа банками Узбекистана."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                83,
                "Remove Password",
                "Parolni olib tashlash",
                "Удалить пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                84,
                "Set Password",
                "Parolni o'rnatish",
                "Установить пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                85,
                "Password removed",
                "Parol olib tashlandi",
                "Пароль удален"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                86,
                "Password added",
                "Parol qo'shildi",
                "Пароль добавлен"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                87,
                "Chat settings",
                "Suhbat sozlamalari",
                "Настройки чата"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                88,
                "Open the desired chat and lock the correspondence by clicking on the 3 dots on the upper right",
                "Istalgan suhbatingizni oching va yuqorida o'ng tarafdagi 3ta nuqtachani bosish orqali yozishmalarni qulflang",
                "Откройте нужный чат и заблокируйте переписку, нажав на 3 точки справа вверху"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                89,
                "Set common password",
                "Umumiy parol o'rnatish",
                "Установить общий пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                90,
                "Set individual password",
                "Alohida parol o'rnatish",
                "Установить индивидуальный пароль"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                91,
                "Do you agree to delete your individual password?",
                "Alohida parolingiz o'chib ketishiga rozimisiz ?",
                "Согласны ли вы удалить свой индивидуальный пароль?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                92,
                "Join all accounts",
                "Barcha akkauntlarni qo'shish",
                "Присоединиться всеми аккаунтами"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                93,
                "Novagram Clones",
                "Novagram Klonlari",
                "Клоны Novagram"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                94,
                "Add Demo Account",
                "Demo akkauntini qo'shing",
                "Добавить демо-аккаунт"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                95,
                "Demo account is already logged in from this app.",
                "Demo akkaunt allaqachon ushbu ilovadan kirgan.",
                "Демо-аккаунт уже авторизован из этого приложения."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                96,
                "We offer you Novagram demo account",
                "Sizga Novagram demo accountini taklif qilamiz",
                "Предлагаем вам демо-аккаунт Novagram"
            )
        )
        titlesLanguages.add(TitleLanguages(97, "Read me", "Meni o'qib chiqdim", "прочти меня"))
        titlesLanguages.add(TitleLanguages(98, "FIRST read", "O'qidim", "Я читаю"))
        titlesLanguages.add(
            TitleLanguages(
                99,
                "Full information about the demo account. Please read first",
                "Demo account haqida to'liq ma'lumot. Iltimos oldin o'qib chiqing",
                "Полная информация о демо-аккаунте. Пожалуйста, прочтите сначала"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                100,
                "Get started with a demo",
                "Demo bilan boshlang",
                "Начните работу с демо-аккаунта"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                101,
                "The demo account part of the program is undergoing maintenance, please try again later",
                "Dasturning demo account qismida tamirlash ishlari olib borilmoqda, keyinroq urinib ko'ring ",
                "Часть программы с демо-аккаунтом находится на техническом обслуживании. Повторите попытку позже."
            )
        )
        titlesLanguages.add(TitleLanguages(102, "Update", "Yangilash", "Обновлять"))
        titlesLanguages.add(
            TitleLanguages(
                103,
                "Please update the application to use the full features of Novagram!",
                "Novagramni to'liq imkoniyatlaridan foydalanishingiz uchun iltimos dasturni yangilang!",
                "Пожалуйста, обновите приложение, чтобы использовать все возможности Novagram Messenger!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                104,
                "Novagram Features",
                "Novagram Xususiyatlari",
                "Особенности Novagram"
            )
        )
        titlesLanguages.add(TitleLanguages(105, "Clear", "Tozalash", "Очистить"))
        titlesLanguages.add(
            TitleLanguages(
                106,
                "Do you want to clear all chats?",
                "Barcha suhbatlarni tozalamoqchimisiz ?",
                "Вы хотите очистить все чаты?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                107,
                "Do you want to clear this chat?",
                "Ushbu suhbatni tozalamoqchimisiz ?",
                "Вы хотите очистить этот чат?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                108,
                "Clear saved messages",
                "Saqlangan xabarlarni tozalash",
                "Очистить сохраненные сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                109,
                "Deleted by me",
                "Men tarafdan o'chirilgan",
                "Удалено мной"
            )
        )
        titlesLanguages.add(TitleLanguages(110, "Deleted", "O'chirilgan", "удаленный"))
        titlesLanguages.add(
            TitleLanguages(
                111,
                "Deleted by channel",
                "Kanal tomonidan o'chirilgan",
                "Удалено каналом"
            )
        )
        titlesLanguages.add(TitleLanguages(112, "Split text", "Matnni ajratish", "Разделить текст"))
        titlesLanguages.add(TitleLanguages(113, "Front camera", "Old kamera", "Фронтальная камера"))
        titlesLanguages.add(TitleLanguages(114, "Rear camera", "Orqa kamera", "Задняя камера"))
        titlesLanguages.add(TitleLanguages(115, "All Chats", "Barcha Suhbatlar", "Все чаты"))
        titlesLanguages.add(
            TitleLanguages(
                116,
                "Translate voice to text",
                "Ovozni tarjima textga o'girish",
                "Перевести голос в текст"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                117,
                "The language you speak",
                "Gapiradigan tilingiz",
                "Язык, на котором вы говорите"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                118,
                "The language to be translated",
                "Tarjima qilinadigan til",
                "Язык для перевода"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                119,
                "Choose which language you speak and which language you want to translate to",
                "Qaysi tilda gapirishingizni va qaysi tilga tarjima qilish kerakligini tanlang",
                "Выберите, на каком языке вы говорите и на какой язык хотите перевести"
            )
        )
        titlesLanguages.add(TitleLanguages(120, "App theme", "Ilova mavzusi", "Тема приложения"))
        titlesLanguages.add(TitleLanguages(121, "App cache", "Ilova keshi", "Кэш приложения"))
        titlesLanguages.add(TitleLanguages(122, "New Channel", "Yangi Kanal", "Новый канал"))
        titlesLanguages.add(TitleLanguages(123, "Chat finder", "Suhbat topuvchi", "Поиск чата"))
        titlesLanguages.add(
            TitleLanguages(
                124,
                "Enter a chat username",
                "Istalgan foydalanuvchi nomini kiriting",
                "Введите имя пользователя чата"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                125,
                "View first message",
                "Birinchi xabarni ko'rish",
                "Просмотреть первое сообщение"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                126,
                "Automatic text adder",
                "Avtomatik text qo'shuvchi",
                "Автоматическое добавление текста"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                127,
                "When this feature is enabled, the pre-entered text will be automatically added at the end of every post published in the channel. If no text is pre-entered but the feature is active, the channel's link will be added at the end of the post.",
                "Bu xususiyat yoqilganda oldindan kiritilgan text kanalda chiqariladigan har bir post oxiridan avtomatik qo'shiladi. Agar text oldindan qo'shilmasa lekin bu xususiyat faollashtirilsa post oxirida kanal manzili qo'shiladi.",
                "При включении этой функции заранее введённый текст будет автоматически добавляться в конец каждого поста, публикуемого в канале. Если текст не введён заранее, но функция активирована, в конце поста будет добавлена ссылка на канал."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                128,
                "Login with bot token",
                "Bot tokeni bilan tizimga kiring",
                "Войти с помощью токена бота"
            )
        )
        titlesLanguages.add(TitleLanguages(129, "Bot Token", "Bot Tokeni", "Токен бота"))
        titlesLanguages.add(
            TitleLanguages(
                130,
                "Please enter bot token",
                "Iltimos, bot tokenini kiriting",
                "пожалуйста, введите токен бота"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                131,
                "Story Ghost",
                "Hikoya Ghost rejimi",
                "Призрачный просмотр историй"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                132,
                "Login via qr",
                "Qr kod bilan kirish",
                "Войти через QR"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                133,
                "Note:\n" +
                        "If you want to log in with a bot, you first need to create a new bot using @BotFather. Then, enter the token provided by it here and log in using the bot.\n" +
                        "\n" +
                        "When logging in with a bot, the following limitations apply:\n" +
                        "\n" +
                        "You will not have access to your contacts.\n" +
                        "You cannot join groups and channels (unless you add them as administrators).\n" +
                        "You can only send messages to users who have previously sent you /start.\n" +
                        "You will not be able to see when users read your messages.\n" +
                        "Initially, the chat list will be empty and will only populate after the bot receives messages from users and groups. However, group detection may take longer.",
                "Eslatma:\n" +
                        "Agar bot orqali tizimga kirishni istasangiz, avval @BotFather yordamida yangi bot yaratishingiz kerak. So‘ngra, u taqdim etgan tokenni shu yerga kiriting va bot orqali tizimga kiring.\n" +
                        "\n" +
                        "Bot bilan tizimga kirganingizda quyidagi cheklovlar mavjud bo‘ladi:\n" +
                        "\n" +
                        "Kontaktlaringizga kira olmaysiz.\n" +
                        "Guruh va kanallarga qo‘shilolmaysiz (faqat administrator sifatida qo‘shsangiz mumkin).\n" +
                        "Faqat sizga avval /start yozgan foydalanuvchilarga xabar yuborishingiz mumkin.\n" +
                        "Foydalanuvchilarning xabaringizni o‘qiganini ko‘ra olmaysiz.\n" +
                        "Dastlab suhbatlar ro‘yxati bo‘sh bo‘ladi va bot foydalanuvchilardan yoki guruhlardan xabar olgandan keyingina to‘ldiriladi. Ammo guruhlarni aniqlash jarayoni ancha sekin bo‘lishi mumkin.",
                "Примечание:\n" +
                        "Если вы хотите войти через бота, вам сначала нужно создать нового бота с помощью @BotFather. Затем введите сюда токен, который он вам предоставил, и войдите в систему через бота.\n" +
                        "\n" +
                        "При входе через бота действуют следующие ограничения:\n" +
                        "\n" +
                        "У вас не будет доступа к вашим контактам.\n" +
                        "Вы не сможете присоединяться к группам и каналам (если только не добавите их администраторами).\n" +
                        "Вы можете отправлять сообщения только тем пользователям, которые ранее написали вам /start.\n" +
                        "Вы не сможете видеть, когда пользователи читают ваши сообщения.\n" +
                        "Изначально список чатов будет пустым и заполнится только после получения сообщений от пользователей и групп. Однако обнаружение групп может занять больше времени."
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                134,
                "Privacy Policy",
                "Maxfiylik siyosati",
                "политика конфиденциальности"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                135,
                "Story Settings",
                "Hikoya Sozlamalari",
                "Настройки историй"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                136,
                "Hide stories",
                "Hikoyalarni yashirish",
                "Скрыть истории"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                137,
                "Hide the stories tray at the top of the chat list.",
                "Suhbatlar ro'yxati tepasidagi hikoyalar tasmasini yashiradi.",
                "Скрывает ленту историй вверху списка чатов."
            )
        )
        titlesLanguages.add(TitleLanguages(138, "Promo code", "Promo kod", "Промо-код"))
        titlesLanguages.add(
            TitleLanguages(
                139,
                "this promo not exist",
                "bu promo mavjud emas",
                "эта акция не существует"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                140,
                "Once the promocode is entered, it cannot be changed!",
                "Promokod kiritilgandan so'ng uni o'zgartirib bo'lmaydi!",
                "После ввода промокода его нельзя изменить!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                141,
                "something went wrong",
                "nimadir noto'g'ri ketdi",
                "что-то пошло не так"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                142,
                "this promo code already exist",
                "bu promo-kod allaqachon mavjud",
                "этот промокод уже существует"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                143,
                "Do you want to delete this promo code ?",
                "Ushbu promo-kodni o'chirib tashlamoqchimisiz ?",
                "Хотите удалить этот промокод ?"
            )
        )
        titlesLanguages.add(TitleLanguages(144, "Referrals: ", "Referallar: ", "Рекомендации: "))
        titlesLanguages.add(
            TitleLanguages(
                145,
                "Confirmed Referrals: ",
                "Tasdiqlangan referallar: ",
                "Подтвержденные рекомендации: "
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                146,
                "Enter the promo code of the person who invited you to this app. Become a referral. If you want, you can also create a promo code and invite referrals yourself.",
                "Sizni ushbu ilovaga taklif qilgan shaxsning promo kodini kiriting. Referralga aylaning. Agar xohlasangiz, promo-kod yaratishingiz va o'zingiz referrallarni taklif qilishingiz mumkin.",
                "Введите промокод человека, который пригласил вас в это приложение. Станьте рефералом. Если хотите, вы также можете создать промокод и приглашать рефералов самостоятельно."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                147,
                "What do you want the above folders to look like ?",
                "Yuqoridagi jildlar ko'rinishi qanday bo'lishini xohlaysiz ?",
                "Как вы хотите, чтобы выглядели указанные выше папки ?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                148,
                "Will you add default folders to Novagram ?",
                "Novagramning standart jiltlarini qo'shasizmi ?",
                "Добавите ли вы папки по умолчанию в Novagram ?"
            )
        )
        titlesLanguages.add(TitleLanguages(149, "text", "matn", "текст"))
        titlesLanguages.add(TitleLanguages(150, "icon", "belgisi", "Значок"))
        titlesLanguages.add(
            TitleLanguages(
                151,
                "Update the app",
                "Ilovani yangilang",
                "Обновите приложение"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                152,
                "A new version of the program has been released. Update it to enjoy the new features of the program. After the update, the program will work even better!",
                "Dasturning yangi versiyasi chiqdi. Dasturning yangi funksiyalaridan bahramand bo'lish uchun uni yangilang. Yangilashdan keyin dastur yanada sifatliroq ishlaydi!",
                "Вышла новая версия программы. Обновите программу, чтобы воспользоваться ее новыми функциями. После обновления программа работает еще лучше!"
            )
        )
        titlesLanguages.add(TitleLanguages(153, "Update", "Yangilash", "Обновлять"))
        titlesLanguages.add(TitleLanguages(154, "Analytics", "Analitika", "Аналитика"))
        titlesLanguages.add(
            TitleLanguages(
                155,
                "Number of Novagram users",
                "Novagram foydalanuvchilari soni",
                "Количество пользователей Novagram"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                156,
                "Number of PRO Messenger channel members and followers",
                "PRO Messenger kanali azolari va bizni kuzatuvchilar soni",
                "Количество участников и подписчиков канала PRO Messenger"
            )
        )
        titlesLanguages.add(TitleLanguages(157, "Ghost button", "Ghost tugmasi", "Кнопка призрака"))
        titlesLanguages.add(
            TitleLanguages(
                158,
                "Hide Story button",
                "Hikoyani yashirish tugmasi",
                "Кнопка Скрыть историю"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                159,
                "Would you like to see a hide stories button at the top of the main dialog box ?",
                "Asosiy dialoglar oynasida tepada hikoyalarni yashirish tugmasini ko'rsatilishini xohlaysizmi ?",
                "Хотите ли вы видеть кнопку «Скрыть истории» в верхней части главного диалогового окна ?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                160,
                "Would you like to see a Ghost button at the top of the main dialog box ?",
                "Asosiy dialoglar oynasida tepada Ghost(sharpa) tugmasini ko'rsatilishini xohlaysizmi ?",
                "Хотите ли вы видеть кнопку «Ghost» в верхней части главного диалогового окна ?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                161,
                "Money earned: ",
                "Ishlangan pul: ",
                "Заработанные деньги: "
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                162,
                "Ready to withdraw: ",
                "Yechishga tayyor pul: ",
                "Доступно к выводу: "
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                163,
                "You can get paid for each of your verified referrals! To do this, click the Claim button and notify the admin about this. The price is set for each verified referral: ",
                "Siz har bir tasdiqlangan takliflaringiz uchun pul olishingiz mumkun! Buning uchun Pulni yechish tugmasini bosing va adminga bu haqida xabar bering. Har bir tasdiqlangan takliflar uchun belgilangan narx: ",
                "Вы можете получать оплату за каждого из ваших проверенных рефералов! Для этого нажмите кнопку «Подать заявку» и сообщите об этом администратору. фиксированная цена за каждого проверенного реферала: "
            )
        )
        titlesLanguages.add(TitleLanguages(164, "Delete", "O'chirish", "Удалить"))
        titlesLanguages.add(TitleLanguages(165, "Claim", "Pulni yechish", "Снять деньги"))
        titlesLanguages.add(TitleLanguages(166, "Text style", "Matn uslubi", "Стиль текста"))
        titlesLanguages.add(TitleLanguages(167, "Regular", "Odatdagi", "Обычный"))
        titlesLanguages.add(TitleLanguages(168, "Spoiler", "Yashirish", "Скрывать"))
        titlesLanguages.add(TitleLanguages(169, "Bold", "Qalin", "жирный"))
        titlesLanguages.add(TitleLanguages(170, "Italic", "Qiya", "Курсивный"))
        titlesLanguages.add(TitleLanguages(171, "Mono", "Monobo'shliq", "Моноширинный"))
        titlesLanguages.add(
            TitleLanguages(
                172,
                "Strikethrough",
                "O'rtasiga chizilgan",
                "Зачеркивание"
            )
        )
        titlesLanguages.add(TitleLanguages(173, "Underline", "Tagiga chizilgan", "Подчеркнуть"))
        titlesLanguages.add(TitleLanguages(174, "Quote", "Iqtibos", "Цитировать"))
        titlesLanguages.add(
            TitleLanguages(
                175,
                "Select any style",
                "Uslubni tanlang",
                "Выберите любой стиль"
            )
        )
        titlesLanguages.add(TitleLanguages(176, "Select", "Tanlang", "Выбирать"))
        titlesLanguages.add(TitleLanguages(177, "Close", "Yopish", "Закрывать"))
        titlesLanguages.add(
            TitleLanguages(
                178,
                "Enter Username",
                "Foydalanuvchi nomini kiriting",
                "Введите имя пользователя"
            )
        )
        titlesLanguages.add(TitleLanguages(179, "Not available", "Mavjud emas", "нет в наличии"))
        titlesLanguages.add(TitleLanguages(180, "Available", "Mavjud", "наличии"))
        titlesLanguages.add(TitleLanguages(181, "What is this", "Bu nima", "Что это?"))
        titlesLanguages.add(
            TitleLanguages(
                182,
                "This indicator is the number of active users of our application.",
                "Bu ko'rsatgich ilovamizning faol foydalanuvchilari soni",
                "Этот показатель — количество активных пользователей нашего приложения."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                183,
                "Stop automatic downloads",
                "Avtomatik yuklab olishni to'xtatish",
                "Остановить автоматические загрузки"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                184,
                "Special forward",
                "Maxsus uzatish",
                "Специальная пересылка"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                185,
                "Auto message translation",
                "Avtomatik xabar tarjimasi",
                "Автоперевод сообщений"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                186,
                "Original condition",
                "Asl xolati",
                "Исходное состояние"
            )
        )
        titlesLanguages.add(TitleLanguages(187, "Message Language", "Xabar tili", "Язык сообщения"))
        titlesLanguages.add(
            TitleLanguages(
                188,
                "Please select the language in which messages will be sent in this chat. If ‘Original condition’ is selected, messages will be sent without translation.",
                "Iltimos, bu suhbat uchun xabar yuboriladigan tilni tanlang. ‘Asl xolati’ tanlansa, xabar tarjima qilinmaydi va asl holida yuboriladi.",
                "Пожалуйста, выберите язык, на котором будут отправляться сообщения в этом чате. При выборе ‘Исходное состояние’ сообщение будет отправлено без перевода."
            )
        )
        titlesLanguages.add(TitleLanguages(189, "Select", "Tanlash", "Выбирать"))
        titlesLanguages.add(
            TitleLanguages(
                190,
                "Confirmation dialogs",
                "Tasdiqlash dialoglari",
                "Диалоги подтверждения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                191,
                "Ask for confirmation before sending a sticker",
                "Stiker yuborishdan oldin tasdiqlashni so'raydi",
                "Спрашивает подтверждение перед отправкой стикера"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                192,
                "Ask for confirmation before sending a voice message",
                "Ovozli xabar yuborishdan oldin tasdiqlashni so'raydi",
                "Спрашивает подтверждение перед отправкой голосового сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                193,
                "Ask for confirmation before sending a GIF",
                "GIF yuborishdan oldin tasdiqlashni so'raydi",
                "Спрашивает подтверждение перед отправкой GIF"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                194,
                "Configure confirmation dialog output before sending Sticker, Voice, Gif",
                "Sticker, Ovoz, Gif yuborishdan oldin tasdiqlash dialogi chiqishini sozlang",
                "Настройте вывод диалогового окна подтверждения перед отправкой стикера, звука, GIF-файла"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                195,
                "Do you really want to send ?",
                "Haqiqatdan ham jo'natmoqchimisiz ?",
                "Вы действительно хотите отправить ?"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                196,
                "Turn off automatic downloading of photos, videos and files in every chat on this account. Saves mobile data and storage — you can still download anything by tapping it.",
                "Bu akkauntdagi barcha suhbatlarda rasm, video va fayllarning avtomatik yuklanishini o'chiradi. Trafik va xotirani tejaydi — kerakli faylni ustiga bosib baribir yuklab olasiz.",
                "Отключает автозагрузку фото, видео и файлов во всех чатах этого аккаунта. Экономит трафик и память — любой файл всё равно можно загрузить нажатием."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                197,
                "Folder settings",
                "Jild sozlamalari",
                "настройки папки"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                198,
                "Hide folders",
                "Jildlarni yashirish",
                "Скрыть папку"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                199,
                "copied to clipboard",
                "nusxalandi",
                "скопировано в буфер обмена"
            )
        )
        titlesLanguages.add(TitleLanguages(200, "Notification", "Eslatma", "Уведомление"))
        titlesLanguages.add(
            TitleLanguages(
                201,
                "Set timer",
                "Timer o'rnatish",
                "Установить таймер"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                202,
                "One-time execution",
                "Bir martali ijro",
                "Единовременное исполнение"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                203,
                "The recipient will be able to listen or play to it only once.",
                "Qabul qiluvchi ovozni yoki dumaloq videoni faqat bir marta tinglashi yoki Ko'rishi mumkin bo'ladi.",
                "Получатель сможет прослушать или воспроизвести его только один раз."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                204,
                "Set a time, choose a sound, and if you receive a message from any chat that you haven't read by the time you set, Novagram will notify you that there is an unread message. This will help you not to miss any message! You can easily turn this feature on or off from the left menu in the main chat window!",
                "Vaqt belgilang, ovozni tanlang va sizga har qanday suhbatlardan kelgan xabar belgilagan vaqtingizgacha o'qimasangiz Novagram sizni o'qilmaga xabar borligi haqida ogoxlantiradi. Bu esa biror xabar etiborsiz qolmasligiga yordam beradi! Bu funksiyani siz asosiy suhbatlar oynasidachi chap menudan ham osongina o'chirib yoqishingiz mumkun!",
                "Установите время, выберите звук, и если вы получите сообщение из любого чата, которое вы не прочитали к установленному вами времени, Novagram уведомит вас о наличии непрочитанного сообщения. Это поможет вам не пропустить ни одного сообщения! Вы можете легко включить или выключить эту функцию из левого меню в главном окне чата!"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                205,
                "Set sound",
                "Ovoz o'rnatish",
                "Установить звук"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                206,
                "sound",
                "ovoz",
                "звук"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                207,
                "Active accounts",
                "Active accountlar",
                "Активные аккаунты"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                208,
                "Do you want to activate the reminder feature?",
                "Eslatma xsusiyatini faollashtirmoqchimisiz ?",
                "Хотите ли вы активировать функцию напоминания?"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                209,
                "Share",
                "Ulashish",
                "Поделиться"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                210,
                "Sending a sticker",
                "Sticker yuborish",
                "Отправка стикера"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                211,
                "Sending a voice",
                "Ovoz yuborish",
                "Отправка голосового"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                212,
                "Sending a gif",
                "Gif yuborish",
                "Отправка GIF"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                213,
                "Secret Chat",
                "Yashirin suhbat",
                "Секретный чат"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                214,
                "Secret Chat",
                "Yashirin suhbat",
                "Секретный чат"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                215,
                "Add to secret chat",
                "Yashirin suhbatga qo'shing",
                "Добавить в секретный чат"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                216,
                "Remove from secret chat",
                "Yashirin suhbatdan chiqarish",
                "Удалить из секретного чата"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                217,
                // Both gestures are named end to end on purpose. The action in the selection menu is
                // getMyTitles(215) — "Add to secret chat" (DialogsActivity.secretChatItem).
                "Move chats into a hidden folder locked with a password, PIN or fingerprint. To hide a chat, press and hold it and choose Add to secret chat. To open the folder, press and hold the app name at the top of the chat list.",
                "Suhbatlarni parol, PIN yoki barmoq izi bilan qulflangan yashirin jildga ko'chiradi. Suhbatni yashirish uchun uni bosib turing va «Yashirin suhbatga qo'shing» ni tanlang. Jildni ochish uchun suhbatlar ro'yxati tepasidagi ilova nomini bosib turing.",
                "Переносит чаты в скрытую папку под паролем, PIN-кодом или отпечатком пальца. Чтобы скрыть чат, нажмите и удерживайте его и выберите «Добавить в секретный чат». Чтобы открыть папку, нажмите и удерживайте название приложения вверху списка чатов."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                218,
                "Accepting requests",
                "So'rovlarni qabul qilish",
                "Прием заявок"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                219,
                "Accepting join requests",
                "Qo'shilish so'rovlarni qabul qilish",
                "Прием заявок на присоединение"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                220,
                "Enter the number of requests and choose to accept or not. Click the Start button. Then Novagram will automatically accept or reject Join requests based on the amount and selection you entered. We recommend that you do not leave the chat while this feature is running!",
                "So'rovlar sonini kiriting va qabul qilish yoki qabul qilmaslikni tanlang. Boshlash tugmasini bosing. Shunda siz kiritgan miqdor va tanlov bo'yicha Novagram avtomatik Qo'shilish so'rovlarni qabul qiladi yoki qilmaydi. Faqat bu Funksiya ishlayotganda suhbatdan chiqmaslikni tafsiya qilamiz!",
                "Введите количество запросов и выберите, принять или отклонить. Нажмите кнопку «Старт». После этого Novagram автоматически примет или отклонит запросы на присоединение в зависимости от количества и выбранного вами варианта. Рекомендуем не выходить из чата, пока эта функция активна!"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                221,
                "Start",
                "Boshlash",
                "Начинать"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                222,
                "Acceptance",
                "Qabul qilish",
                "Принятие"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                223,
                "Reject",
                "Rad qilish",
                "Отказываться"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                224,
                "This indicator is the number of active accounts added to the application.",
                "Bu raqam — ilovaga qo'shilgan faol akkauntlar soni.",
                "Данный показатель представляет собой количество активных аккаунтов, добавленных в приложение."
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                225,
                "Confirm",
                "Tasdiqlash",
                "Подтверждать"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                226,
                "Generate Promo code",
                "Promo-kod yarating",
                "Сгенерировать промокод"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                227,
                "Generate",
                "Yaratish",
                "Генерировать"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                228,
                "Auto answer",
                "Avtomatik javob",
                "Автоматический ответ"
            )
        )

        titlesLanguages.add(
            TitleLanguages(
                229,
                "Ad by Novagram",
                "Novagram reklamasi",
                "Реклама Novagram"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                230,
                "Show Story",
                "Hikoyani Ko'rsatish",
                "Показать Историю"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                231,
                "The translation has not changed",
                "Tarjima o'zgarmadi",
                "Перевод не изменился"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                232,
                "View stories anonymously",
                "Hikoyalarni yashirin ko'rish",
                "Анонимный просмотр историй"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                233,
                "When enabled, story owners won't see that you viewed their story.",
                "Yoqilganda, hikoya egalari sizning ko'rganingizni bilmaydi.",
                "Когда включено, владельцы историй не увидят, что вы посмотрели историю."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                234,
                "Messages",
                "Xabarlar",
                "Сообщения"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                235,
                "Keep messages that others delete instead of removing them.",
                "Boshqalar o'chirgan xabarlarni o'chirmasdan saqlab qoladi.",
                "Сохраняет сообщения, удалённые другими, вместо их удаления."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                236,
                "Settings",
                "Sozlamalar",
                "Настройки"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                237,
                "Download",
                "Yuklab olish",
                "Загрузка"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                238,
                "Reply message",
                "Javob matni",
                "Текст ответа"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                239,
                "Automatically reply once to the first message in each chat while you are away.",
                "Siz yo'qligingizda har bir suhbatdagi birinchi xabarga avtomatik bir marta javob beradi.",
                "Автоматически отвечает один раз на первое сообщение в каждом чате, пока вас нет."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                240,
                "Folder icons",
                "Jild ikonkalari",
                "Иконки папок"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                241,
                // The picker hangs off the icon button beside the name field on the folder edit screen
                // (FilterCreateActivity), not off the folder row itself — say exactly where to tap.
                "Show chat folder tabs as icons instead of names. To change a folder's icon, open its settings and tap the icon next to the name.",
                "Suhbat jild tablarini nom o'rniga ikonka ko'rinishida ko'rsatadi. Jild ikonkasini o'zgartirish uchun uning sozlamalarini ochib, nom yonidagi ikonkani bosing.",
                "Показывает вкладки папок значками вместо названий. Чтобы сменить значок папки, откройте её настройки и нажмите на значок рядом с названием."
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                242,
                "Go to first message",
                "Birinchi xabarga o'tish",
                "Перейти к первому сообщению"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                243,
                "Advanced",
                "Qo'shimcha",
                "Дополнительно"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                244,
                "Privacy",
                "Maxfiylik",
                "Конфиденциальность"
            )
        )
        titlesLanguages.add(
            TitleLanguages(
                245,
                "Hide your online status, typing indicator, and read receipts from everyone.",
                "Online holatingiz, yozayotganingiz va xabarlarni o'qiganingizni hammadan yashiradi.",
                "Скрывает ваш онлайн-статус, набор текста и прочтение сообщений от всех."
            )
        )
        titlesLanguages.add(TitleLanguages(246, "Lock chat", "Suhbatni qulflash", "Заблокировать чат"))
        titlesLanguages.add(TitleLanguages(247, "Unlock chat", "Suhbatni ochish", "Разблокировать чат"))
        titlesLanguages.add(TitleLanguages(248, "How to lock this chat?", "Bu suhbatni qanday qulflaysiz?", "Как заблокировать этот чат?"))
        titlesLanguages.add(TitleLanguages(249, "Common password", "Umumiy parol", "Общий пароль"))
        titlesLanguages.add(TitleLanguages(250, "Separate password", "Alohida parol", "Отдельный пароль"))
        titlesLanguages.add(TitleLanguages(251, "Change common password", "Umumiy parolni o'zgartirish", "Изменить общий пароль"))
        titlesLanguages.add(TitleLanguages(252, "Chat lock", "Suhbat qulfi", "Блокировка чата"))
        titlesLanguages.add(TitleLanguages(253, "Unlock with fingerprint", "Barmoq izi bilan ochish", "Разблокировка отпечатком"))
        titlesLanguages.add(TitleLanguages(254, "Use your fingerprint to open locked chats", "Qulflangan suhbatlarni barmoq izi bilan oching", "Открывайте заблокированные чаты отпечатком"))
        titlesLanguages.add(TitleLanguages(255, "Special forward", "Maxsus uzatish", "Специальная пересылка"))
        titlesLanguages.add(TitleLanguages(256, "Enable one-time voice/video", "Bir martalik ovoz/videoni yoqish", "Включить одноразовое голосовое/видео"))
        titlesLanguages.add(TitleLanguages(257, "Disable one-time voice/video", "Bir martalik ovoz/videoni o'chirish", "Выключить одноразовое голосовое/видео"))
        titlesLanguages.add(TitleLanguages(258, "Log in by QR code", "QR kod orqali kirish", "Вход по QR-коду"))
        titlesLanguages.add(TitleLanguages(259, "1. Open Telegram on your phone\n2. Go to Settings → Devices → Link Device\n3. Point your phone at this screen", "1. Telefoningizda Telegramni oching\n2. Sozlamalar → Qurilmalar → Qurilma ulash\n3. Telefoningizni shu ekranga qarating", "1. Откройте Telegram на телефоне\n2. Настройки → Устройства → Подключить устройство\n3. Наведите телефон на этот экран"))
        titlesLanguages.add(TitleLanguages(260, "Hide folder tabs", "Jild tablarini yashirish", "Скрыть вкладки папок"))
        titlesLanguages.add(TitleLanguages(261, "Hide the folder tabs row in the chat list", "Suhbatlar ro'yxatidagi jild tablari qatorini yashirish", "Скрыть строку вкладок папок в списке чатов"))
        titlesLanguages.add(TitleLanguages(262, "Channels", "Kanallar", "Каналы"))
        titlesLanguages.add(TitleLanguages(263, "Auto-accept join requests", "A'zolik so'rovlarini avto-qabul", "Авто-приём заявок на вступление"))
        titlesLanguages.add(TitleLanguages(264, "Automatically approve join requests in channels/groups you manage", "Siz boshqaradigan kanal/guruhlardagi a'zolik so'rovlarini avtomatik tasdiqlash", "Автоматически одобрять заявки в управляемых вами каналах и группах"))
        titlesLanguages.add(TitleLanguages(265, "Log in with bot token", "Bot tokeni bilan kirish", "Войти по токену бота"))
        titlesLanguages.add(TitleLanguages(266, "Bot token", "Bot tokeni", "Токен бота"))
        titlesLanguages.add(TitleLanguages(267, "Create a bot via @BotFather and paste its token here to sign in as that bot.\n\nNote: a bot can't see your contacts, can only message users who pressed /start, and joins groups only as an admin. The chat list stays empty until the bot receives messages.", "@BotFather orqali bot yarating va uning tokenini shu yerga joylashtirib, o'sha bot sifatida kiring.\n\nEslatma: bot kontaktlaringizni ko'rmaydi, faqat /start bosgan foydalanuvchilarga yoza oladi va guruhlarga faqat admin sifatida qo'shiladi. Bot xabar olmaguncha suhbatlar ro'yxati bo'sh turadi.", "Создайте бота через @BotFather и вставьте его токен сюда, чтобы войти как этот бот.\n\nПримечание: бот не видит ваши контакты, может писать только пользователям, нажавшим /start, и вступает в группы только как админ. Список чатов пуст, пока бот не получит сообщения."))
        titlesLanguages.add(TitleLanguages(268, "Invalid bot token. Copy it from @BotFather (looks like 123456789:AA...).", "Token noto'g'ri. Uni @BotFather'dan nusxalang (123456789:AA... ko'rinishida).", "Неверный токен. Скопируйте его у @BotFather (вид 123456789:AA...)."))
        titlesLanguages.add(TitleLanguages(269, "Message reminder", "Xabar eslatmasi", "Напоминание о сообщении"))
        titlesLanguages.add(TitleLanguages(270, "Unread message reminder", "O'qilmagan xabar eslatmasi", "Напоминание о непрочитанном"))
        titlesLanguages.add(TitleLanguages(271, "If you don't open an unread message within the set time, get reminded so you don't miss it", "Belgilangan vaqt ichida o'qilmagan xabarni ochmasangiz, o'tkazib yubormaslik uchun eslatib turamiz", "Если вы не откроете непрочитанное сообщение за заданное время, мы напомним, чтобы вы не пропустили его"))
        titlesLanguages.add(TitleLanguages(272, "Reminder delay", "Eslatma vaqti", "Задержка напоминания"))
        titlesLanguages.add(TitleLanguages(273, "min", "daq", "мин"))
        titlesLanguages.add(TitleLanguages(274, "Reminder sound", "Eslatma ovozi", "Звук напоминания"))
        titlesLanguages.add(TitleLanguages(275, "Notification tone", "Bildirishnoma ovozi", "Звук уведомления"))
        titlesLanguages.add(TitleLanguages(276, "Alarm tone", "Budilnik ovozi", "Звук будильника"))
        titlesLanguages.add(TitleLanguages(277, "Unread messages", "O'qilmagan xabarlar", "Непрочитанные сообщения"))
        titlesLanguages.add(TitleLanguages(278, "You have unread messages", "Sizda o'qilmagan xabarlar bor", "У вас есть непрочитанные сообщения"))
        titlesLanguages.add(TitleLanguages(279, "Join requests", "A'zolik so'rovlari", "Заявки на вступление"))
        titlesLanguages.add(TitleLanguages(280, "people are waiting to join", "kishi qo'shilishni kutmoqda", "ожидают вступления"))
        titlesLanguages.add(TitleLanguages(281, "No pending requests", "Kutilayotgan so'rovlar yo'q", "Нет ожидающих заявок"))
        titlesLanguages.add(TitleLanguages(282, "Approve", "Qabul qilish", "Принять"))
        titlesLanguages.add(TitleLanguages(283, "Decline", "Rad etish", "Отклонить"))
        titlesLanguages.add(TitleLanguages(284, "How many?", "Nechta?", "Сколько?"))
        titlesLanguages.add(TitleLanguages(285, "All", "Hammasi", "Все"))
        titlesLanguages.add(TitleLanguages(286, "Approving…", "Qabul qilinmoqda…", "Принятие…"))
        titlesLanguages.add(TitleLanguages(287, "Declining…", "Rad etilmoqda…", "Отклонение…"))
        titlesLanguages.add(TitleLanguages(288, "Done", "Tayyor", "Готово"))
        titlesLanguages.add(TitleLanguages(289, "approved", "qabul qilindi", "принято"))
        titlesLanguages.add(TitleLanguages(290, "declined", "rad etildi", "отклонено"))
        titlesLanguages.add(TitleLanguages(291, "Cancel", "Bekor qilish", "Отмена"))
        titlesLanguages.add(TitleLanguages(292, "Something went wrong", "Xatolik yuz berdi", "Что-то пошло не так"))
        titlesLanguages.add(TitleLanguages(293, "Close", "Yopish", "Закрыть"))
        titlesLanguages.add(TitleLanguages(294, "Join with all accounts", "Barcha akkauntlar bilan qo'shilish", "Войти всеми аккаунтами"))
        titlesLanguages.add(TitleLanguages(295, "accounts joined", "akkaunt qo'shildi", "аккаунтов присоединено"))
        titlesLanguages.add(TitleLanguages(296, "Joining on all accounts…", "Barcha akkauntlar bilan qo'shilmoqda…", "Вход всеми аккаунтами…"))
        titlesLanguages.add(TitleLanguages(297, "Download stories", "Hikoyalarni yuklab olish", "Скачивание историй"))
        titlesLanguages.add(TitleLanguages(298, "Add a save button in the story viewer to save anyone's story to your gallery", "Hikoya ko'ruvchida istalgan hikoyani galereyaga saqlash tugmasini qo'shadi", "Добавляет кнопку сохранения любой истории в галерею"))
        titlesLanguages.add(TitleLanguages(299, "Save story", "Hikoyani saqlash", "Сохранить историю"))
        titlesLanguages.add(TitleLanguages(300, "Auto-add text", "Avtomatik matn qo'shish", "Авто-добавление текста"))
        titlesLanguages.add(TitleLanguages(301, "Append your text to every message you send in this chat", "Bu suhbatda yuborgan har bir xabaringizga matningizni qo'shadi", "Добавляет ваш текст к каждому сообщению в этом чате"))
        titlesLanguages.add(TitleLanguages(302, "Edit text", "Matnni tahrirlash", "Изменить текст"))
        titlesLanguages.add(TitleLanguages(303, "Save changes?", "O'zgarishlarni saqlansinmi?", "Сохранить изменения?"))
        titlesLanguages.add(TitleLanguages(304, "Save changes to your auto text?", "Avto matn o'zgarishlarini saqlaysizmi?", "Сохранить изменения авто-текста?"))
        titlesLanguages.add(TitleLanguages(305, "Novagram Settings", "Novagram Sozlamalar", "Настройки Novagram"))
        titlesLanguages.add(TitleLanguages(306, "One-time voice & video", "Bir martalik ovoz va video", "Одноразовые голосовые и видео"))
        titlesLanguages.add(TitleLanguages(307, "Send voice messages and round videos as view-once by default", "Ovozli xabar va dumaloq videolarni standart holatda bir martalik yuborish", "Отправлять голосовые и кружочки как одноразовые по умолчанию"))
        titlesLanguages.add(TitleLanguages(308, "Voice to text", "Ovozni matnga o'girish", "Голос в текст"))
        titlesLanguages.add(TitleLanguages(309, "Listening…", "Tinglanmoqda…", "Слушаю…"))
        titlesLanguages.add(TitleLanguages(310, "Speech recognition unavailable", "Ovozni tanib bo'lmadi", "Распознавание речи недоступно"))
        titlesLanguages.add(TitleLanguages(311, "Voice input language", "Ovozli kiritish tili", "Язык голосового ввода"))
        titlesLanguages.add(TitleLanguages(312, "Default (device)", "Standart (qurilma)", "По умолчанию (устройство)"))
        titlesLanguages.add(TitleLanguages(313, "Speak language", "Gapirish tili", "Язык речи"))
        titlesLanguages.add(TitleLanguages(314, "Translate to", "Tarjima tili", "Перевести на"))
        titlesLanguages.add(TitleLanguages(315, "Off", "O'chiq", "Выкл"))
        titlesLanguages.add(TitleLanguages(316, "Start", "Boshlash", "Начать"))
        titlesLanguages.add(TitleLanguages(317, "Translation failed", "Tarjima qilinmadi", "Не удалось перевести"))
        // Section header for "protect from strangers" + "block APK files". Was a second "Privacy",
        // identical to the Ghost/Secret-chat section above it; both rows here guard against someone
        // else reaching you, so Security separates them without repeating the row wording.
        titlesLanguages.add(TitleLanguages(318, "Security", "Xavfsizlik", "Безопасность"))
        titlesLanguages.add(TitleLanguages(319, "Protect from strangers", "Begonalardan himoya", "Защита от незнакомцев"))
        titlesLanguages.add(TitleLanguages(320, "Hide chats and mute notifications from people not in your contacts", "Kontaktingizda yo'q odamlarning suhbatlarini yashiradi va bildirishnomalarini o'chiradi", "Скрывает чаты и отключает уведомления от людей не из ваших контактов"))
        titlesLanguages.add(TitleLanguages(321, "Stranger chats", "Begona suhbatlar", "Чаты незнакомцев"))
        titlesLanguages.add(TitleLanguages(322, "When enabled, chats from people not in your contacts will be hidden from your main list and silenced. You won't lose anything — they go to \"Stranger chats\", where you can read and reply. Telegram's official chats are never hidden.", "Yoqilsa, kontaktingizda yo'q odamlarning suhbatlari asosiy ro'yxatdan yashiriladi va ovozsiz bo'ladi. Hech narsa yo'qolmaydi — ular \"Begona suhbatlar\"ga tushadi, u yerdan o'qib, javob yozsangiz bo'ladi. Telegramning rasmiy suhbatlari hech qachon yashirilmaydi.", "Если включить, чаты от людей не из ваших контактов будут скрыты из основного списка и беззвучны. Ничего не потеряется — они попадут в \"Чаты незнакомцев\", где их можно прочитать и ответить. Официальные чаты Telegram никогда не скрываются."))
        titlesLanguages.add(TitleLanguages(323, "Enable", "Yoqish", "Включить"))
        titlesLanguages.add(TitleLanguages(324, "Auto-translate", "Avtomatik tarjima", "Автоперевод"))
        titlesLanguages.add(TitleLanguages(325, "Translate your messages to this language before sending. Tap Send once to translate and preview, then again to send.", "Xabarlaringizni yuborishdan oldin shu tilga tarjima qiladi. Bir marta \"Yuborish\"ni bossangiz tarjima qilib ko'rsatadi, yana bossangiz yuboradi.", "Переводит ваши сообщения на этот язык перед отправкой. Нажмите «Отправить» один раз для перевода и предпросмотра, затем ещё раз для отправки."))
        titlesLanguages.add(TitleLanguages(326, "Off", "O'chiq", "Выкл"))
        titlesLanguages.add(TitleLanguages(327, "Translating…", "Tarjima qilinmoqda…", "Перевод…"))
        titlesLanguages.add(TitleLanguages(328, "Couldn't translate. Tap Send again to send as-is.", "Tarjima qilib bo'lmadi. O'zgartirishsiz yuborish uchun \"Yuborish\"ni yana bosing.", "Не удалось перевести. Нажмите «Отправить» ещё раз, чтобы отправить как есть."))
        titlesLanguages.add(TitleLanguages(329, "No internet. Tap Send again to send as-is.", "Internet yo'q. O'zgartirishsiz yuborish uchun \"Yuborish\"ni yana bosing.", "Нет интернета. Нажмите «Отправить» ещё раз, чтобы отправить как есть."))
        titlesLanguages.add(TitleLanguages(330, "Scan text", "Matnni skanerlash", "Распознать текст"))
        titlesLanguages.add(TitleLanguages(331, "No text found", "Matn topilmadi", "Текст не найден"))
        titlesLanguages.add(TitleLanguages(332, "Recognizing…", "Aniqlanmoqda…", "Распознавание…"))
        titlesLanguages.add(TitleLanguages(333, "Analytics", "Analitika", "Аналитика"))
        titlesLanguages.add(TitleLanguages(334, "Number of Novagram users", "Novagram foydalanuvchilari soni", "Количество пользователей Novagram"))
        titlesLanguages.add(TitleLanguages(335, "Active accounts", "Faol akkauntlar", "Активные аккаунты"))
        titlesLanguages.add(TitleLanguages(336, "App usage numbers", "Ilovadan foydalanish ko'rsatkichlari", "Показатели использования приложения"))
        titlesLanguages.add(TitleLanguages(337, "What is this", "Bu nima", "Что это?"))
        titlesLanguages.add(TitleLanguages(338, "This indicator is the number of active users of our application.", "Bu raqam — ilovamizdagi faol foydalanuvchilar soni.", "Этот показатель — количество активных пользователей нашего приложения."))
        titlesLanguages.add(TitleLanguages(339, "This indicator is the number of active accounts added to the application.", "Bu raqam — ilovaga qo'shilgan faol akkauntlar soni.", "Данный показатель представляет собой количество активных аккаунтов, добавленных в приложение."))
        titlesLanguages.add(TitleLanguages(340, "Skip", "O'tkazib yuborish", "Пропустить"))
        titlesLanguages.add(TitleLanguages(341, "Lock this chat behind a passcode", "Bu suhbatni parol bilan himoyalang", "Заблокируйте этот чат паролем"))
        titlesLanguages.add(TitleLanguages(342, "Ghost button on home", "Asosiy oynada Ghost tugmasi", "Кнопка призрака на главном экране"))
        titlesLanguages.add(TitleLanguages(343, "Show a quick toggle next to search to turn Ghost mode on/off from the chat list", "Ghost rejimini suhbatlar ro'yxatidan tez yoqib-o'chirish uchun qidiruv yonida tugma chiqaradi", "Показывать кнопку рядом с поиском для быстрого включения/выключения режима призрака из списка чатов"))
        titlesLanguages.add(TitleLanguages(344, "Bots", "Botlar", "Боты"))
        titlesLanguages.add(TitleLanguages(345, "Novagram bots", "Novagram botlari", "Боты Novagram"))
        titlesLanguages.add(TitleLanguages(346, "Handy bots — tap to open and start", "Foydali botlar — bosib oching va ishga tushiring", "Полезные боты — нажмите, чтобы открыть и запустить"))
        titlesLanguages.add(TitleLanguages(347, "Media & downloads", "Media va yuklab olish", "Медиа и загрузки"))
        titlesLanguages.add(TitleLanguages(348, "Media editing", "Media tahrirlash", "Редактирование медиа"))
        titlesLanguages.add(TitleLanguages(349, "Tools", "Vositalar", "Инструменты"))
        titlesLanguages.add(TitleLanguages(350, "Useful & fun", "Foydali va ko'ngil ochar", "Полезное и развлечения"))
        titlesLanguages.add(TitleLanguages(351, "Copy link", "Havolani nusxalash", "Скопировать ссылку"))
        titlesLanguages.add(TitleLanguages(352, "Send link", "Havolani jo'natish", "Отправить ссылку"))
        titlesLanguages.add(TitleLanguages(353, "Round video", "Dumaloq video", "Видеосообщения"))
        titlesLanguages.add(TitleLanguages(354, "Record round video messages with the front camera. Turn it off to use the rear camera. You can still flip the camera while recording.", "Dumaloq video xabarlarni old kameradan yozadi. O'chirilsa — orqa kameradan. Yozuv paytida kamerani baribir almashtira olasiz.", "Записывать видеосообщения (кружки) с фронтальной камеры. Выключите — будет задняя. Во время записи камеру всё равно можно переключить."))
        titlesLanguages.add(TitleLanguages(355, "About", "Ilova haqida", "О приложении"))
        titlesLanguages.add(TitleLanguages(356, "Support", "Yordam", "Поддержка"))
        titlesLanguages.add(TitleLanguages(357, "Contact support", "Yordam xizmati bilan bog'lanish", "Связаться с поддержкой"))
        titlesLanguages.add(TitleLanguages(358, "Message our admin directly", "Adminimizga to'g'ridan-to'g'ri yozing", "Напишите нашему администратору напрямую"))
        titlesLanguages.add(TitleLanguages(359, "Rate the app", "Ilovani baholang", "Оценить приложение"))
        titlesLanguages.add(TitleLanguages(360, "Leave a review on Google Play", "Google Play'da sharh qoldiring", "Оставьте отзыв в Google Play"))
        titlesLanguages.add(TitleLanguages(361, "Company", "Kompaniya", "Компания"))
        titlesLanguages.add(TitleLanguages(362, "Website", "Veb-sayt", "Веб-сайт"))
        titlesLanguages.add(TitleLanguages(363, "Email", "Email", "Эл. почта"))
        titlesLanguages.add(TitleLanguages(364, "A legally registered technology company in Uzbekistan. We built Novagram to give people fast, secure, and effortless communication.", "Biz — O'zbekistonda rasmiy ro'yxatdan o'tgan texnologiya kompaniyasimiz. Odamlarga tez, xavfsiz va qulay muloqotni taqdim etuvchi Novagram ilovasini ishlab chiqqanmiz.", "Официально зарегистрированная в Узбекистане технологическая компания. Мы создали Novagram, чтобы общение было быстрым, безопасным и удобным."))
        titlesLanguages.add(TitleLanguages(365, "App version", "Ilova versiyasi", "Версия приложения"))
        titlesLanguages.add(TitleLanguages(366, "Secure messenger", "Xavfsiz messenjer", "Безопасный мессенджер"))
        titlesLanguages.add(TitleLanguages(367, "Founded 2022", "2022-yilda tashkil topgan", "Основана в 2022 году"))
        titlesLanguages.add(TitleLanguages(368, "Republic of Uzbekistan", "O'zbekiston Respublikasi", "Республика Узбекистан"))
        titlesLanguages.add(TitleLanguages(369, "Leave a review on RuStore", "RuStore'da sharh qoldiring", "Оставьте отзыв в RuStore"))
        titlesLanguages.add(TitleLanguages(370, "Novagram website", "Novagram sayti", "Сайт Novagram"))
        titlesLanguages.add(TitleLanguages(371, "Company website", "Kompaniya sayti", "Сайт компании"))
        titlesLanguages.add(TitleLanguages(372, "No secret chats", "Yashirin chatlar yo'q", "Нет секретных чатов"))
        titlesLanguages.add(TitleLanguages(373, "Not a stranger", "Begona emas", "Не незнакомец"))
        titlesLanguages.add(TitleLanguages(374, "No stranger chats", "Begona suhbatlar yo'q", "Нет чужих чатов"))
        titlesLanguages.add(TitleLanguages(375, "Select all", "Hammasini belgilash", "Выбрать все"))
        titlesLanguages.add(TitleLanguages(376, "Deselect all", "Belgilashni bekor qilish", "Снять выделение"))
        titlesLanguages.add(TitleLanguages(377, "APK file blocked", "APK fayl bloklandi", "APK-файл заблокирован"))
        titlesLanguages.add(TitleLanguages(378, "Block APK files", "APK fayllarni bloklash", "Блокировать APK-файлы"))
        titlesLanguages.add(TitleLanguages(379, "Incoming .apk files are not shown in chats — protection from malicious apps. Files you send yourself are not affected.", "Kelgan .apk fayllar suhbatlarda ko'rsatilmaydi — zararli ilovalardan himoya. O'zingiz yuborgan fayllarga ta'sir qilmaydi.", "Входящие .apk-файлы не показываются в чатах — защита от вредоносных приложений. На файлы, которые вы отправляете сами, не влияет."))
        titlesLanguages.add(TitleLanguages(380, "Message field", "Xabar maydoni", "Поле сообщения"))
        titlesLanguages.add(TitleLanguages(381, "Voice input button", "Ovozli kiritish tugmasi", "Кнопка голосового ввода"))
        titlesLanguages.add(TitleLanguages(382, "Show the microphone next to the message field to dictate text instead of typing. Turn it off to free up space in the field. Hidden automatically on devices without speech recognition.", "Yozish o'rniga gapirib matn kiritish uchun xabar maydoni yonida mikrofonni ko'rsatadi. O'chirsangiz maydonda joy bo'shaydi. Nutqni tanimaydigan qurilmalarda avtomatik yashiriladi.", "Показывать микрофон рядом с полем сообщения, чтобы диктовать текст вместо набора. Выключите, чтобы освободить место в поле. На устройствах без распознавания речи скрывается автоматически."))
        // Asked when protection is switched OFF and the inbox still holds captured chats. %d is replaced
        // with the count — keep the placeholder in every translation.
        titlesLanguages.add(TitleLanguages(383, "\"Stranger chats\" still holds %d chats. Return them to your main list?", "\"Begona suhbatlar\"da hali %d ta suhbat bor. Ularni asosiy ro'yxatga qaytaramizmi?", "В \"Чатах незнакомцев\" ещё %d чатов. Вернуть их в основной список?"))
        titlesLanguages.add(TitleLanguages(384, "Return", "Qaytarish", "Вернуть"))
        // Deliberately short — two long button labels wrap badly in the alert on narrow screens.
        titlesLanguages.add(TitleLanguages(385, "Keep", "Qolsin", "Оставить"))
        // Tour body for the confirmation-dialogs group. The three rows there differ by one word, so the
        // walkthrough covers them in a single stop rather than three near-identical coach marks.
        titlesLanguages.add(TitleLanguages(386, "Ask for confirmation before sending a sticker, voice message or GIF, so nothing goes out by accident.", "Stiker, ovozli xabar yoki GIF yuborishdan oldin tasdiqlashni so'raydi — tasodifan yuborib qo'ymaysiz.", "Спрашивает подтверждение перед отправкой стикера, голосового сообщения или GIF — ничего не уйдёт случайно."))

        titlesLanguages.add(TitleLanguages(387, "Our channels", "Bizning kanallarimiz", "Наши каналы"))

        titlesLanguages.add(TitleLanguages(388, "Uzbek channel", "O'zbekcha kanal", "Канал на узбекском"))

        titlesLanguages.add(TitleLanguages(389, "Russian channel", "Ruscha kanal", "Канал на русском"))

        titlesLanguages.add(TitleLanguages(390, "Edit history", "Tahrirlar tarixi", "История изменений"))
    }

    init {
        languages.add(Language("English", "en-EN"))
        languages.add(Language("Uzbek", "uz-UZ"))
        languages.add(Language("Russian", "ru-RU"))
        languages.add(Language("العربية", "ar-AR"))
        languages.add(Language("Belarusian ", "by-BY"))
        languages.add(Language("Catalan ", "ca-CA"))
        languages.add(Language("Croatian ", "hr-HR"))
        languages.add(Language("Czech ", "cs-CS"))
        languages.add(Language("Nederlands-Dutch ", "nl-NL"))
        languages.add(Language("Finnish ", "fi-FI"))
        languages.add(Language("French ", "fr-FR"))
        languages.add(Language("German", "de-DE"))
        languages.add(Language("Hebrew", "he-HE"))
        languages.add(Language("Hungarian", "hu-HU"))
        languages.add(Language("Indonesian", "in-IN"))
        languages.add(Language("Italiano", "it-IT"))
        languages.add(Language("Kazakh", "kz-KZ"))
        languages.add(Language("한국어", "ko-KO"))
        languages.add(Language("Malay", "ms-MS"))
        languages.add(Language("Norwegian", "no-NO"))
        languages.add(Language("فарسی", "fa-IR"))
        languages.add(Language("Polish", "pl-PL"))
        languages.add(Language("Português (Brasil)", "pt-PT"))
        languages.add(Language("Serbian", "sr-SR"))
        languages.add(Language("Slovak", "sl-SL"))
        languages.add(Language("Español", "es-ES"))
        languages.add(Language("Swedish", "sv-SV"))
        languages.add(Language("Turkish", "th-TH"))
        languages.add(Language("Ukrainian", "uk-UK"))
    }

    fun getMyTitles(code: Int): String {
        checkTitlesInitialized()
        try {
            return when (languageCode) {
                "en" -> {
                    titlesLanguages[code].en
                }

                "ru" -> {
                    titlesLanguages[code].ru
                }

                "uz" -> {
                    titlesLanguages[code].uz
                }

                else -> {
                    titlesLanguages[code].en
                }
            }
        } catch (e: Exception) {
            return ""
        }
    }

    fun getLan(): Int {
        return sharedPreferences.getInt("mic_language_type", -1)
    }

    private fun saveLanguage(type: Int) {
        editor.putInt("mic_language_type", type)
        editor.commit()
    }

    fun showLanguageDialog(parentActivity: Activity, menuOrChat: Boolean) {
        val languagesCodeName = languages
        val l = arrayOfNulls<CharSequence>(languagesCodeName.size)
        for (i in languagesCodeName.indices) {
            l[i] = languagesCodeName[i].name
        }

        val builder = AlertDialog.Builder(parentActivity)
        builder.setTitle(getMyTitles(34))
        builder.setItems(l) { dialog: DialogInterface?, which: Int ->
            saveLanguage(which)
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
        builder.show()
    }
}

class Language(val name: String, val code: String)

class TitleLanguages(val code: Int, val en: String, val uz: String, val ru: String)