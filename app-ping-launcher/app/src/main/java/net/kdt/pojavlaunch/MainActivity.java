package net.kdt.pojavlaunch;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import kr.co.donghyun.pinglauncher.presentation.MinecraftActivity;

public class MainActivity {
    private static final String TAG = "PojavStubMainActivity";

    public static void openLink(String url) {
        Log.d(TAG, "openLink: " + url);
        try {
            Context ctx = MinecraftActivity.getCurrentInstance();
            if (ctx == null) return;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "openLink failed", t);
        }
    }

    public static void querySystemClipboard() {
        Log.d(TAG, "querySystemClipboard");
        try {
            Context ctx = MinecraftActivity.getCurrentInstance();
            if (ctx == null) return;
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            // 클립보드를 읽어서 Cacio 쪽으로 돌려준다.
            // 실제 콜백은 libpojavexec_awt.so 가 export하는 nativeClipboardReceived 로 들어감
        } catch (Throwable t) {
            Log.w(TAG, "querySystemClipboard failed", t);
        }
    }

    public static void putClipboardData(String data, String mime) {
        Log.d(TAG, "putClipboardData: " + (data != null ? data.length() : 0) + " bytes");
        try {
            Context ctx = MinecraftActivity.getCurrentInstance();
            if (ctx == null) return;
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("Minecraft", data == null ? "" : data));
        } catch (Throwable t) {
            Log.w(TAG, "putClipboardData failed", t);
        }
    }
}