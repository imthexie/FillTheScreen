package com.michaelxie.fillthescreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Scroller;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

/**
 * Created by Michael Xie on 8/9/2014.
 */
public class HelpScreen extends Activity {
	private TextView contents;
	private TextView back_button;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_help_screen);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		contents = (TextView) findViewById(R.id.help_contents);
		contents.setMovementMethod(new ScrollingMovementMethod());
		contents.setVerticalScrollBarEnabled(true);
		back_button = (TextView) findViewById(R.id.help_back);
		back_button.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if(motionEvent.getAction() == (MotionEvent.ACTION_UP)){
					Intent intent = new Intent(getApplicationContext(), EnterScreen.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
					startActivity(intent);
					finish();
				}
				return true;
			}
		});

		//Upload screen view to Google Analytics
		// Get tracker.
		Tracker t = ((FillTheScreen)getApplication()).getTracker(TrackerName.APP_TRACKER);

		// Set screen name.
		// Where path is a String representing the screen name.
		t.setScreenName("HelpScreen onCreate");

		// Send a screen view.
		t.send(new HitBuilders.AppViewBuilder().build());
	}
}
