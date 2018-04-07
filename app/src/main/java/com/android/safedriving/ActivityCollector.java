package com.android.safedriving;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * ActivityCollector功能：
 * 1.创建ActivityCollector类管理所有的活动。
 * 2.添加活动。
 * 3.移除活动。
 * 4.关闭所有的活动。
 * 5.实现强制下线功能的工具。
 */
public class ActivityCollector {
    public static List<Activity> activities = new ArrayList<>();

    public static void addActivity(Activity activity){
        activities.add(activity);
    }

    public static void removeActivity(Activity activity){
        activities.remove(activity);
    }

    public static void finishAll(){
        for(Activity activity:activities){
            if(! activity.isFinishing()){
                activity.finish();
            }
        }
    }
}
