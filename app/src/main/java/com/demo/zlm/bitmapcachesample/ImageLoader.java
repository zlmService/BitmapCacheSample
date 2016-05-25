package com.demo.zlm.bitmapcachesample;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by malinkang on 2016/5/25.
 */
public class ImageLoader {
    private static final String TAG = "ImageLoader";
    private static final int MESSAGE_POST_RESULT = 1;
    //获取当前设备的CPU核心数
    private static final int CPU_COUNT = Runtime.getRuntime()
            .availableProcessors();
    //线程池核心线程 CPU+1
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    //线程池 总数  CPU*2+1
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    //线程空闲时间10秒
    private static final long KEEP_ALIVE = 10L;

    private static final int TAG_KEY_URI = R.id.imageloader_uri;
    //磁盘缓存的大小 50M
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    //BufferedInputStream 缓冲区的大小
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    private static final int DISK_CACHE_INDEX = 0;
    //判断 磁盘存储是否创建成功
    private boolean mIsDiskLruCacheCreated = false;

    //AtomicInteger，一个提供原子操作的Integer的类。在Java语言中，++i和i++操作并不是线程安全的，
    // 在使用的时候，不可避免的会用到synchronized关键字。而AtomicInteger则通过一种线程安全的加减操作接口。
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            //获取当前值，并自减
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };
    //初始化线程池
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(), sThreadFactory);

    //初始化Handler 用于更新UI界面
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            imageView.setImageBitmap(result.bitmap);
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            //在设置图片之前都检查它的url是否发生变化，解决View复用所导致重复图片的问题
            if (uri.equals(result.uri)) {
                imageView.setImageBitmap(result.bitmap);
            } else {
                Log.w(TAG, "set image bitmap,but url has changed, ignored!");
            }
        }
    };
    private Context mContext;
    private ImageResizer mImageResizer = new ImageResizer();
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;

    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize);
        File diskCacheDir = new File(Environment.getExternalStorageDirectory(),"BitmapSample");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        //当剩余的大小 大于 我们创建磁盘缓存的大小  就创建磁盘缓存
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1,
                        DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //用于存储 图片，图片的地址，和显示图片的控件的类，用于Handler传递数据，更新界面
    private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, Bitmap bitmap, String uri) {
            this.imageView = imageView;
            this.bitmap = bitmap;
            this.uri = uri;
        }
    }

    //创建ImageLoader的对象实例
    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    //内存缓存的添加 图片
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    //内存缓存的获取 图片
    private Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    //load bitmap from memory cache or disk cache or network async,then bind imageView and bitmap.
    //加载图片 并绑定图片
    public void bindBitmap(String url, ImageView imageView) {
        bindBitmap(url, imageView, 0, 0);
    }

    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, url);
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            Log.e(TAG, "loadBitmapFromMemCache,url:" );
            return;
        }
        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, bitmap, url);
                    mHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    //加载图片 内存-->内存卡-->网络
    public  Bitmap loadBitmap(String uri, int reqWidth, int reqHeight) {
        //从内存找
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            return bitmap;
        }
        try {
            //从磁盘上找
            bitmap = loadBitmapFromDiskCache(uri, reqWidth, reqHeight);
            if (bitmap != null) {
                Log.d(TAG, "loadBitmapFromDisk,url:" + uri);
                return bitmap;
            }
            //如果没有 就从网络上下载 然后存储到磁盘中 在从磁盘中获取这个图片
            bitmap = loadBitmapFromHttp(uri, reqWidth, reqHeight);
            Log.d(TAG, "loadBitmapFromHttp,url:" + uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bitmap == null && !mIsDiskLruCacheCreated) {
            Log.w(TAG, "error,disk cache is not create");
            bitmap = downloadBitmapFromUrl(uri);
        }
        return bitmap;
    }

    //网络下载图片 并存储到磁盘上 并且调用从磁盘上获取图片的方法
    private Bitmap loadBitmapFromHttp(String uri, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("不能再主线程中使用网络加载");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        String key = hashKeyFormUrl(uri);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(uri, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(uri, reqWidth, reqHeight);
    }

    //从内存中获取
    private Bitmap loadBitmapFromMemCache(String uri) {
        String key = hashKeyFormUrl(uri);
        Bitmap bitmap = getBitmapFromMemCache(key);
        return bitmap;
    }

    //从磁盘中获取
    private Bitmap loadBitmapFromDiskCache(String uri, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hashKeyFormUrl(uri);
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if (snapshot != null) {
            FileInputStream inputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fd = inputStream.getFD();
            bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fd, reqWidth, reqHeight);
            if (bitmap != null) {
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    //从网络上下载图片 //如果没有磁盘存储（可能是设备内存不够了）下载后就直接返回bitmap
    private Bitmap downloadBitmapFromUrl(String uri) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        try {
            URL url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            MyUtils.close(in);
        }
        return bitmap;
    }

    //从网络上下载图片 并写入到磁盘中
    private boolean downloadUrlToStream(String uri, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            URL url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            MyUtils.close(out);
            MyUtils.close(in);
        }
        return false;
    }


    //用于将url转换为16进制的key 通过MD5算法
    private String hashKeyFormUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(url.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    //检测手机设备中内存卡还有多少容量
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

}


