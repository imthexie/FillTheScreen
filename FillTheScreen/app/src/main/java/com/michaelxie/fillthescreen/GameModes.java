package com.michaelxie.fillthescreen;

/**
 * Created by Michael Xie on 8/2/2014.
 */
public class GameModes {
	public static final int CLASSIC = 0;
	public static final int INFINITE = 1;
	public static final int SURVIVAL = 2;
	public static final int DYNAMIC = 3;

	public static int gameModeFromName(String name) {
		if(name.equals("CLASSIC")) {
			return CLASSIC;
		} else if(name.equals("INFINITE")) {
			return INFINITE;
		} else if(name.equals("SURVIVAL")) {
			return SURVIVAL;
		} else if(name.equals("DYNAMIC")) {
			return DYNAMIC;
		}
		return -1;
	}

	public static String gameModeToName(int gameMode) {
		switch(gameMode) {
			case CLASSIC:
				return "CLASSIC";
			case INFINITE:
				return "INFINITE";
			case SURVIVAL:
				return "SURVIVAL";
			case DYNAMIC:
				return "DYNAMIC";
		}
		return null;
	}
}
