package com.example.zzpcamerax1;

import android.util.Log;
public class LogZZP {
    /*const values objects*/
    private static final String TAG = "ZZP_";

    public static void d(String tag,String message) {
        Log.d(TAG+tag, message);
    }

    public static void e(String tag,String message) {
        Log.e(TAG+tag, message);
    }

    public static void i(String tag,String message) {
        Log.i(TAG+tag, message);
    }

    public static void v(String tag,String message) {
        Log.v(TAG+tag, message);
    }

    public static void w(String tag,String message) {
        Log.w(TAG+tag, message);
    }

    public static void d(String tag,String message,Throwable tr) {
        Log.d(TAG+tag, message,tr);
    }

    public static void e(String tag,String message,Throwable tr) {
        Log.e(TAG+tag, message,tr);
    }

    public static void i(String tag,String message,Throwable tr) {
        Log.i(TAG+tag, message,tr);
    }

    public static void v(String tag,String message,Throwable tr) {
        Log.v(TAG+tag, message,tr);
    }

    public static void w(String tag,String message,Throwable tr) {
        Log.w(TAG+tag, message,tr);
    }
}
