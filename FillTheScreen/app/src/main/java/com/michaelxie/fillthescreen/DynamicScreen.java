package com.michaelxie.fillthescreen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.Semaphore;
import static java.lang.Math.sqrt;

/**
 * Created by Michael Xie on 8/2/2014.
 */
public class DynamicScreen extends Activity {
	GameSurface gameSurface;
	private Sounds soundsModule;
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		gameSurface = new GameSurface(this);
		setContentView(gameSurface);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		soundsModule = new Sounds(getApplicationContext());

		//Upload screen view to Google Analytics
		// Get tracker.
		Tracker t = ((FillTheScreen)getApplication()).getTracker(TrackerName.APP_TRACKER);

		// Set screen name.
		// Where path is a String representing the screen name.
		t.setScreenName("DynamicScreen onCreate");

		// Send a screen view.
		t.send(new HitBuilders.AppViewBuilder().build());
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(gameSurface.surfaceHolder.getSurface().isValid()) gameSurface.onResumeGameSurface();
	}

	@Override
	protected void onPause() {
		super.onPause();
		gameSurface.onPauseGameSurface();
	}

	class GameSurface extends SurfaceView implements Runnable, SurfaceHolder.Callback {

		Thread drawer = null;
		SurfaceHolder surfaceHolder;

		ArrayList<Circle> circles;
		ArrayList<Circle> activeCircles;
		Semaphore arrayLock;

		ArrayList<Ball> balls;
		Bitmap background;
		boolean gameOver;

		volatile boolean running = false;

		private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private Paint contrastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		private void initialize(Context context) {
			areaFilled = 0;
			gameOver = false;
			circles = new ArrayList<Circle>();
			activeCircles = new ArrayList<Circle>();
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
		public GameSurface(Context context) {
			super(context);
			initialize(context);
		}

		public GameSurface(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			initialize(context);
		}

		public GameSurface(Context context, AttributeSet attrs) {
			super(context, attrs);
			initialize(context);
		}

		public void addCircle(Circle c) {
			lockUI();

			if(isTouching(c, circles, false, 0) == -1) {
				activeCircles.add(c);
				if(EnterScreen.makeSounds) soundsModule.startSlideSound();
			}
			unlockUI();
		}

		public void addBall(Ball b) {
			balls.add(b);
		}

		public void initializeBalls(int numBalls, int width, int height) {
			for(int i = 0; i < numBalls; i++) {
				Ball b = new Ball(width, height, getResources().getDisplayMetrics());
				b.setDynamicCorrection(true);
				addBall(b);
			}
		}

		int screenWidth, screenHeight;
		DisplayMetrics displayMetrics;
		private float textSize;

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			//Set up balls and get the canvas dimensions
			Canvas canvas = surfaceHolder.lockCanvas();
			screenWidth = canvas.getWidth();
			screenHeight = canvas.getHeight();
			displayMetrics = getResources().getDisplayMetrics();
			textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, displayMetrics);

			/* Initialize the balls */
			if(balls == null) {
				balls = new ArrayList<Ball>();
				initializeBalls(EnterScreen.getNumBallsBasedOnScreen(), screenWidth, screenHeight);
			}

			totalArea = screenWidth * screenHeight;
			background = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.RGB_565);
			canvas.setBitmap(background);
			surfaceHolder.unlockCanvasAndPost(canvas);
			onResumeGameSurface();
		}
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {

		}



		public void onResumeGameSurface(){
			running = true;
			drawer = new Thread(this);
			drawer.start();
		}

		public void onPauseGameSurface(){
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
		boolean firstTime = true;
		private void drawBackground(Canvas canvas) {
			canvas.drawColor(Color.parseColor("#0099cc"));
			if(firstTime) {
				String startHint = getResources().getString(R.string.start_hint);
				float[] widths = new float[(startHint).length()];
				contrastPaint.getTextWidths(startHint, 0, widths.length, widths);
				float textWidth = 0;
				for(int i = 0; i < widths.length; i++) {
					textWidth += widths[i];
				}
				paint.setFakeBoldText(true);
				paint.setTextSize(textSize);
				canvas.drawText(startHint, screenWidth / 2 - textWidth / 2, screenHeight / 2, paint);
			}
		}

		private float areaFilled;
		private float totalArea;

		private int getAreaPercentage() {
			return Math.min(100, Math.round(areaFilled / totalArea * 100));
		}

		public float round(float value, int places) {
			if (places < 0) throw new IllegalArgumentException();

			BigDecimal bd = new BigDecimal(value);
			bd = bd.setScale(places, RoundingMode.HALF_UP);
			return bd.floatValue();
		}

		private String getPreciseAreaPercentageString() {
			return Math.min(100, round(areaFilled / totalArea * 100 , 2)) + "";
		}

		private float computeDistanceBetweenPoints(float x1, float y1, float x2, float y2) {
			return (float)sqrt((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1));
		}

		private float computeDistance(Circle c1, Circle c2) {
			return computeDistanceBetweenPoints(c1.getX(), c1.getY(), c2.getX(), c2.getY());
		}

		private BitSet getSidesCrossed(Circle c) {
			float radius = c.getRadius();
			float x = c.getX();
			float y = c.getY();
			//Bit vector for 4 different side conditions
			BitSet sidesCrossed = new BitSet(4);
			if(x - radius < 0) {
				sidesCrossed.flip(0); //1000
				c.x = radius;
			}
			else if(x + radius > screenWidth) {
				sidesCrossed.flip(1); //0100
				c.x = screenWidth - radius;
			}
			if(y - radius < 0) {
				sidesCrossed.flip(2); //0010
				c.y = radius;
			}
			else if(y + radius > screenHeight) {
				sidesCrossed.flip(3); //0001
				c.y = screenHeight - radius;
			}

			return sidesCrossed;
		}

		private float magnitudeFromLegs(float x, float y) {
			return (float)Math.sqrt(x*x + y*y);
		}

		private float computeArea(Circle c) {
			double radius = c.getRadius();
			return (float)(Math.PI * radius * radius);
		}

		private void incrementArea(Circle c) {
			float area = computeArea(c);
			areaFilled += area;
		}


		//Two different possible policies here - do I stop on border or not?
		private int isTouching(Circle c, ArrayList<Circle> circleArray, boolean removeOnHit, int startIndex) {
			for(int i = startIndex; i < circleArray.size(); i++) {
				float distance = computeDistance(c, circleArray.get(i));
				if(distance <= (c.getRadius() + circleArray.get(i).getRadius())) {
					if(removeOnHit) circleArray.remove(i);
					return i;
				}
			}
			return -1;
		}


		private void updateCircleCollisions() {
			ArrayList<Circle> toRemove = new ArrayList<Circle>();
			for(Circle c : activeCircles) {
				if(!getSidesCrossed(c).isEmpty()) {
					toRemove.add(c);
				}
				//touching a white circle, game over
				if(isTouching(c, circles, false, 0) != -1) {
					gameOver = true;
					return;
				}
			}
			for(Circle c : toRemove) {
				activeCircles.remove(c);
				circles.add(c);
				incrementArea(c);
			}
			for(int i = 0; i < circles.size(); i++) {
				Circle c = circles.get(i);
				//bounce logic
				BitSet sidesCrossed = getSidesCrossed(c);
				//touching screen border, a circle, or active circle
				if(sidesCrossed.cardinality() != 0) {
					int index = -1;
					while((index = sidesCrossed.nextSetBit(index + 1)) != -1) {
						switch(index) {
							case 0: c.vx *= -1; break;
							case 1: c.vx *= -1; break;
							case 2: c.vy *= -1; break;
							case 3: c.vy *= -1; break;
						}
					}
				}
				int touchingCircleIndex = i;
				while((touchingCircleIndex = isTouching(c, circles, false, touchingCircleIndex + 1)) != -1){
					//Bounce if the circle it touches is not itself
					handleCircleBounce(c, circles.get(touchingCircleIndex));
				}
			}
		}
		private void drawCircles(Canvas canvas) {
			for(Circle c : activeCircles) {
				canvas.drawCircle(c.getX(), c.getY(), c.getRadiusAndInc(), paint);
			}
			for(int i = 0; i < circles.size(); i++) {
				Circle c = circles.get(i);
				canvas.drawCircle(c.updateX(), c.updateY(), c.getRadius(), paint);
			}
		}

		private void handleCircleBounce(Circle b, Circle c) {

			//Treating C as ball 1 first
			//normalized normal vector from C's perspective
			float ax = b.getX() - c.getX();
			float ay = b.getY() - c.getY();
			float magA = magnitudeFromLegs(ax, ay);

			//Prevent them from going inside each other
			float diff = b.getRadius() + c.getRadius() - magA;
			if(diff > 0) {
				float scale = (diff + magA) / magA;
				float xInc = (scale * ax - ax) / 2;
				if(b.getX() < c.getX()) {
					b.x -= xInc;
					c.x += xInc;
				} else {
					b.x += xInc;
					c.x -= xInc;
				}

				float yInc = (scale * ay - ay) / 2;
				if(b.getY() < c.getY()) {
					b.y -= yInc;
					c.y += yInc;
				} else {
					b.y += yInc;
					c.y -= yInc;
				}

				ax += xInc;
				ay += yInc;
				magA += diff;
			}

			ax /= magA;
			ay /= magA;

			//COM Frame vector
			float vCOMx = (c.getMass() * c.vx + b.getMass() * b.vx) / (c.getMass() + b.getMass());
			float vCOMy = (c.getMass() * c.vy + b.getMass() * b.vy) / (c.getMass() + b.getMass());
			//Velocities in COM Frame
			float v1Primex = c.vx - vCOMx;
			float v1Primey = c.vy - vCOMx;
			float v2Primex = b.vx - vCOMx;
			float v2Primey = b.vx - vCOMy;

			//New Velocities in COM Frame
			v1Primex = v1Primex - 2 * (c.vx * ax + c.vy * ay) * ax;
			v1Primey = v1Primey - 2 * (c.vx * ax + c.vy * ay) * ay;
			v2Primex = v2Primex - 2 * (b.vx * -ax + b.vy * -ay) * -ax;
			v2Primey = v2Primey - 2 * (b.vx * -ax + b.vy * -ay) * -ay;

			c.vx = v1Primex + vCOMx;
			c.vy = v1Primey + vCOMy;
			b.vx = v2Primex + vCOMx;
			b.vy = v2Primey + vCOMy;
		}

		private void updateBallCollisions() {
			for(Ball b : balls) {
				//bounce logic
				BitSet sidesCrossed = getSidesCrossed(b);
				//touching screen border, a circle, or active circle
				if(sidesCrossed.cardinality() != 0) {
					int index = -1;
					while((index = sidesCrossed.nextSetBit(index + 1)) != -1) {
						switch(index) {
							case 0: b.vx *= -1; break;
							case 1: b.vx *= -1; break;
							case 2: b.vy *= -1; break;
							case 3: b.vy *= -1; break;
						}
					}
				}
				if(isTouching(b, activeCircles, true, 0) != -1) {
					//remove from active circles, ball hit a growing circle. Handled by isTouching
					gameOver = true;
					return;
				} else {
					int touchingCircleIndex = -1;
					while((touchingCircleIndex = isTouching(b, circles, false, touchingCircleIndex + 1)) != -1){
						//Add circle bounce code
						handleCircleBounce(b, circles.get(touchingCircleIndex));
						if(EnterScreen.makeSounds) soundsModule.startPopSound();
					}
				}


			}
		}

		private void drawBalls(Canvas canvas) {
			for(Ball b : balls) {
				canvas.drawCircle(b.updateX(), b.updateY(), b.getRadius(), contrastPaint);
			}
		}

		private void drawPercentage(Canvas canvas) {
			int percentage = getAreaPercentage();
			String text = percentage + "%";
			contrastPaint.setTextSize(textSize);
			contrastPaint.setFakeBoldText(true);
			float yWatermark = screenHeight - screenHeight * ((float)percentage / 100);
			canvas.drawText(text, 50, (yWatermark + 50 >= screenHeight)? yWatermark - 50: yWatermark + 50, contrastPaint);
			canvas.drawLine(0, yWatermark, screenWidth, yWatermark, contrastPaint);
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
				firstTime = false;
				return true;
			}
		};

		private View.OnTouchListener touchListener = new View.OnTouchListener() {

			@Override
			public boolean onTouch(View pView, MotionEvent pEvent) {
				//pView.onTouchEvent(pEvent);
				touchX = pEvent.getX();
				touchY = pEvent.getY();
				// We're only interested in when the button is released.
				if (pEvent.getAction() == MotionEvent.ACTION_UP) {
					// We're only interested in anything if our speak button is currently pressed.
					if (longPress && activeCircles.size() > 0) {
						lockUI();
						incrementArea(activeCircles.get(activeCircles.size()- 1));
						circles.addAll(activeCircles);
						activeCircles.clear();
						unlockUI();
						// Do something when the button is released.
						longPress = false;
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
					updateBallCollisions();
					if(gameOver) {
						if(EnterScreen.makeSounds) soundsModule.startGameOverSound();
						Intent intent = new Intent(getContext(), ScoreScreen.class);
						intent.putExtra("mode", "DYNAMIC");
						intent.putExtra("score", getPreciseAreaPercentageString());
						startActivity(intent);
						finish();
					}
					drawCircles(canvas);
					drawBalls(canvas);

					drawPercentage(canvas);
					unlockUI();
					try{
						Thread.sleep(40);
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

}
