# Перенесення на Android

Код — в `android/`: Java, `ua.museclass.app`, minSdk 24, target/compile 37,
AGP 9.4.1, Gradle 9.6, Material3 DayNight. Збирається JDK з Android Studio.

## Що є (2026-10-06)
Перший наскрізний шматок: вхід → «Моя бібліотека» → партитура.

- **`:musicxml`** — окремий модуль на чистій Java 8 (`--release 8`, без
  Android). Порт `fromMusicXML` з `prototype/index.html` на `XmlPullParser`,
  `.mxl` через `java.util.zip` з обмеженням 64 МБ розпакованого і 1000 файлів.
  Модель: `Score` → `Part` → `Measure` → `Note` → `Pitch` (ступінь +
  альтерація + октава, `unpitched` для ударних); фортепіанні стани — окремі
  партії; позначки такту — `Bar`.
  - Свідомо інакше, ніж у прототипі: розмір, тональність, ключ і стрій партії
    — **початкові** (прототип бере останні в файлі; див. `open.md`); такт без
    нот лишається порожнім тактом, а не випадає; альтерація ціла.
  - Еталони: `android/tools/golden.js` проганяє 9 демо прототипу (через
    `toMusicXML`) і 2 фікстури через JS-парсер і пише JSON у
    `musicxml/src/test/resources/golden/`. `GoldenTest` звіряє Java поле в
    поле — усі 11 збігаються. Перезапускати скрипт, коли міняється парсер
    або демо прототипу (потрібен `npm install` у `prototype/`).
  - Перевірено на 4 файлах з dev-сервера: кількість тактів збігається з
    `measures` сервера, бенд — 7 партій з ударними.
- **Мережа** (`app/.../api/`): OkHttp 4.12 + Gson, `ApiClient` за
  [`docs/api.md`](../api.md). Помилки `problem+json` → поле `detail` як є;
  401 без тіла на запиті з токеном стирає сесію і веде на вхід; формат Spring
  без `detail` → «Щось пішло не так.»; немає зв'язку — окремий текст.
  JWT, `expiresAt`, `userId`, ім'я — у SharedPreferences `session`,
  виключеному з резервних копій. Сесія вважається живою до `expiresAt`
  мінус хвилина. Тести — MockWebServer.
- **Екрани**: `MainActivity` (розподільник) → `LoginActivity` (вхід /
  реєстрація, ім'я підказується з пошти) → `LibraryActivity`
  (`/me/library`, тягнути вниз — оновити, «Вийти» в меню) → `ScoreActivity`
  (картка + файл; назва, партії з ключем/розміром/тональністю і кількість
  тактів — з власного парсера; без рендеру).
- Desugaring увімкнено, R8 для release тримає DTO для Gson (`rules.keep`).

## Перевірки
З `android/`: `./gradlew :musicxml:test :app:testDebugUnitTest assembleDebug lintDebug`
— 11 + 11 тестів, lint 0 помилок. Release з R8 збирається (перевірено з
тимчасовою адресою в `local.properties`).

**Не перевірено в емуляторі**: AVD `Pixel_9` не стартує — бракує місця на
диску (треба 12 ГБ під userdata, вільно ~10). Див. `open.md`.

## Нотатки щодо перенесення
- Парсер MusicXML — чиста логіка, лягає на `XmlPullParser` майже один в один.
- `.mxl` розпаковується через `java.util.zip` з коробки.
- Рендер — `Canvas` (шлях ключа з `clef.js` у `Path` один раз, далі матрицею).
- **Звук — SoundFont (SF2) через FluidSynth або вбудований Sonivox, а не синтез
  із гармонік.** Див. «Звук» у [`decisions.md`](decisions.md).
- Під API 24 стежити за desugaring для Java 8.
- Тема — `DayNight` з `MODE_NIGHT_FOLLOW_SYSTEM` за замовчуванням і збереженим
  вибором користувача.
- Фото — `PickVisualMedia`.
- «Тримати екран» — `FLAG_KEEP_SCREEN_ON` на вікні плеєра.
- Витягнуті партії — через `FileProvider` + `ACTION_SEND`, архів збирати
  `java.util.zip`.
- Мережа: базова адреса — `BuildConfig.API_BASE` (зроблено 2026-10-06).
  - debug: `http://10.0.2.2:8081/api` — dev-сервер на тому ж ПК, емулятор
    бачить комп на `10.0.2.2`. Http дозволено тільки в debug і тільки для
    `10.0.2.2` (`app/src/debug/res/xml/network_security_config.xml`).
  - release: адреса в репо **не вшита**. Її вписують у `android/local.properties`
    рядком `museclass.releaseApiUrl=https://.../api` (адреса Funnel, пізніше
    білий IP). Без неї або без `https://` release не збирається — падає задача
    `checkReleaseApiUrl`. Перевірено обидва випадки.
