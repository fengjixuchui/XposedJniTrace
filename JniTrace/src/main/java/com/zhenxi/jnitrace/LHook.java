package com.zhenxi.jnitrace;

import static com.zhenxi.jnitrace.config.ConfigKey.CONFIG_JSON;
import static com.zhenxi.jnitrace.config.ConfigKey.DEF_VALUE;
import static com.zhenxi.jnitrace.config.ConfigKey.FILTER_LIST;
import static com.zhenxi.jnitrace.config.ConfigKey.IS_LISTEN_TO_ALL;
import static com.zhenxi.jnitrace.config.ConfigKey.IS_SERIALIZATION;
import static com.zhenxi.jnitrace.config.ConfigKey.IS_SYSTEM_LOAD_INTO;
import static com.zhenxi.jnitrace.config.ConfigKey.IS_USE_SYSTEM_PATH;
import static com.zhenxi.jnitrace.config.ConfigKey.LIST_OF_FUNCTIONS;

import static com.zhenxi.jnitrace.config.ConfigKey.MODULE_SO_PATH;
import static com.zhenxi.jnitrace.config.ConfigKey.PACKAGE_NAME;
import static com.zhenxi.jnitrace.config.ConfigKey.SAVE_TIME;
import static com.zhenxi.jnitrace.config.ConfigKey.SYSTEM_INTO_PATH;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.zhenxi.jnitrace.utils.CLog;
import com.zhenxi.jnitrace.utils.ChooseUtils;
import com.zhenxi.jnitrace.utils.ContextUtils;
import com.zhenxi.jnitrace.utils.FileUtils;
import com.zhenxi.jnitrace.utils.GsonUtils;
import com.zhenxi.jnitrace.utils.IntoMySoUtils;
import com.zhenxi.jnitrace.utils.ThreadUtils;

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
    private static boolean isSerialization = false;

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
            isSerialization = json.optBoolean(IS_SERIALIZATION, false);
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
            if(isSystemPath){
                systemIntoPath = json.optString(SYSTEM_INTO_PATH, DEF_VALUE);
                CLog.i("into system path -> "+systemIntoPath);
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
                        + isSerialization + "  into so path -> " + mModuleBaseApkPath + " is hook all -> " + isListenAll);
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

    private Class<?> intoMySo(Context context, boolean isSystemLoadInto) {
        try {

            if(context == null){
                CLog.e("intoMySo context == null ");
                return null;
            }
            ClassLoader classloader = null;
            try {
                classloader = isSystemLoadInto ? mProcessClazzLoader : Objects.requireNonNull(LHook.class.getClassLoader());
                if(classloader == null){
                    CLog.i("intoMySo classloader == null "+isSystemLoadInto);
                    return null;
                }
            } catch (Exception e) {
                CLog.e("intoMySo get classloader error  "+e,e);
            }
            if(classloader == null){
                CLog.e("classloader == null error  ");
                return null;
            }
            IntoMySoUtils.initMySoForName(context,
                    "lib" + BuildConfig.project_name + ".so",
                    classloader,
                    mModuleBaseApkPath,
                    systemIntoPath
            );
            //返回class提供给调用者使用 。
            Class<?> clazz;
            try {
                clazz = XposedHelpers.findClass(
                        "com.zhenxi.jnitrace.NativeEngine",
                        isSystemLoadInto ? mProcessClazzLoader : LHook.class.getClassLoader()
                        );
                CLog.i("init my so finish , get class success !");
                return clazz;
            } catch (Throwable e) {
                CLog.e("callStartHookJni "+e,e);
            }
        } catch (Throwable e) {
            CLog.e("initSo error " + e,e);
            e.printStackTrace();
            return null;
        }
        return null;
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
                                initFunJni(ret);
                            }
                        });
            } catch (Throwable e) {
                CLog.e("hook createAppContext error  " + e.getMessage());
            }
        } else {
            initFunJni(context);
        }
    }

    @SuppressWarnings("all")
    private void startSerialization(Context context) {
        try {
            //手动触发gc,清空多余实例
            System.gc();
            final File file = new File("/data/data/"
                    + mTagPackageName + "/" + mProcessName + "_MemorySerializationInfo.txt");
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            //子线程和主线程共享数据
            ThreadUtils.runOnNonUIThread(() -> {
                ArrayList<Object> choose = ChooseUtils.choose(Object.class, true);
                int size = choose.size();
                CLog.e("memory object size -> " + size);
                for (int index = 0; index < size; index++) {
                    Object obj = choose.get(index);
                    String objStr = GsonUtils.obj2str(obj);
                    if (objStr != null) {
                        String objClassName = obj.getClass().getName();
                        String infoStr = index + "/" + size + "[" + mProcessName + "]" + objClassName + " " + objStr + "\n";
                        //增加效率暂不打印进度
                        //printfProgress(size,index,context);
                        //ToastUtils.showToast(context,"MemorySerialization["+index+"/"+size+"]");
                        CLog.i(infoStr);
                        FileUtils.saveStringNoClose(infoStr, file);
                    }
                }
                FileUtils.saveStringClose();
            }, 30 * 1000);
        } catch (Throwable e) {
            CLog.e("startSerialization error " + e);
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
    private void initFunJni(Context context) {
        if (isInit) {
            return;
        }
        if (context == null) {
            return;
        }
        CLog.i(">>>>>>>> start init funJni , " +
                "get context sucess [" + context.getPackageName() + "]");
        if (isSerialization) {
            //处理内存序列化
            CLog.e(">>>>>>>>>>>>>>>>> start mem serialization !!!!!");
            startSerialization(context);
        } else {
            CLog.e(">>>>>>>>>>>>>>> start into my so ,is system.load ["+isSystemLoadInto+"] <<<<<<<<<<<<<<<<<");
            try {
                //处理JNI监听
                Class<?> nativeEngineClazz = intoMySo(context, isSystemLoadInto);
                if (nativeEngineClazz == null) {
                    CLog.e("intoMySo get clazz == null !");
                    return;
                }
                CLog.e(">>>>>>>>>>>>>>>  into my finish  <<<<<<<<<<<<<<<<<");

                File file = new File("/data/data/"
                        + mTagPackageName + "/(" + mProcessName + ")"
                        + (isListenAll ? "[ALL]" : mFilterList.toString()) + ".txt");
                if (file.exists()) {
                    file.delete();
                }
                file.createNewFile();
                CLog.i(">>>>>>>>>>> start hook jni " + file.getPath());
                if (mFunctionList != null && mFunctionList.size() == 0) {
                    CLog.e("function list size == 0 !!!!!!!!!!!!!!!!! " + mProcessName);
                    CLog.e("function list size == 0 !!!!!!!!!!!!!!!!! " + mTagPackageName);
                    CLog.e("function list size == 0 !!!!!!!!!!!!!!!!! ");
                    return;
                }
                //start hook native
                callStartHookJni(nativeEngineClazz, isListenAll, file.getPath());
            }
            catch (Throwable e) {
                CLog.e("into&hook jni error " + e,e);
            }
        }
        isInit = true;
    }

    private void callStartHookJni(Class<?> NativeEngineClazz, boolean isListenAll,
                                  String path) {
        if (NativeEngineClazz == null) {
            return;
        }
        //startHookJni(isListenAll, mFilterList, mFunctionList, file.getPath());
        try {
            XposedHelpers.callStaticMethod(NativeEngineClazz,
                    "startHookJni",
                    isListenAll, LHook.mFilterList, LHook.mFunctionList, path
            );
        } catch (Throwable e) {
            CLog.e("callStartHookJni error " + e);
            return;
        }
        CLog.i("callStartHookJni success !");
    }


    @Override
    public void initZygote(StartupParam startupParam) {

    }


}
