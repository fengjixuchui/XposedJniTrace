package com.zhenxi.il2cpptrace;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import com.google.android.material.snackbar.Snackbar;
import com.zhenxi.il2cpptrace.bean.AppBean;
import com.zhenxi.il2cpptrace.utils.PermissionUtils;
import com.zhenxi.il2cpptrace.utils.ToastUtils;
import com.zhenxi.il2cpptrace.view.Xiaomiquan;
import com.zhenxi.il2cpptrace.adapter.MainListViewAdapter;
import com.xiaoyouProject.searchbox.SearchFragment;
import com.xiaoyouProject.searchbox.custom.IOnSearchClickListener;
import com.xiaoyouProject.searchbox.entity.CustomLink;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ArrayList<AppBean> mAllPackageList = new ArrayList<>();
    private final ArrayList<AppBean> mCommonPackageList = new ArrayList<>();

    private final String[] permissionList = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INSTALL_PACKAGES,
            Manifest.permission.GET_PACKAGE_SIZE
    };


    private CheckBox mCb_checkbox;
    private CheckBox mCb_Il2cppTrace;
    private MainListViewAdapter mMainListViewAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();
        initView();
        PermissionUtils.initPermission(this, permissionList);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0) {
            boolean granted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {

            } else {

            }
        }
    }


    private void initData() {
        mAllPackageList = getPackageList();
    }

    private void initView() {
        ListView lv_list = findViewById(R.id.lv_list);

        Toolbar toolbar = findViewById(R.id.tb_toolbar);

        mCb_checkbox = findViewById(R.id.cb_checkbox);
        mCb_Il2cppTrace = findViewById(R.id.cb_il2cppTrace);

        mCb_Il2cppTrace.setOnClickListener(v -> {
            if (mCb_Il2cppTrace.isChecked()) {
                showDialog();
            }
        });
        CheckBox cb_IsContextClassloader = findViewById(R.id.cb_isUserContextLoader);


        CheckBox cb_isSystemPath = findViewById(R.id.cb_isSystemPath);

        ImageView search = findViewById(R.id.iv_search);

        mMainListViewAdapter =
                new MainListViewAdapter(this,
                        mCommonPackageList, mCb_Il2cppTrace,
                        cb_IsContextClassloader,cb_isSystemPath);

        mCb_checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                //需要显示 系统 app
                mMainListViewAdapter.setData(mAllPackageList);
            } else {
                mMainListViewAdapter.setData(mCommonPackageList);
            }
        });


        toolbar.setTitle("");

        toolbar.inflateMenu(R.menu.main_activity);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.xiaomiquan) {
                xiaomiquan();
            } else if (item.getItemId() == R.id.kecheng) {
                Uri uri = Uri.parse("https://pan.baidu.com/s/17aDu5b0Qb0OR4qwBfxhFgw");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                ToastUtils.showToast(getBaseContext(), "解压密码 qqqq");
            } else if (item.getItemId() == R.id.info) {
                Uri uri = Uri.parse("https://pan.baidu.com/s/17aDu5b0Qb0OR4qwBfxhFgw");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                ToastUtils.showToast(getBaseContext(), "解压密码 qqqq");
            }
            return false;
        });

        SearchFragment<AppBean> searchFragment = SearchFragment.newInstance();
        searchFragment.setOnSearchClickListener(new IOnSearchClickListener<AppBean>() {
            @Override
            public void onSearchClick(String key) {
                ArrayList<AppBean> dataList = null;
                if (mCb_checkbox.isChecked()) {
                    dataList = mAllPackageList;
                } else {
                    dataList = mCommonPackageList;
                }
                for (AppBean bean : dataList) {
                    if (bean.appName.equals(key)) {
                        mMainListViewAdapter.initConfig(bean);
                        //CLog.e("history " + bean.toString());
                        return;
                    }
                }
                ToastUtils.showToast(getBaseContext(), "没有找到 " + key + " 程序可能已经被卸载");
            }

            @Override
            public void onLinkClick(AppBean appBean) {
                mMainListViewAdapter.initConfig(appBean);
                searchFragment.historyDb.insertHistory(appBean.appName);
            }


            @Override
            public void onTextChange(String key) {
                List<CustomLink<AppBean>> data = new ArrayList<>();
                ArrayList<AppBean> dataList = null;
                if (mCb_checkbox.isChecked()) {
                    dataList = mAllPackageList;
                } else {
                    dataList = mCommonPackageList;
                }
                for (AppBean bean : dataList) {
                    if (bean.appName.contains(key)) {
                        data.add(new CustomLink<AppBean>(bean.appName, bean));
                    }
                }
                searchFragment.setLinks(data);
            }

        });
        search.setOnClickListener(v -> searchFragment.showFragment(getSupportFragmentManager(), SearchFragment.TAG));
        lv_list.setAdapter(mMainListViewAdapter);
        mMainListViewAdapter.notifyDataSetChanged();

    }

    private void showDialog() {
        Snackbar make = Snackbar.make(mCb_Il2cppTrace,
                "监听il2cpp全部函数调用,文件保存在il2cpp_trace.txt\n",
                Snackbar.LENGTH_LONG);
        make.setDuration(Snackbar.LENGTH_INDEFINITE);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) make.getView();
        TextView textView =
                layout.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setMaxLines(100);
        make.setAction("关闭", view -> make.dismiss()).show();
    }

    private void xiaomiquan() {
        startActivity(new Intent(this, Xiaomiquan.class));
    }

    public ArrayList<AppBean> getPackageList() {
        PackageManager packageManager = getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);
        ArrayList<AppBean> appBeans = new ArrayList<>();

        for (PackageInfo packageInfo : packages) {
            AppBean appBean = new AppBean();
            // 判断系统/非系统应用
            // 非系统应用
            // 系统应用
            appBean.isSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            appBean.appName = packageInfo.applicationInfo.loadLabel(getPackageManager()).toString();
            appBean.packageName = packageInfo.packageName;
            appBean.appIcon = packageInfo.applicationInfo.loadIcon(getPackageManager());

            appBeans.add(appBean);

            if (!appBean.isSystemApp) {
                mCommonPackageList.add(appBean);
            }

        }
        return appBeans;
    }


}
