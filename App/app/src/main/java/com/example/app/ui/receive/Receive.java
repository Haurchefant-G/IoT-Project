package com.example.app.ui.receive;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;
import com.example.app.Utils.WaveFileReader;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;

import static org.apache.commons.math3.util.FastMath.max;

public class Receive extends Fragment implements View.OnClickListener {

    private ReceiveViewModel receiveViewModel;

    private Toast mToast;

    Button start, stop, decodeButton, play;
    TextView receiveText, result;

    String textResult = "";

    private static final int f0=6400;//信号0为4500Hz
    private static final int f1=9600;//信号1为4750Hz
    private static final int length = 1024;//一个傅里叶变换时间窗口1024
    private static final int rate = 48000;//采样率48000

    private static final int step = 64;//滑动窗口为64个采样点

    //格式：双声道
    int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    //16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    // 是否在录制
    boolean isRecording = false;
    // 每次从audiorecord输入流中获取到的buffer的大小
    int bufferSize = 0;

    public void showToast(CharSequence text) {
        if(mToast != null) {
            mToast.cancel();
            mToast = null;
        }
        mToast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        receiveViewModel =
                ViewModelProviders.of(this).get(ReceiveViewModel.class);
        View root = inflater.inflate(R.layout.receive_fragment, container, false);
//        final TextView textView = root.findViewById(R.id.text_receive);
//        receiveViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
//            @Override
//            public void onChanged(@Nullable String s) {
//                textView.setText(s);
//            }
//        });
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        start = getActivity().findViewById(R.id.startReceive);
        stop = getActivity().findViewById(R.id.stopReceive);
        decodeButton = getActivity().findViewById(R.id.decodeButton);
        play = getActivity().findViewById(R.id.playRecord);
        receiveText = getActivity().findViewById(R.id.receiveText);
        result = getActivity().findViewById(R.id.decodeText);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        decodeButton.setOnClickListener(this);
        play.setOnClickListener(this);
    }

    public void decode(String fileName)
    {
        StringBuilder finalRes = new StringBuilder("");
        try {
            WaveFileReader reader = new WaveFileReader(fileName);
            int[] data = reader.getData()[0];
            //傅里叶变换找到两个频率的强度
            int index_0 = (int)((double)f0 / rate * length);
            int index_1 = (int)((double)f1 / rate * length);
            int result_length = (data.length - length) / step + 1;
            double [][]fftResult = new double[result_length][2];
            for(int i = 0; i < result_length; i++){
                double[] inputData = new double[length];
                for (int j = 0; j < length; j++){
                    inputData[j] = (double)data[step * i + j] / 32767;
                }
                FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
                Complex[] result = fft.transform(inputData, TransformType.FORWARD);
                fftResult[i][0] = max(max(result[index_0 - 1].abs(), result[index_0].abs()), result[index_0 + 1].abs());
                fftResult[i][1] = max(max(result[index_1 - 1].abs(), result[index_1].abs()), result[index_1 + 1].abs());
            }
            //寻找起始点
            int start_length=0;
            double base=10.0;
            for(int i=0;i<fftResult.length;i++){
                if(fftResult[i][0]<base&&fftResult[i][1]<base){
                    start_length++;
                }
                else{
                    break;
                }
            }

            //从起始点开始解码音频文件
            int blocklength = 2880 / step;
            StringBuilder list = new StringBuilder("");
            for(int i=start_length;i<fftResult.length;i+=blocklength){
                int zeros=0;
                int time0=0;
                int time1=0;
                for(int j=i;j<i+blocklength;j++) {
                    if (fftResult[i][0] < base && fftResult[i][1] < base) {
                        zeros+=1;
                    }
                    else {
                        if (fftResult[j][0]>base&&fftResult[j][0]>fftResult[j][1]){
                            time0++;
                        }
                        else if(fftResult[j][1]>base&&fftResult[j][0]<fftResult[j][1]){
                            time1++;
                        }
                    }
                }
                if(zeros>blocklength/3){
                    break;
                }
                if(time0>time1){
                    list.append("0");
                }
                else{
                    list.append("1");
                }
            }

            int idx = 0;
            String msg_recv = list.toString();
            int listSize = msg_recv.length();
            String preamble = "10101010";
            while(idx < listSize)
            {
                idx = msg_recv.indexOf(preamble, idx);
                if (idx == -1)
                    break;
                idx += 8;
                //TODO: 检查大小端问题
                String s_seq_num = msg_recv.substring(idx, idx + 8);
                int seq_num = Integer.parseInt(s_seq_num, 2);
                idx += 8;
                String s_pkg_len = msg_recv.substring(idx, idx + 10);
                int pkg_len = Integer.parseInt(s_pkg_len, 2) + 1;
                idx += 10;

                byte[] bytes = new byte[pkg_len / 8];
                for (int i = 0; i < pkg_len/8; i++)
                {
                    bytes[i] = 0;
                    for (int j = 0; j < 8; j++)
                    {
                        bytes[i] += (byte)((int)(msg_recv.charAt(idx)-'0') << (7-j));
                        idx++;
                    }
                }
                String frag = new String(bytes);
                finalRes.append(frag);
            }
            textResult = finalRes.toString();

        }
        catch(NullPointerException e){
            Toast t = Toast.makeText(this.getContext(),"Error: Cannot get the path.", Toast.LENGTH_LONG);
            t.show();
        }
    }

    //开始录音
    public void StartRecord(String name) {

        //生成原始数据文件
        File file = new File(name);
        //如果文件已经存在，就先删除再创建
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            //文件输出流
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            //获取在当前采样和信道参数下，每次读取到的数据buffer的大小
            bufferSize = AudioRecord.getMinBufferSize(rate, channelConfiguration, audioEncoding);
            //建立audioRecord实例
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfiguration, audioEncoding, bufferSize);

            //设置用来承接从audiorecord实例中获取的原始数据的数组
            byte[] buffer = new byte[bufferSize];
            //启动audioRecord
            audioRecord.startRecording();
            //设置正在录音的参数isRecording为true
            isRecording = true;
            //只要isRecording为true就一直从audioRecord读出数据，并写入文件输出流。
            //当停止按钮被按下，isRecording会变为false，循环停止
            while (isRecording) {
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                for (int i = 0; i < bufferReadResult; i++) {
                    dos.write(buffer[i]);
                }
            }
            //停止audioRecord，关闭输出流
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            Log.e("MainActivity", "录音失败");
        }
    }

    public void onStartRecordClick() {
        // 判断是否有录制音频并保存文件所需的相关权限，如果没有则动态申请相应权限
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)!=
                PackageManager.PERMISSION_GRANTED||
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)!=
                        PackageManager.PERMISSION_GRANTED||
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)!=
                        PackageManager.PERMISSION_GRANTED
        )
        {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
        stop.setEnabled(true);
        start.setEnabled(false);
        decodeButton.setEnabled(false);
        play.setEnabled(false);
        // toast提示开始相应录制
        showToast("开始录制，采样频率为" + rate + "Hz");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //设置用于临时保存录音原始数据的文件的名字
                //String name = Environment.getExternalStorageDirectory().getAbsolutePath()+"/myrecorder/raw.wav";
                String name = getContext().getExternalFilesDir("")+"/raw.wav";
                //调用开始录音函数，并把原始数据保存在指定的文件中
                StartRecord(name);
                //获取此刻的时间
                Date now = Calendar.getInstance().getTime();
                //用此刻时间为最终的录音wav文件命名
                String filepath = getContext().getExternalFilesDir("")+"/"+"receive.wav";
                //把录到的原始数据写入到wav文件中。
                copyWaveFile(name, filepath);
                // toast提示结束相应录制
                Looper.prepare();
                showToast("结束录制，保存为" + filepath);
                Looper.loop();
            }
        });
        //开启线程
        thread.start();
    }

    public void onStopRecordClick() {
        //停止录音
        isRecording = false;
        //恢复开始录音按钮，并禁用停止录音按钮
        stop.setEnabled(false);
        start.setEnabled(true);
        decodeButton.setEnabled(true);
        play.setEnabled(true);
    }

    private void copyWaveFile(String inFileName, String outFileName)
    {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        //wav文件比原始数据文件多出了44个字节，除去表头和文件大小的8个字节剩余文件长度比原始数据多36个字节
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = rate;
        int channels = 2;
        //每分钟录到的数据的字节数
        long byteRate = 16 * rate * channels / 8;

        byte[] data = new byte[bufferSize];
        try
        {
            in = new FileInputStream(inFileName);
            out = new FileOutputStream(outFileName);
            //获取真实的原始数据长度
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            //为wav文件写文件头
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            //把原始数据写入到wav文件中。
            while(in.read(data) != -1)
            {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void playWav(){
        //设置保存数据的文件名
        String name = getContext().getExternalFilesDir("")+"/"+"receive.wav";
        //调用生成声音函数
        MediaPlayer mMediaPlayer=new MediaPlayer();
        try{
            mMediaPlayer.setDataSource(name) ;
            mMediaPlayer.prepareAsync();

            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {
                    // 装载完毕回调
                    mp.start();
                }
            });
        }
        catch(IOException err){
            showToast("Error: File not exist, please encode again.");
        }
        catch(IllegalStateException err){
            showToast("Error: File not exist, please encode again.");
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // WAV type format = 1
        header[21] = 0;
        header[22] = (byte) channels; //指示是单声道还是双声道
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff); //采样频率
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff); //每分钟录到的字节数
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff); //真实数据的长度
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        //把header写入wav文件
        out.write(header, 0, 44);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.startReceive:
                onStartRecordClick();
                break;
            case R.id.stopReceive:
                onStopRecordClick();
                break;
            case R.id.decodeButton:
                decode(getContext().getExternalFilesDir("")+"/"+"receive.wav");
                showToast("decode完成");
                result.setText(textResult);
                //pausePlayer(v);
                break;
            case R.id.playRecord:
                playWav();
                break;
            default:
                break;
        }
    }
}
