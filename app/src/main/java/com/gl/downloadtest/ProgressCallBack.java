package com.gl.downloadtest;

public interface ProgressCallBack {

    void showProgress(float progress);

    void downloadComplate();

    void downloadError(int code, String msg);

    void verifyResult(boolean success, long totalByte, long downloadByte, String rightMd5, String currentMd5);
}
