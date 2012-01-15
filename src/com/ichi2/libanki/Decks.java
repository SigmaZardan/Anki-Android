/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.os.DropBoxManager.Entry;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.DeckTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a
 * file with a name ending in .anki See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Decks {

	// private TreeMap<Integer, Model> mModelCache;
	// private TreeMap<Integer, JSONObject> mGroupConfCache;

	private static final String defaultDeck = "{" + "'newToday': [0, 0], "
			+ // currentDay, count
			"'revToday': [0, 0], " + "'lrnToday': [0, 0], "
			+ "'timeToday': [0, 0], " + // time in ms
			"'conf': 1, " + "'usn': 0, " + "'desc': \"\" }";

	// default group conf
	private static final String defaultConf = "{" + "'name': \"Default\""
			+ "'new': {" + "'delays': [1, 10], " + "'ints': [1, 4, 7], " + // 7
																			// is
																			// not
																			// currently
																			// used
			"'initialFactor': 2500, " + "'separate': True, " + "'order': "
			+ Sched.NEW_CARDS_DUE
			+ ", "
			+ "'perDay': 20, }, "
			+ "'lapse': {"
			+ "'delays': [1, 10], "
			+ "'mult': 0, "
			+ "'minInt': 1, "
			+ "'leechFails': 8, "
			+ "'leechAction': 0, }, "
			+ // type 0=suspend, 1=tagonly
			"'cram': { "
			+ "'delays': [1, 5, 10], "
			+ "'resched': True, "
			+ "'reset': True, "
			+ "'mult': 0, "
			+ "'minInt': 1, }, "
			+ "'rev': { "
			+ "'perDay': 100"
			+ "'ease4': 1.3, "
			+ "'fuzz': 0.05, "
			+ "'minSpace': 1, "
			+ "'fi': [10, 10], "
			+ "'order': "
			+ Sched.REV_CARDS_RANDOM
			+ "}, "
			+ "'maxTaken': 60, "
			+ "'timer': 0, "
			+ "'autoplay': True, "
			+ "'mod': 0, "
			+ "'usn': 0, }";

	private Collection mCol;
	private HashMap<Long, JSONObject> mDecks;
	private HashMap<String, Long> mDeckIds;
	private HashMap<Long, JSONObject> mDconf;
	private boolean mChanged;

	/**
	 * Registry save/load
	 * *******************************************************
	 * ****************************************
	 */

	public Decks(Collection col) {
		mCol = col;
	}

	public void load(String decks, String dconf) {
		mDecks = new HashMap<Long, JSONObject>();
		mDeckIds = new HashMap<String, Long>();
		mDconf = new HashMap<Long, JSONObject>();
		try {
			JSONObject decksarray = new JSONObject(decks);
			JSONArray ids = decksarray.names();
			for (int i = 0; i < ids.length(); i++) {
				String id = ids.getString(i);
				JSONObject o = decksarray.getJSONObject(id);
				long longId = Long.parseLong(id);
				mDecks.put(longId, o);
				mDeckIds.put(o.getString("name"), longId);
			}
			JSONObject confarray = new JSONObject(dconf);
			ids = confarray.names();
			for (int i = 0; i < ids.length(); i++) {
				String id = ids.getString(i);
				mDconf.put(Long.parseLong(id), confarray.getJSONObject(id));
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		mChanged = false;
	}

	public void save() {
		save(null);
	}

	/** Can be called with either a deck or a deck configuration. */
	public void save(JSONObject g) {
		if (g != null) {
			try {
				g.put("mod", Utils.intNow());
				g.put("usn", mCol.getUsn());
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		mChanged = true;
	}

	public void flush() {
		ContentValues values = new ContentValues();
		if (mChanged) {
			try {
				JSONObject decksarray = new JSONObject();
				for (Map.Entry<Long, JSONObject> d : mDecks.entrySet()) {
					decksarray.put(Long.toString(d.getKey()), d.getValue());
				}
				values.put("decks", decksarray.toString());
				JSONObject confarray = new JSONObject();
				for (Map.Entry<Long, JSONObject> d : mDconf.entrySet()) {
					confarray.put(Long.toString(d.getKey()), d.getValue());
				}
				values.put("dconf", confarray.toString());
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			mCol.getDb().getDatabase().update("col", values, null, null);
			mChanged = false;
		}
	}

	/**
	 * Deck save/load
	 * ***********************************************************
	 * ************************************
	 */

	public long id(String name) {
		return id(name, true);
	}

	/** Add a deck with NAME. Reuse deck if already exists. Return id as int. */
	public long id(String name, boolean create) {
		name = name.replace("\'", "").replace("\"", "");
		for (Map.Entry<Long, JSONObject> g : mDecks.entrySet()) {
			try {
				if (g.getValue().getString("name").equalsIgnoreCase(name)) {
					return g.getKey();
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		if (!create) {
			return 0;
		}
		if (name.matches("::")) {
			// not top level; ensure all parents exist
			_ensureParents(name);
		}
		JSONObject g;
		long id;
		try {
			g = new JSONObject(defaultDeck);
			g.put("name", name);
			id = Utils.intNow(1000);
			while (mDecks.containsKey(id)) {
				id = Utils.intNow();
			}
			g.put("id", id);
			mDecks.put(id, g);
			mDeckIds.put(name, id);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		save(g);
		maybeAddToActive();
		return id;
	}

	public void rem(long did) {
		rem(did, false);
	}

	/** Remove the deck. If cardsToo, delete any cards inside. */
	public void rem(long did, boolean cardsToo) {
		if (did == 1) {
			return;
		}
		if (!mDecks.containsKey(did)) {
			return;
		}
		// delete children first
		for (long id : children(did).values()) {
			rem(id, cardsToo);
		}
		// delete cards too?
		if (cardsToo) {
			mCol.remCards(cids(did));
		}
		// delete the deck and add a grave
		mDecks.remove(did);
		mCol._logRem(new long[] { did }, Sched.REM_DECK);
		// ensure we have an active deck
		if (active().contains(did)) {
			select((long) (mDecks.keySet().iterator().next()));
		}
		save();
	}

	/** An unsorted list of all deck names. */
	public ArrayList<String> allNames() {
		ArrayList<String> list = new ArrayList<String>();
		for (JSONObject x : mDecks.values()) {
			try {
				list.add(x.getString("name"));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		return list;
	}

	/**
	 * A list of all decks.
	 */
	public ArrayList<JSONObject> all() {
		ArrayList<JSONObject> decks = new ArrayList<JSONObject>();
		Iterator<JSONObject> it = mDecks.values().iterator();
		while (it.hasNext()) {
			decks.add(it.next());
		}
		return decks;
	}

	public int count() {
		return mDecks.size();
	}

	public JSONObject get(long did) {
		return get(did, true);
	}

	public JSONObject get(long did, boolean defaultvalue) {
		if (mDecks.containsKey(did)) {
			return mDecks.get(did);
		} else if (defaultvalue) {
			return mDecks.get(1);
		} else {
			return null;
		}
	}

	// TODO: update

	/** Rename deck prefix to NAME if not exists. Updates children. */
	public void rename(JSONObject g, String newName) {
		// make sure target node doesn't already exist
		if (allNames().contains(newName) || newName.length() == 0) {
			return;
		}
		// rename children
		String oldName;
		try {
			oldName = g.getString("name");
			for (JSONObject grp : all()) {
				if (grp.getString("name").startsWith(oldName + "::")) {
					String on = grp.getString("name");
					String nn = on.replace(oldName + "::", newName + "::");
					grp.put("name", nn);
					mDeckIds.put(nn, mDeckIds.remove(on));
				}
			}
			// adjust name and save
			g.put("name", newName);
			mDeckIds.put(newName, mDeckIds.remove(oldName));
			save(g);
			// finally, ensure we have parents
			_ensureParents(newName);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private void _ensureParents(String name) {
		String[] path = name.split("::");
		String s = "";
		for (int i = 0; i < path.length - 1; i++) {
			if (i == 0) {
				s = path[0];
			} else {
				s = s + "::" + path[i];
			}
			id(s);
		}
	}

	/**
	 * Deck configurations
	 * ******************************************************
	 * *****************************************
	 */

	/** A list of all deck config. */
	public ArrayList<JSONObject> allConf() {
		ArrayList<JSONObject> confs = new ArrayList<JSONObject>();
		for (JSONObject c : mDconf.values()) {
			confs.add(c);
		}
		return confs;
	}

	public JSONObject confForDid(long did) {
		try {
			return getConf(get(did).getLong("conf"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public JSONObject getConf(long confId) {
		return mDconf.get(confId);
	}

	public void updateConf(JSONObject g) {
		try {
			mDconf.put(g.getLong("id"), g);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		save();
	}

	// confid
	// remConf

	public void setConf(JSONObject deck, long id) {
		try {
			deck.put("conf", id);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		save(deck);
	}

	// setConf
	// didsforConf
	// restoretodefault

	/**
	 * Deck utils
	 * ***************************************************************
	 * ********************************
	 */

	public String name(long did) {
		try {
			return get(did).getString("name");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public void setDeck(long[] cids, long did) {
		mCol.getDb()
				.getDatabase()
				.execSQL(
						"UPDATE cards SET did = ?, usn = ?, mod = ? WHERE id IN "
								+ Utils.ids2str(cids),
						new Object[] { did, mCol.getUsn(), Utils.intNow() });
	}

	private void maybeAddToActive() {
		// reselect current deck, or default if current has disappeared
		JSONObject c = current();
		try {
			select(c.getLong("id"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	// sendhome

	private long[] cids(long did) {
		ArrayList<Long> cids = mCol.getDb().queryColumn(long.class,
				"SELECT id FROM cards WHERE did = " + did, 0);
		long[] result = new long[cids.size()];
		for (int i = 0; i < cids.size(); i++) {
			result[i] = cids.get(i);
		}
		return result;
	}

	/**
	 * Deck selection
	 * ***********************************************************
	 * ************************************
	 */

	/* The currrently active dids. */
	public LinkedList<Long> active() {
		try {
			String actv = mCol.getConf().getString("activeDecks");
			JSONArray ja = new JSONArray(actv);
			LinkedList<Long> result = new LinkedList<Long>();
			for (int i = 0; i < ja.length(); i++) {
				result.add(ja.getLong(i));
			}
			return result;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/* The currently selected did. */
	public long selected() {
		try {
			return mCol.getConf().getLong("curDeck");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public JSONObject current() {
		return get(selected());
	}

	/* Select a new branch. */
	public void select(long did) {
		try {
			String name = mDecks.get(did).getString("name");
			// current deck
			mCol.getConf().put("curDeck", Long.toString(did));
			// and active decks (current + all children)
			// TODO: test, if order is correct
			TreeMap<String, Long> actv = children(did);
			actv.put(name, did);
			mCol.getConf().put("activeDecks", actv.values().toString());
			mChanged = true;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/* all children of did as (name, id) */
	public TreeMap<String, Long> children(long did) {
		String name;
		try {
			name = get(did).getString("name");
			TreeMap<String, Long> list = new TreeMap<String, Long>();
			for (JSONObject g : all()) {
				if (g.getString("name").startsWith(name + "::")) {
					list.put(g.getString("name"), g.getLong("id"));
				}
			}
			return list;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/* all parents of did */
	public ArrayList<JSONObject> parents(long did) {
		ArrayList<JSONObject> list = new ArrayList<JSONObject>();
		try {
			String[] path = get(did).getString("name").split("::");
			String deckpath = null;
			for (int i = 0; i < path.length - 1; i++) {
				if (i == 0) {
					deckpath = path[0];
				} else {
					deckpath = deckpath + "::" + path[i];
				}
				list.add(get(mDeckIds.get(deckpath)));
			}
			return list;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Sync handling
	 * ************************************************************
	 * ***********************************
	 */

	// TODO: beforeUpload

	public String getActualDescription() {
		try {
			return current().getString("desc");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	// /**
	// * Yes counts
	// * @return todayAnswers, todayNoAnswers, matureAnswers, matureNoAnswers
	// */
	// public int[] yesCounts() {
	// int dayStart = (getSched().mDayCutoff - 86400) * 10000;
	// int todayAnswers = (int)
	// getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " +
	// dayStart);
	// int todayNoAnswers = (int)
	// getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " +
	// dayStart + " AND ease = 1");
	// int matureAnswers = (int)
	// getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21");
	// int matureNoAnswers = (int)
	// getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21 AND ease = 1");
	// return new int[] { todayAnswers, todayNoAnswers, matureAnswers,
	// matureNoAnswers };
	// }
	//
	//
	// /**
	// * Yes rates for today's and mature cards
	// * @return todayRate, matureRate
	// */
	// public double[] yesRates() {
	// int[] counts = yesCounts();
	// return new double[] { 1 - (double)counts[1]/counts[0], 1 -
	// (double)counts[3]/counts[2] };
	// }
	//
	//
	// // Media
	// // *****
	//
	// /**
	// * Return the media directory if exists, none if couldn't be created.
	// *
	// * @param create If true it will attempt to create the folder if it
	// doesn't exist
	// * @param rename This is used to simulate the python with create=None that
	// is only used when renaming the mediaDir
	// * @return The path of the media directory
	// */
	// public String mediaDir() {
	// return mediaDir(false, false);
	// }
	// public String mediaDir(boolean create) {
	// return mediaDir(create, false);
	// }
	// public String mediaDir(boolean create, boolean rename) {
	// String dir = null;
	// File mediaDir = null;
	// if (mDeckPath != null && !mDeckPath.equals("")) {
	// Log.i(AnkiDroidApp.TAG, "mediaDir - mediaPrefix = " + mMediaPrefix);
	// if (mMediaPrefix != null) {
	// dir = mMediaPrefix + "/" + mDeckName + ".media";
	// } else {
	// dir = mDeckPath.replaceAll("\\.anki$", ".media");
	// }
	// if (rename) {
	// // Don't create, but return dir
	// return dir;
	// }
	// mediaDir = new File(dir);
	// if (!mediaDir.exists() && create) {
	// try {
	// if (!mediaDir.mkdir()) {
	// Log.e(AnkiDroidApp.TAG, "Couldn't create media directory " + dir);
	// return null;
	// }
	// } catch (SecurityException e) {
	// Log.e(AnkiDroidApp.TAG,
	// "Security restriction: Couldn't create media directory " + dir);
	// return null;
	// }
	// }
	// }
	//
	// if (dir == null) {
	// return null;
	// } else {
	// if (!mediaDir.exists() || !mediaDir.isDirectory()) {
	// return null;
	// }
	// }
	// Log.i(AnkiDroidApp.TAG, "mediaDir - mediaDir = " + dir);
	// return dir;
	// }
	//
	// public String getMediaPrefix() {
	// return mMediaPrefix;
	// }
	// public void setMediaPrefix(String mediaPrefix) {
	// mMediaPrefix = mediaPrefix;
	// }
	// //
	// //
	// //
	// // private boolean hasLaTeX() {
	// // Cursor cursor = null;
	// // try {
	// // cursor = getDB().getDatabase().rawQuery(
	// // "SELECT Id FROM fields WHERE " +
	// // "(value like '%[latex]%[/latex]%') OR " +
	// // "(value like '%[$]%[/$]%') OR " +
	// // "(value like '%[$$]%[/$$]%') LIMIT 1 ", null);
	// // if (cursor.moveToFirst()) {
	// // return true;
	// // }
	// // } finally {
	// // if (cursor != null) {
	// // cursor.close();
	// // }
	// // }
	// // return false;
	// // }

}
