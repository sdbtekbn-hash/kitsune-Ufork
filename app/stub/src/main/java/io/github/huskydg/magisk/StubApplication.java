package io.github.huskydg.magisk;

import android.app.Application;
import android.content.Context;

public class StubApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        DynLoad.loadAndInitializeApp(this);
    }
}
