package com.zhenxi.il2cpptrace;

import android.app.Application;
import android.content.Context;

import com.zhenxi.il2cpptrace.utils.CLog;
import com.zhenxi.il2cpptrace.utils.RootUtils;
import com.zhenxi.il2cpptrace.utils.ThreadUtils;
import com.zhenxi.il2cpptrace.utils.ToastUtils;

/**
 * Created by Zhenxi on
 * 2019/10/18
 */
public class App extends Application {

    public native void AppSecure(Context context);

    static {
        try {
            System.loadLibrary("secure");
        } catch (Throwable e) {
            CLog.e("load Secure so error  " + e.getMessage());
        }
    }


    public void getRoot() {
        ThreadUtils.runOnNonUIThread(() -> {
            if (RootUtils.upgradeRootPermission(getPackageCodePath())) {
                ToastUtils.showToast(getApplicationContext(), "获取root权限成功");
                CLog.e("get root success !");
            } else {
                ToastUtils.showToast(getApplicationContext(), "获取root失败,加壳程序可能导致读取配失败");
                CLog.e("get root fail !");
            }
        });
    }

    public Context getMyApplicationContext() {
        return getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getRoot();

        AppSecure(getApplicationContext());
    }
}
