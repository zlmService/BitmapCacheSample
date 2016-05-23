package com.demo.zlm.bitmapcachesample;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获取当前进程的大小
        int maxMemory= (int) (Runtime.getRuntime().maxMemory()/1024);
        //当前进程的8分之一当做缓存的大小
        int cacheSize=maxMemory/8;
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();//获取Activity在内存中的大小
        System.out.println(cacheSize+"--"+memoryClass);//
        LruCache<String,Bitmap> bitmapLruCache=new LruCache<String,Bitmap>(cacheSize){
            @Override
            //sizeof方法作用是 计算缓存对象的大小
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes()*bitmap.getHeight()/1024;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
            }
        };

    }

    public Bitmap BitmapInSampleSize(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;//为true时，代表只会解析图片的宽和高，并不会真正的加载图片
        BitmapFactory.decodeResource(res, resId, options);//解析图片
        //设置完缩放比例后 在解析图片
        options.inSampleSize = calculateInSampleSize(options, reqHeight, reqWidth);
        options.inJustDecodeBounds=false;
        //最后返回解析后的Bitmap
        return BitmapFactory.decodeResource(res,resId,options);
    }
    //设置缩放比例
    private int calculateInSampleSize(BitmapFactory.Options options, int reqHeight, int reqWidth) {
        int inSampleSize = 1;
        int width = options.outWidth;//从BitmapFactory.Options中取出图片的原始宽高
        int height = options.outHeight;
        //根据传过来的参数（需要展现的宽高）和图片实际的宽高进行对比
        if (width > reqWidth || height > reqHeight) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2; // 缩放比例一般都是2的倍数存在
            }
        }
        return inSampleSize;
    }


}
