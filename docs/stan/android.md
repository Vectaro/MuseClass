# Перенесення на Android

Клієнт ще не почато; код піде в `android/`.

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
- Мережа: базова адреса в `BuildConfig`; емулятор бачить комп на `10.0.2.2`
  (dev-сервер на тому ж ПК — порт 8081, бойовий — адреса Funnel).
