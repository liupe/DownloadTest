package com.gl.downloadtest;



import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;


import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;


public class DownloadThread implements Runnable {

    public static final int TYPE_OUTPUTSTREAM = 1;
    public static final int TYPE_RANDOMACCESSFILE = 2;

    private static final String TAG = DownloadThread.class.getSimpleName();

    public static final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    public static final int HTTP_TEMP_REDIRECT = 307;

    public static final int DEFAULT_TIMEOUT = (int) (20 * SECOND_IN_MILLIS);

    /**
     * A single thread pool to release resources
     */
    private static final ExecutorService releaseExecutor = Executors.newSingleThreadExecutor();


    private final Context mContext;

    private State mState;

    private String mUrl;

    private String mFileName;

    private int mType;

    private WeakReference<ProgressCallBack> mProgressWeakReference;

    private Handler mHandler;


    public DownloadThread(Context context, String url, String filename, int type, ProgressCallBack callBack) {
        mContext = context;
        mUrl = url;
        mFileName = filename;
        mType = type;
        mProgressWeakReference = new WeakReference<>(callBack);
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the user agent provided by the initiating app, or use the default one
     */
    private String userAgent() {
        return "android downloadtest1.0";
    }

    /**
     * State for the entire run() method.
     */
    static class State {
        public String mFilename;
        public long mTotalBytes = -1;
        public long mCurrentBytes = 0;
        public String mHeaderETag;
        public int mSourceFrom = -1;


        public long mContentLength = -1;
        public String mContentDisposition;
        public String mContentLocation;

        public int mRedirectionCount;
        public URL mUrl;
        public String mOriginalUrl;
        public String mRedirectUrl;


        public State(final Context context, final String url) throws StopRequestException {
            File f = null;
            try {
                mOriginalUrl = url;

                mUrl = new URL(url);
            } catch (MalformedURLException e) {
                throw new StopRequestException(1, e);
            }
        }

        public State(String url, String fileName) throws StopRequestException {
            mFilename = fileName;
            try {
                mUrl = new URL(url);
                mOriginalUrl = url;
            } catch (MalformedURLException e) {
                throw new StopRequestException(1, e);
            }
        }

        public void resetBeforeExecute() {
            // Reset any state from previous execution
            mContentLength = -1;
            mContentDisposition = null;
            mContentLocation = null;
            mRedirectionCount = 0;
        }
    }


    private void log(String message) {

        StringBuilder sb = new StringBuilder()
                .append("\r\n")
                .append(message)
                .append("\r\n");

        message = sb.append("-------------------------------------------------------").toString();


        Log.i(TAG, message);
    }

    @Override
    @SuppressLint("NewApi")
    public void run() {
        log("************************BEGIN**************************");

        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);


        runInternal();


        log("**************************END**************************");
    }


    @SuppressLint("NewApi")
    private void runInternal() {

        final int finalStatus;
        final String errorMsg;
        try {
            mState = new State(mContext, mUrl);

            executeDownload(mState);

            validate();

        } catch (StopRequestException error) {
            finalStatus = error.getFinalStatus();

            final Throwable cause = error.getCause();
            if (cause != null && cause instanceof MalformedURLException) {
                log("Wrong url!\r\n " + " URL:" + mUrl);
            }
            if (mState == null) {
                log("State initialize failed!");
                log("URL:" + mUrl);
            } else {
                log("FROM:" + mState.mSourceFrom);
            }

            log("runInternal StopRequestException:\r\n " + " finalstatus:" + finalStatus + "\r\n" + Log.getStackTraceString(error));

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ProgressCallBack callBack = mProgressWeakReference.get();
                    if (callBack != null) {
                        callBack.downloadError(finalStatus, cause.getMessage());
                    }
                }
            });

        } catch (Throwable ex) {
            errorMsg = ex.getMessage();
            final String msg = "Exception for " + errorMsg;
            Log.w(TAG, msg, ex);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ProgressCallBack callBack = mProgressWeakReference.get();
                    if (callBack != null) {
                        callBack.downloadError(-1, msg);
                    }
                }
            });

        }

    }

    private void validate() {
        File file = new File(mState.mFilename);
        final long length = file.length();
        final String md5 = getMD5Three(file);
        final boolean success = "1e114963ff63adb8c150488fdb9e0547".equals(md5);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ProgressCallBack progressCallBack = mProgressWeakReference.get();
                if (progressCallBack != null) {
                    progressCallBack.verifyResult(success, mState.mTotalBytes, length, "1e114963ff63adb8c150488fdb9e0547", md5);
                    progressCallBack.downloadComplate();
                }
            }
        });
    }

    public static String getMD5Three(File file) {
        BigInteger bi = null;
        try {
            byte[] buffer = new byte[8192];
            int len = 0;
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            fis.close();
            byte[] b = md.digest();
            bi = new BigInteger(1, b);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bi.toString(16);
    }

    /**
     * Fully execute a single download request. Setup and send the request,
     * handle the response, and transfer the data to the destination file.
     * Response codes explained in <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">Status Code Definitions</a> in detail.
     */
    private void executeDownload(State state) throws StopRequestException {
        if (state == null) {
            return;
        }
        state.resetBeforeExecute();
        setupDestinationFile(state);

        // skip when already finished; remove after fixing race in 5217390
        if (state.mCurrentBytes == state.mTotalBytes) {
            return;
        }


        log("Original url:" + mState.mOriginalUrl);
        while (state.mRedirectionCount++ < 5) {
            log("redirectionCount:" + (state.mRedirectionCount - 1));

            long t1 = System.currentTimeMillis();
            log("request begin at " + t1);

            // Open connection and follow any redirects until we have a useful
            // response with body.
            HttpURLConnection conn = null;

            try {
                state.mUrl = getEncodeURL(state.mUrl);
                conn = (HttpURLConnection) state.mUrl.openConnection();

                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(DEFAULT_TIMEOUT);
                conn.setReadTimeout(DEFAULT_TIMEOUT);
                log("Actual URL:" + state.mUrl.toString());

                addRequestHeaders(state, conn);
                final int responseCode = conn.getResponseCode();

                long t2 = System.currentTimeMillis();
                log("response at " + t2 + ",the request cost " + (t2 - t1) + " milliseconds, responseCode:" + responseCode);

                InetAddress address = InetAddress.getByName(state.mUrl.getHost());

                log("ip:" + address.getHostAddress());

                switch (responseCode) {
                    case HTTP_OK:

                        processResponseHeaders(state, conn);

                        transferData(state, conn, HTTP_OK);
                        return;

                    case HTTP_PARTIAL:

                        processResponseHeaders(state, conn);
                        transferData(state, conn, HTTP_PARTIAL);
                        return;

                    case HTTP_MOVED_PERM:
                    case HTTP_MOVED_TEMP:
                    case HTTP_SEE_OTHER:
                    case HTTP_TEMP_REDIRECT:

                        final String location = conn.getHeaderField("Location");
                        log("redirect:" + location);
                        try {
                            state.mUrl = new URL(state.mUrl, location);
                            state.mRedirectUrl = state.mUrl.toString();
                        } catch (MalformedURLException e) {
                            throw new StopRequestException(1, e);
                        }

                        continue;


                    case 404:
                        throw new StopRequestException(
                                404, "The server has not found anything matching the requested URI");

                    case 412:
                        throw new StopRequestException(
                                412, "The precondition given in one or more of the request header fields evaluated to false when it was tested on the server");
                    case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:

                        throw new StopRequestException(
                                489, "Requested range not satisfiable");

                    case HTTP_UNAVAILABLE:

                        throw new StopRequestException(
                                HTTP_UNAVAILABLE, conn.getResponseMessage());

                    case HTTP_INTERNAL_ERROR:
                        throw new StopRequestException(
                                HTTP_INTERNAL_ERROR, conn.getResponseMessage());

                    default:

                        throw new StopRequestException(responseCode, "Unhandled HTTP response: " + responseCode);

                }
            } catch (IOException e) {
                log("executeDownload " + Log.getStackTraceString(e));
                throw new StopRequestException(495, e);

            } finally {

                // In some time, in.close() will cost a long time,so start a new thread to release resources
                final HttpURLConnection finalConn = conn;
                releaseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (finalConn != null) finalConn.disconnect();
                    }
                });
            }
        }
        log("Too many redirects");
        throw new StopRequestException(497, "Too many redirects");
    }

    private URL getEncodeURL(URL url) {
        try {
            String urlString = url.toString();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < urlString.length(); i++) {
                char c = urlString.charAt(i);
                if (isChinese(c)) {
                    String s = URLEncoder.encode(String.valueOf(c), "utf-8");
                    sb.append(s);
                } else {
                    sb.append(c);
                }
            }
            return new URL(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return url;
        }
    }

    public static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }


    /**
     * Transfer data from the given connection to the destination file.
     */
    private void transferData(State state, HttpURLConnection conn, int code) throws StopRequestException {
        if (state == null || conn == null) {
            return;
        }
        InputStream in = null;
        OutputStream out = null;
        FileDescriptor outFd = null;
        RandomAccessFile accessFile = null;
        try {
            try {
                in = conn.getInputStream();
            } catch (IOException e) {
                throw new StopRequestException(495, e);
            }

            if (mType == TYPE_OUTPUTSTREAM) {
                try {
                    out = new FileOutputStream(state.mFilename, true);
                    outFd = ((FileOutputStream) out).getFD();
                } catch (IOException e) {
                    throw new StopRequestException(492, e);
                }

                // Start streaming data, periodically watch for pause/cancel
                // commands and checking disk space as needed.

                transferData(state, in, out, code);
            } else {
                try {
                    accessFile = new RandomAccessFile(state.mFilename, "rw");
                    accessFile.seek(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                transferData(state, in, accessFile, code);
            }


        } finally {

            if (out != null) {
                try {
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (outFd != null) {
                try {
                    outFd.sync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (accessFile != null) {
                try {
                    accessFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            final InputStream finalIn = in;
            releaseExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (finalIn != null) {
                        try {
                            finalIn.close();
                            log("inputStream close");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }


    private void transferData(State state, InputStream in, OutputStream out, int code) throws StopRequestException {
        if (state == null || out == null) {
            return;
        }

        long rawLength = new File(mState.mFilename).length();
        log("The current file LENGTH is " + rawLength);
        if (rawLength != mState.mCurrentBytes) {
            log("Exception : Begin file size not match, rawLength is " + rawLength + ", mState.mCurrentBytes is " + mState.mCurrentBytes);
        }

        final byte data[] = new byte[4096];

        byte dataTotal[] = null;
        int totalByteLength = 0;

        float progressRatio = 0.0f;
        float currRatio = 0.0f;
        long currProgressSize = 0l;

        boolean getData = false;
        int i = 0;
        OutputStream realOps = out;
        FileDescriptor outFd = null;

        try {
            dataTotal = new byte[4096 * 128 + 100];
        } catch (Throwable e) {
            e.printStackTrace();
        }


        try {
            for (; ; ) {
                int bytesRead = readFromResponse(state, data, in);
                if (bytesRead == -1) { // success, end of stream already reached
                    if (totalByteLength > 0) {
                        writeDataToDestination(state, dataTotal, totalByteLength, realOps);
                    }
                    return;
                }

                if (!getData) {
                    getData = true;
                    log("Current Bytes:" + state.mCurrentBytes);
                    log("the stream has got data");
                }

                try {
                    if (i < 5 /*i == 0*/) {
                        writeDataToDestination(state, data, bytesRead, realOps);
                    } else {
                        if (totalByteLength > dataTotal.length - 100 - 4096) {
                            writeDataToDestination(state, dataTotal, totalByteLength, realOps);
                            totalByteLength = 0;
                        }

                        System.arraycopy(data, 0, dataTotal, totalByteLength, bytesRead);
                        totalByteLength += bytesRead;
                    }

                } catch (StopRequestException e) {
                    throw e;
                }

                state.mCurrentBytes += bytesRead;
                if (++i % 200 == 0) {
                    log("Current Bytes:" + state.mCurrentBytes);
                }

                currRatio = state.mCurrentBytes * 1.0f / state.mTotalBytes;
                if ((progressRatio >= -0.000001f && progressRatio <= 0.000001f)
                        || (currRatio >= -0.000001f && currRatio <= 0.01f)
                        || state.mCurrentBytes - currProgressSize >= 512 * 1024
                        || currRatio - progressRatio >= 0.005f /*0.005f*/) {

                    reportProgress(currRatio);
                    progressRatio = currRatio;
                    currProgressSize = state.mCurrentBytes;
                }

            }
        } finally {

            if (realOps != null) {
                try {
                    realOps.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (outFd != null) {
                try {
                    outFd.sync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (realOps != null) {
                try {
                    realOps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void transferData(State state, InputStream in, RandomAccessFile saveFile, int code) throws StopRequestException {
        if (state == null || saveFile == null) {
            return;
        }

        long rawLength = new File(mState.mFilename).length();
        log("The current file LENGTH is " + rawLength);
        if (rawLength != mState.mCurrentBytes) {
            log("Exception : Begin file size not match, rawLength is " + rawLength + ", mState.mCurrentBytes is " + mState.mCurrentBytes);
        }

        final byte data[] = new byte[4096];

        byte dataTotal[] = null;
        int totalByteLength = 0;

        float progressRatio = 0.0f;
        float currRatio = 0.0f;
        long currProgressSize = 0l;

        boolean getData = false;
        int i = 0;


        try {
            dataTotal = new byte[4096 * 128 + 100];
        } catch (Throwable e) {
            e.printStackTrace();
        }


        try {
            for (; ; ) {
                int bytesRead = readFromResponse(state, data, in);
                if (bytesRead == -1) {
                    if (totalByteLength > 0) {
                        writeDataToDestinationByAccessFile(state, dataTotal, totalByteLength, saveFile);
                    }
                    return;
                }

                if (!getData) {
                    getData = true;
                    log("Current Bytes:" + state.mCurrentBytes);
                    log("the stream has got data");
                }

                try {
                    if (i < 5 /*i == 0*/) {
                        writeDataToDestinationByAccessFile(state, data, bytesRead, saveFile);
                    } else {
                        if (totalByteLength > dataTotal.length - 100 - 4096) {
                            writeDataToDestinationByAccessFile(state, dataTotal, totalByteLength, saveFile);
                            totalByteLength = 0;
                        }

                        System.arraycopy(data, 0, dataTotal, totalByteLength, bytesRead);
                        totalByteLength += bytesRead;
                    }

                } catch (StopRequestException e) {
                    throw e;
                }

                state.mCurrentBytes += bytesRead;
                if (++i % 200 == 0) {
                    log("Current Bytes:" + state.mCurrentBytes);
                }

//                reportProgress(state);

                currRatio = state.mCurrentBytes * 1.0f / state.mTotalBytes;
                if ((progressRatio >= -0.000001f && progressRatio <= 0.000001f)
                        || (currRatio >= -0.000001f && currRatio <= 0.01f)
                        || state.mCurrentBytes - currProgressSize >= 512 * 1024
                        || currRatio - progressRatio >= 0.005f /*0.005f*/) {

                    reportProgress(currRatio);
                    progressRatio = currRatio;
                    currProgressSize = state.mCurrentBytes;
                }

            }
        } finally {

            if (saveFile != null) {
                try {
                    saveFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Report download progress through the database if necessary, for download opt2
     */
    private void reportProgress(float progress) {

        final float p = getNewPercent(progress);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ProgressCallBack callBack = mProgressWeakReference.get();
                if (callBack != null) {
                    callBack.showProgress(p);
                }
            }
        });

    }

    private float getNewPercent(float progress) {

        float difP;
        if (progress == 0) {
            difP = 1f;
        } else {
            difP = 1f - progress;
        }
        return 1f - (difP * difP);
    }


    /**
     * Write a data buffer to the destination file.
     *
     * @param data      buffer containing the data to write
     * @param bytesRead how many bytes to write from the buffer
     */
    private void writeDataToDestination(State state, byte[] data, int bytesRead, OutputStream out)
            throws StopRequestException {

        if (state == null || out == null) {
            return;
        }

        if (!new File(state.mFilename).exists()) {
            log("The file is deleted");
            throw new StopRequestException(498, "The file is deleted");
        }

        boolean forceVerified = false;
        while (true) {
            try {
                out.write(data, 0, bytesRead);
                return;
            } catch (IOException ex) {
                if (!forceVerified) {
                    // couldn't write to file. are we out of space? check.
                    String path = new File(state.mFilename).getParentFile().getAbsolutePath();
                    forceVerified = true;
                } else {
                    throw new StopRequestException(492,
                            "Failed to write data: " + ex);
                }
            }
        }
    }

    private void writeDataToDestinationByAccessFile(State state, byte[] data, int bytesRead, RandomAccessFile saveFile)
            throws StopRequestException {

        if (state == null || saveFile == null) {
            return;
        }

        if (!new File(state.mFilename).exists()) {
            log("The file is deleted");
            throw new StopRequestException(498, "The file is deleted");
        }

        boolean forceVerified = false;
        while (true) {
            try {
                saveFile.write(data, 0, bytesRead);
                return;
            } catch (IOException ex) {
                if (!forceVerified) {
                    // couldn't write to file. are we out of space? check.
                    String path = new File(state.mFilename).getParentFile().getAbsolutePath();
                    forceVerified = true;
                } else {
                    throw new StopRequestException(492,
                            "Failed to write data: " + ex);
                }
            }
        }
    }

    /**
     * Read some data from the HTTP response stream, handling I/O errors.
     *
     * @param data         buffer to use to read data
     * @param entityStream stream for reading the HTTP response entity
     * @return the number of bytes actually read or -1 if the end of the stream has been reached
     */
    private int readFromResponse(final State state, byte[] data, InputStream entityStream)
            throws StopRequestException {
        try {
            return entityStream.read(data);
        } catch (IOException ex) {
            log("readFromResponse exception occur \r\n" + Log.getStackTraceString(ex));
            if ("unexpected end of stream".equals(ex.getMessage())) {
                return -1;
            }

            throw new StopRequestException(495,
                    "Failed reading response: " + ex, ex);
        }
    }

    /**
     * Prepare target file based on given network response. Derives filename and
     * target size as needed.
     */
    private void processResponseHeaders(State state, HttpURLConnection conn)
            throws StopRequestException {
        // TODO: fallocate the entire file if header gave us specific length
        if (state == null) {
            return;
        }

        readResponseHeaders(state, conn);

        state.mFilename = generateSaveFile(state);
        log("Filename:" + state.mFilename);

    }

    private String generateSaveFile(DownloadThread.State state) throws StopRequestException {

        if (state.mContentLength < 0) {
            state.mContentLength = 0;
        }

        File dir = getDownloadDir();


        if (!dir.exists() && !dir.mkdir()) {
            throw new StopRequestException(492,
                    "unable to create external downloads directory " + dir.getPath());
        }

        String filename = dir.getAbsolutePath() + File.separator + mFileName;

        try {
            File file = new File(filename);
            if (file.exists() && !file.delete()) {
                throw new StopRequestException(492,
                        "unable to create external downloads directory " + dir.getPath());
            }
            if (!new File(filename).createNewFile()) {
                throw new StopRequestException(492,
                        "unable to create external downloads directory " + dir.getPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new StopRequestException(492,
                    "unable to create external downloads directory " + dir.getPath());
        }

        return filename;
    }

    public File getDownloadDir() {
        File dir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            dir = new File(Environment.getExternalStorageDirectory(), "DownloadTest/apk");
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS);
            }
        } else {
            dir = new File(mContext.getFilesDir(), "apk");
        }
        return dir;
    }


    /**
     * Read headers from the HTTP response and store them into local state.
     */
    private void readResponseHeaders(State state, HttpURLConnection conn)
            throws StopRequestException {
        if (state == null || conn == null) {
            return;
        }
        log("readResponseHeaders");
        Map<String, List<String>> headerFields = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> header : headerFields.entrySet()) {
            List<String> headerValues = header.getValue();
            for (String value : headerValues) {
                log(header.getKey() + ":" + value);
            }
        }

        state.mContentDisposition = conn.getHeaderField("Content-Disposition");

        state.mContentLocation = conn.getHeaderField("Content-Location");

        state.mHeaderETag = conn.getHeaderField("ETag");
        if (TextUtils.isEmpty(state.mHeaderETag)) {
            state.mHeaderETag = conn.getHeaderField("Last-Modified");
        }
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            state.mContentLength = -1L;
            try {
                state.mContentLength = Long.parseLong(conn.getHeaderField("Content-Length"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("Header get mContentLength is " + state.mContentLength);
        } else {
            Log.i(TAG, "Ignoring Content-Length since Transfer-Encoding is also defined");
            state.mContentLength = -1;
            log("Ignoring Content-Length since Transfer-Encoding is also defined");
        }

        state.mTotalBytes = state.mContentLength;

    }


    /**
     * Prepare the destination file to receive data.  If the file already exists, we'll set up
     * appropriately for resumption.
     */
    private void setupDestinationFile(State state) throws StopRequestException {
        if (state == null) {
            return;
        }
        if (!TextUtils.isEmpty(state.mFilename)) { // only true if we've already run a thread for this download
            // We're resuming a download that got interrupted
            File f = new File(state.mFilename);
            if (f.exists()) {
                log("Target file " + state.mFilename + "exists, ready to resume");

                long fileLength = f.length();
                if (fileLength == 0) {
                    log("The size of target file is 0");

                    // The download hadn't actually started, we can restart from scratch
                    if (!f.delete()) {
                        throw new StopRequestException(
                                492, "The file can not be deleted!");
                    }

                    state.mFilename = null;
                } else {
                    if (!f.delete()) {
                        throw new StopRequestException(
                                492, "The file can not be deleted!");
                    }

                    state.mFilename = null;
                }
            }
        }
    }

    /**
     * Add custom headers for this download to the HTTP request.
     * Reference to <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html">Header Field Definitions</a>
     */
    private void addRequestHeaders(State state, HttpURLConnection conn) {
        if (state == null || conn == null) {
            return;
        }
        // Only splice in user agent when not already defined
        if (conn.getRequestProperty("User-Agent") == null) {
            conn.addRequestProperty("User-Agent", userAgent());
        }

        conn.setRequestProperty("Accept-Encoding", "identity");
    }

}

