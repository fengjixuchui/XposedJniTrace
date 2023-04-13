package com.zhenxi.jnitrace.config;

/**
 * Created by Zhenxi on 2019/4/3.
 */

public class ConfigKey {

    public static final String DEF_VALUE = "DEF";

    public static final String CONFIG_JSON="CONFIG_JSON";
    /**
     * 选中的包名
     */
    public static final String PACKAGE_NAME="PACKAGE_NAME";

    /**
     * 注入模块So的Path
     * 主要是我们自己模块的Base.apk的路径
     */
    public static final String MODULE_SO_PATH="MODULE_SO_PATH";

    /**
     * 选择Apk的时间,十分钟有效
     */
    public static final String SAVE_TIME="SAVE_TIME";

    /**
     * 是否开启内存序列化
     */
    public static final String IS_SERIALIZATION="IS_SERIALIZATION";

    /**
     * 是否监听全部的SO文件
     */
    public static final String IS_LISTEN_TO_ALL="IS_LISTEN_TO_ALL";


    /**
     * 过滤的集合
     */
    public static final String FILTER_LIST="FILTER_LIST";

    /**
     * 开启的功能列表
     */
    public static final String LIST_OF_FUNCTIONS="LIST_OF_FUNCTIONS";

    /**
     * 修改注入方式,使用System.loadlib的方式进行注入。
     * 用这种注入的方式的好处是利用原始的API进行注入,可以共享和原始Apk一样的内存空间。
     * 一样的Maps,不会导致在注入的SO在遍历maps的时候,里面找不到目标Apk的so的item,
     * 因为xposedJnitrace只需要hook系统api,系统apk是所有so共享的,所以这个功能默认为关闭的。
     */
    public static final String IS_SYSTEM_LOAD_INTO="IS_SYSTEM_LOAD_INTO";

    /**
     * 将需要注册的核心的Native方法,放在这里面。
     * 这个Class单独以一个Dex存在,因为注入的时候需要传入的Classloader不同。
     * 所以不能直接将native方法放在Xposed的hook模块里面 。
     * 单独剥离出来成为一个Dex , 在Apk加载的时候根据传入的Classloader加载Dex进行注入和Native方法的注册 。
     */
    public static final String JNITRACE_DEX_NAME = "JnitraceDex.dex";

    /**
     * 正常我们将模块的So进行注入,是将So移动到被Hook apk的沙箱目录,然后进行注入 。
     * 比如/data/data/被HookApp/FunJnilib.so 将这个So进行注入 。
     * 在Maps里面可以看到这个So的路径。
     * 我们可以我们的So移动到系统目录下,比如 。
     * System/lib64/FunJnilib.so
     * 这样maps里面看到的我们注入的So在系统目录下。以逃避检测。
     */
    public static final String IS_USE_SYSTEM_PATH = "IS_USE_SYSTEM_PATH";


    /**
     * 系统注入的具体路径,先尝试获取libart.so的路径,尝试写入。
     * 如果获取不到的话则往/data/location/tmp下写入
     */
    public static final String SYSTEM_INTO_PATH = "SYSTEM_INTO_PATH";
}
