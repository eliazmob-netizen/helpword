# VPN App — Clash-Android

Нативное Android приложение на Kotlin.  
Использует **Clash Meta** (Go core) для SS, VLESS, VMess, Trojan и других протоколов.

---

## Архитектура

```
Телефон
  └── VpnService (TUN интерфейс)
        └── перенаправляет весь трафик на 127.0.0.1:7890
              └── Clash Go Core
                    └── выбранный прокси (SS / VLESS / VMess / Trojan)
                          └── реальный сервер
```

---

## Сборка (шаг за шагом)

### 1. Установи Android Studio
https://developer.android.com/studio  
Версия Hedgehog (2023.1.1) или новее.

### 2. Скачай clash-android AAR

Приложение использует **ClashMetaForAndroid** — официальный Clash Meta для Android.

Вариант A — автоматически через JitPack (уже прописано в build.gradle):
```
implementation 'com.github.MetaCubeX:ClashMetaForAndroid:v2.10.1-Alpha-release'
```
При первой синхронизации Gradle скачает AAR (~25 MB) автоматически.

Вариант B — вручную (если JitPack недоступен):
1. Скачай релиз с https://github.com/MetaCubeX/ClashMetaForAndroid/releases
2. Файл `app-release.aar` скопируй в `app/libs/`
3. В `app/build.gradle` замени строку с JitPack на:
   ```
   implementation fileTree(dir: 'libs', include: ['*.aar'])
   ```

### 3. Открой проект
File → Open → выбери папку `vpnapp2`

### 4. Синхронизируй Gradle
Android Studio предложит сам. Первый раз ~3-5 мин.

### 5. Собери APK
Build → Build Bundle(s) / APK(s) → **Build APK(s)**

APK будет здесь:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 6. Установи на телефон
- Включи «Установка из неизвестных источников» в настройках телефона
- Перенеси APK и установи
- При первом нажатии «Подключить» Android запросит разрешение VPN — разрешить

---

## Командная строка (если есть JDK 17+ и Android SDK)

```bash
cd vpnapp2
chmod +x gradlew
./gradlew assembleDebug
```

---

## Что происходит при подключении

1. Приложение загружает YAML с `http://217.26.28.135/clash_final.yaml`
2. Пингует все серверы параллельно (реальный TCP connect)
3. Показывает только живые — отсортированные по пингу
4. При нажатии «Подключить быстрый»:
   - Берёт сервер с минимальным пингом
   - Записывает на диск минимальный Clash конфиг для этого одного сервера
   - Запускает Clash Go core (открывает SOCKS5 на 127.0.0.1:7891)
   - Поднимает Android VpnService (TUN интерфейс)
   - Весь трафик телефона идёт через Clash → выбранный сервер

---

## Файлы проекта

```
app/src/main/java/com/vpnapp/
  model/ProxyServer.kt          — модель сервера
  parser/ClashConfigParser.kt   — парсер YAML + генератор конфига
  utils/PingUtil.kt             — параллельный TCP пинг
  utils/ConfigRepository.kt     — загрузка и кеш YAML
  service/ClashVpnService.kt    — VpnService + Clash core
  ui/MainActivity.kt            — главный экран
  ui/MainViewModel.kt           — ViewModel
  ui/ServerAdapter.kt           — список серверов
```

---

## Изменить URL конфига

В файле `app/src/main/java/com/vpnapp/utils/ConfigRepository.kt`:

```kotlin
const val CONFIG_URL = "http://217.26.28.135/clash_final.yaml"
```

---

## Минимальные требования

- Android 7.0+ (API 24)
- ~30 MB свободного места (Clash .so библиотека)
- Интернет для загрузки конфига
