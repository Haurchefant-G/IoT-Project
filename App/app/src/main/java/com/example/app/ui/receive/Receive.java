package com.example.app.ui.receive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.app.R;
import com.example.app.Utils.WaveFileReader;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;

import static org.apache.commons.math3.util.FastMath.max;

public class Receive extends Fragment {

    private ReceiveViewModel receiveViewModel;

    String text_result = "";

    private static final int f0=6400;//信号0为4500Hz
    private static final int f1=9600;//信号1为4750Hz
    private static final int length = 1024;//一个傅里叶变换时间窗口1024
    private static final int rate = 48000;//采样率48000

    private static final int step = 64;//滑动窗口为64个采样点

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
                for (int i = idx; i < idx + pkg_len; i++)
                {
                    bytes[i] = 0;
                    for (int j = 0; j < 8; j++)
                    {
                        bytes[i] += (byte)((int)msg_recv.charAt(i * 8 + j) << (7 - j));
                    }
                }
                String frag = new String(bytes);
                finalRes.append(frag);
                idx += pkg_len;
            }

//            byte[] notes=new byte[list.toString().size()/8];
//            for(int i = 0; i< list.toString().size()/8; i++){
//                notes[i]=0;
//                for(int j=0;j<8;j++){
//                    notes[i]+=(byte)((int) list.toString().get(i*8+j))<<(7-j);
//                }
//            }
//            String str=new String(notes);
//            str="Result:"+str;
//            Toast t = Toast.makeText(this.getContext(),"Decoding finished.", Toast.LENGTH_LONG);
//            t.show();
        }
        catch(NullPointerException e){
            Toast t = Toast.makeText(this.getContext(),"Error: Cannot get the path.", Toast.LENGTH_LONG);
            t.show();
        }
    }

}
