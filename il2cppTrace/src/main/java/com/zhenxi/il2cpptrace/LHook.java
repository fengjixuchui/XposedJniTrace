package com.zhenxi.il2cpptrace;

import static com.zhenxi.il2cpptrace.config.ConfigKey.CONFIG_JSON;
import static com.zhenxi.il2cpptrace.config.ConfigKey.DEF_VALUE;
import static com.zhenxi.il2cpptrace.config.ConfigKey.FILTER_LIST;
import static com.zhenxi.il2cpptrace.config.ConfigKey.IS_LISTEN_TO_ALL;
import static com.zhenxi.il2cpptrace.config.ConfigKey.IS_Il2CPPTRACE;
import static com.zhenxi.il2cpptrace.config.ConfigKey.IS_SYSTEM_LOAD_INTO;
import static com.zhenxi.il2cpptrace.config.ConfigKey.IS_USE_SYSTEM_PATH;
import static com.zhenxi.il2cpptrace.config.ConfigKey.LIST_OF_FUNCTIONS;

import static com.zhenxi.il2cpptrace.config.ConfigKey.MODULE_SO_PATH;
import static com.zhenxi.il2cpptrace.config.ConfigKey.PACKAGE_NAME;
import static com.zhenxi.il2cpptrace.config.ConfigKey.SAVE_TIME;
import static com.zhenxi.il2cpptrace.config.ConfigKey.SYSTEM_INTO_PATH;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.zhenxi.il2cpptrace.utils.CLog;
import com.zhenxi.il2cpptrace.utils.ChooseUtils;
import com.zhenxi.il2cpptrace.utils.ContextUtils;
import com.zhenxi.il2cpptrace.utils.FileUtils;
import com.zhenxi.il2cpptrace.utils.GsonUtils;
import com.zhenxi.il2cpptrace.utils.IntoMySoUtils;
import com.zhenxi.il2cpptrace.utils.ThreadUtils;

import org.json.JSONObject;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class LHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static void passApiCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        HiddenApiBypass.addHiddenApiExemptions("");
    }


    /**
     * 目标的包名
     */
    private static String mTagPackageName = null;

    /**
     * 进程名字
     */
    private static String mProcessName = null;

    /**
     * 保存当前进程的classloader,在apk没加载的时候,手动去调用
     * context.getClassLoader()程序会进入阻塞状态,原因未知。
     */
    private static ClassLoader mProcessClazzLoader = null;

    /**
     * 注入模块的apk路径
     */
    private static String mModuleBaseApkPath = null;

    private static long mSaveTime = 0;
    /**
     * 是否开启内存序列化
     */
    private static boolean isIl2cppTrace = false;

    private static boolean isSystemLoadInto = false;
    /**
     * 是否监听全部的SO调用
     */
    private static boolean isListenAll = false;

    private static final ArrayList<String> mFilterList = new ArrayList<>();

    private static final ArrayList<String> mFunctionList = new ArrayList<>();


    private static boolean isInit = false;

    private static String systemIntoPath = null;

    private void initConfigData(String configJson) {
        if (configJson == null || configJson.length() == 0 || configJson.equals(DEF_VALUE)) {
            return;
        }
        try {
            JSONObject json = new JSONObject(configJson);
            mTagPackageName = json.optString(PACKAGE_NAME, DEF_VALUE);
            mModuleBaseApkPath = json.optString(MODULE_SO_PATH, DEF_VALUE);
            mSaveTime = json.optLong(SAVE_TIME, 0L);
            isIl2cppTrace = json.optBoolean(IS_Il2CPPTRACE, false);
            isSystemLoadInto = json.optBoolean(IS_SYSTEM_LOAD_INTO, false);
            String functionList = json.optString(LIST_OF_FUNCTIONS, DEF_VALUE);
            //CLog.e("json get function list str info -> " + functionList);
            if (!functionList.equals(DEF_VALUE)) {
                ArrayList<?> arrayList = GsonUtils.str2obj(functionList, ArrayList.class);
                if (arrayList != null) {
                    CLog.e("function list get info -> " + arrayList);
                    for (Object obj : arrayList) {
                        String item = String.valueOf(obj);
                        //CLog.e("function list add  " + obj);
                        mFunctionList.add(item);
                    }
                } else {
                    CLog.e("function list  == null !!!!!!!");
                }
            }

            isListenAll = json.optBoolean(IS_LISTEN_TO_ALL, false);
            if (!isListenAll) {
                String filterList = json.optString(FILTER_LIST, DEF_VALUE);
                if (!filterList.equals(DEF_VALUE)) {
                    ArrayList<?> arrayList = GsonUtils.str2obj(filterList, ArrayList.class);
                    if (arrayList != null) {
                        for (Object obj : arrayList) {
                            mFilterList.add((String) obj);
                        }
                        CLog.e("filter so list  " + mFilterList);
                    } else {
                        CLog.e("filter so list  == null !!!!!!!");
                    }
                }
            }
            boolean isSystemPath = json.optBoolean(IS_USE_SYSTEM_PATH, false);
            if (isSystemPath) {
                systemIntoPath = json.optString(SYSTEM_INTO_PATH, DEF_VALUE);
                CLog.i("into system path -> " + systemIntoPath);
            }
        } catch (Throwable e) {
            CLog.e("initConfigData error " + e, e);
        }
    }

    @Override
    @SuppressWarnings("all")
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        mProcessName = loadPackageParam.processName;
        mProcessClazzLoader = loadPackageParam.classLoader;
        try {
            String configJson = DEF_VALUE;
            try {
                //尝试通过 XSharedPreferences 读取
                XSharedPreferences shared = new XSharedPreferences(BuildConfig.APPLICATION_ID, "config");
                shared.reload();
                configJson = shared.getString(CONFIG_JSON, DEF_VALUE);
                CLog.i(">>>>>>>>> XSharedPreferences find config package name " + configJson);
                initConfigData(configJson);
            } catch (Throwable e) {
                CLog.e("handleLoadPackage XSharedPreferences getString error " + e);
                configJson = DEF_VALUE;
            }
            //二次尝试读取root移动过来的配置文件
            if (configJson.equals(DEF_VALUE)) {
                String configInfo = null;
                try {
                    CLog.i("find config package name == null ,start read config  ");
                    File file = new File("/data/data/" +
                            loadPackageParam.packageName + "/" + BuildConfig.project_name + "Config");
                    if (!file.exists()) {
                        CLog.e("not find root config file " + file.getPath());
                        return;
                    }

                    configInfo = FileUtils.readToString(file);
                    CLog.i("start read config success  " + configInfo);
                } catch (Throwable e) {
                    CLog.e("read root file config error " + e, e);
                }
                initConfigData(configInfo);
            }

            CLog.i("load app -> " +
                    loadPackageParam.packageName + " process name ->[" + mProcessName + "]" +
                    "  tag package name -> " + mTagPackageName);

            if (isMatch(loadPackageParam.packageName)) {
                CLog.e("find tag app ->  " + loadPackageParam.packageName);
                CLog.i("[" + mTagPackageName + "]init config success ! isSerialization -> "
                        + isIl2cppTrace + "  into so path -> " + mModuleBaseApkPath + " is hook all -> " + isListenAll);
//                CLog.e("LHook classloader "+LHook.class.getClassLoader()+" "+LHook.class.getClassLoader().hashCode());
//                CLog.e("LHook loadPackageParam.classLoader "
//                        +loadPackageParam.classLoader+" "+loadPackageParam.classLoader.hashCode());

                startInit();

            }
        } catch (Throwable e) {
            e.printStackTrace();
            CLog.e("handleLoadPackage  Exception  " + e.getMessage());
        }
    }


    private boolean isMatch(String packageName) {
        //包名匹配&&10分钟的有效期
        return packageName.equals(mTagPackageName);
        //&& (System.currentTimeMillis() - mSaveTime) < (1000 * 60 * 10);
    }

    private void intoMySo(Context context, boolean isSystemLoadInto) {
        try {
            if (context == null) {
                CLog.e("intoMySo context == null ");
                return ;
            }
            ClassLoader classloader = null;
            try {
                classloader = isSystemLoadInto ? mProcessClazzLoader : Objects.requireNonNull(LHook.class.getClassLoader());
                if (classloader == null) {
                    return ;
                }
            } catch (Exception e) {
                CLog.e("intoMySo get classloader error  " + e, e);
            }
            if (classloader == null) {
                CLog.e("classloader == null error  ");
                return ;
            }
            IntoMySoUtils.initMySoForName(context,
                    "lib" + BuildConfig.project_name + ".so",
                    classloader,
                    mModuleBaseApkPath,
                    systemIntoPath,
                    isIl2cppTrace
            );
        } catch (Throwable e) {
            CLog.e("initSo error " + e, e);
        }
    }

    @SuppressWarnings("all")
    private void startInit() {
        passApiCheck();
        Context context = ContextUtils.getContext();
        if (context == null) {
            try {
                XposedBridge.hookAllMethods(
                        Class.forName("android.app.ContextImpl"),
                        "createAppContext",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                CLog.e("hook createAppContext success !");
                                Context ret = (Context) param.getResult();
                                initIL2CPP(ret);
                            }
                        });
            } catch (Throwable e) {
                CLog.e("hook createAppContext error  " + e.getMessage());
            }
        } else {
            initIL2CPP(context);
        }
    }


    private static final int NOTIFICATION_ID = 8888;
    private static final String CHANNEL_ID = "MEMORY_SERIALIZATION";
    private NotificationCompat.Builder builder = null;

    @SuppressWarnings("unused")
    private void printfProgress(int max, int index, Context context) {
        try {
            ThreadUtils.runOnMainThread(() -> {
                if (builder == null) {
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    builder =
                            new NotificationCompat.Builder(context, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .setContentTitle("MemorySerialization")
                                    .setProgress(max, index, false);
                    //显示Notification
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                }
                builder.setProgress(max, index, false);
                builder.setContentText("Downloaded " + index + "%");
            });
        } catch (Throwable e) {
            CLog.e("printfProgress error " + e);
        }
    }

    @SuppressWarnings("All")
    private void initIL2CPP(Context context) {
        if (isInit) {
            return;
        }
        if (context == null) {
            return;
        }
        CLog.i(">>>>>>>> start init funJni , " +
                "get context sucess [" + context.getPackageName() + "]");

        CLog.e(">>>>>>>>>>>>>>> start into my so ,is system.load [" + isSystemLoadInto + "] <<<<<<<<<<<<<<<<<");
        try {
            intoMySo(context, isSystemLoadInto);
            CLog.e(">>>>>>>>>>>>>>>  into my finish  <<<<<<<<<<<<<<<<<");
        } catch (Throwable e) {
            CLog.e("into&hook jni error " + e, e);
        }

        isInit = true;
    }




    @Override
    public void initZygote(StartupParam startupParam) {

    }


}
