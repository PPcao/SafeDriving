package com.android.safedriving;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Handler;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.android.safedriving.BluetoothHelper.BluetoothService;
import com.android.safedriving.BluetoothHelper.DeviceListActivity;
import com.android.safedriving.HttpUtil.HttpUrlConstant;
import com.android.safedriving.SelfDialogUtil.TipDialog;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.poi.BaiduMapPoiSearch;
import com.baidu.mapapi.utils.poi.PoiParaOption;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 前提：用户此前已经使用蓝牙配对了监控仪。
 *
 * RealtimeAnalysisActivity功能:
 * 1.从登陆页面跳转到此页面后，首先判断手机是否已经打开蓝牙，如未打开，提示用户打开蓝牙，如用户选择不打开蓝牙，则此页面不显示任何数据；
 *      如用户选择打开蓝牙，则后台打开蓝牙并自动连接监控仪。
 * 2.打开蓝牙连接蓝牙设备后，接收监控仪传来的数据。
 * 3.将从监控仪接收的数据实时显示在此页面上。
 * 4.将从监控仪接收的数据上传到服务器，留备下一个历史数据分析使用。
 * 5.调用百度地图进行POI检索。
 * 6.检测到疲劳状态，播放提示音。
 */
public class RealtimeAnalysisActivity extends AppCompatActivity implements OnChartValueSelectedListener {

    private static final String TAG = "RAnalysisActivity";
    private static final boolean DEBUG = false;
    private static boolean isTipDialogShow = false;

    public Float upDataflag;
    public LineDataSet set = new LineDataSet(null, "EAR");

    public static final int REC_DATA = 2;
    public static final int CONNECTED_DEVICE_NAME = 4;
    public static final int BT_TOAST = 5;

    public static final String DEVICE_NAME = "device name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

//    private TextView RecDataView;
//    private Button ClearWindow;
//    private Button LocateButton;
//    private Button OverlookButton;
    private FloatingActionButton mFloatingActionButton;
    private LineChart mChart;

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoohAdapter = null;
    private BluetoothService mConnectService = null;

    // 定位相关
    public LocationClient mLocationClient;
    public double mCurrentLat ;
    public double mCurrentLog ;

    //播放音频相关
    public MediaPlayer mMediaPlayer;

    //滑动菜单相关
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private android.support.design.widget.NavigationView mNavigationView;
    private ImageButton mImageButton;

    //提示对话框相关
    private TipDialog mTipDialog;


    /**
     * 验证本应用是否能进行SD卡的读写操作
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(new MyLocationListener());
        setContentView(R.layout.realtime_analysis_layout);

//        ClearWindow = (Button) findViewById(R.id.ClearWindow);
//        LocateButton = (Button) findViewById(R.id.locate_button);
//        OverlookButton = (Button) findViewById(R.id.overlook_button);
        mFloatingActionButton = findViewById(R.id.fab);
        mImageButton = findViewById(R.id.bluetooth_imageButton);
        setupListener();
        mChart = (LineChart) findViewById(R.id.realtimelinechart);
        mChart.setOnChartValueSelectedListener(this);

        //播放音频相关
        mMediaPlayer = new MediaPlayer().create(this,R.raw.alarm);

        mBluetoohAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoohAdapter == null){
            Toast.makeText(this,"本设备不支持蓝牙功能！",Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        verifyStoragePermissions(this);
        initDrawChart();
        initLocationSettings();

        //滑动菜单相关
        initView();

        //提示对话框相关
//        findViewById(R.id.self_dialog).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                openTipDialog();
//            }
//        });
    }

    //提示对话框相关
    private void openTipDialog(){
        mTipDialog = new TipDialog(RealtimeAnalysisActivity.this);
        mTipDialog.setTitle("提示");
        mTipDialog.setMessage("检测到您可能正处于疲劳驾驶状态，是否调取百度地图寻找附近的服务区（休息站）？");
        mTipDialog.setYesOnclickListener("寻找附近服务区", new TipDialog.onYesOnclickListener() {
            @Override
            public void onYesClick() {
                //调用百度地图，进行POI检索
                LatLng ptCenter = new LatLng(mCurrentLat,mCurrentLog); // 获取当前位置的经纬度
                PoiParaOption para = new PoiParaOption()
                        .key("服务区")
                        .center(ptCenter)
                        .radius(2000);
                try {
                    BaiduMapPoiSearch.openBaiduMapPoiNearbySearch(para, RealtimeAnalysisActivity.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mTipDialog.dismiss();
                isTipDialogShow = false;
            }
        });
        mTipDialog.setNoOnclickListener("忽略", new TipDialog.onNoOnclickListener() {
            @Override
            public void onNoClick() {
                //若驾驶员选择忽略提示，则直接隐藏提示框。
                mTipDialog.dismiss();
                isTipDialogShow = false;
            }
        });
        mTipDialog.show();
        isTipDialogShow = true;
    }

    //滑动菜单相关
    private void initView() {

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); //1.决定显示.
        getSupportActionBar().setDisplayShowTitleEnabled(false);//不显示Toolbar里的APP名称
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mActionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.drawer_open, R.string.drawer_close); //2.传入Toolbar可以点击.
        mDrawerLayout.addDrawerListener(mActionBarDrawerToggle); //3.监听变化.

        mNavigationView = findViewById(R.id.navigation);
        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected( MenuItem item) {
                Log.d("onSelected", "id=" + item.getItemId());
                return true;
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        //4.同步状态
        mActionBarDrawerToggle.syncState();
    }

    /**
     * 为绘图做准备
     */
    private void initDrawChart(){
        // enable description text
        mChart.getDescription().setEnabled(false);//不显示X轴上的描述

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);


        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
//        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);


        XAxis xl = mChart.getXAxis();
//        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis leftAxis = mChart.getAxisLeft();
//        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaximum(0.5f);
        leftAxis.setAxisMinimum(0.0f);
        leftAxis.setDrawGridLines(true);
//        leftAxis.setInverted(true);//y轴向下为正

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    /**
     * 初始化定位
     */
    private void initLocationSettings(){
        List<String> permissionList=new ArrayList<>();
        if(ContextCompat.checkSelfPermission(RealtimeAnalysisActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(RealtimeAnalysisActivity.this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if(ContextCompat.checkSelfPermission(RealtimeAnalysisActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!permissionList.isEmpty()){
            String[] perimissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(RealtimeAnalysisActivity.this,perimissions,1);
        }else {
            requestLocation();
        }
    }

    private void requestLocation(){
        initLocation();
        mLocationClient.start();
    }

    private void initLocation(){
        LocationClientOption option=new LocationClientOption();
        option.setScanSpan(1000);
        option.setIsNeedAddress(true);
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setCoorType("bd09ll");
        option.setOpenGps(true);
        option.setNeedDeviceDirect(true);
        mLocationClient.setLocOption(option);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions, int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults.length>0){
                    for(int result:grantResults){
                        if(result!=PackageManager.PERMISSION_GRANTED){
                            Toast.makeText(this,"必须同意所有权限才能使用本程序",Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                    requestLocation();
                }else {
                    Toast.makeText(this,"发生未知错误",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    public class MyLocationListener implements BDLocationListener{
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            mCurrentLat = bdLocation.getLatitude();
            mCurrentLog = bdLocation.getLongitude();
//            float radius = bdLocation.getRadius();    //获取定位精度，默认值为0.0，加上此句会导致定位有偏差

        }
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

    @Override
    protected void onRestart() {
        super.onRestart();
        mLocationClient.restart();
    }

    // 用于从线程获取信息的Handler对象
    private final Handler mHandler = new Handler(){
        StringBuffer sb = new StringBuffer();
        byte[] bs;
//        float sWidth;
        int i;
//        int lineWidth = 0;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REC_DATA:
                    sb.setLength(0);
                    bs = (byte[]) msg.obj;

//                    System.out.println(bs);

                    char[] c = new char[msg.arg1];

                    for(i = 0; i < msg.arg1 ; i++){
                        c[i] = (char) (bs[i] & 0xff);
//                        sWidth = RecDataView.getPaint().measureText(c,i,1);
////                        lineWidth += sWidth;
////
////                        if(lineWidth > RecDataView.getWidth()){
////                            lineWidth = (int) sWidth;
////                            sb.append('\n');
////                        }
////                        if(c[i] == '\n'){
////                            lineWidth = 0;
////                        }
                        sb.append(c[i]);
                    }//处理蓝牙设备接收的数据完毕
                    String sbtostring = sb.toString();
					String[] split = sbtostring.split("-");
					String ear = split[0];
					String flag = split[1];
                    //System.out.println(sb);
                    //RecDataView.append(sb);

                    try{
                        Float upDataear = Float.parseFloat(ear);
						upDataflag = Float.parseFloat(flag);
                        if(upDataflag == 1.0 ){
                            set.setColor(Color.RED);//线的颜色为红色
//                            set.setDrawCircles(true);
                            if(!isTipDialogShow){
                                openTipDialog();
                            }

//                            if(! mMediaPlayer.isPlaying())
//                                mMediaPlayer.start();

//                            initMediaPlayer();
//                            if(! mediaPlayer.isPlaying()){
//                                preparedListener = new OnPreparedListener() {
//
//                                    @Override
//                                    public void onPrepared(MediaPlayer mp) {
//                                        mediaPlayer.start();
//                                        System.out.println("正在播放音频文件...");
//                                    }
//                                };
//                            }
//                            OverlookButton.setVisibility(VISIBLE);
//                            LocateButton.setVisibility(VISIBLE);
                        }else {
                            set.setDrawCircles(false);
//                            if(mMediaPlayer.isPlaying())
//                                mMediaPlayer.stop();
//                            OverlookButton.setVisibility(INVISIBLE);
//                            LocateButton.setVisibility(INVISIBLE);
//                            set.setCircleColor(Color.WHITE);
//                            set.setCircleColorHole(Color.WHITE);

                        }
                        setupListener();
                        uploadDataToServer(upDataear,upDataflag);
                        addEntry(upDataear,upDataflag);
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    break;
                case CONNECTED_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);// 提示已连接设备名
                    Toast.makeText(getApplicationContext(), "已连接到" + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case BT_TOAST:
                    if(mConnectedDeviceName!=null){
                        Toast.makeText(getApplicationContext(), "与" + mConnectedDeviceName + msg.getData().getString(TOAST),Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "与" + target_device_name + msg.getData().getString(TOAST),Toast.LENGTH_SHORT).show();
                    }
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
//                case R.id.ClearWindow:
////                    RecDataView.setText("");
//                    mChart.clearValues();
//                    break;
//                case R.id.locate_button:
//                    LatLng ptCenter = new LatLng(mCurrentLat,mCurrentLog); // 获取当前位置的经纬度
//                    PoiParaOption para = new PoiParaOption()
//                            .key("服务区")
//                            .center(ptCenter)
//                            .radius(2000);
//                    try {
//                        BaiduMapPoiSearch.openBaiduMapPoiNearbySearch(para, RealtimeAnalysisActivity.this);
//                    } catch (Exception e) {
//                        e.printStackTrace();
////                        showDialog();
//                    }
//                case R.id.overlook_button:
//                    break;
                case R.id.fab:
                    mChart.clearValues();
                    break;
                case R.id.bluetooth_imageButton:
                    System.out.println("点击了ImageButton");
                    PopupMenu popupMenu = new PopupMenu(RealtimeAnalysisActivity.this,mImageButton);
                    popupMenu.getMenuInflater().inflate(R.menu.bluetoothdevicelist,popupMenu.getMenu());
                    popupMenu.show();//若不加此句，点击按钮，弹出的菜单不可见
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            switch (menuItem.getItemId()){
                                case R.id.Connect:
                                    if(! mBluetoohAdapter.isEnabled()){
                                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                        startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
                                        return true;
                                    }
                                    Intent serverIntent = new Intent(RealtimeAnalysisActivity.this,DeviceListActivity.class);
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
                    });
                    break;
            }
        }
    };

//    /**
//     * 提示未安装百度地图app或app版本过低
//     */
//    public void showDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setMessage("您尚未安装百度地图app或app版本过低，点击确认安装？");
//        builder.setTitle("提示");
//        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//                OpenClientUtil.getLatestBaiduMapApp(RealtimeAnalysisActivity.this);
//            }
//        });
//
//        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//            }
//        });
//
//        builder.create().show();
//
//    }

    /**
     * 设置自定义按键的监听方法
     */
    private void setupListener(){
//        ClearWindow.setOnClickListener(ButtonClickListener);
//        LocateButton.setOnClickListener(ButtonClickListener);
//        OverlookButton.setOnClickListener(ButtonClickListener);
        mFloatingActionButton.setOnClickListener(ButtonClickListener);
        mImageButton.setOnClickListener(ButtonClickListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(DEBUG) {
            Log.i(TAG, "onPause");
        }

        if (thread != null) {
            thread.interrupt();
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

        if(mMediaPlayer != null){
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }

        mLocationClient.stop();
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

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.bluetoothdevicelist,menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()){
//            case R.id.Connect:
//                if(! mBluetoohAdapter.isEnabled()){
//                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                    startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
//                    return true;
//                }
//                Intent serverIntent = new Intent(this,DeviceListActivity.class);
//                startActivityForResult(serverIntent,REQUEST_CONNECT_DEVICE);
//                return true;
//            case R.id.discoverable:
//                if (mBluetoohAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
//                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
//                    startActivity(discoverableIntent);
//                }
//                return true;
//        }
//        return  false;
//    }

    private Thread thread;
    private void addEntry(final float sb,final float upDataflag){

        System.out.println("addEntry "+sb);

//        if (thread != null) {
//            thread.interrupt();
//        }

        final Runnable runnable = new Runnable() {

            @Override
            public void run() {
                LineData data = mChart.getData();

                if (data != null) {

                    ILineDataSet set = data.getDataSetByIndex(0);
                    // set.addEntry(...); // can be called as well

                    if (set == null) {
                        set = createSet();
                        data.addDataSet(set);
                    }


                    data.addEntry(new Entry(set.getEntryCount(), sb), 0);
                    data.notifyDataChanged();

                    System.out.println("addEntry done");

                    // let the chart know it's data has changed
                    mChart.notifyDataSetChanged();

                    // limit the number of visible entries
                    mChart.setVisibleXRangeMaximum(50);
                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);

                    // move to the latest entry
                    mChart.moveViewToX(data.getEntryCount());

                    // this automatically refreshes the chart (calls invalidate())
                    // mChart.moveViewTo(data.getXValCount()-7, 55f,
                    // AxisDependency.LEFT);

                    //refresh mChart
                    mChart.invalidate();
                }
            }
        };

        thread = new Thread(new Runnable() {
            @Override
            public void run() {

                /**
                 * 错误：刷新UI的子线程放于while{}循环中，且未等待子线程结束，
                 *      导致每次调用addEntry()添加数据点时，出现添加一个数据点，多次将数据点绘入图中的结果。
                 * 解决：
                 *      1.去掉循环。
                 *      2.调用等待子线程结束的方法。
                 */
                    runOnUiThread(runnable);

//                    try {
//                        Thread.sleep(25);
//                    } catch (InterruptedException e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }

            }
        });

        thread.start();
        try {
            thread.join();
        }catch (Exception e){

        }

    }

    private LineDataSet createSet() {


        set.setCircleColor(Color.RED);
        set.setCircleColorHole(Color.RED);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(2f);
//        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.BLACK);
        set.setValueTextSize(9f);
        set.setDrawValues(false);//是否显示数据值，默认true
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        //根据EAR的不同分级，以不同的颜色标记
//        if(upDataflag == 1.0){
//            set.setCircleColor(Color.RED);
//            set.setCircleColorHole(Color.parseColor("#CD0000"));
//            set.setColor(Color.parseColor("#CD0000"));
//        }else {
//            set.setCircleColor(Color.WHITE);
//            set.setCircleColorHole(Color.BLUE);
//            set.setColor(ColorTemplate.getHoloBlue());
//        }


        return set;
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }

    private void uploadDataToServer(Float ear, Float flag) {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        String account = bundle.getString("account");
//        Calendar nowTime = Calendar.getInstance();

        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(currentTime);

//        System.out.println(dateString);

        String url = HttpUrlConstant.uploadDataURL + "?DAccount=" + account + "&time=" + dateString + "&ear=" + ear + "&flag=" + flag;

        new uploadDataAsyncTask().execute(url);
    }

    public class uploadDataAsyncTask extends AsyncTask<String,Integer,String>{
        @Override
        protected void onPreExecute() {
            Log.d("RAnalysisActivity","onPreExecute");
        }

        /**
         *
         * @param strings params是一个数组，是AsyncTask在激活运行时调用的execute()方法传入的参数。
         * @return
         */
        @Override
        protected String doInBackground(String... strings) {
            Log.d("RAnalysisActivity","doInBackground");

            HttpURLConnection connection = null;
            StringBuilder response = new StringBuilder();
            try{
                URL url = new URL(strings[0]);
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(80000);
                connection.setReadTimeout(80000);
                InputStream in = connection.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine() )!= null){
                    response.append(line);
                }
            }catch (MalformedURLException e){
                e.printStackTrace();
            }catch (IOException e){
                e.printStackTrace();
            }

            return response.toString();//此处的返回值作为参数传入onPostExecute()
        }

        /**
         * 本方法在UI线程中执行，典型用法是更新进度条。
         * @param values
         */
        @Override
        protected void onProgressUpdate(Integer... values) {

        }

        /**
         * 本方法在UI线程中运行，可直接操作UI元素。
         * @param s
         */
        @Override
        protected void onPostExecute(String s) {
            Log.d("RAnalysisActivity","onPostExecute");

            if(s.equals("code:300")){
                Toast.makeText(RealtimeAnalysisActivity.this,"上传数据失败",Toast.LENGTH_SHORT).show();
            }
        }
    }

}
