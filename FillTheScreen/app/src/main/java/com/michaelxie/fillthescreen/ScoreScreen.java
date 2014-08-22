package com.michaelxie.fillthescreen;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.widget.FacebookDialog;
import com.facebook.widget.WebDialog;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.michaelxie.fillthescreen.basegameutils.BaseGameActivity;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import com.facebook.*;
import com.facebook.model.*;

/**
 * Created by Michael Xie on 7/30/2014.
 */
public class ScoreScreen extends BaseGameActivity {
	private TextView score;
	private TextView greeting;
	private TextView high_score;
	private TextView mode_name;
	private TextView letterGrade;
	private int mode;
	ImageButton leaderboardButton, playAgainButton, playHomeButton, fbPostButton;
	private static final String TAG = "ScoreScreen";

	// request codes we use when invoking an external activity
	final int RC_RESOLVE = 5000, RC_UNUSED = 5001;

	//Facebook share stuff
	private UiLifecycleHelper uiHelper;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		getGameHelper().setMaxAutoSignInAttempts(1);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_score_screen);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		score = (TextView) findViewById(R.id.score);
		letterGrade = (TextView) findViewById(R.id.your_score_is);
		greeting = (TextView) findViewById(R.id.greeting);
		high_score = (TextView) findViewById(R.id.high_score);
		mode_name = (TextView) findViewById(R.id.mode_name);
		TextView score_text = ((TextView)findViewById(R.id.share_text));
		score_text.setTypeface(score_text.getTypeface(), Typeface.BOLD_ITALIC);
		Intent intent = getIntent();
		//Get the game mode
		String modeName = intent.getStringExtra("mode");
		mode_name.setText(modeName + " MODE");
		mode = GameModes.gameModeFromName(modeName);
		//Get the score
		String scoreText = intent.getStringExtra("score");
		setScoreText(scoreText);

		//Set up buttons
		leaderboardButton = (ImageButton) findViewById(R.id.leaderboard);
		playAgainButton = (ImageButton) findViewById(R.id.play_again);
		playHomeButton = (ImageButton) findViewById(R.id.play_home);
		fbPostButton = (ImageButton) findViewById(R.id.fb_post);

		leaderboardButton.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if(motionEvent.getAction() == (MotionEvent.ACTION_UP)){
					if (isSignedIn()) {
						if(mode == GameModes.CLASSIC) {
							startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), Ids.classic_leaderboard_id), RC_UNUSED);
						} else if(mode == GameModes.INFINITE) {
							startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), Ids.infinite_leaderboard_id), RC_UNUSED);

						} else if(mode == GameModes.SURVIVAL) {
							startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), Ids.survival_leaderboard_id), RC_UNUSED);

						} else if(mode == GameModes.DYNAMIC) {
							startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), Ids.dynamic_leaderboard_id), RC_UNUSED);
						}
					} else {
						beginUserInitiatedSignIn();
					}
				}
				return true;
			}
		});
		playAgainButton.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if(motionEvent.getAction() == (MotionEvent.ACTION_UP)){
					if(mode == GameModes.CLASSIC) {
						Intent intent = new Intent(getApplicationContext(), GameScreen.class);
						startActivity(intent);
						finish();
					} else if(mode == GameModes.INFINITE) {
						Intent intent = new Intent(getApplicationContext(), InfiniteScreen.class);
						startActivity(intent);
						finish();
					} else if(mode == GameModes.SURVIVAL) {
						Intent intent = new Intent(getApplicationContext(), SurvivalScreen.class);
						startActivity(intent);
						finish();
					} else if(mode == GameModes.DYNAMIC) {
						Intent intent = new Intent(getApplicationContext(), DynamicScreen.class);
						startActivity(intent);
						finish();
					}

				}
				return true;
			}
		});
		playHomeButton.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if(motionEvent.getAction() == (MotionEvent.ACTION_UP)){
					Intent intent = new Intent(getApplicationContext(), ChooseScreen.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
					startActivity(intent);
					finish();
				}
				return true;
			}
		});

		//For Facebook Share function
		uiHelper = new UiLifecycleHelper(this, null);
		uiHelper.onCreate(savedInstanceState);

		fbPostButton.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if(motionEvent.getAction() == MotionEvent.ACTION_UP) doFBLinkPost();
				return true;
			}
		});

		//Upload screen view to Google Analytics
		// Get tracker.
		Tracker t = ((FillTheScreen)getApplication()).getTracker(TrackerName.APP_TRACKER);

		// Set screen name.
		// Where path is a String representing the screen name.
		t.setScreenName("ScoreScreen onCreate");

		// Send a screen view.
		t.send(new HitBuilders.AppViewBuilder().build());
	}

	@Override
	public void onStart() {
		super.onStart();
		//EnterScreen.displayInterstitial(); no ads updated 9/28
	}

	private void setLocalHighScoreFloat(String scoreText, float current_score) {
		//Get old high score
		SharedPreferences settings = getSharedPreferences(Ids.LOCAL_HIGH_SCORE_FILE, 0);
		String old_score_text = settings.getString(GameModes.gameModeToName(mode), "notFound");
		float oldScore;
		try {
			oldScore = Float.valueOf(old_score_text);
		} catch(Exception e) {
			oldScore = 0;
		}
		if(old_score_text.equals("notFound") || oldScore < current_score) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(GameModes.gameModeToName(mode), scoreText);
			// Commit the edits!
			editor.apply();
			high_score.setTextColor(Color.RED);
			high_score.setText("High Score: " + scoreText);
		} else {
			high_score.setTextColor(Color.WHITE);
			high_score.setText("High Score: " + old_score_text);
		}
	}

	private void setLocalHighScoreInt(String scoreText, int current_score) {
		//Get old high score
		SharedPreferences settings = getSharedPreferences(Ids.LOCAL_HIGH_SCORE_FILE, 0);
		String old_score_text = settings.getString(GameModes.gameModeToName(mode), "notFound");
		int oldScore;
		try {
			oldScore = Integer.valueOf(old_score_text);
		} catch(Exception e) {
			oldScore = 0;
		}
		if(old_score_text.equals("notFound") || oldScore < current_score) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(GameModes.gameModeToName(mode), scoreText);
			// Commit the edits!
			editor.apply();
			high_score.setTextColor(Color.RED);
			high_score.setText("High Score: " + scoreText);
		} else {
			high_score.setTextColor(Color.WHITE);
			high_score.setText("High Score: " + old_score_text);
		}
	}

	public void setScoreText(String scoreText) {
		score.setTextColor(Color.YELLOW);
		if(mode == GameModes.INFINITE) {
			int current_score = Integer.valueOf(scoreText);
			setLocalHighScoreInt(scoreText, current_score);
			letterGrade.setVisibility(View.GONE);
			score.setText(scoreText);
		} else {
			NumberFormat df = DecimalFormat.getInstance();
			df.setMinimumFractionDigits(2);
			df.setMaximumFractionDigits(2);
			float current_score = Float.valueOf(scoreText);
			scoreText = df.format(current_score);
			score.setText(scoreText + "%");
			letterGrade.setText("Grade: " + getLetterGrade((int)current_score));
			setLocalHighScoreFloat(scoreText, current_score);
		}
		//Downscale to fit
		while(score.getWidth() > EnterScreen.screenWidth) {
			score.setTextSize(score.getTextSize() - 1);
		}
	}

	private String getLetterGrade(int score) {
		if(score < 20) {
			return "F";
		} else if (score < 40) {
			return "D";
		} else if (score < 60) {
			return "C";
		} else if (score < 80) {
			return "B!";
		} else if (score <= 100) {
			return "A!!";
		}
		return "";
	}

	public void onSignInSucceeded() {
		// Set the greeting appropriately on main menu
		Player p = Games.Players.getCurrentPlayer(getApiClient());
		String displayName;
		if (p == null) {
			Log.w(TAG, "mGamesClient.getCurrentPlayer() is NULL!");
			displayName = "???";
		} else {
			displayName = p.getDisplayName();
		}
		greeting.setText("Hello, " + displayName + "!");
		submitScore();
	}

	private void submitScore() {
		String scoreText = score.getText().toString();
		if(mode == GameModes.INFINITE) {
			Games.Leaderboards.submitScore(getApiClient(), Ids.infinite_leaderboard_id, Long.valueOf(scoreText));
		} else {
			int indexOfDecimal;
			indexOfDecimal = scoreText.length() - 4;
			scoreText = scoreText.substring(0, indexOfDecimal) + scoreText.substring(indexOfDecimal + 1);
			if(mode == GameModes.CLASSIC) {
				Games.Leaderboards.submitScore(getApiClient(), Ids.classic_leaderboard_id, Long.valueOf(scoreText.substring(0, scoreText.length() - 1)));
			} else if(mode == GameModes.DYNAMIC) {
				Games.Leaderboards.submitScore(getApiClient(), Ids.dynamic_leaderboard_id, Long.valueOf(scoreText.substring(0, scoreText.length() - 1)));
			} else if(mode == GameModes.SURVIVAL) {
				Games.Leaderboards.submitScore(getApiClient(), Ids.survival_leaderboard_id, Long.valueOf(scoreText.substring(0,scoreText.length() - 1)));
			}
		}
	}

	public void onSignInFailed() {}

	private void doFBLinkPost() {
		if (FacebookDialog.canPresentShareDialog(getApplicationContext(),
				FacebookDialog.ShareDialogFeature.SHARE_DIALOG)) {
			FacebookDialog shareDialog = new FacebookDialog.ShareDialogBuilder(this)
					.setName("Fill The Screen - " + score.getText().toString() + " on " + mode_name.getText().toString() + "!")
					.setApplicationName("Fill The Screen")
					.setDescription(score.getText().toString() + " on " + mode_name.getText().toString() + "!")
					.setPicture("https://lh3.ggpht.com/SIYazIdCzPuDYANLh-fzbcVOWa9UfRkeXBSe3lJ21s5oRP0rZCvEy5sHmiKYaceB_rYI=w300")
					.setLink("https://play.google.com/store/apps/details?id=com.michaelxie.fillthescreen")
					.build();

			uiHelper.trackPendingDialogCall(shareDialog.present());
		} else {
			publishFeedDialog();
		}
	}

	private void publishFeedDialog() {
		Bundle params = new Bundle();
		params.putString("name", "Fill The Screen");
		params.putString("description", score.getText().toString() + " on " + mode_name.getText().toString() + "!");
		params.putString("link", "https://play.google.com/store/apps/details?id=com.michaelxie.fillthescreen");
		params.putString("picture", "https://lh3.ggpht.com/SIYazIdCzPuDYANLh-fzbcVOWa9UfRkeXBSe3lJ21s5oRP0rZCvEy5sHmiKYaceB_rYI=w300");

		try {
			WebDialog feedDialog = (
					new WebDialog.FeedDialogBuilder(this,
							Session.getActiveSession(),
							params))
					.setOnCompleteListener(new WebDialog.OnCompleteListener() {

						@Override
						public void onComplete(Bundle values,
											   FacebookException error) {
							if (error == null) {
								// When the story is posted, echo the success
								// and the post Id.
								final String postId = values.getString("post_id");
							} else if (error instanceof FacebookOperationCanceledException) {
								Log.i(TAG, "Operation Canceled");
							} else {
								Log.e(TAG, "error on Feed Dialog");
							}
						}

					}).build();
			feedDialog.show();
		} catch(Exception e) {
			Toast.makeText(getApplicationContext(), "Facebook share failed.", Toast.LENGTH_SHORT).show();
		}
	}

	private void doFBLogin() {
		// start Facebook Login
		Session.openActiveSession(this, true, new Session.StatusCallback() {

			// callback when session changes state
			@Override
			public void call(Session session, SessionState state, Exception exception) {
				if (session.isOpened()) {

					// make request to the /me API
					Request.newMeRequest(session, new Request.GraphUserCallback() {

						// callback after Graph API response with user object
						@Override
						public void onCompleted(GraphUser user, Response response) {
							if (user != null) {
								Log.i(TAG, "Hello " + user.getName() + "!");
							}
						}
					}).executeAsync();
				}
			}
		});
	}

	//Callback when post UI closes
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		uiHelper.onActivityResult(requestCode, resultCode, data, new FacebookDialog.Callback() {
			@Override
			public void onError(FacebookDialog.PendingCall pendingCall, Exception error, Bundle data) {
				Log.e("Activity", String.format("Error: %s", error.toString()));
			}

			@Override
			public void onComplete(FacebookDialog.PendingCall pendingCall, Bundle data) {
				Log.i("Activity", "Success!");
			}
		});
	}

	/*Facebook UI helper lifecycle methods*/
	@Override
	protected void onResume() {
		super.onResume();
		uiHelper.onResume();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);
	}

	@Override
	public void onPause() {
		super.onPause();
		uiHelper.onPause();
	}

	@Override
	public void onStop() {
		super.onPause();
		uiHelper.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		uiHelper.onDestroy();
	}
}
