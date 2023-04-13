package com.zhenxi.jnitrace.utils;

import static com.zhenxi.jnitrace.config.ConfigKey.DEF_VALUE;
import static com.zhenxi.jnitrace.config.ConfigKey.JNITRACE_DEX_NAME;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.zhenxi.jnitrace.BuildConfig;


import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.XposedHelpers;

/**
 * @author Zhenxi on 2021/5/17
 */
public class IntoMySoUtils {

    public static final String V8 = "arm64-v8a";
    public static final String V7 = "armeabi-v7a";

    private static final String ARM = "arm";
    private static final String ARM64 = "arm64";

    public static final String LibDIRName = BuildConfig.project_name + "Cache";

    /**
     * 最多尝试获取四层
     */
    @SuppressWarnings("all")
    private static Field getPathListField(ClassLoader classLoader) {
        Field pathListField = null;
        try {
            pathListField = classLoader.getClass().getDeclaredField("pathList");
        } catch (NoSuchFieldException e) {
            try {
                pathListField = classLoader.
                        getClass().getSuperclass()
                        .getDeclaredField("pathList");
            } catch (NoSuchFieldException ex) {
                try {
                    pathListField = classLoader.getClass().
                            getSuperclass().getSuperclass().
                            getDeclaredField("pathList");
                } catch (NoSuchFieldException exc) {
                    try {
                        pathListField = classLoader.getClass().
                                getSuperclass().getSuperclass().getSuperclass().
                                getDeclaredField("pathList");
                    } catch (NoSuchFieldException noSuchFieldException) {

                    }
                }
            }
        }
        return pathListField;
    }

    /**
     * 获取classloader里面的elements数组
     */
    private static Object[] getClassLoaderElements(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        //CLog.e("getClassLoaderElements class loader name " + classLoader);
        try {
            Field pathListField = getPathListField(classLoader);
            if (pathListField != null) {
                pathListField.setAccessible(true);
                Object dexPathList = pathListField.get(classLoader);
                Field dexElementsField = Objects.requireNonNull(dexPathList).getClass().getDeclaredField("dexElements");
                dexElementsField.setAccessible(true);
                Object[] dexElements = (Object[]) dexElementsField.get(dexPathList);
                if (dexElements != null) {
                    return dexElements;
                } else {
                    CLog.e("AddElements  get dexElements == null");
                }
            } else {
                CLog.e("AddElements  get pathList == null");
            }
        } catch (Throwable e) {
            CLog.e("AddElements  Throwable   " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将 Elements 数组 set回系统的 classloader里面
     */
    private static boolean SetDexElements(Object[] dexElementsResut,
                                          int conunt, ClassLoader classLoader) {
        try {
            Field pathListField = getPathListField(classLoader);
            if (pathListField != null) {
                pathListField.setAccessible(true);
                Object dexPathList = pathListField.get(classLoader);
                Field dexElementsField = dexPathList.getClass().getDeclaredField("dexElements");
                dexElementsField.setAccessible(true);
                dexElementsField.set(dexPathList, dexElementsResut);
                Object[] dexElements = (Object[]) dexElementsField.get(dexPathList);
                if (Objects.requireNonNull(dexElements).length == conunt &&
                        Arrays.hashCode(dexElements) == Arrays.hashCode(dexElementsResut)) {
                    //CLog.i("merge dexElements.length -> " + dexElements.length);
                    return true;
                } else {
                    CLog.e("merge dexElements.length ->  " + dexElements.length + " conunt ->   " + conunt);
                    CLog.e("dexElements hashCode " +
                            Arrays.hashCode(dexElements) + "  " + Arrays.hashCode(dexElementsResut));
                    return false;
                }
            } else {
                CLog.e("SetDexElements  get pathList == null");
            }
        } catch (Throwable e) {
            CLog.e("SetDexElements  NoSuchFieldException   " + e, e);
        }
        return false;
    }

    /**
     * 初始化我们的SO,加载我们的so到被Hook的apk内存里 。
     * 支持两种加载方式,主要区别是两种Classloader不同 。
     * <p>
     * <p>
     * 1,使用当前进程的Classloader
     * 不可以直接使用,System.loadlib注入,System.loadlib会使用调用者类的Classloader,
     * 而非当前进程的Classloader,会导致注入的So,使用的是Xposed 模块的Classloader 。
     * 这种加载方法和apk直接加载so是一样的,不存在作用域问题。
     * 这种方法更适用于hook apk里面so的逻辑,因为作用域是一样的。
     * <p>
     * <p>
     * 2,使用lsp模块的Classloader,这时候的classloader用的是xposed模块的classloader 。
     * 这时候有个弊端,就是遍历maps的时候,只能拿到当前classloader里面的so文件。比如Lsp的so文件
     * 拿不到apk本身的so文件,因为classloader的作用域不同 。
     * 这种方法更适用于hook系统api,系统api是公用的,不存在作用域问题。
     * <p>
     * 但是如果只是Hook 系统api 两种方式区别不是很大 。反而第2种更隐藏。
     * <p>
     * 这块还有一个细节,native方法如何注册的问题。
     * 如果这个native方法写在xposed模块里面,就只能通过第二种方式去加载。
     * 使用第一种会报错,所以干脆直接将native方法搞成dex文件。
     * 哪个classloader用于加载,就往哪个classloader的作用域里面去加。这样可以同时支持1&2两种方法
     */
    public static void initMySoForName(Context context,
                                       String name,
                                       ClassLoader so_classloader,
                                       String intoSoPath,
                                       String systemPath

    ) {
        CLog.i("start initMySoForName " + name
                + " [" + so_classloader.getClass().getName() + "]");
        CLog.i("initMySoForName into path ->" + intoSoPath);
        try {
            //尝试将Classloader和native注册方法合并
            //防止因为传入不同的Classloader导致,无法对native方法进行注册。
            //每次将我们需要注册的native方法先用classloader进行加载,然后和需要注册的
            //classloader进行合并,这样不会存在在native层注册导致class not find
            if (!MergeClassloader(context, so_classloader)) {
                CLog.e("initMySoForName classloader merge error " + so_classloader.getClass().getName());
                return;
            }
            String path;
            if (systemPath==null||systemPath.equals(DEF_VALUE)) {
                //本地路径
                path = getSoPath(context, name, intoSoPath);
            } else {
                path = systemPath;
            }
            CLog.i("initMySoForName into so path ->  " + path);
            if (path != null) {
                LoadSoForPath(path, so_classloader);
            } else {
                CLog.e(">>>>>>>>>>>>>  not found into so path -> " + name);
            }
        } catch (Throwable e) {
            CLog.e("initMySo error,start printf " + e.getMessage() + " " + e.getLocalizedMessage());
            Log.getStackTraceString(e);
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                CLog.e(element.toString());
            }
        }
    }

    private static void tryAddNativeLib(Context context, ClassLoader so_classloader) {
        //ClassUtils.getClassMethodInfo(so_classloader.getClass().getSuperclass());
        try {
            Method addNativePath = Objects.requireNonNull
                            (so_classloader.getClass().getSuperclass())
                    .getDeclaredMethod("addNativePath", Collection.class);
            addNativePath.setAccessible(true);
            String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
            CLog.i("tryAddNativeLib path " + nativeLibraryDir);
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(nativeLibraryDir);
            addNativePath.invoke(so_classloader, arrayList);
            CLog.i("tryAddNativeLib path finish ");
        } catch (Throwable e) {
            CLog.e("tryAddNativeLib  error " + e, e);
        }
    }

    private static boolean MergeClassloader(Context context, ClassLoader mainClassloader) {
        DexClassLoader classLoader = null;
        try {
            File dexFile = new File(context.getApplicationInfo().dataDir + "/" + JNITRACE_DEX_NAME);
            if(!dexFile.exists()){
                CLog.e(">>>>>>>>>> MergeClassloader dex not exists "+dexFile.getPath());
                return false;
            }
            CLog.i("MergeClassloader dex file path -> " + dexFile);
            String cacheDir = context.getCacheDir().getAbsolutePath();
            classLoader = new DexClassLoader(dexFile.getPath(), cacheDir, null, mainClassloader);
        } catch (Throwable e) {
            CLog.e("initMySoForName load class loader error " + e);
        }
        if (classLoader == null) {
            CLog.e("initMySoForName DexClassLoader == null ");
            return false;
        }

        //将两个classloader进行合并,方便native层进行查找 。
        //将宿主的classloader里面填充我们注入native的method
        Object[] MyDexClassloader = getClassLoaderElements(mainClassloader);
        if (MyDexClassloader == null || MyDexClassloader.length == 0) {
            CLog.e("get MyDexClassloader Elements == null");
            return false;
        }
        Object[] otherClassloader = getClassLoaderElements(classLoader);
        if (otherClassloader == null || otherClassloader.length == 0) {
            CLog.e(">>>>>>>>>>>> get otherClassloader Elements == null");
            return false;
        }
        try {
            CLog.e("get classloader Elements success !");
            Object[] combined =
                    (Object[]) Array.newInstance(otherClassloader.getClass().getComponentType(),
                            MyDexClassloader.length + otherClassloader.length);
            //将自己classloader 数组的内容 放到 前面位置
            System.arraycopy(MyDexClassloader, 0, combined, 0, MyDexClassloader.length);
            System.arraycopy(otherClassloader, 0, combined, MyDexClassloader.length, otherClassloader.length);
            CLog.i("System.arraycopy finish ");
            if ((MyDexClassloader.length +
                    otherClassloader.length) != combined.length) {
                CLog.e("merge elements size error ");
                return false;
            }
            if (SetDexElements(combined, MyDexClassloader.length + otherClassloader.length, mainClassloader)) {
                CLog.i("merge classloader success !");
                return true;
            } else {
                CLog.e("merge classloader fail ");
            }
        } catch (Throwable e) {
            CLog.e("merge classloader error " + e, e);
        }
        CLog.i(">>>>>>>>>>>>>>>>> merge classloader finish ");
        return false;
    }


    public static boolean is64bitSelf(String xpMoudleName, Context context) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Process.is64Bit();
        }
        return is64bitForPackageName(context, xpMoudleName);
    }

    public static boolean is64bitForPackageName(Context context, String packageName) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
        String nativeLibraryDir = packageInfo.applicationInfo.nativeLibraryDir;
        boolean is64 = !nativeLibraryDir.startsWith(ARM);
        CLog.i("is64bitForPackageName [" + packageName + "(" + is64 + ")] " + nativeLibraryDir);
        //如果对方App没有So的话,默认使用64
        return is64;
    }


    /**
     * 获取So路径。
     */
    public static String getSoPath(Context context, String so_name, String into_so_path_baseApk) throws Exception {
        String ret = null;
        String baseApkPath = null;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            if (packageInfo != null) {
                //base apk的路径
                baseApkPath = packageInfo.applicationInfo.publicSourceDir;
            }
        } catch (Throwable e) {
            //很多加壳app会在getPackageInfo 失败,这个时候采用默认的config目录
            CLog.e("getSoPath getPackageInfo so path error ,start append path " + e.getMessage());
            baseApkPath = into_so_path_baseApk;
        }
        if (baseApkPath == null) {
            CLog.e(">>>>>>>>>>>>  get so path base.apk == null !!!!!!!!!!!!!!");
            return null;
        }
        CLog.e("baseApkPath base.apk path -> " + baseApkPath);

        String cacheDir = context.getApplicationInfo().dataDir + "/" + LibDIRName;
        //尝试解压
        UnZipUtils.UnZipFolder(baseApkPath, cacheDir);
        try {
            ret = cacheDir + "/lib/" +
                    (is64bitSelf(BuildConfig.APPLICATION_ID, context) ? V8 : V7) + "/" +
                    so_name;
        } catch (Throwable exception) {
            CLog.e("getSoPath is64bit   error " + exception.getMessage());
        }
        return ret;

    }


    /**
     * 这块有个细节问题
     * 注入时候传入的Classloader问题
     * 这个Classloader标识当前So的Classloader()
     * (So 也是需要Classloader的,用于标识)
     * <p>
     * 情况1:
     * 如果传Null 当前Classloader为系统的Classloader
     * 系统的Classloader没有权限去反射得到当前进程的Class
     * 系统的Class里面没有当前进程的Class
     * <p>
     * 情况2:
     * 如果传当前被Hook进程的Classloader进入的时候会直接挂掉
     * 因为模块的这个类，是Xposed new了一个PathClassloader （是个成员变量）
     * (
     * 具体参考 XposedBridge-》loadModule 方法
     * private static void loadModule(String apk) {
     * log("Loading modules from " + apk);
     * <p>
     * if (!new File(apk).exists()) {
     * log("  File does not exist");
     * return;
     * }
     * //加载Xposed模块的 Classloader
     * ClassLoader mcl = new PathClassLoader(apk, BOOTCLASSLOADER);
     * <p>
     * InputStream is = mcl.getResourceAsStream("assets/xposed_init");
     * if (is == null) {
     * log("assets/xposed_init not found in the APK");
     * return;
     * }
     * .....
     * )
     * 这个PathClassloader 不属于当前进程,所以会find不到当前模块的Class直接挂掉
     * （java.lang.ClassNotFoundException: Didn't find class "com.example.vmp.Hook.LHookConfig"
     * on path: DexPathList[[zip file "/data/user/0/com.xx.main/.cache/classes.jar",
     * zip file "/data/app/com.xx.main-1/base.apk"],
     * nativeLibraryDirectories=[/data/app/com.xx.main-1/lib/arm,
     * /data/app/com.xx.main-1/base.apk!/lib/armeabi-v7a, /system/lib, /vendor/lib]]）
     * <p>
     * 情况3:
     * 直接传入当前模块的Classloader this.getclass.getClassloader
     */
    public static void LoadSoForPath(String path, Object object) {
        try {
            CLog.e("load so path ->  " + path);
            String Msg = null;
            if (Build.VERSION.SDK_INT >= 28) {
                Msg = (String) XposedHelpers.callMethod(Runtime.getRuntime(), "nativeLoad", path, object);
            } else {
                Msg = (String) XposedHelpers.callMethod(Runtime.getRuntime(), "doLoad", path, object);
            }
            if(Msg!=null){
                CLog.e(">>>>>>>> into so for path error -> "+Msg+" "+path);
            }else {
                CLog.i("load so for path success " + path);
            }
        } catch (Throwable e) {
            CLog.e("load so for path error" + e.getMessage());
        }
    }
}
