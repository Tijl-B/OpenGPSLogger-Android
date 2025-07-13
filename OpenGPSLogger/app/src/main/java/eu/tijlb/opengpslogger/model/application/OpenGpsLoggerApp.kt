package eu.tijlb.opengpslogger.model.application

import android.app.Application
import android.util.Log

private const val TAG = "ogl-app"

class OpenGpsLoggerApp : Application() {
    override fun onCreate() {
        Log.d(TAG, "Application.onCreate()")
        super.onCreate()
    }

    override fun onTerminate() {
        Log.d(TAG, "Application.onTerminate()")
        super.onTerminate()
    }
}
