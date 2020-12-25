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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Device2 extends Fragment implements View.OnClickListener {

    private Device2ViewModel device2ViewModel;


    Socket socket = null;
    String serverip = null;

    boolean connected = false;
    EditText serveriptext, tb1text, tb2text;
    TextView socketstatus;
    Button connectbutton;

    long tb1, tb3;

    public Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0x11) {
                connected = true;
                connectbutton.setEnabled(false);
                socketstatus.setText("已连接，等待接收声音信号");
            } else if (msg.what == 0x12) {
                connected = false;
                connectbutton.setEnabled(true);
                socketstatus.setText("连接失败！请重新连接");
                socket = null;
            } else if (msg.what == 0x13) {
                connected = false;
                connectbutton.setEnabled(true);
                socketstatus.setText("未连接");
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
        tb2text = getActivity().findViewById(R.id.tb3text);
        socketstatus = getActivity().findViewById(R.id.socketstatus);
        connectbutton = getActivity().findViewById(R.id.connectbutton);
        connectbutton.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (socket != null)
        {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

                                new SendResult(1234).start();
                            }
                        } catch (SocketTimeoutException aa) {
                            msg.what = 0x12;
                            myHandler.sendMessage(msg);
                        } catch (IOException e) {
                            msg.what = 0x12;
                            myHandler.sendMessage(msg);
                            e.printStackTrace();
                        }
                    };
                }.start();
                break;
        }
    }

    class SendResult extends Thread {

        public long tb;

        public SendResult(long t) {
            tb = t;
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
                try {
                    //获取输入输出流
                    Thread.sleep(5000);


                    OutputStream out = socket.getOutputStream();
                    OutputStreamWriter osr = new OutputStreamWriter(out); // 输出
                    BufferedWriter bufw = new BufferedWriter(osr); // 缓冲

                    bufw.write(String.valueOf(tb));
                    bufw.flush();
                    bufw.close();
                    socket.close();

                    myHandler.sendMessage(msg);
                } catch (InterruptedException e) {
                        e.printStackTrace();
                } catch (SocketTimeoutException aa) {
                    msg.what = 0x12;
                    myHandler.sendMessage(msg);
                } catch (IOException e) {
                    msg.what = 0x12;
                    myHandler.sendMessage(msg);
                    e.printStackTrace();
                }
            }
        }
    }

}
