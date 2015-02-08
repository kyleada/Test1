package com.example.kyle.proxyApp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.example.kyle.tools.ReflectUtils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import dalvik.system.DexClassLoader;

public class Proxy extends Application {

	static {
		System.loadLibrary("encrypt-jni");
	}

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		// 读取程序classes.dex文件
		File libs = this.getDir("payload_lib", MODE_PRIVATE);
		String libPath = libs.getAbsolutePath();
		byte[] dexdata;
		try {
			dexdata = readDexFile();
			// 配置动态加载环境
			Object currentActivityThread = ReflectUtils.invokeStaticMethod(
					"android.app.ActivityThread", "currentActivityThread",
					new Class[] {}, new Object[] {});
			String packageName = this.getPackageName();
			HashMap mPackages = (HashMap) ReflectUtils.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mPackages");
			WeakReference wr = (WeakReference) mPackages.get(packageName);

			// 换Loader操作 动态加载如被加密又装换回来的apk文件
			DynamicDexClassLoder dLoader = new DynamicDexClassLoder(base,
					dexdata, libPath,
					(ClassLoader) ReflectUtils.getFieldOjbect(
							"android.app.LoadedApk", wr.get(), "mClassLoader"),
					getPackageResourcePath(), getDir(".dex", MODE_PRIVATE)
							.getAbsolutePath());

			ReflectUtils.setFieldOjbect("android.app.LoadedApk",
					"mClassLoader", wr.get(), dLoader);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		// 如果源应用配置有Appliction对象，则替换为源应用Applicaiton，以便不影响源程序逻辑。
		String appClassName = null;
		try {
			ApplicationInfo ai = this.getPackageManager().getApplicationInfo(
					this.getPackageName(), PackageManager.GET_META_DATA);
			Bundle bundle = ai.metaData;
			String key = "APPLICATION_CLASS_NAME";
			if (bundle != null && bundle.containsKey(key)) {
				appClassName = bundle.getString(key);
			} else {
				return;
			}
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Object currentActivityThread = ReflectUtils.invokeStaticMethod(
				"android.app.ActivityThread", "currentActivityThread",
				new Class[] {}, new Object[] {});

		Object mBoundApplication = ReflectUtils.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mBoundApplication");

		Object loadedApkInfo = ReflectUtils.getFieldOjbect(
				"android.app.ActivityThread$AppBindData", mBoundApplication,
				"info");

		ReflectUtils.setFieldOjbect("android.app.LoadedApk", "mApplication",
				loadedApkInfo, null);

		Object oldApplication = ReflectUtils.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mInitialApplication");

		ArrayList<Application> mAllApplications = (ArrayList<Application>) ReflectUtils
				.getFieldOjbect("android.app.ActivityThread",
						currentActivityThread, "mAllApplications");
		mAllApplications.remove(oldApplication);

		ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) ReflectUtils
				.getFieldOjbect("android.app.LoadedApk", loadedApkInfo,
						"mApplicationInfo");

		ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) ReflectUtils
				.getFieldOjbect("android.app.ActivityThread$AppBindData",
						mBoundApplication, "appInfo");

		appinfo_In_LoadedApk.className = appClassName;
		appinfo_In_AppBindData.className = appClassName;

		// ReflectUtils.setFieldOjbect("android.app.LoadedApk",
		// "mApplicationInfo", currentActivityThread, app);

		Application app = (Application) ReflectUtils.invokeMethod(
				"android.app.LoadedApk", "makeApplication", loadedApkInfo,
				new Class[] { boolean.class, Instrumentation.class },
				new Object[] { false, null });

		ReflectUtils.setFieldOjbect("android.app.ActivityThread",
				"mInitialApplication", currentActivityThread, app);

		// if (VERSION.SDK_INT >= 19) {
		// android.util.ArrayMap mProviderMap = (android.util.ArrayMap)
		// ReflectUtils
		// .getFieldOjbect("android.app.ActivityThread",
		// currentActivityThread, "mProviderMap");
		// Iterator it = mProviderMap.values().iterator();
		// while (it.hasNext()) {
		// Object providerClientRecord = it.next();
		// Object localProvider = ReflectUtils.getFieldOjbect(
		// "android.app.ActivityThread$ProviderClientRecord",
		// providerClientRecord, "mLocalProvider");
		// ReflectUtils.setFieldOjbect("android.content.ContentProvider",
		// "mContext", localProvider, app);
		// }
		// } else {
		HashMap mProviderMap = (HashMap) ReflectUtils.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mProviderMap");
		Iterator it = mProviderMap.values().iterator();
		while (it.hasNext()) {
			Object providerClientRecord = it.next();
			Object localProvider = ReflectUtils.getFieldOjbect(
					"android.app.ActivityThread$ProviderClientRecord",
					providerClientRecord, "mLocalProvider");
			ReflectUtils.setFieldOjbect("android.content.ContentProvider",
					"mContext", localProvider, app);
		}
		// }

		app.onCreate();

	}

	private void splitPayLoadFromDex(byte[] data) throws IOException {

		// int ablen = data.length;
		// byte[] dexlen = new byte[4];
		// System.arraycopy(data, ablen - 4, dexlen, 0, 4);
		// ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
		// DataInputStream in = new DataInputStream(bais);
		// int readInt = in.readInt();
		//
		// byte[] apkdata = new byte[readInt];
		// System.arraycopy(data, ablen - 4 - readInt, apkdata, 0, readInt);
		//
		// // apk data
		// byte[] newdex = decrypt(apkdata);

		//openDexFile(data);

	}

	public void openDexFile2(byte[] bytearray) {
		Class<?> obj_class;
		try {
			obj_class = Class.forName("dalvik.system.DexFile");
			Method methodOpenDexFile = obj_class.getDeclaredMethod(
					"openDexFile", new Class[] { byte[].class });
			methodOpenDexFile.setAccessible(true);

			Method methodGetClassNameList = obj_class.getDeclaredMethod(
					"getClassNameList", new Class[] { int.class });
			methodGetClassNameList.setAccessible(true);

			Method methodDefineClass = obj_class.getDeclaredMethod(
					"defineClass", new Class[] { String.class,
							ClassLoader.class, int.class });
			methodDefineClass.setAccessible(true);

			int cookie = (Integer) methodOpenDexFile.invoke(null, bytearray);
			Class<?> cls = null;
			String[] as = (String[]) methodGetClassNameList
					.invoke(null, cookie);
			for (int z = 0; z < as.length; z++) {

				if (as[z].startsWith("android.")) {
					continue;
				} else if (as[z].equals("com.example.blank.MainActivity")) {
					cls = (Class<?>) methodDefineClass.invoke(null,
							as[z].replace('.', '/'), this.getClassLoader(),
							cookie);
				} else {
					methodDefineClass.invoke(null, as[z].replace('.', '/'),
							this.getClassLoader(), cookie);
				}

			}

			if (cls != null) {
				System.out.println(cls.getName());
				Intent intent = new Intent(this, cls);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				// startActivity(intent);
			}

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private byte[] readDexFileFromApk() throws IOException {
		String sourceDir = this.getApplicationInfo().sourceDir;
		ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
		ZipInputStream localZipInputStream = new ZipInputStream(
				new BufferedInputStream(new FileInputStream(sourceDir)));
		while (true) {
			ZipEntry localZipEntry = localZipInputStream.getNextEntry();
			if (localZipEntry == null) {
				localZipInputStream.close();
				break;
			}
			if (localZipEntry.getName().equals("classes.dex")) {
				byte[] arrayOfByte = new byte[1024];
				while (true) {
					int i = localZipInputStream.read(arrayOfByte);
					if (i == -1)
						break;
					dexByteArrayOutputStream.write(arrayOfByte, 0, i);
				}
			}
			localZipInputStream.closeEntry();
		}
		localZipInputStream.close();
		return dexByteArrayOutputStream.toByteArray();
	}

	// public void openDexFile2(byte[] bytearray) {
	// Class<?> obj_class;
	//
	// try {
	// int cookie = openDexFile(bytearray);
	// Class<?> cls = null;
	// String as[] = getClassNameList(cookie);
	// for (int z = 0; z < as.length; z++) {
	// if (as[z].equals("com.immunapp.hes2013.MainActivity")) {
	// cls = defineClass(as[z].replace('.', '/'),
	// getClassLoader(), cookie);
	// } else {
	// defineClass(as[z].replace('.', '/'), getClassLoader(),
	// cookie);
	// }
	// }
	//
	// } catch (Exception e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }

	public byte[] readDexFile() {

		try {
			InputStream is = getResources().getAssets().open("classes.dex", 0);
			return toByteArray(is);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] toByteArray(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
		}
		return output.toByteArray();
	}

}
