package com.michaelxie.fillthescreen;

import com.michaelxie.fillthescreen.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.Semaphore;

import static java.lang.Math.*;

public class GameScreen extends Activity {

    GameSurface gameSurface;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameSurface = new GameSurface(this);
        setContentView(gameSurface);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        /*gameSurface.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                Circle c = new Circle(e.getX(), e.getY(), 100);
                System.out.println("NEW CIRCLE (" + e.getX() + "," + e.getY() + ")");
                gameSurface.addCircle(c);
                return false;
            }
        });*/
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

        volatile boolean running = false;

        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint contrastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private void initialize(Context context) {
            areaFilled = 0;

            /* Initialize the balls */
            balls = new ArrayList<Ball>();


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
            try{
                arrayLock.acquire();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }

            if(!isTouching(c, circles, false)) {
                activeCircles.add(c);
            }
            arrayLock.release();
        }

        public void addBall(Ball b) {
            balls.add(b);
        }

        public void initializeBalls(int numBalls) {
            for(int i = 0; i < numBalls; i++) {
                Ball b = new Ball(width, height, i);
                addBall(b);
            }
        }

        boolean initialCreate = true;
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            //Set up balls and get the canvas dimensions
            Canvas canvas = surfaceHolder.lockCanvas();
            width = canvas.getWidth();
            height = canvas.getHeight();
            if(initialCreate) {
                initializeBalls(2);
                initialCreate = false;
            }
            totalArea = width * height;
            background = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            canvas.setBitmap(background);
            surfaceHolder.unlockCanvasAndPost(canvas);
            onResumeGameSurface();
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }

        int width, height;

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
        private void drawBackground(Canvas canvas) {
            canvas.drawColor(Color.parseColor("#0099cc"));
        }

        private float areaFilled;
        private float totalArea;
        private String getAreaPercentage() {
            return Math.min(100, Math.round(areaFilled / totalArea * 100)) + "%";
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
                case 1: return  width - x;
                case 2: return y;
                case 3: return height - y;
            }
            return -1;
        }

        private BitSet getSidesCrossed(Circle c) {
            double radius = c.getRadius();
            float x = c.getX();
            float y = c.getY();
            //Bit vector for 4 different corner conditions
            BitSet sidesCrossed = new BitSet(4);
            if(x - radius < 0) sidesCrossed.flip(0); //1000
            if(x + radius > width) sidesCrossed.flip(1); //0100
            if(y - radius < 0) sidesCrossed.flip(2); //0010
            if(y + radius > height) sidesCrossed.flip(3); //0001

            return sidesCrossed;
        }

        private float computeArea(Circle c) {
            double radius = c.getRadius();
            float x = c.getX();
            float y = c.getY();

            //Bit vector for 4 different corner conditions
            BitSet sidesCrossed = getSidesCrossed(c);

            //return (float)(radius * radius * Math.PI);

            if(sidesCrossed.cardinality() == 0) {
                //Not overlapping any boundaries
                return (float)(radius * radius * Math.PI);
            } else if(sidesCrossed.cardinality() == 1) {
                //overlapping two corner boundaries
                int sideIndex = sidesCrossed.nextSetBit(0);
                double distanceToSide = computeDistToSide(sideIndex, radius, x, y);
                double triangleAlpha = Math.acos(distanceToSide / radius);
                double sectorTheta = 2 * PI - 2 * triangleAlpha;
                float triangleBase = (float)Math.sqrt(radius * radius - distanceToSide * distanceToSide);
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
                    float alphaTriangleBase = (float)Math.sqrt(radius * radius - distanceTo1 * distanceTo1);
                    float betaTriangleBase = (float)Math.sqrt(radius * radius - distanceTo2 * distanceTo2);
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

                float xTriangleBase = (float)Math.sqrt(radius * radius - distanceToX * distanceToX);
                float yTriangleBase = (float)Math.sqrt(radius * radius - distanceToY * distanceToY);

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
                float triangle1Base = (float)Math.sqrt(radius * radius - distanceToDoubleCrossed1 * distanceToDoubleCrossed1);
                float triangle2Base = (float)Math.sqrt(radius * radius - distanceToDoubleCrossed2 * distanceToDoubleCrossed2);
                return (float)((sectorTheta / 2) * (radius * radius)
                        + 0.5 * triangle1Base * distanceToDoubleCrossed1 + 0.5 * triangle2Base * distanceToDoubleCrossed2
                        + (distanceToDoubleCrossed1 + distanceToDoubleCrossed2) * distanceToSingleCrossed);
            } else {
                //overlapping 3 or more corners, touching all 4 sides. Don't expect this at all. Approximate by straight line when implemented
                return (float)(Math.PI * radius * radius);

            }

        }

        private void incrementArea(ArrayList<Circle> circleArray) {
            for(int i = 0; i < circleArray.size(); i++) {
                float area = computeArea(circleArray.get(i));
                //System.out.println(area);
                areaFilled += area;
            }
        }


        //Two different possible policies here - do I stop on border or not?
        private boolean isTouching(Circle c, ArrayList<Circle> circleArray, boolean removeOnHit) {
            /*double radius = c.getRadius();
            float x = c.getX();
            float y = c.getY();

            //Bit vector for 4 different corner conditions
            BitSet sidesCrossed = new BitSet(4);
            if(x - radius < 0) sidesCrossed.flip(0); //1000
            if(x + radius > width) sidesCrossed.flip(1); //0100
            if(y - radius < 0) sidesCrossed.flip(2); //0010
            if(y + radius > height) sidesCrossed.flip(3); //0001
            
            float threshold = 10;
            int sideIndex = 0;
            boolean crossedSide = false;
            while((sideIndex = sidesCrossed.nextSetBit(sideIndex++)) != -1 ) {
                float distanceToSide = computeDistToSide(sideIndex, radius, x, y) -;
                if (sideOverlap > 0 && sideOverlap < threshold) { //close to a side, let them reach the side
                            return true;
                } else if (sideOverlap < )
                
            }*/
          
            for(int i = 0; i < circleArray.size(); i++) {
                float distance = computeDistance(c, circleArray.get(i));
                if(distance <= (c.getRadius() + circleArray.get(i).getRadius())) {
                    if(removeOnHit) circleArray.remove(i);
                    return true;
                }
            }
            return false;
        }

        private void drawCircles(Canvas canvas) {

            for(int i = 0; i < activeCircles.size(); i++) {
                Circle c = activeCircles.get(i);
                if(!isTouching(c, circles, false)) {
                    canvas.drawCircle(c.getX(), c.getY(), c.getRadiusAndInc(), paint);
                } else {
                    //computeArea();
                    incrementArea(activeCircles);
                    circles.addAll(activeCircles);
                    activeCircles.clear();
                }
            }
            for(int i = 0; i < circles.size(); i++) {
                Circle c = circles.get(i);
                canvas.drawCircle(c.getX(), c.getY(), c.getRadius(), paint);
            }


        }



        private void drawBalls(Canvas canvas) {
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
                } else if( isTouching(b, activeCircles, true) || isTouching(b, circles, false)) {

                }
                canvas.drawCircle(b.updateX(), b.updateY(), b.getRadius(), contrastPaint);
            }
        }

        private void drawPercentage(Canvas canvas) {
            String text = getAreaPercentage();
            contrastPaint.setTextSize(30);
            canvas.drawText(text, 50, 50, contrastPaint);
        }

        private volatile boolean longPress = false;
        private volatile float touchX, touchY;
        private View.OnLongClickListener longClickListener = new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View pView) {
                //Only make new circles on long click
                Circle c = new Circle(touchX, touchY, 75);
                addCircle(c); //Thread-safe method that also checks if in valid position to add new circle
                longPress = true;
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
                    if (longPress) {
                        incrementArea(activeCircles);

                        circles.addAll(activeCircles);
                        activeCircles.clear();
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
                    try{
                        arrayLock.acquire();
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                    drawCircles(canvas);
                    drawBalls(canvas);
                    drawPercentage(canvas);
                    arrayLock.release();
                    try{
                        Thread.sleep(50);
                    } catch(InterruptedException e) {
                        break;
                    }

                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }

    }
}


/**
 * Circles that fill the screen, generated by the user
 */
class Circle {
    float x;
    float y;
    float radius;
    public Circle() {}
    public Circle(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }
    float getX() { return x; }
    float getY() { return y; }
    float getRadiusAndInc() { return radius += 1.0; } //Increment radius every time it is accessed, which means it's still active
    float getRadius() { return radius; }
}

/**
 * Moving balls that collide with circles
 */
class Ball extends Circle {
    float vx, vy;
    public Ball(float width, float height, int ballNum) {
        x = (float)(random() * (width - 50)) + 25;
        y = (float)(random() * (height - 50)) + 25;

        vx = (float) (random() * 5 + 0.1);
        if(random() < .5) {
            vx = -vx;
        }
        vy = 5;
        if(random() < .5) {
            vy = -vy;
        }
        radius = 40;
    }
    float updateX() {
        return x += vx;
    }
    float updateY() {
        return y += vy;
    }
 }

