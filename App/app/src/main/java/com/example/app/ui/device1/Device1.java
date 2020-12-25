package com.example.app.ui.device1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;
import com.example.app.ui.send.SendViewModel;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import biz.source_code.dsp.transform.Dft;

import static org.apache.commons.math3.util.FastMath.max;

public class Device1 extends Fragment implements View.OnClickListener {

    private Device1ViewModel device1ViewModel;

    TextView iptext, readytext;

    EditText daatext, dbbtext, t1text, t2text, resulttext;

    int daa = 0, dbb = 0;

    long ta1, ta3;

    Button startserver, startcalc;


    public static ServerSocket serverSocket = null;
    private String IP = "";
    String buffer = "";

    private static final int rate = 48000;//采样率48000'
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    int bufferSize = 0;
    long t1 = 0;
    long t2 = 0;

    private final Thread beepPlayThread = new Thread() {
      public void run()
      {
          ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
          toneGen1.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE,500);
      }
    };


    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            if (msg.what==0x11) {
                readytext.setText("已连接，可开始测距");
                startserver.setEnabled(false);
                startcalc.setEnabled(true);
            } else if (msg.what ==0x12) {
                Bundle bundle = msg.getData();
                // mTextView.append("client"+bundle.getString("msg")+"\n");
                System.currentTimeMillis();
                resulttext.setText(bundle.getString("msg"));

                readytext.setText("未开启server");
                serverSocket = null;
                startserver.setEnabled(true);
                startcalc.setEnabled(false);
            } else if (msg.what == 0x13) {
                readytext.setText("未开启server");
                serverSocket = null;
                startserver.setEnabled(true);
                startcalc.setEnabled(false);
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
        readytext = getActivity().findViewById(R.id.ready);
        daatext = getActivity().findViewById(R.id.daatext);
        dbbtext = getActivity().findViewById(R.id.dbbtext);
        t1text = getActivity().findViewById(R.id.t1text);
        t2text = getActivity().findViewById(R.id.t2text);
        resulttext = getActivity().findViewById(R.id.dtext);

        startserver = getActivity().findViewById(R.id.startserver);
        startcalc = getActivity().findViewById(R.id.startcalc);

        startserver.setOnClickListener(this);
        startcalc.setOnClickListener(this);

        iptext.setText(getlocalip());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (serverSocket != null)
        {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
        switch (v.getId())
        {
            case R.id.startserver:
                readytext.setText("等待连接");
                new Thread() {
                    public void run() {
                        Bundle bundle = new Bundle();
                        bundle.clear();
                        try {
                            serverSocket = new ServerSocket(30000);
                            Socket socket = serverSocket.accept();
                            Message msg = new Message();
                            msg.what = 0x11;
                            mHandler.sendMessage(msg);
                            BufferedReader bff = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String line = null;
                            buffer = "";
                            while ((line = bff.readLine()) != null) {
                                buffer = line + buffer;
                            }
                            bundle.putString("msg", buffer);
                            Message msg2 = new Message();
                            msg2.what = 0x12;
                            msg2.setData(bundle);
                            mHandler.sendMessage(msg2);
                            bff.close();
                            socket.close();
                            serverSocket.close();
                        } catch (IOException e1) {
                            Message msg = new Message();
                            msg.what = 0x11;
                            msg.what = 0x13;
                            mHandler.sendMessage(msg);
                            e1.printStackTrace();
                        }
                    };
                }.start();
                break;

            case R.id.startcalc:
                new Thread() {
                    public void run() {
                        bufferSize = AudioRecord.getMinBufferSize(rate, channelConfiguration, audioEncoding);
                        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfiguration, audioEncoding, bufferSize);
                        byte[] buffer = new byte[bufferSize];
                        double[] buffer_d = new double[bufferSize];
                        audioRecord.startRecording();
                        // 开始进行第一次声音接收
                        while (true)
                        {
                            int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                            for (int i = 0; i < bufferReadResult && i < bufferSize; i++)
                                buffer_d[i] = (double)buffer[i] / 32768.0;
                            int index = (int)((double)6000 / rate * bufferReadResult);
                            double fftResult;
                            FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
                            Complex[] result = fft.transform(buffer_d, TransformType.FORWARD);
                            int mean = 0;
                            for (int i = 0; i < result.length; i++)
                            {
                                mean += result[i].abs();
                            }
                            mean /= result.length;
                            fftResult = max(max(result[index - 1].abs(), result[index].abs()), result[index + 1].abs());
                            if (fftResult > 2 * mean)
                            {
                                Log.i("startcalc", "Target Sound Detected");
                                break;
                            }
                        }
                    }
                }.start();
                break;


        }
    }
}
