package com.gl.downloadtest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity implements View.OnClickListener {

    private Button mBtnDownload1, mBtnDownload2;
    private TextView mTvProgress1, mTvProgress2;
    private TextView mTvResult1, mTvResult2;

    private ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool();

    private ProgressCallBack mProgressCallBack1, mProgressCallBack2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_mainactivity);

        mBtnDownload1 = findViewById(R.id.btn_download1);
        mBtnDownload2 = findViewById(R.id.btn_download2);

        mTvProgress1 = findViewById(R.id.tv_progress1);
        mTvProgress2 = findViewById(R.id.tv_progress2);

        mTvResult1 = findViewById(R.id.tv_result1);
        mTvResult2 = findViewById(R.id.tv_result2);

        mBtnDownload1.setOnClickListener(this);
        mBtnDownload2.setOnClickListener(this);

        mProgressCallBack1 = new ProgressCallBack() {
            @Override
            public void showProgress(float progress) {
                if (MainActivity.this.isDestroyed() || mTvProgress1 == null) {
                    return;
                }
                mTvProgress1.setText("进度：" + String.format("%.5f", progress * 100) + "%");
            }

            @Override
            public void downloadComplate() {
                Toast.makeText(getApplicationContext(), "任务一下载完成", Toast.LENGTH_LONG).show();
            }

            @Override
            public void downloadError(int code, String msg) {
                Toast.makeText(getApplicationContext(), "任务一下载失败，错误码：" + code + ", 错误内容：" + msg, Toast.LENGTH_LONG).show();
                if (MainActivity.this.isDestroyed() || mBtnDownload1 == null) {
                    return;
                }
                mBtnDownload1.setEnabled(true);
            }

            @Override
            public void verifyResult(boolean success, long totalByte, long downloadByte, String rightMd5, String currentMd5) {
                if (MainActivity.this.isDestroyed() || mTvResult1 == null) {
                    return;
                }

                mBtnDownload1.setText("下载完成");

                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append(success ? "校验通过" : "校验失败");
                stringBuffer.append("\n");
                stringBuffer.append("apk总大小：" + totalByte + ", 实际下载大小：" + downloadByte);
                stringBuffer.append("\n");
                stringBuffer.append("----------------------");
                stringBuffer.append("文件正确md5：" + rightMd5);
                stringBuffer.append("\n");
                stringBuffer.append("----------------------");
                stringBuffer.append("下载文件md5：" + currentMd5);

                mTvResult1.setText(stringBuffer.toString());
            }
        };

        mProgressCallBack2 = new ProgressCallBack() {
            @Override
            public void showProgress(float progress) {
                if (MainActivity.this.isDestroyed() || mTvProgress2 == null) {
                    return;
                }
                mTvProgress2.setText("进度：" + String.format("%.5f", progress * 100) + "%");
            }

            @Override
            public void downloadComplate() {
                Toast.makeText(getApplicationContext(), "任务二下载完成", Toast.LENGTH_LONG).show();
            }

            @Override
            public void downloadError(int code, String msg) {
                Toast.makeText(getApplicationContext(), "任务二下载失败，错误码：" + code + ", 错误内容：" + msg, Toast.LENGTH_LONG).show();
                if (MainActivity.this.isDestroyed() || mBtnDownload2 == null) {
                    return;
                }
                mBtnDownload2.setEnabled(true);
            }

            @Override
            public void verifyResult(boolean success, long totalByte, long downloadByte, String rightMd5, String currentMd5) {
                if (MainActivity.this.isDestroyed() || mTvResult2 == null) {
                    return;
                }

                mBtnDownload2.setText("下载完成");

                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append(success ? "校验通过" : "校验失败");
                stringBuffer.append("\n");
                stringBuffer.append("apk总大小：" + totalByte + ", 实际下载大小：" + downloadByte);
                stringBuffer.append("\n");
                stringBuffer.append("----------------------");
                stringBuffer.append("文件正确md5：" + rightMd5);
                stringBuffer.append("\n");
                stringBuffer.append("----------------------");
                stringBuffer.append("下载文件md5：" + currentMd5);

                mTvResult2.setText(stringBuffer.toString());
            }
        };

        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
        ActivityCompat.requestPermissions(
                this,
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                1002
        );

    }

    private float getNewPercent(int progressWrapper) {

        float progress = progressWrapper / 1000f;

        float difP;
        if (progress == 0) {
            difP = 1f;
        } else {
            difP = 1f - progress;
        }
        return 1f - (difP * difP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002) {
            mBtnDownload1.setEnabled(true);
            mBtnDownload2.setEnabled(true);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_download1:
                mBtnDownload1.setEnabled(false);
                BACKGROUND_EXECUTOR.execute(new DownloadThread(getApplicationContext(), "http://app.2345.cn/appgame/49327.apk", "fileoutputstreamtest1.apk", DownloadThread.TYPE_OUTPUTSTREAM, mProgressCallBack1));
                break;

            case R.id.btn_download2:
                mBtnDownload2.setEnabled(false);
                BACKGROUND_EXECUTOR.execute(new DownloadThread(getApplicationContext(), "http://app.2345.cn/appgame/49327.apk", "RandomAccessFiletest1.apk", DownloadThread.TYPE_RANDOMACCESSFILE, mProgressCallBack2));
                break;
        }
    }
}
