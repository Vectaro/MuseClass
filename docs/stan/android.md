# Перенесення на Android

Код — в `android/`. 2026-10-06 закомічено каркас з Android Studio: Java,
`ua.museclass.app`, minSdk 24, target/compile 37, AGP 9.4.1, Gradle 9.6,
Material3 DayNight. `gradlew assembleDebug` на робочому ПК проходить (JDK з
Android Studio). Екранів ще немає — одна порожня `MainActivity`.

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
