# Goster
Url Image management library for android. You can download, set image

It should be initialised once before it is used first time.

```java
Goster.init(context,new File(context.getCacheDir()+"/Caching"),7*24*60*60,3*1024*1024,"uimage");
```

Then, you can use bunch of features on it. It is not singleton. It always create an instance per job. It starts to download, decode and set when you push the changes into a image view.
```java
Goster gst = Goster.with(context)
					.load(imageurl)
					.cache(true)
					.fade(true)
					.into(imageView);
```

You can get image actual path in disk with ```getSavingPath()```.

```java
String imageSavingPath = gst.getSavingPath();
```

You have to check it is null or not. If it is null, it can be not downloaded yet, or it can not be downloaded.

Also, you can set a callback to detect image is set or not
```java
Goster.with(activity)
			.load(entry.imgUrl)
			.fade(true)
			.into(image,new Goster.Callback() {
				@Override
				public void onSuccess(Goster urlImage, ImageView imageView) {
					
				}
				
				@Override
				public void onError() {
					
				}
			});
```

