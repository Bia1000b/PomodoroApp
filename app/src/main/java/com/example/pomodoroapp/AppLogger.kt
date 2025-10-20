package com.example.pomodoroapp

import android.util.Log;

object AppLogger {

    private var IS_DEBUG = false;

    private const val DEFAULT_TAG = "App";

    fun init(isDebug: Boolean) {
        IS_DEBUG = isDebug;
    }

    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (IS_DEBUG) {
            Log.v(tag, message);
        }
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (IS_DEBUG) {
            Log.d(tag, message);
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message);
    }

    fun w(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.w(tag, message, error);
        } else {
            Log.w(tag, message);
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(tag, message, error);
        } else {
            Log.e(tag, message);
        }
    }
}