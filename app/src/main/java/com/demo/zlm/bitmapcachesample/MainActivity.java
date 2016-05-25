package com.demo.zlm.bitmapcachesample;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private DiskLruCache mDiskLruCache;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int DISK_CACHE_INDEX = 0;
    private String url = "http://dl.bizhi.sogou.com/images/2012/03/20/257576.jpg";
    private ImageResizer mImageResizer = new ImageResizer();
    private ImageView imageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView= (ImageView) findViewById(R.id.imageView);
        System.out.println(MyUtils.hashKeyFormUrl(url));
        mDiskLruCache=initDiskLruCache();
    }

    public void startMain2Click(View v){
        Intent intent=new Intent(this,Main2Activity.class);
        startActivity(intent);
    }
    //查找图片并显示
    public void findCacheBitmapClick(View v){
        Bitmap bitmap=null;
        String key=MyUtils.hashKeyFormUrl(url);
        try {
            DiskLruCache.Snapshot snapshot=mDiskLruCache.get(key);
            if (snapshot!=null){
                FileInputStream fileInputStream= (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fileDescriptor=fileInputStream.getFD();
                bitmap=mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor,200,200);
                if (bitmap!=null){
                    imageView.setImageBitmap(bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //将图片存储到磁盘上
    public void downBitmapDiskCacheClick(View v) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DiskLruCache.Editor editor = mDiskLruCache.edit(MyUtils.hashKeyFormUrl(url));
                        if (editor != null) {
                            //由于open方法中设置了一个节点只能有一个数据，所以常量设置为0即可。
                            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
                            if (MyUtils.downUrlToStream(url, outputStream)) {
                                editor.commit();//通过这个方法 才真正的存储到手机里
                            } else {
                                //如果下载过程出现异常，可以通过这个方法进行回退整个操作
                                editor.abort();
                            }
                            mDiskLruCache.flush();
                    }
                }catch (IOException e){
                    e.toString();
                }

            }
        }).start();
    }

    //初始化DiskLruCache
    public DiskLruCache initDiskLruCache() {
        File cacheDir = getCacheDir();
        if (cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        try {
            /**
             * 参数：1.表示磁盘存储存放文件的路径
             *       2.表示应用的版本号，设置为1即可，当版本号发生改变的时候，会清空之前所有的缓存
             *       3.表示单个节点对应的数据的个数，设置为1即可
             *       4.缓存的总大小。
             */
            mDiskLruCache = mDiskLruCache.open(cacheDir, 1, 1, DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mDiskLruCache;
    }

    //初始化LruCache
    public void initLruCache() {
        //获取当前进程的大小
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        //当前进程的8分之一当做缓存的大小
        int cacheSize = maxMemory / 8;
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();//获取Activity在内存中的大小
        System.out.println(cacheSize + "--" + memoryClass);//
        LruCache<String, Bitmap> bitmapLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            //sizeof方法作用是 计算缓存对象的大小
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }

            //当移除的时候，会调用这个方法
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
            }
        };
    }



}
