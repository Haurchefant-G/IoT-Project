package com.example.app.ui.device2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Device2 extends Fragment implements View.OnClickListener {

    private Device2ViewModel device2ViewModel;


    Socket socket = null;
    String serverip = null;
    String buffer = "";
    TextView txt1;
    Button send;
    EditText ed1;
    String geted1;

    Boolean receive = false;

    boolean connected = false;
    EditText serveriptext, tb1text, tb2text;
    TextView socketstatus;
    Button connectbutton;

    public Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0x11) {
                connected = true;
                connectbutton.setEnabled(false);
                socketstatus.setText("已连接，等待接收声音信号");
            } else if (msg.what == 0x12) {
                socketstatus.setText("连接失败！请重新连接");
            }
        }

    };


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        device2ViewModel =
                ViewModelProviders.of(this).get(Device2ViewModel.class);
        View root = inflater.inflate(R.layout.device2_fragment, container, false);
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        serveriptext = getActivity().findViewById(R.id.serveriptext);
        tb1text = getActivity().findViewById(R.id.tb1text);
        tb2text = getActivity().findViewById(R.id.tb2text);
        socketstatus = getActivity().findViewById(R.id.socketstatus);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.connectbutton:
                serverip = serveriptext.getText().toString();
                new Thread() {
                    public void run() {
                        Message msg = new Message();
                        msg.what = 0x11;
                        Bundle bundle = new Bundle();
                        bundle.clear();
                        try {
                            //连接服务器 并设置连接超时为1秒
                            if (serverip.length() > 0)
                            {
                                socket = new Socket();
                                socket.connect(new InetSocketAddress(serverip, 30000), 1000); //端口号为30000
                                myHandler.sendMessage(msg);
                            }
                        } catch (SocketTimeoutException aa) {
                            msg.what = 0x12;
                            myHandler.sendMessage(msg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    };
                }.start();
                break;
        }
    }

    class SendResult extends Thread {

        public String t1, t2;

        public OutputStream ou;

        public SendResult(String time1, String time2) {
            t1 = time1;
            t2 = time2;
        }

        @Override
        public void run() {
            //定义消息
            Message msg = new Message();
            msg.what = 0x13;
            Bundle bundle = new Bundle();
            bundle.clear();

            if (socket != null)
            {

            }
            try {
                //连接服务器 并设置连接超时为1秒
                socket = new Socket();
                socket.connect(new InetSocketAddress("192.168.1.104", 30000), 1000); //端口号为30000
                //获取输入输出流
                OutputStream ou = socket.getOutputStream();
                BufferedReader bff = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                //读取发来服务器信息
                String line = null;
                buffer="";
                while ((line = bff.readLine()) != null) {
                    buffer = line + buffer;
                }

                //向服务器发送信息
                //ou.write(txt1.getBytes("gbk"));
                ou.flush();
                bundle.putString("msg", buffer.toString());
                msg.setData(bundle);
                //发送消息 修改UI线程中的组件
                myHandler.sendMessage(msg);
                //关闭各种输入输出流
                bff.close();
                ou.close();
                socket.close();
            } catch (SocketTimeoutException aa) {
                //连接超时 在UI界面显示消息
                bundle.putString("msg", "服务器连接失败！请检查网络是否打开");
                msg.setData(bundle);
                //发送消息 修改UI线程中的组件
                myHandler.sendMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
