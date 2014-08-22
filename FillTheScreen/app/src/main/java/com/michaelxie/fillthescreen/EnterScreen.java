package com.michaelxie.fillthescreen;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import com.facebook.Settings;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import bolts.AppLinks;

import static java.lang.Math.sqrt;

/**
 * Created by Michael Xie on 7/26/2014.
 */
public class EnterScreen extends Activity {
	enterSurface enterSurface;
	public static int screenWidth, screenHeight;
	public static InterstitialAd interstitial;
	private Sounds soundsModule;
	private static int numBallsBasedOnScreen;
	public static boolean makeSounds;
	private SharedPreferences settings;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		enterSurface = new enterSurface(this);
		setContentView(enterSurface);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Create the interstitial ad.
//		interstitial = new InterstitialAd(this);
//		interstitial.setAdUnitId(Ads.AD_ID);
//		AdRequest adRequest = new AdRequest.Builder().addTestDevice("3C2F4500A5404F50D9E60E07C7227E75").build();
//		interstitial.loadAd(adRequest);

		settings = getSharedPreferences(Ids.LOCAL_SOUND_PREF_FILE, 0);
		makeSounds = settings.getBoolean("makeSounds", true);

		soundsModule = new Sounds(getApplicationContext());

		Settings.sdkInitialize(this);
		Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(this, getIntent());
		if (targetUrl != null) {
			Log.i("Activity", "App Link Target URL: " + targetUrl.toString());
		}


		//Upload screen view to Google Analytics
		// Get tracker.
		Tracker t = ((FillTheScreen)getApplication()).getTracker(TrackerName.APP_TRACKER);

		// Set screen name.
		// Where path is a String representing the screen name.
		t.setScreenName("EnterScreen onCreate");

		// Send a screen view.
		t.send(new HitBuilders.AppViewBuilder().build());
	}

	@Override
	protected void onResume() {
		super.onResume();

		//Check if user's Google Play services is updated
		int status;
		int MAIN_ACTIVITY = 0;

		if((status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext())) != ConnectionResult.SUCCESS) {
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(status, this, MAIN_ACTIVITY);

			errorDialog.show();
		}

		if(enterSurface.surfaceHolder.getSurface().isValid()) enterSurface.onResumeEnterSurface();
	}

	@Override
	protected void onPause() {
		super.onPause();
		enterSurface.onPauseEnterSurface();
	}

	class enterSurface extends SurfaceView implements Runnable, SurfaceHolder.Callback {
		Thread drawer = null;
		SurfaceHolder surfaceHolder;

		ArrayList<Circle> circles;
		ArrayList<Circle> activeCircles;
		Semaphore arrayLock;

		Bitmap background;

		volatile boolean running = false;

		private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private Paint contrastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		private void initialize(Context context) {
			circles = new ArrayList<Circle>();
			activeCircles = new ArrayList<Circle>();
			float x = (float)(Math.random() * (screenWidth - 100) + 50);
			float y = (float)(Math.random() * (screenHeight - 100) + 50);
			activeCircles.add(new Circle(x, y, getResources().getDisplayMetrics()));
			arrayLock = new Semaphore(1);
			surfaceHolder = getHolder();
			surfaceHolder.addCallback(this);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(Color.WHITE);
			contrastPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			contrastPaint.setColor(Color.YELLOW);
			this.setOnTouchListener(touchListener);
			this.setOnLongClickListener(longClickListener);
		}

		//Constructors
		public enterSurface(Context context) {
			super(context);
			initialize(context);
		}

		public enterSurface(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			initialize(context);
		}

		public enterSurface(Context context, AttributeSet attrs) {
			super(context, attrs);
			initialize(context);
		}

		public void addCircle(Circle c) {
			lockUI();
			if(isTouching(c, circles, false, 0) == -1) {
				activeCircles.add(c);
				if(makeSounds) soundsModule.startSlideSound();
			}
			unlockUI();
		}

		private Drawable title, playButton, soundsOnButton, soundsOffButton;
		int playButtonX, playButtonY, soundsX, soundsY;
		DisplayMetrics displayMetrics;
		private float textSize;

		private int calculateNumBalls() {
			float areaInSquareInches = (float) ((displayMetrics.widthPixels * displayMetrics.heightPixels) / Math.pow(displayMetrics.densityDpi, 2));
			return (int)Math.ceil(areaInSquareInches / 3); //A ball every 3 square inches
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			displayMetrics = getResources().getDisplayMetrics();
			textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, displayMetrics);
			numBallsBasedOnScreen = calculateNumBalls();
			//Set up balls and get the canvas dimensions
			Canvas canvas = surfaceHolder.lockCanvas();
			screenWidth = canvas.getWidth();
			screenHeight = canvas.getHeight();

			//Set title
			title = getResources().getDrawable(R.drawable.title);
			int x = screenWidth / 2 - title.getIntrinsicWidth() / 2;
			int y = screenHeight / 3 - title.getIntrinsicHeight() / 2;
			title.setBounds(x, y, x + title.getIntrinsicWidth(), y + title.getIntrinsicHeight());

			//Set play button
			playButton = getResources().getDrawable(R.drawable.play_button);
			playButtonX = screenWidth / 2 - playButton.getIntrinsicWidth() / 2;
			playButtonY = y + title.getIntrinsicHeight();
			playButton.setBounds(playButtonX, playButtonY, playButtonX + playButton.getIntrinsicWidth(), playButtonY + playButton.getIntrinsicHeight());

			//Set sounds button
			soundsOnButton = getResources().getDrawable(R.drawable.ic_action_volume_on);
			soundsX = screenWidth - soundsOnButton.getIntrinsicWidth() - 10;
			soundsY = 10;
			soundsOnButton.setBounds(soundsX, soundsY, soundsX + soundsOnButton.getIntrinsicWidth(), soundsY + soundsOnButton.getIntrinsicHeight());

			soundsOffButton = getResources().getDrawable(R.drawable.ic_action_volume_muted);
			soundsOffButton.setBounds(soundsX, soundsY, soundsX + soundsOffButton.getIntrinsicWidth(), soundsY + soundsOffButton.getIntrinsicHeight());

			background = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.RGB_565);
			canvas.setBitmap(background);
			surfaceHolder.unlockCanvasAndPost(canvas);
			onResumeEnterSurface();
		}
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {

		}

		public void onResumeEnterSurface(){
			initialize(getContext());
			running = true;
			drawer = new Thread(this);
			drawer.start();
		}

		public void onPauseEnterSurface(){
			boolean retry = true;
			running = false;
			while(retry){
				try {
					if(drawer != null) drawer.join();
					retry = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		private void drawBackground(Canvas canvas) {
			canvas.drawColor(Color.parseColor("#0099cc"));
		}
		
		private float computeDistanceBetweenPoints(float x1, float y1, float x2, float y2) {
			return (float)sqrt((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1));
		}

		private float computeDistance(Circle c1, Circle c2) {
			return computeDistanceBetweenPoints(c1.getX(), c1.getY(), c2.getX(), c2.getY());
		}

		//Two different possible policies here - do I stop on border or not?
		private int isTouching(Circle c, ArrayList<Circle> circleArray, boolean removeOnHit, int startIndex) {
			for(int i = startIndex; i < circleArray.size(); i++) {
				float distance = computeDistance(c, circleArray.get(i));
				if(distance <= (c.getRadius() + circleArray.get(i).getRadius())) {
					if(makeSounds) soundsModule.startPopSound();
					if(removeOnHit) circleArray.remove(i);
					return i;
				}
			}
			return -1;
		}

		private void updateCircleCollisions() {
			ArrayList<Circle> toRemove = new ArrayList<Circle>();
			for(Circle c : activeCircles) {
				if(isTouching(c, circles, false, 0) != -1) {
					toRemove.add(c);
				}
			}
			for(Circle c : toRemove) {
				activeCircles.remove(c);
				circles.add(c);
			}
		}

		private void drawCircles(Canvas canvas) {
			for(Circle c : activeCircles) {
					canvas.drawCircle(c.getX(), c.getY(), c.getRadiusAndInc(), paint);
			}

			for(Circle c : circles) {
				canvas.drawCircle(c.getX(), c.getY(), c.getRadius(), paint);
			}
		}

		private void drawTitle(Canvas canvas) {
			title.draw(canvas);
			playButton.draw(canvas);
			if(makeSounds) {
				soundsOnButton.draw(canvas);
			} else {
				soundsOffButton.draw(canvas);
			}
			//Draw help button
			contrastPaint.setTextSize(textSize);
			contrastPaint.setFakeBoldText(true);
			canvas.drawText("?", 30, textSize, contrastPaint);
		}

		private volatile boolean longPress = false;
		private volatile float touchX, touchY;
		private View.OnLongClickListener longClickListener = new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View pView) {
				//Only make new circles on long click
				Circle c = new Circle(touchX, touchY, getResources().getDisplayMetrics());
				addCircle(c); //Thread-safe method that also checks if in valid position to add new circle
				longPress = true;
				return true;
			}
		};

		private View.OnTouchListener touchListener = new View.OnTouchListener() {

			@Override
			public boolean onTouch(View pView, MotionEvent pEvent) {
				touchX = pEvent.getX();
				touchY = pEvent.getY();

				// We're only interested in when the button is released.
				if (pEvent.getAction() == MotionEvent.ACTION_UP) {
					//Only get take user circles out of active circles
					int indexToRemove = activeCircles.size() - 1;
					if (longPress && indexToRemove > 0) {
						lockUI();
						circles.add(activeCircles.get(activeCircles.size() - 1));
						activeCircles.remove(activeCircles.size() - 1);
						unlockUI();
						// Do something when the button is released.
						longPress = false;
					} else if(touchX > playButtonX && touchX < (playButtonX + playButton.getIntrinsicWidth())
								&& touchY > playButtonY && touchY < (playButtonY + playButton.getIntrinsicHeight())) {
						Intent intent = new Intent(getContext(), ChooseScreen.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
						startActivity(intent);
					} else if(touchX < 75 && touchY < 75) { //Help button
						Intent intent = new Intent(getContext(), HelpScreen.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
						startActivity(intent);
					} else if(touchX > soundsX && touchX < (soundsX + soundsOnButton.getIntrinsicWidth())
								&& touchY > soundsY && touchY < (soundsY + soundsOnButton.getIntrinsicHeight())) {
						makeSounds = !makeSounds;
						SharedPreferences.Editor editor = settings.edit();
						editor.putBoolean("makeSounds", makeSounds);
						// Commit the edits!
						editor.apply();
					}
				}
				return false;
			}
		};

		@Override
		public void run() {
			while(running){
				if(surfaceHolder.getSurface().isValid()){
					Canvas canvas = surfaceHolder.lockCanvas();
					drawBackground(canvas);
					lockUI();
					updateCircleCollisions();
					drawCircles(canvas);
					unlockUI();
					drawTitle(canvas);
					try{
						Thread.sleep(50);
					} catch(InterruptedException e) {
						break;
					}
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}

		private void lockUI() {
			try{
				arrayLock.acquire();
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		private void unlockUI() {
			arrayLock.release();
		}
	}

	// Invoke displayInterstitial() when you are ready to display an interstitial.
	public static void displayInterstitial() {
		if (interstitial.isLoaded()) {
			interstitial.show();
		}
		AdRequest adRequest = new AdRequest.Builder().build();
		interstitial.loadAd(adRequest);
	}

	public static int getNumBallsBasedOnScreen() {
		return numBallsBasedOnScreen;
	}
}