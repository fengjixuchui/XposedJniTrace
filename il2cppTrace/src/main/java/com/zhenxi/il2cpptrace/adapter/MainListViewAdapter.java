package com.zhenxi.il2cpptrace.adapter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.zhenxi.il2cpptrace.bean.AppBean;
import com.zhenxi.il2cpptrace.config.ConfigKey;
import com.zhenxi.il2cpptrace.utils.CLog;
import com.zhenxi.il2cpptrace.utils.Constants;
import com.zhenxi.il2cpptrace.utils.FileUtils;
import com.zhenxi.il2cpptrace.utils.IntoMySoUtils;
import com.zhenxi.il2cpptrace.utils.RootUtils;
import com.zhenxi.il2cpptrace.utils.SpUtil;
import com.zhenxi.il2cpptrace.utils.ToastUtils;
import com.zhenxi.il2cpptrace.BuildConfig;
import com.zhenxi.il2cpptrace.R;

import java.io.File;
import java.util.ArrayList;


import org.json.JSONObject;


/**
 * Created by lyh on 2019/2/14.
 */
public class MainListViewAdapter extends BaseAdapter {


    private ArrayList<AppBean> data;

    private final Context mContext;
    private final CheckBox isTraceIl2cpp;

    private final CheckBox isSystemLoad;
    private final CheckBox isSystemPathLoad;


    public MainListViewAdapter(Context context,
                               ArrayList<AppBean> data,
                               CheckBox isTraceIl2cpp,
                               CheckBox isSystemLoad, CheckBox isSystemPathLoad) {
        this.mContext = context;
        this.data = data;
        this.isTraceIl2cpp = isTraceIl2cpp;
        this.isSystemLoad = isSystemLoad;
        this.isSystemPathLoad = isSystemPathLoad;
    }


    public void setData(ArrayList<AppBean> data) {

        this.data = data;

        notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        return data == null ? 0 : data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = View.inflate(mContext, R.layout.activity_list_item, null);
        }
        ViewHolder holder = ViewHolder.getHolder(convertView);
        AppBean appBean = data.get(position);

        holder.iv_appIcon.setImageBitmap(Constants.drawable2Bitmap(appBean.appIcon));
        holder.tv_appName.setText(appBean.appName);
        holder.tv_packageName.setText(appBean.packageName);
        holder.All.setOnClickListener(v ->
                initConfig(appBean)
        );
        return convertView;
    }

    /**
     * 保存配置信息
     */
    public void initConfig(AppBean bean) {
        saveConfig(bean, null);
    }

    private void saveConfig(AppBean bean, JSONObject jsonObject) {
        if (jsonObject == null) {
            jsonObject = new JSONObject();
        }
        try {
            jsonObject.put(ConfigKey.PACKAGE_NAME, bean.packageName);
            jsonObject.put(ConfigKey.IS_SYSTEM_LOAD_INTO, isSystemLoad.isChecked());
            boolean isSystemPath = isSystemPathLoad.isChecked();
            jsonObject.put(ConfigKey.IS_USE_SYSTEM_PATH, isSystemPath);
            try {
                //普通的根目录注入
                PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
                //Base.apk路径
                jsonObject.put(ConfigKey.MODULE_SO_PATH, packageInfo.applicationInfo.publicSourceDir);
            } catch (Throwable e) {
                CLog.e("MOUDLE_SO_PATH error " + e, e);
                jsonObject.put(ConfigKey.MODULE_SO_PATH, null);
            }
            //CLog.i("module into base.apk path  -> " + jsonObject.getString(MODULE_SO_PATH));
            //保存时间,增加时效性
            jsonObject.put(ConfigKey.SAVE_TIME, System.currentTimeMillis());
            jsonObject.put(ConfigKey.IS_Il2CPPTRACE, isTraceIl2cpp.isChecked());
        } catch (Throwable e) {
            CLog.e("save config to json error " + e);
        }
        saveConfigForLocation(bean, jsonObject);
    }

    private void saveConfigForLocation(AppBean bean, JSONObject jsonObject) {
        initConfig(bean.packageName, jsonObject);
        CLog.e("save config file info -> " + jsonObject);
        SpUtil.putString(mContext, ConfigKey.CONFIG_JSON, jsonObject.toString());
        ToastUtils.showToast(mContext,"保存成功文件路径为\n" + "data/data/" + bean.packageName);
    }


    private void setSystemInfoPath(String savepath, JSONObject jsonObject) {
        try {

            String path = savepath + "/lib" + BuildConfig.project_name + ".so";
            RootUtils.execShell("chmod 777 " + path);
            jsonObject.put(ConfigKey.SYSTEM_INTO_PATH, path);
            CLog.i(">>>>>>>>>>>>>>>> cp file success !! " + path);
        } catch (Throwable e) {
            CLog.e("setSystemInfoPath error " + e, e);
        }
    }

    /**
     * 很多加壳app
     * shared = new XSharedPreferences(BuildConfig.APPLICATION_ID, "config"); 导致失效
     * 通过root强行将数据保存一份到目标apk 私有目录
     */
    @SuppressWarnings("All")
    private void initConfig(String packageName, JSONObject jsonObject) {
        try {
            if (isSystemPathLoad.isChecked()) {
                boolean is64 = IntoMySoUtils.is64bitForPackageName(mContext, packageName);
                String into_so_path = IntoMySoUtils.getSoPath(mContext, "lib" + BuildConfig.project_name + ".so", null);
                String intoSystemPath = null;
                PackageManager pm = mContext.getPackageManager();
                PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
                if (packageInfo != null) {
                    //base apk的路径
                    intoSystemPath = packageInfo.applicationInfo.nativeLibraryDir;
                }
                //将so放到/cache 或者/data/local/tmp/但是发现没权限
                if(intoSystemPath==null){
                    intoSystemPath = ConfigKey.DEF_VALUE;
                }else {
                    CLog.i("system into path lib -> "+intoSystemPath);
                    //尝试删除
                    RootUtils.execShell("rm -f " + (intoSystemPath+ "/lib" + BuildConfig.project_name + ".so"));
                    RootUtils.execShell("cp -f " + into_so_path + " " + intoSystemPath);
                    setSystemInfoPath(intoSystemPath,jsonObject);
                }
            }
            File jnitraceModleData
                    = new File("/data/data/" + BuildConfig.APPLICATION_ID);
            File config = new File(jnitraceModleData, BuildConfig.project_name + "Config");
            CLog.e("temp config file path " + config.getPath());
            if (config.exists()) {
                boolean delete = config.delete();
                if (!delete) {
                    CLog.e("delete org config file error ,start root delete " + config.getPath());
                    RootUtils.execShell("rm -f " + config.getPath());
                }
            }
            FileUtils.makeSureDirExist(config.getParentFile());
            boolean configNewFile = config.createNewFile();
            if (!configNewFile) {
                CLog.e(">>>>>>>>>>> create temp config file error " + config.getPath());
                return;
            }
            config.setExecutable(true, false);
            config.setReadable(true, false);
            config.setWritable(true, false);
            CLog.e("start save config file " + config.getPath());
            FileUtils.saveString(config, jsonObject.toString());

            File temp = new File("/data/data/" + packageName);
            File tagConfigFile = new File(temp, BuildConfig.project_name + "Config");
            if (tagConfigFile.exists()) {
                RootUtils.execShell("rm -f " + tagConfigFile.getPath());
                CLog.i(">>>>>>>> initConfig rm -f finish  " + tagConfigFile.getPath());
            }
            File tagDexFile = new File(temp, ConfigKey.IL2CPP_DEX_NAME);
            if (tagDexFile.exists()) {
                RootUtils.execShell("rm -f " + tagDexFile.getPath());
                CLog.i(">>>>>>>> initConfig rm -f finish  " + tagDexFile.getPath());
            }

            CLog.i(">>>>>>>>> start mv " +
                    "file " + config.getPath() + "->" + temp);
            //强制覆盖
            RootUtils.execShell("mv -f " + config.getPath() + " " + temp);

            //asset release
            File dexFile =
                    FileUtils.extractAssetFile(mContext, ConfigKey.IL2CPP_DEX_NAME
                            , jnitraceModleData.getPath());
            if (!dexFile.exists()) {
                CLog.i(">>>>>>>> JnitraceDex.dex  release error " + dexFile.getPath());
                return;
            }
            RootUtils.execShell("mv -f " + dexFile.getPath() + " " + temp);

            //防止因为用户组权限问题导致open failed: EACCES (Permission denied)
            CLog.i(">>>>>>>>>> chmod 777 path -> " + tagConfigFile + " " + dexFile);
            RootUtils.execShell("chmod 777 " + tagConfigFile.getPath());
            RootUtils.execShell("chmod 777 " + tagDexFile.getPath());


        } catch (Throwable e) {
            CLog.e("initConfig error  " + e, e);
        }
    }


    private static class ViewHolder {
        TextView tv_appName, tv_packageName;
        LinearLayout All;
        ImageView iv_appIcon;

        ViewHolder(View convertView) {
            All = convertView.findViewById(R.id.ll_all);
            tv_packageName = convertView.findViewById(R.id.tv_packName);
            tv_appName = convertView.findViewById(R.id.tv_appName);
            iv_appIcon = convertView.findViewById(R.id.iv_appIcon);
        }

        static ViewHolder getHolder(View convertView) {
            ViewHolder holder = (ViewHolder) convertView.getTag();
            if (holder == null) {
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            }
            return holder;
        }
    }
}
