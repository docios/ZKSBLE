package com.demo.mybluetoothdemo.utils.bleutils;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;


import com.demo.mybluetoothdemo.callback.BleConnectionCallBack;
import com.demo.mybluetoothdemo.callback.MyBleCallBack;
import com.demo.mybluetoothdemo.entity.EventMsg;
import com.demo.mybluetoothdemo.utils.CheckUtils;
import com.demo.mybluetoothdemo.utils.Constants;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.UUID;

/**
 * Created by admin on 2017/11/8.
 */

public class BleConnectUtil {
    private final String TAG = "TAG";
    /**********开始连接Service**********/
    private String serviceUuidStr, writeCharactUuid, notifyCharactUuid;
    public static BluetoothGattCharacteristic mBluetoothGattCharacteristic, mBluetoothGattCharacteristicNotify;

    public static BluetoothAdapter mBluetoothAdapter;
    public BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice device;

    public String mDeviceAddress = "";
    public boolean mConnected;
    /**
     * 操作间要有至少15ms的间隔
     */
    private static final int DELAY_TIME = 15;
    private Activity context;
    private MyBleCallBack myBleCallBack;

    Handler handler = new Handler();

    public BleConnectUtil(Activity context) {
        this.context = context;
        init();
    }

    private void init() {
        serviceUuidStr = "6326edac-53ec-4e94-916b-6d9702abb4de";    //服务uuid
        writeCharactUuid = "6326edac-53ec-4e94-916c-6d9702abb4de";     //写通道 uuid  6326edac-53ec-4e94-916c-6d9702abb4de     Rx
        notifyCharactUuid = "";// 6326edac-53ec-4e94-916d-6d9702abb4de//通知通道 uuid    Tx
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mConnected = false;
    }

    /**
     * 判断是否可见
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void bluetoothIsAble(MyBleCallBack myBleCallBack) {
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "蓝牙不可见");

            //方法二 推荐
            Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            context.startActivity(enable);
        } else {
            this.myBleCallBack = myBleCallBack;
            scanLeDevice();
        }
    }


    /**
     * 搜索Ble设备
     */
    public void scanLeDevice() {
        if (mBluetoothAdapter == null) {
            return;
        }
        //扫描所有设备
        mBluetoothAdapter.startLeScan(mLeScanCallback);

        //扫描同一类设备
//        UUID[] serviceUuids = {UUID.fromString(serviceUuidStr)};
//        mBluetoothAdapter.startLeScan(serviceUuids, mLeScanCallback);
    }

    /**
     * 停止扫描
     */
    public void stopScan() {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    /**
     * 蓝牙扫描回调接口
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device.getName() == null) {
                return;
            }
            myBleCallBack.callbleBack(device);
        }
    };

    public void connectBle(BluetoothDevice device) {
        //获取所需地址
        mDeviceAddress = device.getAddress();
        Log.e(TAG, "connectBle: " + mDeviceAddress);
        new connectThread().start();

    }


    /**
     * 连接并且读取数据线程
     */
    private class connectThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                //连接
                if (!connect(mDeviceAddress, 10000, 2)) {
                    Log.e(TAG, "---------------------------连接--------------------------------------------------------------------");
                    // 52322122
                    disConnect();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            EventBus.getDefault().postSticky(new EventMsg(Constants.BLE_CONNECTION_FINISH_MSG));
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public String last_mac = "";

    // 连接设备
    public boolean connect(String mac, int sectime, int reset_times) {
        Log.e(TAG, "连接设备----------------------------------------------------------------------");

        if (!mBluetoothAdapter.isEnabled()) {// 没有打开蓝牙
            return false;
        }
        disConnect();
        for (int i = 0; i < reset_times; i++) {
            initTimeFlag(WORK_onServicesDiscovered);

            if ((mBluetoothGatt != null) && mac.equals(last_mac)) {
                // 当前已经连接好了
                if (mConnected == true) {
                    return true;
                }
                Log.e(TAG, "重连");
                mBluetoothGatt.connect();
            } else {
                Log.e(TAG, "新连接");
                disConnect(); // 新设备进行连接
                device = mBluetoothAdapter.getRemoteDevice(mac);
                Log.e(TAG, "device:" + device);
                if (device == null) {
                    return false;
                }
                Log.e(TAG, "这是我已知监听回调的地方:");
                mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
            }

            // 连接超时
            if (startTimeOut(sectime)) {
                Log.e(TAG, "连接超时");
                disConnect();
                continue;
            }

            mConnected = true;
            last_mac = mac;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }


    // 断开连接
    public boolean disConnect() {
        if (mBluetoothGatt != null) {
            Log.e(TAG, "disConnect：断开连接---mBluetoothGatt不为null");
            //  setEnableNotify(BleConnectUtil.mBluetoothGattCharacteristicNotify, false);
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mConnected = false;
            mDeviceAddress = "";
            return true;
        }
        return false;
    }

    /**
     * 销毁连接
     */
    public void close() {
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * 查看连接状态
     */
    public boolean isConnected() {
        return mConnected;
    }

    /**
     * BLE回调操作
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {


            // 连接成功
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "连接成功");
                mConnected = true;

                Log.e(TAG, "连接成功-------------------------------------------------------------------------------------");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                if (work_witch == WORK_onConnectionStateChange) {
                    work_ok_flag = true;
                }
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "连接断开");
                if (mConnected) {
                    mConnected = false;
                }
                EventBus.getDefault().postSticky(Constants.BLE_CONNECTION_FINISH_MSG);
                if (callback != null) {
                    callback.onDisconnect();
                }
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {


            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "发现设备");
                if (work_witch == WORK_onServicesDiscovered) {
                    work_ok_flag = true;
                }
                //发现设备，遍历服务，初始化特征
                initBLE(gatt);
            } else {
                System.out.println("onServicesDiscovered fail-->" + status);
            }
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            Log.e(TAG, "onDescriptorRead");
            if ((status == BluetoothGatt.GATT_SUCCESS)
                    && (work_witch == WORK_onDescriptorRead)) {
                work_ok_flag = true;
            }
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            Log.e(TAG, "onDescriptorWrite");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (work_witch == WORK_onDescriptorWrite) {
                    work_ok_flag = true;
                }
            }
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            Log.e(TAG, "onCharacteristicRead");
            byte[] receive_byte = characteristic.getValue();
            String str = CheckUtils.byte2hex(receive_byte).toString();
            Log.e(TAG, "onCharacteristicRead-----$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$" + str);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (work_witch == WORK_onCharacteristicRead) {
                    work_ok_flag = true;
                }
                if (callback != null) {
                    callback.onRecive(characteristic);
                }
            } else {

            }
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            Log.e(TAG, "onCharacteristicChanged---------------------------------");
            byte[] receive_byte = characteristic.getValue();
            String str = CheckUtils.byte2hex(receive_byte).toString();
            Log.e(TAG, "characteristic-----$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$" + str);
            if (work_witch == WORK_onCharacteristicChanged) {
                work_ok_flag = true;
            }

            if (callback != null) {
                Log.e(TAG, "onCharacteristicChanged-方法调用 onRecive------------------");
                callback.onRecive(characteristic);
            }
            super.onCharacteristicChanged(gatt, characteristic);
        }

        /**
         * 收到BLE终端写入数据回调
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            Log.e(TAG, "onCharacteristicWrite");
            Log.e(TAG, "onCharacteristicWrite的状态值：" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] receive_byte = characteristic.getValue();
                String str = CheckUtils.byte2hex(receive_byte).toString();
                Log.e(TAG, "characteristic-----$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$:" + str);
                Log.e(TAG, "数据写入成功");
                if (work_witch == WORK_onCharacteristicWrite) {
                    work_ok_flag = true;
                }
                if (callback != null) {
                    Log.e(TAG, "数据写入成功。kaishi 毁掉哦");
                    boolean b = mBluetoothGatt.readCharacteristic(characteristic);

                    Log.e(TAG, "设置什么鬼东西:" + b);
                    callback.onSuccessSend();
                }
            } else {
                Log.e(TAG, "数据写入失败" + status);
            }
            super.onCharacteristicWrite(gatt, characteristic, status);
        }


        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {

            Log.e(TAG, "onReadRemoteRssi");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (work_witch == WORK_onReadRemoteRssi) {
                    work_ok_flag = true;
                }
                rssi_value = (rssi_value + rssi) / 2;
                // rssi_value = rssi;
            }
            super.onReadRemoteRssi(gatt, rssi, status);
        }

    };

    //初始化特征
    public void initBLE(BluetoothGatt gatt) {
        if (gatt == null) {
            return;
        }
        //遍历所有服务
        for (BluetoothGattService bluetoothGattService : gatt.getServices()) {
            Log.e(TAG, "--->BluetoothGattService---------" + bluetoothGattService.getUuid().toString());

            //遍历所有特征
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {

                String str = bluetoothGattCharacteristic.getUuid().toString();
                if (str.equals(writeCharactUuid)) {
                    mBluetoothGattCharacteristic = bluetoothGattCharacteristic;
                } else if (str.equals(notifyCharactUuid)) {
                    mBluetoothGattCharacteristicNotify = bluetoothGattCharacteristic;
                }
            }
        }
        if (mBluetoothGattCharacteristic == null) {
            Log.e(TAG, "mBluetoothGattCharacteristic 阿是空");
        }
        if (mBluetoothGattCharacteristicNotify == null) {
            Log.e(TAG, "mBluetoothGattCharacteristicNotify 阿是空");
        }

        //判断是否获取到特征
        if ((mBluetoothGattCharacteristic == null) || (mBluetoothGattCharacteristicNotify == null)) {
            //
            Log.e(TAG, "你为啥非要走这样方法那      cnm");
            //连接失败
            mConnected = false;
        } else {
            setNotify(mBluetoothGattCharacteristicNotify);
            setEnableNotify(mBluetoothGattCharacteristicNotify, true);
            //连接成功
            mConnected = true;
            try {
                // 刚刚使能上需要等待一下才能
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.e(TAG, "mConnected-" + mConnected);
        EventBus.getDefault().postSticky(new EventMsg(Constants.BLE_CONNECTION_FINISH_MSG));
    }

    public List<BluetoothGattService> getServiceList() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * 设置可通知
     */
    public boolean setNotify(BluetoothGattCharacteristic data_char) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (data_char == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }

        // 查看是否带有可通知属性
        if (0 != (data_char.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
            mBluetoothGatt.setCharacteristicNotification(data_char, true);
            BluetoothGattDescriptor descriptor = data_char.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));//00002902-0000-1000-8000-00805f9b34fb
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } else if (0 != (data_char.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
            mBluetoothGatt.setCharacteristicNotification(data_char, true);
            BluetoothGattDescriptor descriptor = data_char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        return true;
    }

    /**
     * 设置允许通知
     */
    public boolean setEnableNotify(BluetoothGattCharacteristic data_char, boolean enable) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (data_char == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(data_char, enable);

        return true;
    }

    /**
     * 读取信息
     */
    public boolean readData(BluetoothGattCharacteristic data_char) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (data_char == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }
        Log.e(TAG, "加载 readData （）------");
        mBluetoothGatt.readCharacteristic(data_char);

//        mBluetoothGatt.setCharacteristicNotification(data_char, true);
//        BluetoothGattDescriptor descriptor = data_char.getDescriptor(
//                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));//00002902-0000-1000-8000-00805f9b34fb
//        byte[] tmpStr = {0x7E, 0x00, 0x02, 0x13, 0x00, 0x11, 0x7E};//0x13  获取已记录数据
//        descriptor.setValue(tmpStr);
//        mBluetoothGatt.readDescriptor(descriptor);
        return true;
    }

    /**
     * 发送并带返回的命令
     */
    public boolean sendCmd(BluetoothGattCharacteristic data_char, int milsec) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (data_char == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }

        initTimeFlag(WORK_onCharacteristicChanged);

        mBluetoothGatt.setCharacteristicNotification(data_char, true);
        mBluetoothGatt.writeCharacteristic(data_char);

        if (startTimeOut(milsec)) {

            Log.e("TAG", "startTimeOut-------------------------------------------------");
            return false;
        }
        Log.e("TAG", "开始发送数据啦---------------------------");
        mBluetoothGatt.setCharacteristicNotification(data_char, false);

        try { // 发送数据一定要有一些延迟
            Thread.sleep(DELAY_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }


    public void writeCharacteristic(byte[] value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return ;
        }
        BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(
                "6326edac-53ec-4e94-916b-6d9702abb4de"));
        BluetoothGattCharacteristic characteristic1 = service.getCharacteristic(UUID.fromString(
                "6326edac-53ec-4e94-916c-6d9702abb4de"));

        characteristic1.setValue(value);

        mBluetoothGatt.writeCharacteristic(characteristic1);

    }

    public void readCharacteristic() {

        if (mBluetoothGatt == null) {
            return ;
        }
        Log.e(TAG, "我开始读取数据啦------");
        BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(
                "6326edac-53ec-4e94-916b-6d9702abb4de"));
        BluetoothGattCharacteristic characteristic1 = service.getCharacteristic(UUID.fromString(
                "6326edac-53ec-4e94-916d-6d9702abb4de"));

        mBluetoothGatt.readCharacteristic(characteristic1);
    }

    public void mCharacteristic() {

        if (mBluetoothGatt == null) {
            return ;
        }
        Log.e(TAG, "我开始读取数据啦------");
        BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(
                "6326edac-53ec-4e94-916b-6d9702abb4de"));
        BluetoothGattCharacteristic characteristic1 = service.getCharacteristic(UUID.fromString(
                "6326edac-53ec-4e94-916c-6d9702abb4de"));
        BluetoothGattCharacteristic characteristic2 = service.getCharacteristic(UUID.fromString(
                "6326edac-53ec-4e94-916d-6d9702abb4de"));
        setNotify(characteristic2);
        setEnableNotify(characteristic1, true);
    }



    // 发送数据
    public boolean sendData(BluetoothGattCharacteristic data_char) {
        if (!mBluetoothAdapter.isEnabled()) {
            // 没有打开蓝牙
            return false;
        }

        if (data_char == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }

        if (mBluetoothGatt != null) {
            mBluetoothGatt.setCharacteristicNotification(data_char, true);
            mBluetoothGatt.writeCharacteristic(data_char);
        } else {
            return false;
        }

        try { // 发送数据一定要有一些延迟
            Thread.sleep(DELAY_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 读属性值
     */
    public boolean readDescriptor(BluetoothGattDescriptor descriptor, int milsec) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (descriptor == null) {
            return false;
        }

        if (!isConnected()) {
            return false;
        }

        initTimeFlag(WORK_onDescriptorRead);

        mBluetoothGatt.readDescriptor(descriptor);

        if (startTimeOut(milsec)) {
            return false;
        }

        try { // 发送数据一定要有一些延迟
            Thread.sleep(DELAY_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * 读取
     */
    private int rssi_value;

    public int getRssi(int milsec) {
        // 没有打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            return 0;
        }
        initTimeFlag(WORK_onReadRemoteRssi);

        mBluetoothGatt.readRemoteRssi();

        if (startTimeOut(milsec)) {
            return 0;
        }

        return rssi_value;
    }

    // 回调方法
    private BleConnectionCallBack callback;

    // 设置回调
    public void setCallback(BleConnectionCallBack callback) {
        this.callback = callback;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    // 初始化定时变量
    private int work_witch = 0;
    private final int WORK_onConnectionStateChange = 1;
    private final int WORK_onServicesDiscovered = 2;
    private final int WORK_onCharacteristicRead = 4;
    private final int WORK_onCharacteristicChanged = 5;
    private final int WORK_onCharacteristicWrite = 6;
    private final int WORK_onDescriptorWrite = 7;
    private final int WORK_onReadRemoteRssi = 8;
    private final int WORK_onDescriptorRead = 9;

    private void initTimeFlag(int work_index) {
        work_witch = work_index;
        timeout_flag = false;
        work_ok_flag = false;
    }

    // 开始计时
    private boolean startTimeOut(int minsec) {
        handl.sendEmptyMessageDelayed(HANDLE_TIMEOUT, minsec);
        while (!work_ok_flag) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (exit_flag) {
                return true;
            }
            if (timeout_flag) {
                return true;
            }
        }
        handl.removeMessages(HANDLE_TIMEOUT);

        return false;
    }

    // 强制退出
    private boolean exit_flag = false;

    public void exit() {
        disConnect();
        handl.removeMessages(HANDLE_TIMEOUT);
        exit_flag = true;
    }

    // 事件处理
    private static final int HANDLE_TIMEOUT = 0;
    private boolean timeout_flag = false;
    private boolean work_ok_flag = false;
    private Handler handl = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == HANDLE_TIMEOUT) {
                Log.e(TAG, "超时");
                timeout_flag = true;
                return;
            }

        }
    };

}
