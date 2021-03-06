package com.michaelxie.fillthescreen;

/**
 * Created by Michael Xie on 7/26/2014.
 */

import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.Collections;

import static java.lang.Math.random;

/**
 * Circles that fill the screen, generated by the user
 */
class Circle {
	float area;
	float x;
	float y;
	float radius;
	float nonDIPRadius;
	float vx, vy;
	int numBounces;
	private float limitingRadius;
	DisplayMetrics displayMetrics;
	public Circle() {}
	public Circle(float x, float y, DisplayMetrics displayMetrics) {
		this.x = x;
		this.y = y;
		nonDIPRadius = 25;
		this.radius = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, nonDIPRadius, displayMetrics);
		limitingRadius = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 75, displayMetrics);
		vx = 0;
		vy = 0;
		numBounces = 0;
		this.displayMetrics = displayMetrics;
	}
	float getX() { return x; }
	float getY() { return y; }
	float getRadiusAndInc() { return radius += (limitingRadius / radius); } //Increment radius every time it is accessed, which means it's still active
	float getRadius() { return radius; }
	float getNonDIPRadius() {return nonDIPRadius;}
	float getMass() { return (float) Math.log10(nonDIPRadius); } //Mass logarithmic with radius
	int incNumBounces() {return ++numBounces; }
	float updateX() {
		//Linear Friction
		vx *= 0.8;
		return x += vx;
	}
	float updateY() {
		vy *= 0.8;
		return y += vy;
	}
	void setArea(float area) {this.area = area;}
	float getArea() {return area;}
}

/**
 * Moving balls that collide with circles
 */
class Ball extends Circle {
	boolean dynamicCorrection;

	public Ball(int width, int height, DisplayMetrics displayMetrics) {
		this.displayMetrics = displayMetrics;
		dynamicCorrection = false;
		nonDIPRadius = 20;
		radius = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, nonDIPRadius, displayMetrics);
		x = (float)(random() * (width - 2 * radius) + radius); //Random pos on screen
		y = (float) (random() * (height - 2 * radius) + radius);
		ArrayList<Float> randomArray = new ArrayList<Float>();
		for(int i = 5; i < 15; i++) {
			randomArray.add(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, i, displayMetrics));
		}
		Collections.shuffle(randomArray);
		vx = randomArray.get(0);
		if(randomArray.get(1) < TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics)) {
			vx = -vx;
		}
		vy = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, displayMetrics);
		if(randomArray.get(2) < TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics)) {
			vy = -vy;
		}
	}
	public void setDynamicCorrection(boolean dynamicCorrection) {
		this.dynamicCorrection = dynamicCorrection;
	}

	@Override
	float updateX() {
		if(dynamicCorrection && (vx * vx + vy * vy) > TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, displayMetrics)) {
			vx *= 0.9;
			vy *= 0.9;
		} else if(dynamicCorrection && vx + vy < TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float)0.5, displayMetrics)) {
			vx *= 5;
			vy *= 5;
		}
		return x += vx;
	}
	@Override
	float updateY() {
		return y += vy;
	}
}