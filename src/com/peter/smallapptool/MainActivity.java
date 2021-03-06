package com.peter.smallapptool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.peter.smallapptool.HorizontalPullView.ScrollListener;
import com.peter.smallapptool.MyMenu.ItemViewCreater;
import com.peter.smallapptool.MyMenu.ItemViewOnClickListener;
import com.peter.smallapptool.R;
import com.peter.smallapptool.AppAdapter.AppInfo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue.IdleHandler;
import android.os.Vibrator;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener,
		OnLongClickListener {

	private String forecStopPackageName;
	private AppAdapter<AppInfo> appAdapter;
	private static final String APP_STATE = "app_state";
	private BroadcastReceiver forceStopReceiver;
	private ListView appListView;
	private FrameLayout mContainer;
	private MyMenu mMenu;
	private View mCover;
	private int[] menuTitleRes = { R.string.action_refresh,
			R.string.action_help, R.string.action_about,
			R.string.action_feedback, R.string.action_about_exit };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		initView();
		relodData();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	private void initView() {
		getOverflowMenu();
		mContainer = (FrameLayout) findViewById(R.id.container);
		mCover = findViewById(R.id.cover);
		mCover.setOnTouchListener(new OnTouchListener() {

			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}
		});
		mMenu = new MyMenu(MainActivity.this);
		View anchor = findViewById(R.id.menu);
		mMenu.setAnchor(anchor);
		for (int i = 0; i < menuTitleRes.length; i++) {
			mMenu.addMenuItem(i, menuTitleRes[i], menuTitleRes[i]);
		}
		mMenu.setMenuItemCreater(new ItemViewCreater() {

			@Override
			public View createView(int position, ViewGroup parent) {
				LayoutInflater factory = LayoutInflater.from(MainActivity.this);
				View menu = factory.inflate(R.layout.menu_item, parent, false);
				TextView tv = (TextView) menu.findViewById(R.id.text);
				tv.setText(menuTitleRes[position]);
				return menu;
			}
		});
		mMenu.setMenuItemOnClickListener(new ItemViewOnClickListener() {

			@Override
			public void OnItemClick(int order) {

				switch (order) {
				case R.string.action_refresh:
					relodData();
					break;
				case R.string.action_help:
					showToast(R.string.action_help_msg);
					break;
				case R.string.action_about:
					showToast(R.string.action_about_msg);
					break;
				case R.string.action_feedback:
					break;
				case R.string.action_about_exit:
					finish();
					break;
				}
				mMenu.dismiss();
			}
		});
	}

	private void showLoading() {
		mCover.setVisibility(View.VISIBLE);
		mCover.findViewById(R.id.loading).clearAnimation();
		Animation anim = AnimationUtils.loadAnimation(this,
				R.anim.data_loading_rotate);
		mCover.findViewById(R.id.loading).startAnimation(anim);
	}

	private void hideLoading() {
		mCover.findViewById(R.id.loading).clearAnimation();
		mCover.setVisibility(View.INVISIBLE);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			mMenu.show();
		}
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * 获取所有的应用信息
	 * 
	 * @return
	 */
	private List<AppInfo> getAllAppInfos() {
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();

		PackageManager pm = getPackageManager();
		List<ApplicationInfo> appList = pm.getInstalledApplications(0);
		List<AppInfo> allNoSystemApps = new ArrayList<AppInfo>(appList.size());

		SharedPreferences spLock = getSharedPreferences(APP_STATE, MODE_PRIVATE);

		for (ApplicationInfo info : appList) {// 非系统APP
			if (info != null && !isSystemApp(info)
					&& !info.packageName.equals(getPackageName())) {
				AppInfo inf = new AppInfo();
				inf.packageName = info.packageName;
				inf.state = AppInfo.NO_RUNNING;

				if (isRunndingApp(inf, runningApps)) {// 运行的APP
					inf.state = AppInfo.RUNNING;
					int state = spLock.getInt(info.packageName,
							AppInfo.NO_RUNNING);
					if (state == AppInfo.LOCKED) {// lock的APP
						inf.state = AppInfo.LOCKED;
					}
				} else {
					int state = spLock.getInt(info.packageName,
							AppInfo.NO_RUNNING);// 默认
					inf.state = state;
				}
				allNoSystemApps.add(inf);
			}
		}

		Collections.sort(allNoSystemApps, AppComparator);

		return allNoSystemApps;
	}

	public static long getMemoryTotalKb() {
		long totalSize = -1L;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("/proc/meminfo"));
			String line, totle = null;

			while ((line = br.readLine()) != null) {
				if (line.startsWith("MemTotal:")) {
					totle = line.split(" +")[1];
					break;
				}
			}

			totalSize = Long.valueOf(totle);

			return totalSize;
		} catch (Exception e) {
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception e) {
					// ignore
				}
			}
		}

		return totalSize;
	}

	public static long getMemoryFreeKb() {
		long freeSize = -1L;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("/proc/meminfo"));
			String line, buff = null, cache = null, free = null;
			int count = 0;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("MemFree")) {
					count++;
					free = line.split(" +")[1];
					if (count >= 3) {
						break;
					}
				} else if (line.startsWith("Buffers")) {
					count++;
					buff = line.split(" +")[1];
					if (count >= 3) {
						break;
					}
				} else if (line.startsWith("Cached")) {
					count++;
					cache = line.split(" +")[1];
					if (count >= 3) {
						break;
					}
				} else {
					continue;
				}
			}

			freeSize = (Long.valueOf(free) + Long.valueOf(buff) + Long
					.valueOf(cache));

			return freeSize;
		} catch (Exception e) {
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return freeSize;
	}

	Comparator<AppInfo> AppComparator = new Comparator<AppInfo>() {

		@Override
		public int compare(AppInfo lhs, AppInfo rhs) {
			if (lhs.state < rhs.state) {
				return 1;
			} else if (lhs.state > rhs.state) {
				return -1;
			}
			return 0;
		}
	};

	private boolean isRunndingApp(AppInfo info,
			List<RunningAppProcessInfo> runningApps) {
		for (RunningAppProcessInfo runInfo : runningApps) {
			String[] pkgs = runInfo.pkgList;
			for (String pagName : pkgs) {
				if (!TextUtils.isEmpty(pagName)
						&& pagName.equals(info.packageName)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isSystemApp(ApplicationInfo appInfo) {
		if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0) {// system apps
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (forceStopReceiver != null) {
			unregisterReceiver(forceStopReceiver);
			forceStopReceiver = null;
			appAdapter.notifyDataSetChanged();
		}
	}

	private void getOverflowMenu() {
		try {
			ViewConfiguration config = ViewConfiguration.get(this);
			Field menuKeyField = ViewConfiguration.class
					.getDeclaredField("sHasPermanentMenuKey");
			if (menuKeyField != null) {
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void relodData() {
		Looper.myQueue().addIdleHandler(new IdleHandler() {

			@Override
			public boolean queueIdle() {
				if (appAdapter == null) {
					appAdapter = new AppAdapter<AppInfo>(MainActivity.this);
				}
				appAdapter.setData(getAllAppInfos());
				mContainer.removeAllViews();
				View.inflate(getApplicationContext(), R.layout.textview,
						mContainer);
				View.inflate(getApplicationContext(), R.layout.list, mContainer);
				TextView tv = (TextView) findViewById(R.id.title);
				HorizontalPullView hp = (HorizontalPullView) findViewById(R.id.hp);
				hp.setScrollListener(mScrollListener);
				hp.setTextView(tv);
				appListView = (ListView) findViewById(R.id.app_list);
				appListView.setAdapter(appAdapter);
				return false;
			}
		});

		refreshMemery();
	}
	
	private ScrollListener mScrollListener = new ScrollListener() {

		@Override
		public void scrollEnd() {
			Log.i("peter", "end==========");
			forceStopAll();
		}
	};

	private void refreshMemery() {

		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {

			@Override
			public void run() {
				TextView memery = (TextView) findViewById(R.id.memery);
				String total = Formatter.formatFileSize(getBaseContext(),
						getMemoryTotalKb() * 1024);
				String used = Formatter.formatFileSize(getBaseContext(),
						getMemoryTotalKb() * 1024 - getMemoryFreeKb() * 1024);
				String info = used + "/" + total;
				memery.setText(info);
			}

		}, 1600);
	}

	public void setImageBitmapWithFade(final ImageView imageView,
			final Bitmap bitmap) {
		Resources resources = imageView.getResources();
		BitmapDrawable bitmapDrawable = new BitmapDrawable(resources, bitmap);
		setImageDrawableWithFade(imageView, bitmapDrawable);
	}

	public void setImageDrawableWithFade(final ImageView imageView,
			final Drawable drawable) {
		Drawable currentDrawable = imageView.getDrawable();
		if (currentDrawable != null) {
			Drawable[] arrayDrawable = new Drawable[2];
			arrayDrawable[0] = currentDrawable;
			arrayDrawable[1] = drawable;
			TransitionDrawable transitionDrawable = new TransitionDrawable(
					arrayDrawable);
			transitionDrawable.setCrossFadeEnabled(true);
			imageView.setImageDrawable(transitionDrawable);
			transitionDrawable.startTransition(600);
		} else {
			imageView.setImageDrawable(drawable);
		}
	}

	private void uninstall(final AppInfo info) {
		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected void onPreExecute() {
				showLoading();
			}

			@Override
			protected void onPostExecute(Boolean result) {
				hideLoading();
				if (result) {
					info.state = AppInfo.UNINSTALLED;
					appAdapter.notifyDataSetChanged();
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				boolean result = true;
				String cmd = "pm uninstall " + info.packageName + " \n";
				int exeCode = ProcessUtils.executeCommand(cmd, 1000);

				if (exeCode == 0 || exeCode == -2 || exeCode == -3
						|| exeCode == -4) {
					result = true;
				} else {
					result = false;
					registReceiver(info);
					Uri uri = Uri.parse("package:" + info.packageName);
					Intent intent = new Intent(Intent.ACTION_DELETE, uri);
					startActivity(intent);
				}
				return result;
			}

		}.execute();
	}

	private void clearCache(final AppInfo info) {
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected void onPreExecute() {
				showLoading();
			}

			@Override
			protected void onPostExecute(Void result) {
				hideLoading();
			}

			@Override
			protected Void doInBackground(Void... params) {
				String cmd = "pm clear " + info.packageName + " \n";
				int exeCode = ProcessUtils.executeCommand(cmd, 1000);
				if (exeCode == 0 || exeCode == -2 || exeCode == -3
						|| exeCode == -4) {

				} else {
					showForceStopView(info);
				}
				return null;
			}

		}.execute();
	}

	private void forceStopAll() {
		final List<AppInfo> infos = appAdapter.getData();
		
		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected void onPreExecute() {
				showLoading();
			}

			@Override
			protected void onPostExecute(Boolean result) {
				hideLoading();
				for(AppInfo info : infos) {
					if (info.state == AppInfo.RUNNING) {
						info.state = AppInfo.KILLING;
					}
				}
				appAdapter.notifyDataSetChanged();
				refreshMemery();
			}

			@Override
			protected Boolean doInBackground(Void... params) {

				for (AppInfo info : infos) {
					if (info.state == AppInfo.RUNNING) {
						
						Log.i("peter", "stop all -name=" + info.appName);
						String cmd = "am force-stop " + info.packageName
								+ " \n";
						ProcessUtils.executeCommand(cmd, 1000);
					}
				}
				try {
					Thread.sleep(600);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return true;
			}

		}.execute();
	}

	private void forceStop(final AppInfo info) {
		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected void onPreExecute() {
				showLoading();
			}

			@Override
			protected void onPostExecute(Boolean result) {
				hideLoading();
				if (result) {
					info.state = AppInfo.KILLING;
					appAdapter.notifyDataSetChanged();
					refreshMemery();
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				boolean result = true;
				String cmd = "am force-stop " + info.packageName + " \n";
				int exeCode = ProcessUtils.executeCommand(cmd, 1000);
				if (exeCode == 0 || exeCode == -2 || exeCode == -3
						|| exeCode == -4) {
					result = true;
				} else {
					result = false;
					showForceStopView(info);
				}

				return result;
			}

		}.execute();
	}

	private void showForceStopView(AppInfo info) {
		int version = Build.VERSION.SDK_INT;
		Intent intent = new Intent();
		if (version >= 9) {
			intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
			Uri uri = Uri.fromParts("package", info.packageName, null);
			intent.setData(uri);
		} else {
			final String appPkgName = "pkg";
			intent.setAction(Intent.ACTION_VIEW);
			intent.setClassName("com.android.settings",
					"com.android.settings.InstalledAppDetails");
			intent.putExtra(appPkgName, info.packageName);
		}
		registReceiver(info);
		startActivity(intent);
	}

	private void registReceiver(AppInfo info) {
		forecStopPackageName = info.packageName;
		forceStopReceiver = new MyReceiver(info);
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		registerReceiver(forceStopReceiver, filter);
	}

	private void finishSetting() {
		Intent in = new Intent();
		in.setClass(MainActivity.this, MainActivity.class);
		in.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);// 确保finish掉setting
		in.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);// 确保MainActivity不被finish掉
		startActivity(in);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.uninstall:
			View parent = (View) v.getParent().getParent();
			AppInfo info = (AppInfo) parent.getTag(R.id.appinfo);
			uninstall(info);
			break;
		case R.id.detail:
			parent = (View) v.getParent().getParent();
			info = (AppInfo) parent.getTag(R.id.appinfo);
			showForceStopView(info);
			break;
		case R.id.clearcache:
			parent = (View) v.getParent().getParent();
			info = (AppInfo) parent.getTag(R.id.appinfo);
			clearCache(info);
			break;
		case R.id.kill_lock:
			parent = (View) v.getParent().getParent();
			info = (AppInfo) parent.getTag(R.id.appinfo);
			if (info.state == AppInfo.RUNNING) {
				forceStop(info);
			}
			break;
		case R.id.menu:
			mMenu.show();
			break;
		case R.id.item:
			parent = (View) v.getParent();
			info = (AppInfo) parent.getTag(R.id.appinfo);
			if (info.state != AppInfo.LOCKED) {
				info.mShowOperation = !info.mShowOperation;
				appAdapter.notifyDataSetChanged();
			}
			break;
		case R.id.app_icon:
			parent = (View) v.getParent().getParent();
			info = (AppInfo) parent.getTag(R.id.appinfo);
			Intent intent = getPackageManager().getLaunchIntentForPackage(
					info.packageName);
			startActivity(intent);

			if (info.state != AppInfo.LOCKED && info.state != AppInfo.RUNNING) {
				SharedPreferences sp = getSharedPreferences(APP_STATE,
						MODE_PRIVATE);
				sp.edit().putInt(info.packageName, info.state + 1).commit();
			}
			break;
		default:
			break;
		}
	}

	private class MyReceiver extends BroadcastReceiver {

		AppInfo mInfo;

		public MyReceiver(AppInfo info) {
			mInfo = info;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			Uri data = intent.getData();
			if (data != null) {
				String str = data.getSchemeSpecificPart();
				if (!TextUtils.isEmpty(forecStopPackageName)
						&& !TextUtils.isEmpty(str)
						&& str.equals(forecStopPackageName)) {

					String action = intent.getAction();
					if (Intent.ACTION_PACKAGE_RESTARTED.equals(action)) {
						finishSetting();
						mInfo.state = AppInfo.KILLING;
						refreshMemery();
					} else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
						mInfo.state = AppInfo.UNINSTALLED;
					}
				}
			}
		}

	}

	@Override
	public boolean onLongClick(View v) {

		switch (v.getId()) {
		case R.id.kill_lock:
			View parent = (View) v.getParent().getParent();
			AppInfo info = (AppInfo) parent.getTag(R.id.appinfo);
			if (info.state == AppInfo.RUNNING) {
				info.state = AppInfo.LOCKED;
				info.mShowOperation = false;
			} else if (info.state == AppInfo.LOCKED) {
				info.state = AppInfo.RUNNING;
			}

			SharedPreferences sp = getSharedPreferences(APP_STATE, MODE_PRIVATE);
			sp.edit().putInt(info.packageName, info.state).commit();
			appAdapter.notifyDataSetChanged();
			Vibrator mVibrator01 = (Vibrator) getApplication()
					.getSystemService(Service.VIBRATOR_SERVICE);
			mVibrator01.vibrate(100);
			break;
		}

		return true;
	}

	private void showToast(int StringId) {
		Toast toast = Toast.makeText(getApplicationContext(), StringId,
				Toast.LENGTH_LONG);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

}
