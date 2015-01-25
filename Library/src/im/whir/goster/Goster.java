package im.whir.goster;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

public class Goster  {
	private static final int THREAD_COUNT_FOR_POOL = 4;
	private static DownloaderManager downloadManager;
	private static Vector<Goster> url_images = new Vector<Goster>();
	private static HashMap<String, WeakReference<Bitmap>> bitmap_cache = new HashMap<String, WeakReference<Bitmap>>();
	private static File cache_dir;
	private static String cache_file_prefix;
	private static boolean init = false;
	private static long cache_saving_bytes;
	private static long cache_saving_time;
	private static boolean log_enabled;
	private static String log_tag;
	
	public static void init(Context ctx){
		init(ctx,null);
	}
	public static void init(Context ctx,File cache_dir){
		init(ctx,cache_dir,7*24*60*60,10*1024*1024,"uim_");
	}
	public static void init(Context ctx,File cache_dir,long cache_saving_time,long cache_saving_bytes,String cache_file_prefix){
		init(ctx, cache_dir, cache_saving_time, cache_saving_bytes, cache_file_prefix, false, "GOSTER");
	}
	public static void init(Context ctx,File cache_dir,long cache_saving_time,long cache_saving_bytes,String cache_file_prefix,boolean log_enabled,String log_tag){
		if (init){
			//throw new UrlImageException("UrlImage have to be initialized once. But it is already initialized");
			return;
		}
		init = true;
		Goster.log_enabled = log_enabled;
		Goster.log_tag = log_tag;
		if (cache_dir == null){
			cache_dir = ctx.getCacheDir();
		} else {
			Goster.cache_dir = cache_dir;
		}
		Goster.cache_dir.mkdirs();
		
		if (cache_file_prefix == null){
			Goster.cache_file_prefix = "uimage";
		} else {
			Goster.cache_file_prefix = cache_file_prefix;
		}
		Goster.cache_saving_bytes = cache_saving_bytes;
		Goster.cache_saving_time = cache_saving_time;
		clearCache();
	}
	private static void clearCache(){
		if (cache_saving_time > 0){
			File delete[] = cache_dir.listFiles();
			for (int i = 0; i < delete.length; i++) {
				String filename = delete[i].getName();
				if (delete[i].isDirectory())
					continue;
				if (filename.startsWith(cache_file_prefix) && filename.indexOf("_",cache_file_prefix.length()+1) != -1 ){
					if (filename.endsWith("~")){
						delete[i].delete();
					} else {
						String time = filename.substring(cache_file_prefix.length()+1,filename.indexOf("_",cache_file_prefix.length()+1));
						try {
							long time_d = Long.parseLong(time);
							if (System.currentTimeMillis() - time_d > 7 * 24 * 60 * 60 * 1000){
								delete[i].delete();
								System.out.println("delete::"+delete[i]);
							} else {
								System.out.println("not delete::"+delete[i]);
							}
						} catch (Exception e){
							log(e);
							delete[i].delete();
						}
					}
				}
			}
		}
		if (cache_saving_bytes > 0){
			
			File delete[] = cache_dir.listFiles();
			Vector<File> target_files = new Vector<File>();
			for (int i = 0; i < delete.length; i++) {
				String filename = delete[i].getName();
				if (filename.startsWith(cache_file_prefix) && filename.indexOf("_",cache_file_prefix.length()+1) != -1 ){
					target_files.add(delete[i]);
				}
			}
			Collections.sort(target_files, new Comparator<File>() {
				@Override
				public int compare(File lhs, File rhs) {
					String lhs_name = lhs.getName();
					String rhs_name = rhs.getName();
					
					lhs_name = lhs_name.substring(cache_file_prefix.length() + 1, lhs_name.indexOf("_",cache_file_prefix.length()+1) );
					rhs_name = rhs_name.substring(cache_file_prefix.length() + 1, rhs_name.indexOf("_",cache_file_prefix.length()+1) );
					long lhs_time = 0;
					long rhs_time = 0;
					try {
						lhs_time = Long.parseLong(lhs_name);
					} catch (Exception e){
					}
					try {
						rhs_time = Long.parseLong(rhs_name);
					} catch (Exception e){
					}
					if (lhs_time == rhs_time)
						return 0;
					else if (lhs_time > rhs_time)
						return -1;
					else
						return 1;
				}
			});
			
			long total_size = 0;
			Vector<File> will_delete = new Vector<File>();
			for (int i = 0; i < target_files.size(); i++) {
				long size = target_files.get(i).length();
				if (total_size + size > cache_saving_bytes){
					will_delete.add(target_files.get(i));
				} else {
					total_size += size;
				}
			}
			for (int i = 0; i < will_delete.size(); i++) {
				will_delete.get(i).delete();
			}
		}
	}
	
	
	public static interface Callback{
		public void onSuccess(Goster urlImage,ImageView imageView);
		public void onError();
	}
	private Context context;
	
	private WeakReference<ImageView> weak;
	private Callback callback;
	private String error_message;
	private String path;
	private String savingpath;
	private boolean caching;
	private boolean with_fade;
	private Goster(Context context){
		this.context = context;
		this.caching = true;
		this.with_fade = false;
	}
	
	
	public static Goster with(Context ctx){
		if (cache_dir == null){
			cache_dir = ctx.getCacheDir();
		}
		if (downloadManager == null){
			downloadManager = new DownloaderManager();
		}
		Goster im = new Goster(ctx);
		url_images.add(im);
		return im;
	}
	
	public String getSavingPath(){
		return savingpath;
	}
	
	public Goster cache(boolean cache){
		this.caching = cache;
		return this;
	}
	public Goster load(final String path){
		this.path = path;
		return this;
	}
	public Goster fade(boolean fade){
		this.with_fade = fade;
		return this;
	}
	private void download(final String url){
		downloadManager.download(this);
	}
	private void decodeAndSet(){
		if (canBeSet()){
			ImageView image = weak.get();
			final int image_width = image.getWidth();
			final int image_height = image.getHeight();
			
			if (image_width == 0 && image_height == 0){
				ViewTreeObserver viewTreeObserver = image.getViewTreeObserver();
				if (viewTreeObserver.isAlive()) {
					viewTreeObserver.addOnGlobalLayoutListener( new ViewTreeObserver.OnGlobalLayoutListener() {
						@Override
						public void onGlobalLayout() {
							ImageView image = weak.get();
							if (image != null && image.getWidth() > 0 && image.getHeight() > 0){
								decodeAndSet();
							}
						}
					});
				}
			} else {
				WeakReference<Bitmap> weak_cached_bitmap = bitmap_cache.get(savingpath);
				Bitmap cached_bitmap = null;
				if (weak_cached_bitmap != null){
					cached_bitmap = weak_cached_bitmap.get();
				}
				if (cached_bitmap != null){
					if (Looper.getMainLooper().getThread() == Thread.currentThread()){
						setImage(image, cached_bitmap);
					} else {
						Handler h = new Handler(Looper.getMainLooper());
						final Bitmap _cached_bitmap = cached_bitmap;
						final ImageView _image = image;
						
						h.post(new Runnable() {
							@Override
							public void run() {
								setImage(_image, _cached_bitmap);
							}
						});
					}
				} else {
					AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>(){
						@Override
						protected Bitmap doInBackground(Void... params) {
							Bitmap out = null;
							if (image_width > 0 && image_height > 0 && savingpath != null){
								out = decodeSampledBitmapFromResource(savingpath, image_width, image_height);
								bitmap_cache.put(savingpath, new WeakReference<Bitmap>(out));
							}
							return out;
						}
						@Override
						protected void onPostExecute(Bitmap result) {
							if (result != null){
								ImageView image = weak.get();
								setImage(image, result);
							}
						}
					};
					task.execute();
				}
			}
		}
	}
	@SuppressLint("NewApi")
	private void setImage(final ImageView image,Bitmap result){
		if (canBeSet()){
			image.setImageBitmap(result);
			if (with_fade){
				
				Animation a = new Animation(){
					protected void applyTransformation(float interpolatedTime, android.view.animation.Transformation t) {
						super.applyTransformation(interpolatedTime, t);
						if (Build.VERSION.SDK_INT >= 11){
							image.setAlpha(interpolatedTime);
						} else {
							image.setAlpha((int)(interpolatedTime*256));										
						}
					};
				};
				a.setInterpolator(new LinearInterpolator());
				a.setFillAfter(true);
				a.setDuration(200);
				a.setAnimationListener(new Animation.AnimationListener() {
					
					@Override
					public void onAnimationStart(Animation animation) {
						if (Build.VERSION.SDK_INT >= 11){
							image.setAlpha(0.0f);
						} else {
							image.setAlpha(0);										
						}
					}
					
					@Override
					public void onAnimationRepeat(Animation animation) {
					}
					
					@Override
					public void onAnimationEnd(Animation animation) {
						if (Build.VERSION.SDK_INT >= 11){
							image.setAlpha(1f);
						} else {
							image.setAlpha(256);										
						}
						image.setAnimation(null);
					}
				});
				image.startAnimation(a);
			}
		}
	}
	private boolean canBeSet(){
		ImageView image = null;
		boolean found = false;
		boolean image_null = false;
		boolean start_to_check = false;
		for (int i = 0; i < url_images.size() ; i++) {
			Goster im = url_images.get(i);
			if (im == this){
				image = im.weak.get();
				start_to_check = true;
				if (image == null){
					image_null = true;
					break;
				}
			} else if ( start_to_check && im.weak != null){
				ImageView image_in_stack = im.weak.get();
				if (image_in_stack == image){
					found = true;
				}
			}
		}
		if (image_null){
			return false;
		} else if (found){
			return false;
		} else {
			return true;
		}
	}
	
	
	private static String getFileIfIsInCache(String url,boolean caching){
		File files[] = cache_dir.listFiles();
		File targetfile = new File(convertUrlNoFileName(url,caching));
		String targetname = targetfile.getName();
		targetname = targetname.substring(targetname.indexOf("_",cache_file_prefix.length() + 1));
		for (int i = 0; i < files.length; i++) {
			String filename = files[i].getName();
			if (filename.startsWith(cache_file_prefix+"_") && !filename.endsWith("~")){
				filename = filename.substring(filename.indexOf("_",cache_file_prefix.length() + 1));
				if (filename.startsWith(targetname)){
					return files[i].getAbsolutePath();
				}
			}
		}
		return null;
	}
	private static String convertUrlNoFileName(String url,boolean caching){
		String s = cache_file_prefix+"_"+(caching?System.currentTimeMillis():0)+"_"+md5(url)+".tmp";
		return s;
	}
	private static final String md5(final String s) {
	    final String MD5 = "MD5";
	    try {
	        // Create MD5 Hash
	        MessageDigest digest = java.security.MessageDigest
	                .getInstance(MD5);
	        digest.update(s.getBytes());
	        byte messageDigest[] = digest.digest();

	        // Create Hex String
	        StringBuilder hexString = new StringBuilder();
	        for (byte aMessageDigest : messageDigest) {
	            String h = Integer.toHexString(0xFF & aMessageDigest);
	            while (h.length() < 2)
	                h = "0" + h;
	            hexString.append(h);
	        }
	        return hexString.toString();

	    } catch (NoSuchAlgorithmException e) {
	    	log(e);
	    }
	    return "";
	}
	
	public Goster into(ImageView imageView){
		into(imageView,null);
		return this;
	}
	public Goster into(ImageView imageView,Callback callback){
		imageView.setImageBitmap(null);
		weak = new WeakReference<ImageView>(imageView);
		this.callback = callback;
		if (path.toLowerCase().startsWith("http://") || path.toLowerCase().startsWith("https://")) {
			download(path);
		} else {
			decodeAndSet();
		}
		return this;
	}
	
	private static class DownloaderManager extends Thread {
		ExecutorService pool;
		private DownloaderManager(){
			pool = new ThreadPoolExecutor(THREAD_COUNT_FOR_POOL, 16, 1, TimeUnit.MINUTES, new BlockingLifoQueue<Runnable>());
		}
		private class Task implements Runnable {
			Goster image;
			@Override
			public void run() {
				String filename = convertUrlNoFileName(image.path,image.caching);
				String cachefile = getFileIfIsInCache(image.path,image.caching);
				if (cachefile != null){
					image.savingpath = cachefile;
				} else {
					File file = new File(cache_dir,filename);
					File tmp = new File(cache_dir,filename+ "~");
					if (file.exists() ){
						image.savingpath = file.getAbsolutePath();
					} else {
						try {
							URL u = new URL(image.path);
							URLConnection con = u.openConnection();
							InputStream input = con.getInputStream();
							FileOutputStream fo = new FileOutputStream(tmp);
							byte buf[] = new byte[1024 * 10];
							int l = input.read(buf);
							while (l>0){
								fo.write(buf,0,l);
								l = input.read(buf);
							}
							fo.close();
							input.close();
							tmp.renameTo(file);
							image.savingpath = file.getAbsolutePath();
						} catch (Exception e){
							log(e);
						}
					}
				}
				image.decodeAndSet();
			}
		}
		
		private void download(Goster urlImage){
			Task task = new Task();
			task.image = urlImage;
			pool.submit(task);
		}
	}
	
	public static class UrlImageException extends RuntimeException {
		private UrlImageException(String msg){
			super(msg);
		}
	}
	
	private static void log(Throwable t){
		if (log_enabled){
			Log.e(log_tag, "ERROR", t);
		}
	}
	private static void log(String log){
		if (log_enabled){
			Log.d(log_tag,log);
		}
	}
	
	private static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and
			// keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight
					&& (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	private static Bitmap decodeSampledBitmapFromResource(String filename,
			int reqWidth, int reqHeight) {

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filename, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(filename, options);
	}
}
