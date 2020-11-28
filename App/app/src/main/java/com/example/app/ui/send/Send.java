package com.example.app.ui.send;

import androidx.lifecycle.ViewModelProviders;

import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static java.lang.Math.cos;

public class Send extends Fragment {

    private SendViewModel mViewModel;

    private static final int SamplingRate = 48000;//采样率
    private static final int f0=6400;//频率0位4500Hz
    private static final int f1=9600;//频率0位4750Hz
    private static final int block_size=2880;//一个块2880采样点
    private static final int max_length=1024;//包长度
    private static final int interval_length=4;//包间隔
    private static final int header_size = 8 + 4 + 10; //包头长度

    public static Send newInstance() {
        return new Send();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.send_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(SendViewModel.class);
        // TODO: Use the ViewModel
    }

    //编码与分包
    public void encode(String message){
        Charset charset_utf8 = Charset.forName("ascii");
        ByteBuffer buff = charset_utf8.encode(message);
        byte[] bArr = new byte[buff.remaining()];
        buff.get(bArr);

        int message_length = 8*bArr.length;
        int package_num = message_length/max_length;
        if (package_num*max_length != message_length) {
            package_num++;
        }

        int array_size = package_num * (header_size + interval_length) + 8 * bArr.length;
        int[] encode_bits = new int[array_size];
        int cur = 0;

        for (int i=0; i<= package_num; i++){
            //创建间隔
            for (int j=0; j<interval_length; j++){
                encode_bits[cur++] = -1;
            }
            //创建表头
            
        }


        for(int i=0;i<bArr.length;i++) {
            int code=(int)bArr[i];
            for(int j=0;j<8;j++){
                int bit=code&1;
                encode_bits[i*8+7-j]=bit;
                code=code>>1;
            }
        }
        //设置保存数据的文件名
        String name = Environment.getExternalStorageDirectory().getAbsolutePath()+"/AudioProject/encoding/message.wav";



        //调用生成声音函数
        GenerateAudio(name,encode_bits);

        Toast t = Toast.makeText(this.getContext(),"Encoding finished, press PLAY to play.", Toast.LENGTH_LONG);
        t.show();
    }

    //开始生成编码声音
    public void GenerateAudio(String name,int[] encode_message) {
        //生成原始数据文件
        File file = new File(name);
        //如果文件已经存在，就先删除再创建
        if (file.exists())
            file.delete();
        try {
            //先创建文件夹
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            FileOutputStream out = null;
            out = new FileOutputStream(name);
            long totalAudioLen = encode_message.length*2880*2;
            long totalDataLen = totalAudioLen + 36;
            long longSampleRate = SamplingRate;
            int channels=1;
            long byteRate = 16 * SamplingRate * channels / 8;
            //写文件头部
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            int rate = SamplingRate;//采样率48000
            byte[] chunk = new byte[block_size*2];
            double am = 32767.0;
            //将信号数据写入wav文件
            for (int i=0; i<encode_message.length; i++) {
                if(encode_message[i]==0){
                    for(int j=0;j<block_size;j++){

                        double fun=am*cos(2*Math.PI*(double)f0*(double)j/(double)rate);
                        int data=(int)Math.round(fun);
                        chunk[j*2]=(byte)(data&0xff);
                        chunk[j*2+1]=(byte)((data>>8)&0xff);
                    }
                    out.write(chunk, 0, block_size*2);
                }
                else if (encode_message[i]==1){
                    for(int j=0;j<block_size;j++){

                        double fun=am*cos(2*Math.PI*(double)f1*(double)j/(double)rate);
                        int data=(int)Math.round(fun);
                        chunk[j*2]=(byte)(data&0xff);
                        chunk[j*2+1]=(byte)((data>>8)&0xff);
                    }
                    out.write(chunk, 0, block_size*2);
                }
                else if (encode_message[i]==-1){
                    for(int j=0;j<block_size;j++){
                        int data=0;
                        chunk[j*2]=(byte)(data&0xff);
                        chunk[j*2+1]=(byte)((data>>8)&0xff);
                    }
                    out.write(chunk, 0, block_size*2);
                }
            }
            out.close();
        }
        catch (Throwable t) {
            Log.e("MainActivity", "创建音频失败");
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
        header[32] = (byte) (channels * 16 / 8); // block align
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

    private void playWav(){
        //设置保存数据的文件名
        String name = Environment.getExternalStorageDirectory().getAbsolutePath()+"/AudioProject/encoding/message.wav";
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
            Toast t = Toast.makeText(this.getContext(),"Error: File not exist, please encode again.", Toast.LENGTH_LONG);
            t.show();
        }
        catch(IllegalStateException err){
            Toast t = Toast.makeText(this.getContext(),"Error: File not exist, please encode again.", Toast.LENGTH_LONG);
            t.show();
        }
    }

}