# Pico IME Unlock

Pico IME Unlock is an LSPosed/Vector module for Pico 4 (tested on 5.9.9)
(Android 10 / API 29). It prevents `com.picovr.systemext` from forcing iFlyIME
as the system keyboard.

Now you can install Google Board or Microsoft Swiftkey or other as default keyboard.

<img width="946" height="557" alt="image" src="https://github.com/user-attachments/assets/7170e0bc-cab7-4599-81e8-ff0e1a0d1f3a" />
<img width="982" height="653" alt="image" src="https://github.com/user-attachments/assets/925cad10-74c3-4724-94c0-a37e4209cc65" />

The module remembers the last non-iFly keyboard selected in Android. When Pico
requests a switch back to iFlyIME, the request is redirected to that keyboard
as long as it is still installed and enabled. If no valid alternative is
available, iFlyIME remains available as a safe fallback.

## Recommended setup: use SystemSettings

The easiest way to enable and switch the system keyboard is the included
`SystemSettings.apk`. It opens the full Android Settings application that
Pico normally hides, so you do not have to enter several ADB commands every
time you want to change the keyboard.

1. Install your preferred Android keyboard.
2. Install `SystemSettings.apk` and
   `pico-ime-unlock-v1.0.3.apk`. You can sideload both APKs with a file
   manager or use ADB only for installation:

   ```bash
   adb install -r SystemSettings.apk
   adb install -r pico-ime-unlock-v1.0.3.apk
   ```

3. Enable **Pico IME Unlock** in LSPosed or Vector. On first enable, the
   manager automatically selects the scopes recommended by the APK:

   - **System Framework** (`android`)
   - `com.picovr.systemext`

4. Reboot the headset.
5. Launch **SystemSettings** from the application library. In the standard
   Android Settings interface, open **System → Languages & input → Virtual
   keyboard → Manage keyboards** and enable the keyboard. Then open **Current
   keyboard** and select it. Menu names can differ slightly between firmware
   versions and system languages.

Use SystemSettings again whenever you want to switch keyboards. It is only a
shortcut to the built-in Android Settings application and does not need to be
added to the module scope.

If the module already has a saved non-empty scope, LSPosed/Vector preserves
that selection. Use **Apply recommended scope** once to select the two entries
listed above.

## Compatibility and upgrades

Pico IME Unlock uses the legacy Xposed API supported by LSPosed 1.9.x and
Vector 2.2.

## Build

```bash
./build.sh
```

The signed APK is written to `pico-ime-unlock-v1.0.3.apk`.

##############################################################################

Pico IME Unlock — модуль для LSPosed/Vector на Pico 4 (протестировано на прошивке 5.9.9)
(Android 10 / API 29). Он не позволяет `com.picovr.systemext` принудительно
возвращать iFlyIME в качестве системной клавиатуры.

Теперь вы можете установить Google Board или Microsoft Swiftkey или любую другую как системную клавиатуру.

Модуль запоминает последнюю выбранную в Android клавиатуру, отличную от
iFlyIME. Когда Pico пытается снова включить iFlyIME, модуль подставляет
запомненную клавиатуру, если она всё ещё установлена и включена. Если
подходящей альтернативы нет, iFlyIME остаётся доступной как безопасный
резервный вариант.

## Рекомендуемый способ: SystemSettings

Для включения и переключения системной клавиатуры лучше всего использовать
приложенный `SystemSettings.apk`. Он открывает полные системные настройки
Android, которые Pico обычно скрывает. Благодаря этому не нужно каждый раз
вводить несколько ADB-команд для смены клавиатуры.

1. Установите нужную Android-клавиатуру.
2. Установите `SystemSettings.apk` и `pico-ime-unlock-v1.0.3.apk`. Оба APK можно установить через файловый
   менеджер либо воспользоваться ADB только для установки:

   ```bash
   adb install -r SystemSettings.apk
   adb install -r pico-ime-unlock-v1.0.3.apk
   ```

3. Включите **Pico IME Unlock** в LSPosed или Vector. При первом включении модуля выберите в списке:

   - **System Framework** (`android`)
   - `com.picovr.systemext`

4. Перезагрузите шлем.
5. Запустите **SystemSettings** из библиотеки приложений. В стандартных
   настройках Android откройте **Система → Язык и ввод → Виртуальная
   клавиатура → Управление клавиатурами** и включите нужную клавиатуру. Затем
   откройте пункт **Текущая клавиатура** и выберите её. Названия пунктов могут
   немного отличаться в зависимости от прошивки и языка системы.

В дальнейшем для смены клавиатуры достаточно снова открыть SystemSettings.
Это только ярлык для встроенных настроек Android — добавлять SystemSettings в
область действия модуля не требуется.

## Совместимость и обновление

Pico IME Unlock использует legacy Xposed API, поддерживаемый LSPosed 1.9.x и
Vector 2.2.

## Сборка

```bash
./build.sh
```

Подписанный APK создаётся в `pico-ime-unlock-v1.0.3.apk`.
