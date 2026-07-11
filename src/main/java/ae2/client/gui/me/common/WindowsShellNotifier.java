package ae2.client.gui.me.common;

import ae2.core.AELog;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * In-process Windows shell notifier. Uses the JNA version bundled with Minecraft instead of an
 * additional native library or an external PowerShell process.
 */
final class WindowsShellNotifier {
    private static final String APP_ID = "AppliedEnergistics2.Supergiant.Client";
    private static final String APP_NAME = "AE2 Supergiant";
    private static final String APP_ID_REGISTRY_PATH = "Software\\Classes\\AppUserModelId\\" + APP_ID;

    private static final int NIM_ADD = 0;
    private static final int NIM_MODIFY = 1;
    private static final int NIM_DELETE = 2;
    private static final int NIM_SETVERSION = 4;
    private static final int NOTIFYICON_VERSION_4 = 4;
    private static final int NIF_ICON = 0x2;
    private static final int NIF_TIP = 0x4;
    private static final int NIF_INFO = 0x10;
    private static final int NIF_SHOWTIP = 0x80;
    private static final int NIIF_USER = 0x4;
    private static final int NIIF_LARGE_ICON = 0x20;
    private static final int ICON_RESOURCE_VERSION = 0x00030000;
    private static final int LR_DEFAULTCOLOR = 0;
    private static final int TRAY_ICON_ID = 0x0AE2;

    private static final Pointer HKEY_CURRENT_USER = Pointer.createConstant(-2147483647L);
    private static final int KEY_SET_VALUE = 0x0002;
    private static final int KEY_CREATE_SUB_KEY = 0x0004;
    private static final int REG_SZ = 1;
    private static final int REG_EXPAND_SZ = 2;
    private static final int REG_DWORD = 4;

    private static Pointer windowHandle;
    private static Pointer currentIcon;
    private static Pointer currentBalloonIcon;
    private static boolean initialized;
    private static boolean shutdownHookInstalled;

    private WindowsShellNotifier() {
    }

    static synchronized boolean show(BufferedImage applicationImage,
                                     BufferedImage notificationImage,
                                     String title,
                                     String body) {
        try {
            if (!initialized && !initialize(applicationImage)) {
                return false;
            }

            Pointer notificationIcon = createIcon(notificationImage);
            if (notificationIcon == null) {
                return false;
            }

            NotifyIconData data = createBaseData();
            data.uFlags = NIF_TIP | NIF_INFO | NIF_SHOWTIP;
            data.hBalloonIcon = notificationIcon;
            data.dwInfoFlags = NIIF_USER | NIIF_LARGE_ICON;
            copyWideString(title, data.szInfoTitle);
            copyWideString(body, data.szInfo);

            if (Shell32.INSTANCE.Shell_NotifyIconW(NIM_MODIFY, data) == 0) {
                User32.INSTANCE.DestroyIcon(notificationIcon);
                AELog.warn("Shell_NotifyIconW failed with Windows error %d", Native.getLastError());
                return false;
            }

            Pointer previousBalloonIcon = currentBalloonIcon;
            currentBalloonIcon = notificationIcon;
            if (previousBalloonIcon != null) {
                User32.INSTANCE.DestroyIcon(previousBalloonIcon);
            }
            return true;
        } catch (Throwable e) {
            AELog.warn(e, "Could not show in-process Windows crafting notification");
            return false;
        }
    }

    private static boolean initialize(BufferedImage image) {
        long nativeWindow = GLFWNativeWin32.glfwGetWin32Window(Display.getWindow());
        if (nativeWindow == 0) {
            return false;
        }
        windowHandle = new Pointer(nativeWindow);

        registerApplication(image);
        int appIdResult = Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString(APP_ID));
        if (appIdResult != 0) {
            AELog.warn("Could not set Windows AppUserModelID; HRESULT=0x%08X", appIdResult);
        }

        Pointer icon = createIcon(image);
        if (icon == null) {
            return false;
        }

        NotifyIconData data = createBaseData();
        data.uFlags = NIF_ICON | NIF_TIP | NIF_SHOWTIP;
        data.hIcon = icon;
        if (Shell32.INSTANCE.Shell_NotifyIconW(NIM_ADD, data) == 0) {
            User32.INSTANCE.DestroyIcon(icon);
            AELog.warn("Could not add Windows notification icon; error=%d", Native.getLastError());
            return false;
        }

        data.uFlags = 0;
        data.uVersion = NOTIFYICON_VERSION_4;
        Shell32.INSTANCE.Shell_NotifyIconW(NIM_SETVERSION, data);

        currentIcon = icon;
        initialized = true;
        installShutdownHook();
        return true;
    }

    private static NotifyIconData createBaseData() {
        NotifyIconData data = new NotifyIconData();
        data.cbSize = data.size();
        data.hWnd = windowHandle;
        data.uID = TRAY_ICON_ID;
        copyWideString(APP_NAME, data.szTip);
        return data;
    }

    private static Pointer createIcon(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                return null;
            }
            byte[] png = output.toByteArray();
            Memory resource = new Memory(png.length);
            resource.write(0, png, 0, png.length);
            return User32.INSTANCE.CreateIconFromResourceEx(resource, png.length, 1,
                ICON_RESOURCE_VERSION, image.getWidth(), image.getHeight(), LR_DEFAULTCOLOR);
        } catch (Exception e) {
            AELog.warn(e, "Could not create Windows icon for crafting notification");
            return null;
        }
    }

    private static void registerApplication(BufferedImage applicationImage) {
        PointerByReference keyReference = new PointerByReference();
        int result = Advapi32.INSTANCE.RegCreateKeyExW(
            HKEY_CURRENT_USER,
            new WString(APP_ID_REGISTRY_PATH),
            0,
            null,
            0,
            KEY_SET_VALUE | KEY_CREATE_SUB_KEY,
            null,
            keyReference,
            new IntByReference());
        if (result != 0) {
            AELog.warn("Could not register Windows notification sender; error=%d", result);
            return;
        }

        Pointer key = keyReference.getValue();
        try {
            setRegistryString(key, "DisplayName", APP_NAME, REG_SZ);
            String iconPath = writeApplicationIcon(applicationImage);
            if (iconPath != null) {
                setRegistryString(key, "IconUri", iconPath, REG_EXPAND_SZ);
            }
            Advapi32.INSTANCE.RegSetValueExW(key, new WString("ShowInSettings"), 0, REG_DWORD,
                new byte[]{1, 0, 0, 0}, 4);
        } finally {
            Advapi32.INSTANCE.RegCloseKey(key);
        }
    }

    private static void setRegistryString(Pointer key, String name, String value, int type) {
        byte[] data = (value + '\0').getBytes(StandardCharsets.UTF_16LE);
        Advapi32.INSTANCE.RegSetValueExW(key, new WString(name), 0, type, data, data.length);
    }

    private static String writeApplicationIcon(BufferedImage image) {
        try (ByteArrayOutputStream pngOutput = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", pngOutput)) {
                return null;
            }
            byte[] png = pngOutput.toByteArray();
            ByteBuffer icon = ByteBuffer.allocate(22 + png.length).order(ByteOrder.LITTLE_ENDIAN);
            icon.putShort((short) 0);
            icon.putShort((short) 1);
            icon.putShort((short) 1);
            icon.put((byte) (image.getWidth() >= 256 ? 0 : image.getWidth()));
            icon.put((byte) (image.getHeight() >= 256 ? 0 : image.getHeight()));
            icon.put((byte) 0);
            icon.put((byte) 0);
            icon.putShort((short) 1);
            icon.putShort((short) 32);
            icon.putInt(png.length);
            icon.putInt(22);
            icon.put(png);

            Path directory = Minecraft.getMinecraft().gameDir.toPath().toAbsolutePath().normalize().resolve("ae2");
            Files.createDirectories(directory);
            Path iconFile = directory.resolve("windows-notification-icon.ico");
            Files.write(iconFile, icon.array());
            return iconFile.toString();
        } catch (Exception e) {
            AELog.warn(e, "Could not write Windows notification application icon");
            return null;
        }
    }

    private static void copyWideString(String value, short[] target) {
        Arrays.fill(target, (short) 0);
        if (value == null) {
            return;
        }
        int length = Math.min(value.length(), target.length - 1);
        for (int i = 0; i < length; i++) {
            target[i] = (short) value.charAt(i);
        }
    }

    private static synchronized void installShutdownHook() {
        if (shutdownHookInstalled) {
            return;
        }
        shutdownHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(WindowsShellNotifier::shutdown,
            "AE2 Windows notification cleanup"));
    }

    private static synchronized void shutdown() {
        if (initialized) {
            NotifyIconData data = createBaseData();
            Shell32.INSTANCE.Shell_NotifyIconW(NIM_DELETE, data);
            initialized = false;
        }
        if (currentIcon != null) {
            User32.INSTANCE.DestroyIcon(currentIcon);
            currentIcon = null;
        }
        if (currentBalloonIcon != null) {
            User32.INSTANCE.DestroyIcon(currentBalloonIcon);
            currentBalloonIcon = null;
        }
        windowHandle = null;
    }

    public static final class NotifyIconData extends Structure {
        public int cbSize;
        public Pointer hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public Pointer hIcon;
        public short[] szTip = new short[128];
        public int dwState;
        public int dwStateMask;
        public short[] szInfo = new short[256];
        public int uVersion;
        public short[] szInfoTitle = new short[64];
        public int dwInfoFlags;
        public byte[] guidItem = new byte[16];
        public Pointer hBalloonIcon;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                "cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip",
                "dwState", "dwStateMask", "szInfo", "uVersion", "szInfoTitle", "dwInfoFlags",
                "guidItem", "hBalloonIcon");
        }
    }

    private interface Shell32 extends StdCallLibrary {
        Shell32 INSTANCE = Native.loadLibrary("shell32", Shell32.class);

        int Shell_NotifyIconW(int message, NotifyIconData data);

        int SetCurrentProcessExplicitAppUserModelID(WString appId);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.loadLibrary("user32", User32.class);

        Pointer CreateIconFromResourceEx(Pointer resourceBits, int resourceSize, int icon,
                                         int version, int desiredWidth, int desiredHeight, int flags);

        int DestroyIcon(Pointer icon);
    }

    private interface Advapi32 extends StdCallLibrary {
        Advapi32 INSTANCE = Native.loadLibrary("advapi32", Advapi32.class);

        int RegCreateKeyExW(Pointer key, WString subKey, int reserved, WString keyClass,
                            int options, int desiredAccess, Pointer securityAttributes,
                            PointerByReference result, IntByReference disposition);

        int RegSetValueExW(Pointer key, WString valueName, int reserved, int type,
                           byte[] data, int dataSize);

        int RegCloseKey(Pointer key);
    }
}
