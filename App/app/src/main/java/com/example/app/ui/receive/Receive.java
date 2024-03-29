package com.example.app.ui.receive;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignFisher;

import static org.apache.commons.math3.util.FastMath.max;
import static org.apache.commons.math3.util.FastMath.min;

public class Receive extends Fragment implements View.OnClickListener {

    private ReceiveViewModel receiveViewModel;

    private Toast mToast;

    Button start, stop, decodeButton, decode2Button, play;
    TextView receiveText, result;

    String textResult = "";

    private static final int f0=4000;//信号0为4000Hz
    private static final int f1=5000;//信号1为5000Hz
    private static final int length = 1024;//一个傅里叶变换时间窗口1024
    private static final int rate = 48000;//采样率48000

    private static final int step = 40;//滑动窗口为40个采样点
    private static final int stepT = 40;//测试滑动窗口为32个采样点

    // 现场测试相关
    int test_f1 = 4000;
    int test_f2 = 6000;
    int sampleRate = 48000;
    double symbolDuration = 0.025;
    int blocksize = 2400;

    List<Integer> onset;



    //格式：双声道
    int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    //16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    // 是否在录制
    boolean isRecording = false;
    // 每次从audiorecord输入流中获取到的buffer的大小
    int bufferSize = 0;

    Handler handle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            start.setEnabled(true);
            play.setEnabled(true);
            decodeButton.setEnabled(true);
            result.setText(textResult);
        }

    };

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
        decode2Button = getActivity().findViewById(R.id.decodeButton2);
        play = getActivity().findViewById(R.id.playRecord);
        receiveText = getActivity().findViewById(R.id.receiveText);
        result = getActivity().findViewById(R.id.decodeText);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        decodeButton.setOnClickListener(this);
        decode2Button.setOnClickListener(this);
        //decodeButton.setEnabled(true);
        play.setOnClickListener(this);
    }

    public void decode(String fileName)
    {
        StringBuilder finalRes = new StringBuilder("");
        try {
            //WaveFileReader reader = new WaveFileReader(getContext().getExternalFilesDir("")+"/"+"receive.wav");
            WaveFileReader reader = new WaveFileReader(fileName);
            int[] dataR = reader.getData()[0];
            double[] data = new double[dataR.length];

            IirFilterCoefficients iirFilterCoefficients1;
            iirFilterCoefficients1 = IirFilterDesignFisher.design(FilterPassType.bandpass, FilterCharacteristicsType.butterworth, 5, 0,
                    3500.0 / 48000, 5500.0 / 48000);
            IirFilter filter1 = new IirFilter(iirFilterCoefficients1);

//                IirFilterCoefficients iirFilterCoefficients2;
//                iirFilterCoefficients2 = IirFilterDesignFisher.design(FilterPassType.bandpass, FilterCharacteristicsType.butterworth, 5, 0,
//                        5700.0 / 48000, 6300.0 / 48000);
//                IirFilter filter2 = new IirFilter(iirFilterCoefficients2);

            for (int i = 0; i<dataR.length; i++) {
                //data[i] = max(filter1.step(data[i]), filter2.step(data[i]));
                data[i] = filter1.step(dataR[i]);
                //data[i] = dataR[i];
            }

            
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
            double base = 2.0, base1 = 0, base2 = 0;

            double max0 = 0.0, max1 = 0.0; //辅助设置阈值
            for(int i=0;i<fftResult.length;i++){
                if (max0 < fftResult[i][0]) {
                    max0 = fftResult[i][0];
                }
                if (max1 < fftResult[i][1]) {
                    max1 = fftResult[i][1];
                }
            }
            base1 = max0 * 0.12;
            base2 = max1 * 0.12;


            for(int i=0;i<fftResult.length;i++){
                if(fftResult[i][0]<base1&&fftResult[i][1]<base2){
                    start_length++;
                }
                else{
                    break;
                }
            }

            Log.i("Info", "base1: " + base1 + ", " + "base2: " + base2 + " startIndex: " + start_length + " fftresult length: " + fftResult.length);

            //从起始点开始解码音频文件
            int blocklength = blocksize / step;
            StringBuilder list = new StringBuilder("");
            for(int i=start_length;i<fftResult.length;i+=blocklength){
                int zeros=0;
                int time0=0;
                int time1=0;
                for(int j=i;j<i+blocklength;j++) {
                    if (j >= fftResult.length) {
                        break;
                    }
                    if (fftResult[i][0] < base1 && fftResult[i][1] < base2) {
                        zeros+=1;
                    }
                    else {
                        if (fftResult[j][0] > base1 && fftResult[j][0] > fftResult[j][1]){ //
                            time0++;
                        }
                        else if(fftResult[j][1] > base2 && fftResult[j][0] < fftResult[j][1]){ //
                            time1++;
                        }
                    }
                }

                if(zeros > blocklength / 2){
                    break;
                }
                if(time0 > time1){
                    list.append("0");
                }
                else{
                    list.append("1");
                }
            }

            int idx = 0;
            String msg_recv = list.toString();
            Log.i("Info", "msg: " + msg_recv.length());

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
                Log.i("Info", "payload len: " + (pkg_len - 1));
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
            //Toast t = Toast.makeText(this.getContext(),"Error: Cannot get the path.", Toast.LENGTH_LONG);
            //t.show();
            Looper.prepare();
            showToast("Error: Cannot get the path.");
            Looper.loop();
        }
    }

    //测试用函数
    public void decode2(String fileName)
    {
        readContentCsv();

        Log.i("Info", "start decode");

        String CSV_FILE_PATH = getContext().getExternalFilesDir("")+"/"+"res.csv";
        CsvWriter csvWriter = new CsvWriter(CSV_FILE_PATH,',', Charset.forName("GBK"));
        StringBuilder finalRes = new StringBuilder("");
        try {
            //WaveFileReader reader = new WaveFileReader(getContext().getExternalFilesDir("")+"/"+"receive.wav");
            WaveFileReader reader = new WaveFileReader(fileName);
            int[] dataR = reader.getData()[0];

            //int[] startRef = {2173721, 2517984, 4075113};

            
            Integer[] startRef = onset.toArray(new Integer[0]);
            int seq_num = 0;

            for (int onsetIndex = 0; onsetIndex < startRef.length; onsetIndex++){
                double[] data;
                if (startRef.length != onsetIndex+1)
                    data = new double[startRef[onsetIndex+1]-startRef[onsetIndex]+1];
                else
                    data = new double[dataR.length-startRef[onsetIndex]+1];

                for (int i = 0; i < data.length; i++){
                    int deltaIndex = startRef[onsetIndex]-1 + i;
                    if (deltaIndex >= dataR.length) break;
                    data[i] = dataR[deltaIndex];
                }


                //傅里叶变换找到两个频率的强度
                int index_0 = (int)((double)4000 / rate * length);
                int index_1 = (int)((double)6000 / rate * length);

                // todo
                int result_length = (data.length - length) / stepT + 1;
                //int result_length = (data.length / 8 - length) / stepT + 1;

                IirFilterCoefficients iirFilterCoefficients1;
                iirFilterCoefficients1 = IirFilterDesignFisher.design(FilterPassType.bandpass, FilterCharacteristicsType.butterworth, 5, 0,
                        3700.0 / 48000, 6300.0 / 48000);
                IirFilter filter1 = new IirFilter(iirFilterCoefficients1);

//                IirFilterCoefficients iirFilterCoefficients2;
//                iirFilterCoefficients2 = IirFilterDesignFisher.design(FilterPassType.bandpass, FilterCharacteristicsType.butterworth, 5, 0,
//                        5700.0 / 48000, 6300.0 / 48000);
//                IirFilter filter2 = new IirFilter(iirFilterCoefficients2);

                for (int i = 0; i<data.length; i++) {
                    //data[i] = max(filter1.step(data[i]), filter2.step(data[i]));
                    data[i] = filter1.step(data[i]);
                    //data[i] = dataR[i];
                }

                double [][]fftResult = new double[result_length][2];
                for(int i = 0; i < result_length; i++){
                    double[] inputData = new double[length];
                    for (int j = 0; j < length; j++){
                        inputData[j] = (double)data[stepT * i + j] / 32767;
                    }
                    FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
                    Complex[] result = fft.transform(inputData, TransformType.FORWARD);
                    fftResult[i][0] = max(max(result[index_0 - 1].abs(), result[index_0].abs()), result[index_0 + 1].abs());
                    fftResult[i][1] = max(max(result[index_1 - 1].abs(), result[index_1].abs()), result[index_1 + 1].abs());
                }


                double max0 = 0.0, max1 = 0.0; //辅助设置阈值
                double base0 = 0.0, base1 = 0.0;
                for(int i=0;i<fftResult.length;i++){
                    if (max0 < fftResult[i][0]) {
                        max0 = fftResult[i][0];
                    }
                    if (max1 < fftResult[i][1]) {
                        max1 = fftResult[i][1];
                    }
                }
                base0 = max0 * 0.5;
                base1 = max1 * 0.5;

                //Log.i("Info", "base0: " + base0 + ", " + "base1: " + base1);

//            if (base0 < base1) base1 = base0;
//            else base0 = base1;

                //寻找起始点
                int start_length=0;

                StringBuilder list = new StringBuilder("");

                while (start_length < fftResult.length) {
                    int startIndex = start_length;
                    for (int i = startIndex; i < fftResult.length; i++) {
                        if (fftResult[i][0] < base0 && fftResult[i][1] < base1) {
                            start_length++;
                        } else {
                            break;
                        }
                    }

                    //从起始点开始解码音频文件
                    int blocklength = 1200 / stepT;

                    for (int i = start_length; i <= fftResult.length; i += blocklength) {
                        if (i == fftResult.length) {
                            start_length = i;
                            break;
                        }

                        int zeros = 0;
                        int time0 = 0;
                        int time1 = 0;
                        for (int j = i; j < i + blocklength; j++) {
                            if (j >= fftResult.length) {
                                start_length = j;
                                break;
                            }
                            if (fftResult[i][0] < base0 && fftResult[i][1] < base1) {
                                zeros += 1;
                            } else {
                                if (fftResult[j][0] > base0) { // && fftResult[j][0] > fftResult[j][1]
                                    time0++;
                                } else if (fftResult[j][1] > base1) { // && fftResult[j][0] < fftResult[j][1]
                                    time1++;
                                }
                            }
                        }
                        if (zeros > blocklength / 3) {
                            start_length = i;
                            break;
                        }
                        if (time0 > time1) {
                            list.append("0");
                        } else {
                            list.append("1");
                        }
                    }
                }

                int idx = 0;
                String msg_recv = list.toString();

                Log.i("Info", "msg: " + msg_recv);
                //Log.i("Info", "01s length: " + msg_recv.length());

                int listSize = msg_recv.length();
                String preamble = "01010101010101010101";

                if (msg_recv.indexOf(preamble, idx)==-1) {
                    String[] s_array = new String[1];
                    s_array[0] = String.valueOf(0);
                    csvWriter.writeRecord(s_array);
                }
                else while (idx < listSize) {
                    idx = msg_recv.indexOf(preamble, idx);

                    if (idx == -1)
                        break;
                    seq_num++;

                    Log.i("Info", "package " + seq_num + " start at: " + idx);

                    idx += preamble.length();
                    //TODO: 检查大小端问题

                    String s_pkg_len = msg_recv.substring(idx, idx + 8);
                    int pkg_len = Integer.parseInt(s_pkg_len, 2) + 1;
                    Log.i("Info", "package payload length: " + (pkg_len - 1));

                    idx += 8;

                    String bytes = msg_recv.substring(idx, idx + pkg_len - 1);
                    idx += pkg_len;
                    String frag = bytes;
                    finalRes.append(frag);
                    String[] s_array = new String[pkg_len + 1];
                    s_array[0] = String.valueOf(pkg_len - 1);
                    for (int i = 1; i < pkg_len; i++)
                        s_array[i] = String.valueOf(frag.charAt(i - 1));
                    csvWriter.writeRecord(s_array);

                    //Log.i("Info", "s array: " + s_array[0] + s_array[1]);

                    //TODO 测试用
                    //break;
                }
                //textResult = finalRes.toString();
            }


        }
        catch(NullPointerException | IOException e){
            //Toast t = Toast.makeText(this.getContext(),"Error: Cannot get the path.", Toast.LENGTH_LONG);
            //t.show();
            Looper.prepare();
            showToast("Error: Cannot get the path.");
            Looper.loop();
        }

        csvWriter.close();
    }

    public void readContentCsv()
    {
        onset = new ArrayList<>();
        String CSV_FILE_PATH = getContext().getExternalFilesDir("")+"/"+"content.csv";
        try {
            CsvReader reader = new CsvReader(CSV_FILE_PATH, ',');
            String record[];
            reader.readRecord();
            record = reader.getValues();
            sampleRate = Integer.parseInt(record[1]);
            Log.i("Info", record[1]);
            reader.readRecord();
            record = reader.getValues();
            symbolDuration = Double.parseDouble(record[1]);
            Log.i("Info", record[1]);
            reader.readRecord();
            record = reader.getValues();
            test_f1 = Integer.parseInt(record[1]);
            Log.i("Info", record[1]);
            reader.readRecord();
            record = reader.getValues();
            test_f2 = Integer.parseInt(record[1]);
            Log.i("Info", record[1]);
            // 忽略表头一行
            reader.readHeaders();
            String[] head = reader.getHeaders(); //获取表头
            while(reader.readRecord())
            {
                onset.add(Integer.valueOf(reader.get(3)));
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
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
                start.setEnabled(false);
                stop.setEnabled(false);
                play.setEnabled(false);
                decodeButton.setEnabled(false);
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //TODO 切换
                        decode(getContext().getExternalFilesDir("")+"/"+"receive.wav");
                        //decode(getContext().getExternalFilesDir("")+"/AudioProject/encoding/message.wav");
                        Message msg = new Message();
                        handle.sendMessage(msg);
                        Looper.prepare();
                        showToast("decode完成");
                        Looper.loop();
                    }
                });
                thread.start();
                //pausePlayer(v);
                break;
            case R.id.decodeButton2:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        decode2(getContext().getExternalFilesDir("")+"/"+"res.wav");
                        Message msg = new Message();
                        handle.sendMessage(msg);
                        Looper.prepare();
                        showToast("decode完成");
                        Looper.loop();
                    }
                }).start();
                break;
            case R.id.playRecord:
                playWav();
                break;
            default:
                break;
        }
    }
}
