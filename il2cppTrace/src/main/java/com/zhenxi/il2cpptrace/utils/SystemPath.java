package com.zhenxi.il2cpptrace.utils;

import android.os.Build;

/**
 * @author Zhenxi on 2023/4/7
 */
public class SystemPath {

    public static final int ANDROID_K = 19;
    public static final int ANDROID_L = 21;
    public static final int ANDROID_L2 = 22;
    public static final int ANDROID_M = 23;
    public static final int ANDROID_N = 24;
    public static final int ANDROID_N2 = 25;
    //Android 8.0
    public static final int ANDROID_O = 26;
    //Android 8.1
    public static final int ANDROID_O2 = 27;
    //Android 9.0
    public static final int ANDROID_P = 28;
    //Android 10.0
    public static final int ANDROID_Q = 29;
    //Android 11.0
    public static final int ANDROID_R = 30;
    //Android 12.0
    public static final int ANDROID_S = 31;


    public static String getSystemPath(boolean is64) {
        String path;
        if (is64) {
            if (Build.VERSION.SDK_INT >= ANDROID_R) {
                path = "/apex/com.android.art/lib64/";
            } else if (Build.VERSION.SDK_INT >= ANDROID_Q) {
                path = "/apex/com.android.runtime/lib64/";
            } else {
                path = "/system/lib64/";
            }
        } else {
            if (Build.VERSION.SDK_INT >= ANDROID_R) {
                path = "/apex/com.android.art/lib/";
            } else if (Build.VERSION.SDK_INT >= ANDROID_Q) {
                path = "/apex/com.android.runtime/lib/";
            } else {
                path = "/system/lib/";
            }
        }
        return path;
    }

}