package com.example.app.ui.device2;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.apache.commons.math3.util.FastMath.max;

public class Device2 extends Fragment implements View.OnClickListener {

    private Device2ViewModel device2ViewModel;


    Socket socket = null;
    String serverip = null;

    boolean connected = false;
    EditText serveriptext, tb1text, tb3text;
    TextView socketstatus;
    Button connectbutton;

    private static final int rate = 48000;//采样率48000'
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    int bufferSize = 0;
    private static final int blockSize = 256;
    long tb1, tb3;
    int freqOfTone = 4000;

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0x11) {
                connected = true;
                connectbutton.setEnabled(false);
                socketstatus.setText("已连接，等待接收声音信号");
                startRecord();
            } else if (msg.what == 0x12) {
                connected = false;
                connectbutton.setEnabled(true);
                socketstatus.setText("连接失败！请重新连接");
                socket = null;
            } else if (msg.what == 0x13) {
                connected = false;
                connectbutton.setEnabled(true);
                socketstatus.setText("已发送结果，断开连接");
            } else if (msg.what == 0x14) {
                tb1text.setText(String.valueOf(tb1));
                new beepPlayThread().start();
            } else if (msg.what == 0x15) {
                tb3text.setText(String.valueOf(tb3));

                new SendResult(tb3 - tb1).start();
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
        tb3text = getActivity().findViewById(R.id.tb3text);
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
                                mHandler.sendMessage(msg);
                            }
                        } catch (SocketTimeoutException aa) {
                            msg.what = 0x12;
                            mHandler.sendMessage(msg);
                        } catch (IOException e) {
                            msg.what = 0x12;
                            mHandler.sendMessage(msg);
                            e.printStackTrace();
                        }
                    };
                }.start();
                break;
        }
    }

    void startRecord() {
        new Thread() {
            public void run() {
                bufferSize = AudioRecord.getMinBufferSize(rate, channelConfiguration, audioEncoding);
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfiguration, audioEncoding, bufferSize);
                byte[] buffer = new byte[blockSize];
                double[] buffer_d = new double[blockSize];
                audioRecord.startRecording();
                // 开始进行第一次声音接收
                int beepnum = 0;
                while (beepnum < 2)
                {
                    int bufferReadResult = audioRecord.read(buffer, 0, blockSize);
                    int max0 = 0;
                    for (int i = 0; i < bufferReadResult && i < blockSize; i++)
                        //buffer_d[i] = (double)buffer[i] / 32768.0;
                        max0 = max(max0, buffer[i]);
//                    int index = (int)((double)freqOfTone / rate * blockSize);
//                    double fftResult;
//                    FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
//                    Complex[] result = fft.transform(buffer_d, TransformType.FORWARD);
//                    double mean = 0;
//                    double max = 0;
//                    int idx = 0;
//                    for (int i = 0; i < result.length; i++)
//                    {
//                        double k = result[i].abs();
//                        mean += k;
//                        if (k > max)
//                        {
//                            max = k;
//                            idx = i;
//                        }
//
//                    }
//                    mean /= result.length;
//                    fftResult = max(max(result[index - 1].abs(), result[index].abs()), result[index + 1].abs());
                    //if (fftResult > 2 * mean)

                    Log.i("startcalc", "max num: " + max0);
                    if (max0 > 120)
                    {

//                        Log.i("startcalc", "fftRes:" + fftResult);
//                        Log.i("startcalc", "mean:" + mean);
//                        Log.i("startcalc", "max:" + max);
//                        Log.i("startcalc", "max idx:" + idx);
                        Log.i("startcalc", "Target Sound Detected");
                        if (beepnum == 0)
                        {
                            tb1 = System.nanoTime();
                            Message msg = new Message();
                            msg.what = 0x14;
                            mHandler.sendMessage(msg);
                            ++beepnum;
                        } else if (beepnum == 1) {
                            tb3 = System.nanoTime();
                            if (tb3 - tb1 > 3000000000l) {
                                Message msg = new Message();
                                msg.what = 0x15;
                                mHandler.sendMessage(msg);
                                ++beepnum;
                            }
                        }
                    }
                }
            }
        }.start();
    }

    class beepPlayThread extends Thread{
        @Override
        public void run() {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            double duration = 0.3; // seconds
            int sampleRate = 48000;
            int numSamples = (int)(duration * sampleRate);
            double sample[] = new double[numSamples];
            byte generatedSnd[] = new byte[2 * numSamples];
            for (int i = 0; i < numSamples; ++i) {
                sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/(double)freqOfTone));
            }

            // convert to 16 bit pcm sound array
            // assumes the sample buffer is normalised.
            int idx = 0;
            for (final double dVal : sample) {
                // scale to maximum amplitude
                final short val = (short) ((dVal * 32767));
                // in 16 bit wav PCM, first byte is the low order byte
                generatedSnd[idx++] = (byte) (val & 0x00ff);
                generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

            }
            final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(generatedSnd, 0, generatedSnd.length);
            audioTrack.play();
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

                    OutputStream out = socket.getOutputStream();
                    OutputStreamWriter osr = new OutputStreamWriter(out); // 输出
                    BufferedWriter bufw = new BufferedWriter(osr); // 缓冲

                    bufw.write(String.valueOf(tb));
                    bufw.flush();
                    bufw.close();
                    socket.close();

                    mHandler.sendMessage(msg);
                } catch (SocketTimeoutException aa) {
                    msg.what = 0x12;
                    mHandler.sendMessage(msg);
                } catch (IOException e) {
                    msg.what = 0x12;
                    mHandler.sendMessage(msg);
                    e.printStackTrace();
                }
            }
        }
    }

}
