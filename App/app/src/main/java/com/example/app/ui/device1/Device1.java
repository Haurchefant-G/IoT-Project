package com.example.app.ui.device1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;
import com.example.app.ui.send.SendViewModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Device1 extends Fragment implements View.OnClickListener {

    private Device1ViewModel device1ViewModel;

    TextView iptext;

    public static ServerSocket serverSocket = null;
    private String IP = "";
    String buffer = "";
    public static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            if (msg.what==0x11) {
                Bundle bundle = msg.getData();
                // mTextView.append("client"+bundle.getString("msg")+"\n");
            }
        };
    };


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.device1_fragment, container, false);
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        iptext = getActivity().findViewById(R.id.iptext);
        iptext.setText(getlocalip());
        new Thread() {
            public void run() {
                Bundle bundle = new Bundle();
                bundle.clear();
                OutputStream output;
                String str = "hello hehe";
                try {
                    serverSocket = new ServerSocket(30000);
                    while (true) {
                        Message msg = new Message();
                        msg.what = 0x11;
                        try {
                            Socket socket = serverSocket.accept();
                            output = socket.getOutputStream();
                            output.write(str.getBytes("gbk"));
                            output.flush();
                            socket.shutdownOutput();
                            BufferedReader bff = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String line = null;
                            buffer = "";
                            while ((line = bff.readLine())!=null) {
                                buffer = line + buffer;
                            }
                            bundle.putString("msg", buffer.toString());
                            msg.setData(bundle);
                            mHandler.sendMessage(msg);
                            bff.close();
                            output.close();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            };
        }.start();
    }


    private String getlocalip(){
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_WIFI_STATE)!=
                        PackageManager.PERMISSION_GRANTED
        )
        {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 0);
        }
        WifiManager wifiManager = (WifiManager)getContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        //  Log.d(Tag, "int ip "+ipAddress);
        if(ipAddress==0)return null;
        return ((ipAddress & 0xff)+"."+(ipAddress>>8 & 0xff)+"."
                +(ipAddress>>16 & 0xff)+"."+(ipAddress>>24 & 0xff));
    }

    @Override
    public void onClick(View v) {

    }
}
