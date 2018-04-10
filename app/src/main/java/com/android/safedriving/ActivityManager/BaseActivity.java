package com.android.safedriving.ActivityManager;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.android.safedriving.ActivityManager.ActivityCollector;

/**
 * BaseActivity类功能：
 * 1.创建BaseActivity类作为所有活动的父类。
 * 2.使用ActivityCollector完成活动的创建和移除。
 */
public class BaseActivity extends AppCompatActivity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCollector.addActivity(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityCollector.removeActivity(this);
    }
}
