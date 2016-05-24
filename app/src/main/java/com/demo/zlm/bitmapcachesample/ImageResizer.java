package com.demo.zlm.bitmapcachesample;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by malinkang on 2016/5/24.
 */
public class ImageResizer {

    public ImageResizer() {
    }

    //从磁盘中查找图片 并按照比例缩放
    public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth,
                reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }
    public Bitmap BitmapInSampleSize(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;//为true时，代表只会解析图片的宽和高，并不会真正的加载图片
        BitmapFactory.decodeResource(res, resId, options);//解析图片
        //设置完缩放比例后 在解析图片
        options.inSampleSize = calculateInSampleSize(options, reqHeight, reqWidth);
        options.inJustDecodeBounds = false;
        //最后返回解析后的Bitmap
        return BitmapFactory.decodeResource(res, resId, options);
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
