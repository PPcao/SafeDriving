package com.android.safedriving;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.safedriving.BluetoothHelper.BluetoothService;
import com.android.safedriving.BluetoothHelper.DeviceListActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * 前提：用户此前已经使用蓝牙配对了监控仪。
 *
 * RealtimeAnalysisActivity功能:
 * 1.从登陆页面跳转到此页面后，首先判断手机是否已经打开蓝牙，如未打开，提示用户打开蓝牙，如用户选择不打开蓝牙，则此页面不显示任何数据；
 *      如用户选择打开蓝牙，则后台打开蓝牙并自动连接监控仪。
 * 2.打开蓝牙连接蓝牙设备后，接收监控仪传来的数据。
 * 3.将从监控仪接收的数据实时显示在此页面上。
 * 4.将从监控仪接收的数据上传到服务器，留备下一个历史数据分析使用。
 */
public class RealtimeAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "RAnalysisActivity";
    private static final boolean DEBUG = false;

    public static final int REC_DATA = 2;
    public static final int CONNECTED_DEVICE_NAME = 4;
    public static final int BT_TOAST = 5;

    public static final String DEVICE_NAME = "device name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private TextView RecDataView;
    private Button ClearWindow;

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoohAdapter = null;
    private BluetoothService mConnectService = null;

    /**
     * 验证我们是否能进行SD卡的读写操作
     */
    private static final int REQUEST_EXTERNAL_STORAG = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static void verifyStoragePermissions(Activity activity){
        int permission = ActivityCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(permission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity,PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAG);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.realtime_analysis_layout);

        RecDataView = (TextView) findViewById(R.id.Rec_Text_show);
        ClearWindow = (Button) findViewById(R.id.ClearWindow);
        setupListener();

        mBluetoohAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoohAdapter == null){
            Toast.makeText(this,"本设备不支持蓝牙功能！",Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        verifyStoragePermissions(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(DEBUG){
            Log.i(TAG, "++ ON START ++");
        }
        if(!mBluetoohAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }else if(mConnectService == null){
            mConnectService = new BluetoothService(mHandler);
        }
    }

    // 用于从线程获取信息的Handler对象
    private final Handler mHandler = new Handler(){
        StringBuffer sb=new StringBuffer();
        byte[] bs;
        float sWidth;
        int b,i,lineWidth=0,align_i=0;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REC_DATA:
                    sb.setLength(0);
                    bs=(byte[])msg.obj;
                    char[] c=new char[msg.arg1];
                    for(i=0;i<msg.arg1;i++){
                        c[i]=(char)(bs[i]&0xff);
                        sWidth=RecDataView.getPaint().measureText(c,i,1);
                        lineWidth+=sWidth;
                        if(lineWidth>RecDataView.getWidth()){
                            lineWidth=(int)sWidth;
                            sb.append('\n');
                        }
                        if(c[i]=='\n')lineWidth=0;
                        sb.append(c[i]);
                    }
                    RecDataView.append(sb);
                    break;
                case CONNECTED_DEVICE_NAME:
                    // 提示已连接设备名
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "已连接到" + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case BT_TOAST:
                    if(mConnectedDeviceName!=null)
                        Toast.makeText(getApplicationContext(), "与"+mConnectedDeviceName + msg.getData().getString(TOAST),Toast.LENGTH_SHORT).show();
                    else Toast.makeText(getApplicationContext(), "与"+target_device_name + msg.getData().getString(TOAST),Toast.LENGTH_SHORT).show();
                    mConnectedDeviceName=null;
                    break;
            }
        }
    };

    @Override
    protected synchronized void onResume() {
        super.onResume();

        if(mConnectService!=null){
            if(mConnectService.getBluetoothState() == BluetoothService.IDLE){
                mConnectService.acceptWait();
            }
        }
    }

    private View.OnClickListener ButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.ClearWindow:
                    RecDataView.setText("");
                    break;
            }
        }
    };

    /**
     * 设置自定义按键的监听方法
     */
    private void setupListener(){
        ClearWindow.setOnClickListener(ButtonClickListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(DEBUG) {
            Log.i(TAG, "onPause");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(DEBUG){
            Log.i(TAG, "onStop");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(DEBUG) {
            Log.e(TAG, "onDestroy");
        }
        // Stop the Bluetooth connection
        if (mConnectService != null) {
            mConnectService.cancelAllBtThread();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    private String target_device_name = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e(TAG, "onActivityResult");
        switch (requestCode){
            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK){
                    String address = data.getExtras().getString(DeviceListActivity.DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoohAdapter.getRemoteDevice(address);
                    target_device_name = device.getName();
                    if(target_device_name.equals(mConnectedDeviceName)){
                        Toast.makeText(this,"已连接" + mConnectedDeviceName,Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this,"正在连接" + target_device_name,Toast.LENGTH_SHORT).show();
                    mConnectService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK){
                    mConnectService = new BluetoothService(mHandler);
                }else {
                    Toast.makeText(this,"拒绝打开蓝牙",Toast.LENGTH_SHORT).show();
                }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bluetoothdevicelist,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.Connect:
                if(! mBluetoohAdapter.isEnabled()){
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
                    return true;
                }
                Intent serverIntent = new Intent(this,DeviceListActivity.class);
                startActivityForResult(serverIntent,REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                if (mBluetoohAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
                    startActivity(discoverableIntent);
                }
                return true;
        }
        return  false;
    }
}
