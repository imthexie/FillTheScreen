package com.michaelxie.fillthescreen;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import static java.lang.Math.sqrt;

/**
 * Created by Michael Xie on 8/2/2014.
 */
public class ChooseScreen extends Activity {
	chooseSurface chooseSurface;
	private static final String TAG = "ChooseScreen";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		chooseSurface = new chooseSurface(this);
		setContentView(chooseSurface);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

	}

	@Override
	protected void onResume() {
		super.onResume();
		if (chooseSurface.surfaceHolder.getSurface().isValid()) chooseSurface.onResumeEnterSurface();
	}

	@Override
	protected void onPause() {
		super.onPause();
		chooseSurface.onPauseEnterSurface();
	}

	class chooseSurface extends SurfaceView implements Runnable, SurfaceHolder.Callback {
		Thread drawer = null;
		SurfaceHolder surfaceHolder;
		Bitmap background;

		volatile boolean running = false;

		private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private Paint contrastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		private void initialize(Context context) {
			surfaceHolder = getHolder();
			surfaceHolder.addCallback(this);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(Color.WHITE);
			contrastPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			contrastPaint.setColor(Color.YELLOW);
			this.setOnTouchListener(touchListener);
		}

		//Constructors
		public chooseSurface(Context context) {
			super(context);
			initialize(context);
		}

		public chooseSurface(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			initialize(context);
		}

		public chooseSurface(Context context, AttributeSet attrs) {
			super(context, attrs);
			initialize(context);
		}

		private Drawable classic, infinite, survival, dynamic;
		float classic_bottom, infinite_bottom, survival_bottom, dynamic_bottom;

		DisplayMetrics displayMetrics;
		private float textSize;
		@Override
		public void surfaceCreated(SurfaceHolder holder) {

			displayMetrics = getResources().getDisplayMetrics();
			textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, displayMetrics);

			//Set up balls and get the canvas dimensions
			Canvas canvas = surfaceHolder.lockCanvas();

			classic = getResources().getDrawable(R.drawable.classic);
			infinite = getResources().getDrawable(R.drawable.infinite);
			survival = getResources().getDrawable(R.drawable.survival);
			dynamic = getResources().getDrawable(R.drawable.dynamic);



			int quarterWidth = EnterScreen.screenWidth / 4;
			int threeQuartersWidth = EnterScreen.screenWidth * 3 / 4;
			int quarterHeight = EnterScreen.screenHeight / 4;
			int threeQuarterHeight = EnterScreen.screenHeight * 3 / 4;


			int x = quarterWidth - classic.getIntrinsicWidth() / 2;
			int y = quarterHeight - classic.getIntrinsicHeight() / 2;
			classic.setBounds(x, y, x + classic.getIntrinsicWidth(), y + classic.getIntrinsicHeight());
			classic_bottom = y + classic.getIntrinsicHeight() + 25;

			x = threeQuartersWidth - infinite.getIntrinsicWidth() / 2;
			y = quarterHeight - infinite.getIntrinsicHeight() / 2;
			infinite.setBounds(x, y, x + infinite.getIntrinsicWidth(), y + infinite.getIntrinsicHeight());
			infinite_bottom = y + infinite.getIntrinsicHeight() + 25;

			x = quarterWidth - survival.getIntrinsicWidth() / 2;
			y = threeQuarterHeight - survival.getIntrinsicHeight() / 2;
			survival.setBounds(x, y, x + survival.getIntrinsicWidth(), y + survival.getIntrinsicHeight());
			survival_bottom = y + survival.getIntrinsicHeight() + 25;

			x = threeQuartersWidth - dynamic.getIntrinsicWidth() / 2;
			y = threeQuarterHeight - dynamic.getIntrinsicHeight() / 2;
			dynamic.setBounds(x, y, x + dynamic.getIntrinsicWidth(), y + dynamic.getIntrinsicHeight());
			dynamic_bottom = y + dynamic.getIntrinsicHeight() + 25;

			background = Bitmap.createBitmap(EnterScreen.screenWidth, EnterScreen.screenHeight, Bitmap.Config.RGB_565);
			canvas.setBitmap(background);
			surfaceHolder.unlockCanvasAndPost(canvas);
			onResumeEnterSurface();
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
		}

		public void onResumeEnterSurface() {
			initialize(getContext());
			running = true;
			drawer = new Thread(this);
			drawer.start();
		}

		public void onPauseEnterSurface() {
			boolean retry = true;
			running = false;
			while (retry) {
				try {
					if (drawer != null) drawer.join();
					retry = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		private void drawBackground(Canvas canvas) {
			canvas.drawColor(Color.parseColor("#0099cc"));
		}

		private View.OnTouchListener touchListener = new View.OnTouchListener() {

			@Override
			public boolean onTouch(View pView, MotionEvent pEvent) {
				if (pEvent.getAction() == (MotionEvent.ACTION_UP)) {
					float x = pEvent.getX();
					float y = pEvent.getY();
					//By quadrant
					if (x < EnterScreen.screenWidth / 2) {
						if (y < EnterScreen.screenHeight / 2) {
							//First Quadrant
							Intent intent = new Intent(getApplicationContext(), GameScreen.class);
							startActivity(intent);
							finish();
						} else {
							//Third Quadrant
							Intent intent = new Intent(getApplicationContext(), SurvivalScreen.class);
							startActivity(intent);
							finish();
						}
					} else {
						if (y < EnterScreen.screenHeight / 2) {
							//Second Quadrant
							Intent intent = new Intent(getApplicationContext(), InfiniteScreen.class);
							startActivity(intent);
							finish();
						} else {
							//Fourth Quadrant
							Intent intent = new Intent(getApplicationContext(), DynamicScreen.class);
							startActivity(intent);
							finish();
						}
					}
				}
				return true;
			}
		};

		private float getTextWidth(String text, Paint paint) {
			float[] widths = new float[text.length()];
			paint.getTextWidths(text, 0, widths.length, widths);
			float textWidth = 0;
			for(int i = 0; i < widths.length; i++) {
				textWidth += widths[i];
			}
			return textWidth;
		}

		private void resizeTextToFit(String text, Paint p) {
			float textSize = p.getTextSize();
			while(getTextWidth(text, p) > EnterScreen.screenWidth / 2 - 10) {
				textSize--;
				p.setTextSize(textSize);
			}
		}

		private void drawText(Canvas canvas){
			String text = getResources().getString(R.string.classic_desc);
			paint.setTextSize(textSize);
			resizeTextToFit(text, paint);
			float textWidth = getTextWidth(text, paint);
			classic.draw(canvas);
			canvas.drawText(text, 0, text.length(), EnterScreen.screenWidth / 4 - textWidth / 2, classic_bottom, paint);

			infinite.draw(canvas);
			text = getResources().getString(R.string.infinite_desc);
			resizeTextToFit(text, paint);
			textWidth = getTextWidth(text, paint);
			canvas.drawText(text, 0, text.length(), EnterScreen.screenWidth * 3 / 4 - textWidth / 2, infinite_bottom, paint);

			survival.draw(canvas);
			text = getResources().getString(R.string.survival_desc);
			resizeTextToFit(text, paint);
			textWidth = getTextWidth(text, paint);
			canvas.drawText(text, 0, text.length(), EnterScreen.screenWidth / 4 - textWidth / 2, survival_bottom, paint);

			dynamic.draw(canvas);
			text = getResources().getString(R.string.dynamic_desc);
			resizeTextToFit(text, paint);
			textWidth = getTextWidth(text, paint);
			canvas.drawText(text, 0, text.length(), EnterScreen.screenWidth * 3 / 4 - textWidth / 2, dynamic_bottom, paint);
		}

		private void drawLines(Canvas canvas) {
			canvas.drawLine(EnterScreen.screenWidth / 2, 0, EnterScreen.screenWidth / 2, EnterScreen.screenHeight, paint);
			canvas.drawLine(0, EnterScreen.screenHeight / 2, EnterScreen.screenWidth, EnterScreen.screenHeight / 2, paint);
		}

		@Override
		public void run() {
			while (running) {
				if (surfaceHolder.getSurface().isValid()) {
					Canvas canvas = surfaceHolder.lockCanvas();
					drawBackground(canvas);
					drawLines(canvas);
					drawText(canvas);

					try {
						Thread.sleep(60);
					} catch (InterruptedException e) {
						break;
					}
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
	}
}