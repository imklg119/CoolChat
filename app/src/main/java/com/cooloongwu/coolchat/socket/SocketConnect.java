package com.cooloongwu.coolchat.socket;

import android.util.Log;

import java.util.Vector;

/**
 * 感谢 Mr_Sanders ，https://github.com/SSOOnline/android-network
 * 连接服务器的线程类
 * Created by CooLoongWu on 2016-8-8 14:37.
 */
public class SocketConnect implements Runnable {

    private boolean isConnect = false;// 是否连接服务器
    private boolean isWrite = false;// 是否发送数据
    private static final Vector<byte[]> data = new Vector<>();// 待发送数据队列
    private SocketBase mSocket;// socket连接
    private WriteRunnable writeRunnable;// 发送数据线程
    private String ip = null;
    private int port = -1;

    /**
     * 创建连接
     *
     * @param callback 回调接口
     */
    public SocketConnect(SocketCallback callback) {
        if (mSocket == null) {
            mSocket = new SocketBase(callback);// 创建socket连接
        }
        if (writeRunnable == null) {
            writeRunnable = new WriteRunnable();// 创建发送线程
        }
    }

    @Override
    public void run() {
        if (ip == null || port == -1) {
            throw new NullPointerException("not set address ！");
        }
        isConnect = true;
        while (isConnect) {
            synchronized (this) {
                try {
                    mSocket.connect(ip, port);// 连接服务器
                } catch (Exception e) {
                    try {
                        mSocket.disconnect();// 断开连接
                        this.wait(5000);
                        continue;
                    } catch (InterruptedException e1) {
                        continue;
                    }
                }
            }
            isWrite = true;// 设置可发送数据
            new Thread(writeRunnable).start();// 启动发送线程
            try {
                mSocket.read();// 获取数据
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Socket", "数据读取错误-" + e.toString());
            } finally {
                //重连
//                disconnect();
                writeRunnable.stop();
                mSocket.disconnect();
            }
        }
    }

    /**
     * 关闭服务器连接
     */
    private synchronized void disconnect() {
        isConnect = false;
        this.notify();
        resetConnect();
    }

    /**
     * 重置连接
     */
    private void resetConnect() {
        writeRunnable.stop();// 发送停止信息
        mSocket.disconnect();
    }

    /**
     * 向发送线程写入发送数据
     */
    public void write(byte[] buffer) {
        writeRunnable.write(buffer);
    }

    /**
     * 设置IP和端口
     *
     * @param host 服务器地址
     * @param port 服务器端口
     */
    public void setRemoteAddress(String host, int port) {
        this.ip = host;
        this.port = port;
    }

    /**
     * 发送数据
     */
    private boolean writes(byte[] buffer) {
        try {
            mSocket.write(buffer);
            return true;
        } catch (Exception e) {
            resetConnect();
        }
        return false;
    }

    /**
     * 发送线程
     *
     * @author Esa
     */
    private class WriteRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(">TCP发送线程开启<", "" + writeRunnable.hashCode());
            while (isWrite) {
                synchronized (this) {
                    if (data.size() <= 0) {
                        try {
                            this.wait();// 等待发送数据
                        } catch (InterruptedException e) {
                            continue;
                        }
                    }
                    while (data.size() > 0) {
                        byte[] buffer = data.remove(0);// 获取一条发送数据
                        if (isWrite) {
                            writes(buffer);// 发送数据
                        } else {
                            this.notify();
                        }
                    }
                }
            }
            Log.e(">TCP发送线程结束<", "" + writeRunnable.hashCode());
        }

        /**
         * 添加数据到发送队列
         *
         * @param buffer 数据字节
         */
        synchronized void write(byte[] buffer) {
            data.add(buffer);// 将发送数据添加到发送队列
            this.notify();// 取消等待
        }

        synchronized void stop() {
            isWrite = false;
            this.notify();
        }
    }
}
