package dev.pico.imeguard;

import android.content.ContentResolver;
import android.provider.Settings;

import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class PicoImeUnlock implements IXposedHookLoadPackage {
    private static final String TAG = "PicoImeUnlock: ";
    private static final String SYSTEM_SERVER = "android";
    private static final String SYSTEM_EXT = "com.picovr.systemext";
    private static final String DEFAULT_IME = "default_input_method";
    private static final String ENABLED_IMES = "enabled_input_methods";
    private static final String IFLY_ID =
            "com.iflytek.inputmethod.pico/com.iflytek.inputmethod.FlyIME";
    private static final String TARGET_PROPERTY = "persist.pico.ime_unlock.target";
    private static final String ENABLED_PROPERTY = "persist.pico.ime_unlock.enabled";
    private static final String LEGACY_TARGET_PROPERTY = "persist.pico.ime_guard.target";
    private static final String LEGACY_ENABLED_PROPERTY = "persist.pico.ime_guard.enabled";

    private static volatile String lastGoodIme;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (SYSTEM_SERVER.equals(lpparam.packageName)
                && SYSTEM_SERVER.equals(lpparam.processName)) {
            hookSystemServer(lpparam.classLoader);
            return;
        }

        if (SYSTEM_EXT.equals(lpparam.packageName)) {
            hookSystemExtSettingsWrites();
        }
    }

    private static void hookSystemServer(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.inputmethod.InputMethodManagerService",
                    classLoader,
                    "setInputMethodLocked",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isUnlockEnabled()) {
                                return;
                            }

                            String requested = asString(param.args[0]);
                            if (isAlternative(requested)) {
                                remember(requested);
                                return;
                            }
                            if (!IFLY_ID.equals(requested)) {
                                return;
                            }

                            String replacement = findSystemServerReplacement(param.thisObject);
                            if (replacement == null || !isInstalled(param.thisObject, replacement)) {
                                log("iFly request allowed: no installed alternative IME is known");
                                return;
                            }

                            param.args[0] = replacement;
                            remember(replacement);
                            restoreSelectedSetting(param.thisObject, replacement);
                            log("blocked setInputMethodLocked(iFly), using " + replacement);
                        }
                    });
            log("hooked InputMethodManagerService.setInputMethodLocked");
        } catch (Throwable error) {
            log("failed to hook setInputMethodLocked", error);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.inputmethod.InputMethodUtils$InputMethodSettings",
                    classLoader,
                    "putSelectedInputMethod",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isUnlockEnabled()) {
                                return;
                            }

                            String requested = asString(param.args[0]);
                            if (isAlternative(requested)) {
                                remember(requested);
                                return;
                            }
                            if (!IFLY_ID.equals(requested)) {
                                return;
                            }

                            String replacement = enabledTargetForSettings(param.thisObject);

                            if (isAlternative(replacement)) {
                                param.args[0] = replacement;
                                remember(replacement);
                                log("blocked default_input_method=iFly, keeping " + replacement);
                            }
                        }
                    });
            log("hooked InputMethodSettings.putSelectedInputMethod");
        } catch (Throwable error) {
            log("failed to hook putSelectedInputMethod", error);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.inputmethod.InputMethodUtils$InputMethodSettings",
                    classLoader,
                    "putEnabledInputMethodsStr",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isUnlockEnabled()) {
                                return;
                            }

                            String enabled = asString(param.args[0]);
                            String target = enabledTargetForSettings(param.thisObject);
                            if (isAlternative(target) && !containsIme(enabled, target)) {
                                param.args[0] = appendIme(enabled, target);
                                log("kept alternative IME enabled in IMMS: " + target);
                            }
                        }
                    });
            log("hooked InputMethodSettings.putEnabledInputMethodsStr");
        } catch (Throwable error) {
            log("failed to hook putEnabledInputMethodsStr", error);
        }
    }

    private static void hookSystemExtSettingsWrites() {
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.Secure.class,
                    "putStringForUser",
                    ContentResolver.class,
                    String.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            protectSettingsWrite(param, 1, 2, 3);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    Settings.Secure.class,
                    "putStringForUser",
                    ContentResolver.class,
                    String.class,
                    String.class,
                    String.class,
                    boolean.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            protectSettingsWrite(param, 1, 2, 5);
                        }
                    });
            log("hooked SystemExt Settings.Secure.putStringForUser overloads");
        } catch (Throwable error) {
            log("failed to hook SystemExt secure settings writes", error);
        }
    }

    private static void protectSettingsWrite(
            XC_MethodHook.MethodHookParam param, int keyIndex, int valueIndex, int userIndex) {
        if (!isUnlockEnabled()) {
            return;
        }

        String key = asString(param.args[keyIndex]);
        String value = asString(param.args[valueIndex]);
        ContentResolver resolver = (ContentResolver) param.args[0];
        int userId = (Integer) param.args[userIndex];

        if (DEFAULT_IME.equals(key)) {
            if (isAlternative(value)) {
                remember(value);
                return;
            }
            if (!IFLY_ID.equals(value)) {
                return;
            }

            String replacement = enabledTargetForResolver(resolver, userId);
            if (isAlternative(replacement)) {
                param.args[valueIndex] = replacement;
                remember(replacement);
                log("blocked SystemExt default_input_method=iFly, using " + replacement);
            }
            return;
        }

        if (ENABLED_IMES.equals(key)) {
            String target = configuredTarget();
            if (!isAlternative(target)) {
                target = lastGoodIme;
            }
            if (isAlternative(target) && !containsIme(value, target)) {
                param.args[valueIndex] = appendIme(value, target);
                log("kept alternative IME enabled: " + target);
            }
        }
    }

    private static String enabledTargetForSettings(Object settings) {
        String enabled = asString(XposedHelpers.callMethod(settings, "getEnabledInputMethodsStr"));
        String configured = configuredTarget();
        if (isAlternative(configured) && containsIme(enabled, configured)) {
            return configured;
        }
        if (isAlternative(lastGoodIme) && containsIme(enabled, lastGoodIme)) {
            return lastGoodIme;
        }
        String selected = asString(XposedHelpers.callMethod(settings, "getSelectedInputMethod"));
        return isAlternative(selected) ? selected : null;
    }

    private static String enabledTargetForResolver(ContentResolver resolver, int userId) {
        String enabled = secureStringForUser(resolver, ENABLED_IMES, userId);
        String configured = configuredTarget();
        if (isAlternative(configured) && containsIme(enabled, configured)) {
            return configured;
        }
        if (isAlternative(lastGoodIme) && containsIme(enabled, lastGoodIme)) {
            return lastGoodIme;
        }
        String selected = secureStringForUser(resolver, DEFAULT_IME, userId);
        return isAlternative(selected) && containsIme(enabled, selected) ? selected : null;
    }

    private static String secureStringForUser(
            ContentResolver resolver, String key, int userId) {
        return asString(XposedHelpers.callStaticMethod(
                Settings.Secure.class, "getStringForUser", resolver, key, userId));
    }

    private static String findSystemServerReplacement(Object service) {
        String configured = configuredTarget();
        if (isAlternative(configured)) {
            return configured;
        }

        String current = asString(XposedHelpers.getObjectField(service, "mCurMethodId"));
        if (isAlternative(current)) {
            return current;
        }

        if (isAlternative(lastGoodIme)) {
            return lastGoodIme;
        }

        try {
            Object settings = XposedHelpers.getObjectField(service, "mSettings");
            String selected = asString(XposedHelpers.callMethod(settings, "getSelectedInputMethod"));
            return isAlternative(selected) ? selected : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isInstalled(Object service, String imeId) {
        try {
            Object methodMap = XposedHelpers.getObjectField(service, "mMethodMap");
            return methodMap instanceof Map && ((Map<?, ?>) methodMap).containsKey(imeId);
        } catch (Throwable error) {
            log("could not validate IME " + imeId, error);
            return false;
        }
    }

    private static void restoreSelectedSetting(Object service, String imeId) {
        try {
            Object settings = XposedHelpers.getObjectField(service, "mSettings");
            XposedHelpers.callMethod(settings, "putSelectedInputMethod", imeId);
        } catch (Throwable error) {
            log("could not restore default_input_method", error);
        }
    }

    private static boolean containsIme(String enabled, String imeId) {
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String[] entries = enabled.split(":");
        for (String entry : entries) {
            int subtypeSeparator = entry.indexOf(';');
            String id = subtypeSeparator >= 0 ? entry.substring(0, subtypeSeparator) : entry;
            if (imeId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String appendIme(String enabled, String imeId) {
        return enabled == null || enabled.isEmpty() ? imeId : enabled + ":" + imeId;
    }

    private static boolean isAlternative(String imeId) {
        return imeId != null && !imeId.isEmpty() && !IFLY_ID.equals(imeId);
    }

    private static void remember(String imeId) {
        if (isAlternative(imeId)) {
            lastGoodIme = imeId;
        }
    }

    private static boolean isUnlockEnabled() {
        return !"0".equals(systemProperty(
                ENABLED_PROPERTY, systemProperty(LEGACY_ENABLED_PROPERTY, "1")));
    }

    private static String configuredTarget() {
        String target = systemProperty(
                TARGET_PROPERTY, systemProperty(LEGACY_TARGET_PROPERTY, "")).trim();
        return isAlternative(target) ? target : null;
    }

    private static String systemProperty(String key, String fallback) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Object value = XposedHelpers.callStaticMethod(properties, "get", key, fallback);
            return value instanceof String ? (String) value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log(TAG + message + ": " + error);
    }
}
