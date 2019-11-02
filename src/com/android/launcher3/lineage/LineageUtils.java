package com.android.launcher3.lineage;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

public class LineageUtils {

    /**
     * Shows authentication screen to confirm credentials (pin, pattern or password) for the current
     * user of the device.
     *
     * @param context The {@code Context} used to get {@code KeyguardManager} service
     * @param title the {@code String} which will be shown as the pompt title
     * @param successRunnable The {@code Runnable} which will be executed if the user does not setup
     *                        device security or if lock screen is unlocked
     */
    public static void showLockScreen(Context context, String title, Runnable successRunnable) {
        final KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);

        if (keyguardManager.isKeyguardSecure()) {
            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                    BiometricPrompt.AuthenticationResult result) {
                            successRunnable.run();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            //Do nothing
                        }
            };

            final BiometricPrompt.Builder builder = new BiometricPrompt.Builder(context)
                    .setTitle(title);

            if (keyguardManager.isDeviceSecure()) {
                builder.setDeviceCredentialAllowed(true);
            }

            final BiometricPrompt bp = builder.build();
            final Handler handler = new Handler(Looper.getMainLooper());
            bp.authenticate(new CancellationSignal(),
                    runnable -> handler.post(runnable),
                    authenticationCallback);
        } else {
            successRunnable.run();
        }
    }

    public static boolean hasPackageInstalled(Context context, String pkgName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkgName, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
