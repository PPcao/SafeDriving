package com.android.safedriving.BluetoothHelper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.safedriving.RealtimeAnalysisActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 蓝牙服务类:
 * 1.蓝牙连接监听线程。
 * 2.蓝牙连接线程。
 * 3.蓝牙已连接线程。
 */
public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final boolean DEBUG = true;

    private static final String BLUETOOTH_NAME = "raspberrypi";//蓝牙端口名
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//此处为蓝牙串口服务的UUID

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int BluetoothState;

    /**
     * 蓝牙状态常量：
     * 0，闲置。
     * 1，监听。
     * 2，正在连接。
     * 3.已连接。
     */
    public static final int IDLE = 0;
    public static final int LISTENING = 1;
    public static final int CONNECTING = 2;
    public static final int CONNECTED = 3;

    public static boolean allowRec = true;

    /**
     * @param handler 在线程与UI间通讯
     */
    public BluetoothService(Handler handler){
        mAdapter = BluetoothAdapter.getDefaultAdapter();//获取本地蓝牙设备
        BluetoothState = IDLE;
        mHandler = handler;
    }

    /**
     * 设置当前蓝牙状态
     * @param state 当前蓝牙状态
     */
    private synchronized void setBluetoothState(int state){
        BluetoothState = state;
    }

    /**
     * 获取当前蓝牙状态
     * @return 当前蓝牙状态
     */
    public synchronized int getBluetoothState(){
        return BluetoothState;
    }

    /**
     * 启动本地蓝牙接收监听
     */
    public synchronized void acceptWait(){
        if(DEBUG){
            Log.e(TAG,"acceptWait");
        }
        if(mAcceptThread == null && mConnectedThread == null){
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setBluetoothState(LISTENING);
    }

    /**
     * 开启连接线程
     * @param device 目标连接设备
     */
    public synchronized void connect(BluetoothDevice device){
        if(DEBUG){
            Log.e(TAG,"正在连接" + device);
        }
        cancelAllBtThread();//关闭所有蓝牙服务线程，开启连接线程
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setBluetoothState(CONNECTING);
    }

    /**
     * 开启已连接线程
     * @param socket 已建立连接的蓝牙端口
     * @param device 已连接的蓝牙设备
     */
    public synchronized void connected(BluetoothSocket socket,BluetoothDevice device){
        if(DEBUG){
            Log.e(TAG,"connected");
        }
        cancelAllBtThread();//关闭蓝牙服务线程，开启已连接线程
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        //发送已连接设备名回UI
        sendStringToUI(RealtimeAnalysisActivity.CONNECTED_DEVICE_NAME,RealtimeAnalysisActivity.DEVICE_NAME,device.getName());
        setBluetoothState(CONNECTED);
    }

    /**
     * 关闭所有蓝牙服务线程
     */
    public synchronized void cancelAllBtThread(){
        if(DEBUG){
            Log.e(TAG,"cancelAllBtThread");
        }
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setBluetoothState(IDLE);
    }

    /**
     * 输出数据
     * @param out 输出字节流
     */
    public void write(byte[] out){
        ConnectedThread r;
        synchronized (this){
            if(BluetoothState != CONNECTING){
                return;
            }
            r = mConnectedThread;
            r.write(out);
        }
    }

    /**
     * 连接失败处理方法
     */
    private void connectionFailed(){
        setBluetoothState(LISTENING);
        mConnectedThread = null;
        BluetoothService.this.acceptWait();
        sendStringToUI(RealtimeAnalysisActivity.BT_TOAST,RealtimeAnalysisActivity.TOAST,"连接失败");
    }

    /**
     * 发送字符串回UI
     * @param what 类型
     * @param key  关键字
     * @param str 字符串
     */
    private void sendStringToUI(int what,String key,String str){
        Message message = mHandler.obtainMessage(what);
        Bundle bundle = new Bundle();
        bundle.putString(key,str);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    /**
     * 连接断开处理方法
     */
    private void connectionBreak(){
        setBluetoothState(LISTENING);
        mConnectedThread = null;
        BluetoothService.this.acceptWait();
        sendStringToUI(RealtimeAnalysisActivity.BT_TOAST,RealtimeAnalysisActivity.TOAST,"连接断开");//向UI发送断开连接
    }

    /**
     * 监听外部蓝牙设备的线程
     */
    private class AcceptThread extends Thread{
        private final BluetoothServerSocket mBtServerSocket;

        /**
         * 获取蓝牙监听端口
         */
        public AcceptThread(){
            BluetoothServerSocket bss = null;
            try {
                bss = mAdapter.listenUsingRfcommWithServiceRecord(BLUETOOTH_NAME,MY_UUID);
            }catch (IOException e){
                Log.e(TAG,"监听失败",e);
            }
            mBtServerSocket = bss;
        }

        public void run(){
          if(DEBUG){
              Log.e(TAG,"Begin mAcceptThread");
          }

          setName("AcceptThread");
          BluetoothSocket socket = null;
          while (BluetoothState != CONNECTED){
              try {
                  socket = mBtServerSocket.accept();//成功连接时，退出循环
              }catch (IOException e){
                  Log.e(TAG,"连接失败",e);
                  break;
              }

              //成功接收主设备
              if(socket != null){
                  synchronized (BluetoothService.this){
                      switch (BluetoothState){
                          case LISTENING:
                          case CONNECTING:
                              connected(socket, socket.getRemoteDevice());
                              break;
                          case IDLE:
                          case CONNECTED:
                              try {
                                  socket.close();
                              }catch (IOException e){
                                  Log.e(TAG,"Could not close unwanted socket", e);
                              }
                              break;
                      }
                  }
              }
          }
          if(DEBUG){
              Log.e(TAG, "End mAcceptThread");
          }
        }

        public void cancel(){
            if(DEBUG){
                Log.e(TAG,"cancel " + this);
            }
            try {
                mBtServerSocket.close();
            }catch (IOException e){
                Log.e(TAG,"server close failed",e);
            }
        }
    }

    /**
     * 连接蓝牙设备的线程
     */
    private class ConnectThread extends Thread{
        private final BluetoothSocket mBtSocket;
        private final BluetoothDevice mBtDevice;

        public ConnectThread(BluetoothDevice device){
            mBtDevice = device;
            BluetoothSocket bs = null;

            //根据UUID获取要连接的设备
            try {
                bs = device.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){
                Log.e(TAG,"create failed");
            }
            mBtSocket = bs;
        }

        public void run(){
            if(DEBUG){
                Log.e(TAG,"Begin mConnectThread");
            }
            setName("ConnectThread");

            //尝试连接蓝牙端口，当连接失败或异常，重新开启连接监听线程并退出连接线程
            try {
                mBtSocket.connect();
            }catch (IOException e){
                connectionFailed();
                try {
                    mBtSocket.close();
                }catch (IOException e2){
                    Log.e(TAG,"close failed",e2);
                }
                BluetoothService.this.acceptWait();
                if(DEBUG){
                    Log.e(TAG,"End mConnectThread");
                }
                return;
            }

            synchronized (BluetoothService.this){
                mConnectThread = null;
            }

            //启动已连接线程
            connected(mBtSocket,mBtDevice);
            if(DEBUG){
                Log.e(TAG,"End mConnectThread");
            }
        }

        public void cancel(){
            if(DEBUG){
                Log.e(TAG,"cancel " + this);
            }
            try {
                mBtSocket.close();
            }catch (IOException e){
                Log.e(TAG,"close failed");
            }
        }
    }

    /**
     * 已连接的相关处理线程
     */
    private class ConnectedThread extends Thread{
        private final BluetoothSocket mBtSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        public ConnectedThread(BluetoothSocket socket){
            if(DEBUG){
                Log.e(TAG,"Begin ConnectedThread");
            }
            mBtSocket = socket;
            InputStream is = null;
            OutputStream os = null;

            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();
            }catch (IOException e){
                Log.i(TAG,"get stream failed",e);
            }

            mInputStream = is;
            mOutputStream = os;
        }

        public void run(){
            if(DEBUG){
                Log.e(TAG,"Begin mConnectedThread");
            }
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {
                    bytes = mInputStream.read(buffer);
                    if(bytes != -1 && allowRec){
                        mHandler.obtainMessage(RealtimeAnalysisActivity.REC_DATA,bytes,-1,buffer).sendToTarget();
                    }
                }catch (IOException e){
                    Log.e(TAG,"connection break",e);
                    connectionBreak();
                    break;
                }
                try {
                    Thread.sleep(20);//沉睡20ms，避免过于频繁工作导致UI处理数据不及时而阻塞
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
            if(DEBUG){
                Log.i(TAG,"End mConnectedThread");
            }
        }

        /**
         * 写输出流，发送数据
         * @param buffer 要输出字节流
         */
        public void write(byte[] buffer){
            try {
                buffer =null;
                mOutputStream.write(buffer);
            }catch (IOException e){
                Log.e(TAG,"Exception during write", e);
            }
        }

        public void cancel(){
            if(DEBUG){
                Log.e(TAG, "cancel " + this);
            }
            try {
                mBtSocket.close();
            }catch (IOException e){
                Log.e(TAG,"close failed",e);
            }
        }
    }
}
