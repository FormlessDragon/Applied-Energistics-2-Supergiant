package ae2.api.util;

import ae2.api.config.Setting;

/**
 * Thrown if {@link ae2.util.ConfigManager} is used with a {@link Setting} that was not previously
 * {@link ae2.util.ConfigManager#registerSetting(Setting, Enum) registered}.
 */
public class UnsupportedSettingException extends RuntimeException {
    public UnsupportedSettingException(String message) {
        super(message);
    }
}
