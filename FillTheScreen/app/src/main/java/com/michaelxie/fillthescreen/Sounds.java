package com.michaelxie.fillthescreen;

import android.content.Context;
import android.media.MediaPlayer;

/**
 * Created by Michael Xie on 8/10/2014.
 */
public class Sounds {
	private MediaPlayer pop;
	private MediaPlayer slide;
	private MediaPlayer gameOver;

	public Sounds(Context context) {
		pop = MediaPlayer.create(context, R.raw.pop);
		gameOver = MediaPlayer.create(context, R.raw.game_over);
		slide = MediaPlayer.create(context, R.raw.slide);
	}
	public void startSlideSound() {
		slide.start();
	}

	public void startPopSound() {
		pop.start();
	}

	public void startGameOverSound() {
		gameOver.start();
	}
}
