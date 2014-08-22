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

import static java.lang.Math.*;

public class SurvivalScreen extends Activity {

	SurvivalSurface survivalSurface;
	private Sounds soundsModule;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		survivalSurface = new SurvivalSurface(this);
		setContentView(survivalSurface);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		soundsModule = new Sounds(getApplicationContext());

		//Upload screen view to Google Analytics
		// Get tracker.
		Tracker t = ((FillTheScreen)getApplication()).getTracker(TrackerName.APP_TRACKER);

		// Set screen name.
		// Where path is a String representing the screen name.
		t.setScreenName("SurvivalScreen onCreate");

		// Send a screen view.
		t.send(new HitBuilders.AppViewBuilder().build());
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(survivalSurface.surfaceHolder.getSurface().isValid()) survivalSurface.onResumeSurvivalSurface();
	}

	@Override
	protected void onPause() {
		super.onPause();
		survivalSurface.onPauseSurvivalSurface();
	}

	class SurvivalSurface extends SurfaceView implements Runnable, SurfaceHolder.Callback {

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
		private Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		private void initialize(Context context) {
			areaFilled = 0;
			highestAreaFilled = 0;
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
			redPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			redPaint.setColor(Color.RED);
			this.setOnTouchListener(touchListener);
			this.setOnLongClickListener(longClickListener);
		}

		//Constructors
		public SurvivalSurface(Context context) {
			super(context);
			initialize(context);
		}

		public SurvivalSurface(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			initialize(context);
		}

		public SurvivalSurface(Context context, AttributeSet attrs) {
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
			onResumeSurvivalSurface();
		}
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {

		}

		public void onResumeSurvivalSurface(){
			running = true;
			drawer = new Thread(this);
			drawer.start();
		}

		public void onPauseSurvivalSurface(){
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

		private float highestAreaFilled;
		private float areaFilled;
		private float totalArea;

		private int getAreaPercentage() {
			return Math.min(100, Math.round(areaFilled / totalArea * 100));
		}

		private int getHighestAreaPercentage() {
			return Math.min(100, Math.round(highestAreaFilled / totalArea * 100));
		}

		public float round(float value, int places) {
			if (places < 0) throw new IllegalArgumentException();

			BigDecimal bd = new BigDecimal(value);
			bd = bd.setScale(places, RoundingMode.HALF_UP);
			return bd.floatValue();
		}

		private String getPreciseAreaPercentageString() {
			return Math.min(100, round(highestAreaFilled / totalArea * 100 , 2)) + "";
		}

		private float computeDistanceBetweenPoints(float x1, float y1, float x2, float y2) {
			return (float)sqrt((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1));
		}

		private float computeDistance(Circle c1, Circle c2) {
			return computeDistanceBetweenPoints(c1.getX(), c1.getY(), c2.getX(), c2.getY());
		}

		private double computeDistToSide(int sideIndex, double radius, float x, float y) {
			switch(sideIndex) {
				case 0: return x;
				case 1: return  screenWidth - x;
				case 2: return y;
				case 3: return screenHeight - y;
			}
			return -1;
		}

		private BitSet getSidesCrossed(Circle c, boolean correction) {
			float radius = c.getRadius();
			float x = c.getX();
			float y = c.getY();
			//Bit vector for 4 different side conditions
			BitSet sidesCrossed = new BitSet(4);
			if(x - radius < 0) {
				sidesCrossed.flip(0); //1000
				if(correction) c.x = radius;
			}
			else if(x + radius > screenWidth) {
				sidesCrossed.flip(1); //0100
				if(correction) c.x = screenWidth - radius;
			}
			if(y - radius < 0) {
				sidesCrossed.flip(2); //0010
				if(correction) c.y = radius;
			}
			else if(y + radius > screenHeight) {
				sidesCrossed.flip(3); //0001
				if(correction) c.y = screenHeight - radius;
			}

			return sidesCrossed;
		}

		private float legFromHypotenuse(double hypotenuse, double leg){
			return (float)Math.sqrt(hypotenuse * hypotenuse - leg * leg);
		}

		private float computeArea(Circle c) {
			double radius = c.getRadius();
			float x = c.getX();
			float y = c.getY();

			//Bit vector for 4 different corner conditions
			BitSet sidesCrossed = getSidesCrossed(c, false);

			if(sidesCrossed.cardinality() == 0) {
				//Not overlapping any boundaries
				return (float)(radius * radius * Math.PI);
			} else if(sidesCrossed.cardinality() == 1) {
				//overlapping two corner boundaries
				int sideIndex = sidesCrossed.nextSetBit(0);
				double distanceToSide = computeDistToSide(sideIndex, radius, x, y);
				double triangleAlpha = Math.acos(distanceToSide / radius);
				double sectorTheta = 2 * PI - 2 * triangleAlpha;
				float triangleBase = legFromHypotenuse(radius, distanceToSide);
				return (float)((sectorTheta / 2) * (radius * radius) + 2 * triangleBase * distanceToSide);

			} else if (sidesCrossed.cardinality() == 2) {
				//overlapping a corner boundary; compute sector plus 2 triangles. Could also be crossing two vertical sides.
				if((sidesCrossed.get(0) && sidesCrossed.get(1)) || (sidesCrossed.get(2) && sidesCrossed.get(3))) {
					//Crossing two same-dimension sides.
					double distanceTo1, distanceTo2;
					if(sidesCrossed.get(0)) {
						distanceTo1 = computeDistToSide(0, radius, x, y);
						distanceTo2 = computeDistToSide(1, radius, x, y);
					} else {
						distanceTo1 = computeDistToSide(2, radius, x, y);
						distanceTo2 = computeDistToSide(3, radius, x, y);
					}
					double alpha = Math.acos(distanceTo1/radius);
					double beta = Math.acos(distanceTo2/radius);
					double sectorTheta = 2 * Math.PI - 2 * alpha - 2 * beta;
					float alphaTriangleBase = legFromHypotenuse(radius, distanceTo1);
					float betaTriangleBase = legFromHypotenuse(radius, distanceTo2);
					float sectorArea = (float)((sectorTheta / 2) * (radius * radius));
					return (float)(sectorArea + alphaTriangleBase * distanceTo1 + betaTriangleBase * distanceTo2);
				}

				double distanceToX = 0;
				double distanceToY = 0;
				int sideIndex = sidesCrossed.nextSetBit(0);
				distanceToX = computeDistToSide(sideIndex, radius, x, y);
				sideIndex = sidesCrossed.nextSetBit(2);
				distanceToY = computeDistToSide(sideIndex, radius, x, y);

				//Calculate angle in triangles bordering x and y borders
				double xTriangleAlpha = Math.acos(distanceToX/radius);
				double yTriangleAlpha = Math.acos(distanceToY/radius);
				double sectorTheta = (1.5)*Math.PI - xTriangleAlpha - yTriangleAlpha;


				float sectorArea = (float)((sectorTheta / 2) * (radius * radius));
				float rectArea = (float)(distanceToX * distanceToY);

				float xTriangleBase = legFromHypotenuse(radius, distanceToX);
				float yTriangleBase = legFromHypotenuse(radius, distanceToY);

				return (float)(sectorArea + rectArea + 0.5 * xTriangleBase * distanceToX + 0.5 * yTriangleBase * distanceToY);
			} else if (sidesCrossed.cardinality() == 3) {
				//overlapping two corners, since crossing 3 sides

				double distanceToDoubleCrossed1, distanceToDoubleCrossed2;
				double distanceToSingleCrossed;
				if(sidesCrossed.get(0) && sidesCrossed.get(1)) {
					//Crossing two x axis boundaries
					distanceToDoubleCrossed1 = computeDistToSide(0, radius, x, y);
					distanceToDoubleCrossed2 = computeDistToSide(1, radius, x, y);
					distanceToSingleCrossed = computeDistToSide(sidesCrossed.nextSetBit(2), radius, x, y);
				} else {
					distanceToDoubleCrossed1 = computeDistToSide(2, radius, x, y);
					distanceToDoubleCrossed2 = computeDistToSide(3, radius, x, y);
					distanceToSingleCrossed = computeDistToSide(sidesCrossed.nextSetBit(0), radius, x, y);
				}

				double triangle1Alpha = Math.acos(distanceToDoubleCrossed1 / radius);
				double triangle2Beta = Math.acos(distanceToDoubleCrossed2 / radius);
				double sectorTheta = Math.PI - triangle1Alpha - triangle2Beta;
				float triangle1Base = legFromHypotenuse(radius, distanceToDoubleCrossed1);
				float triangle2Base = legFromHypotenuse(radius, distanceToDoubleCrossed2);
				return (float)((sectorTheta / 2) * (radius * radius)
						+ 0.5 * triangle1Base * distanceToDoubleCrossed1 + 0.5 * triangle2Base * distanceToDoubleCrossed2
						+ (distanceToDoubleCrossed1 + distanceToDoubleCrossed2) * distanceToSingleCrossed);
			} else {
				//overlapping 3 or more corners, touching all 4 sides. Don't expect this at all. Approximate by straight line when implemented
				return (float)(Math.PI * radius * radius);

			}

		}

		private void incrementArea(Circle c) {
			float area = computeArea(c);
			c.setArea(area);
			areaFilled += area;
			if(areaFilled > highestAreaFilled) highestAreaFilled = areaFilled;
		}

		private void decrementArea(Circle c) {
			areaFilled -= c.getArea();
		}

		//Two different possible policies here - do I stop on border or not?
		private int isTouching(Circle c, ArrayList<Circle> circleArray, boolean removeOnHit, int startIndex) {
			for(int i = startIndex; i < circleArray.size(); i++) {
				float distance = computeDistance(c, circleArray.get(i));
				if(distance <= (c.getRadius() + circleArray.get(i).getRadius())) {
					if(EnterScreen.makeSounds) soundsModule.startPopSound();
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
				incrementArea(c);
			}
		}
		private void drawCircles(Canvas canvas) {
			for(Circle c : activeCircles) {
				canvas.drawCircle(c.getX(), c.getY(), c.getRadiusAndInc(), paint);
			}
			for(int i = 0; i < circles.size(); i++) {
				Circle c = circles.get(i);
				canvas.drawCircle(c.getX(), c.getY(), c.getRadius(), paint);
			}
		}

		private void handleCircleBounce(Ball b, Circle c) {
			//Calculate components of vector from the two circles' origins
			float ax = c.getX() - b.getX();
			float ay = c.getY() - b.getY();

			//Velocity in same quadrant direction as ax, ay means that it's in the circle: allow the ball to make it out.
			if(((ax > 0 && b.vx < 0) || (ax < 0 && b.vx > 0)) && ((ay < 0 && b.vy > 0) || (ay > 0 && b.vy < 0))) return;

			float nn = ax * ax + ay * ay;
			float vn = ax * b.vx + ay * b.vy;

			//if(vn > 0.0f) return; This commented code captures balls in a gravity field like manner

			b.vx -= (2.0f * (vn/nn)) * ax;
			b.vy -= (2.0f * (vn/nn)) * ay;

		}

		private void updateBallCollisions() {
			for(Ball b : balls) {
				//bounce logic
				BitSet sidesCrossed = getSidesCrossed(b, true);
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
						Circle c = circles.get(touchingCircleIndex);
						handleCircleBounce(b, c);
						if(circles.get(touchingCircleIndex).incNumBounces() >= Math.sqrt(c.getNonDIPRadius())) {
							circles.remove(touchingCircleIndex);
							decrementArea(c);
						}
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

			//Draw highest score line
			percentage = getHighestAreaPercentage();
			yWatermark = screenHeight - screenHeight * ((float)percentage / 100);
			canvas.drawLine(0, yWatermark, screenWidth, yWatermark, redPaint);
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
					if (longPress && activeCircles.size() > 0) {
						lockUI();
						incrementArea(activeCircles.get(activeCircles.size()- 1));
						circles.addAll(activeCircles);
						activeCircles.clear();
						unlockUI();
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
						intent.putExtra("mode", "SURVIVAL");
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


