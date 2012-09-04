/*
Copyright (C) 2012 Qweex
This file is a part of Callisto.

Callisto is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

Callisto is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Callisto; If not, see <http://www.gnu.org/licenses/>.
*/
package com.qweex.callisto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

/** An activity for playing the Jupiter Broadcasting live stream.
 * @author MrQweex */

public class LiveStream extends Activity
{
	public static String liveTitle;
	private final String infoURL = "http://jbradio.airtime.pro/api/live-info";
	private final static String errorReportURL = "http://software.qweex.com/error_report.php";
	private Matcher m = null;
    private String currentShow = "Unknown", nextShow = "Unknown";
    private TextView current, next;
    private ImageButton bigButton;
    private String live_url;
    private static ProgressDialog pd;
    private static Dialog dg;
    private final int JBLIVE_MENU_ID = Menu.FIRST;
	
	/** Called when the activity is first created. Sets up the view and whatnot.
	 * @param savedInstanceState Um I don't even know. Read the Android documentation.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nowplaying);
		
		dg = new Dialog(this);
		bigButton = (ImageButton) findViewById(R.id.playPause);
		TextView t = new TextView(this);
		t.setText("An error occurred. This may be a one time thing, or your device does not support the stream. You can try going to JBlive.info (via the Menu button) to see if it's just this app.");
		dg.setContentView(t);
		dg.setTitle("By the beard of Zeus!");
		
		
		current = (TextView) findViewById(R.id.current);
		next = (TextView) findViewById(R.id.next);
		bigButton = (ImageButton) findViewById(R.id.playPause);
		bigButton.setOnClickListener(playButton);
	}
	
	/** Called when the activity is resumed, like when you return from another activity or also when it is first created. */
	@Override
	public void onResume()
	{
		super.onResume();
		live_url = PreferenceManager.getDefaultSharedPreferences(LiveStream.this).getString("live_url", "http://jbradio.out.airtime.pro:8000/jbradio_b");
		
		if(Callisto.live_player!=null)
			update();
		if(Callisto.live_isPlaying)
			bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
	}
	
	/** Called when it is time to create the menu.
	 * @param menu Um, the menu
	 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	menu.add(Menu.NONE, JBLIVE_MENU_ID, Menu.NONE, "JBLive");
    	return true;
    }
    
    /** Called when an item in the menu is pressed.
	 * @param item The menu item ID that was pressed
	 */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	switch (item.getItemId())
    	{
    	case JBLIVE_MENU_ID:
    	    Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://jblive.info"));
    	    startActivity(myIntent);
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
	
    /** Updates the current and next track information. */
	public void update()
    {
	    HttpClient httpClient = new DefaultHttpClient();
	    HttpContext localContext = new BasicHttpContext();
	    HttpGet httpGet = new HttpGet(infoURL);
	    HttpResponse response;
	    try
		{
			response = httpClient.execute(httpGet, localContext);
		    BufferedReader reader = new BufferedReader(
			        new InputStreamReader(
			          response.getEntity().getContent()
			        )
			      );
		    
		    String line = null, result = "";
		    while ((line = reader.readLine()) != null){
		      result += line + "\n";
		    }
		    
		    m = (Pattern.compile(".*?\"currentShow\".*?"
		    					+ "\"name\":\"(.*?)\""
		    					+ ".*"
								+ "\"name\":\"(.*?)\""
		    					+ ".*?")
		    					).matcher(result);
		    if(m.find())
		    	currentShow = m.group(1);
		    if(m.groupCount()>1)
		    	nextShow = m.group(2);
	    	
		    current.setText(currentShow);
		    liveTitle = currentShow;
		    next.setText(nextShow);
		    
		} catch (ClientProtocolException e) {
			// TODO EXCEPTION
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Initiates the live player. Can be called across activities. */
	static public void liveInit()
	{
		Log.d("LiveStream:liveInit", "Initiating the live player.");
		Callisto.live_player = new MediaPlayer();
		Callisto.live_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		Callisto.live_player.setOnErrorListener(new OnErrorListener() {
		    public boolean onError(MediaPlayer mp, int what, int extra) {
		    	pd.cancel();
		    	dg.show();
		    	String whatWhat="";
		    	switch (what) {
		        case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
		            whatWhat = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
		            break;
		        case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
		        	whatWhat = "MEDIA_ERROR_SERVER_DIED";
		            break;
		        case MediaPlayer.MEDIA_ERROR_UNKNOWN:
		        	whatWhat = "MEDIA_ERROR_UNKNOWN";
		            break;
		        default:
		        	whatWhat = "???";
		        }

		    	LiveStream.sendErrorReport(whatWhat);
		        return true;
		    }
		});
	}
	
	/** Method to prepare the live player; shows a dialog and then sets it up to be transfered to livePreparedListenerOther. */
	static public void livePrepare(Context c)
	{
		pd = ProgressDialog.show(c, "Buffering", Callisto.RESOURCES.getString(R.string.loading_msg), true, false);
		pd.setCancelable(true);
		Callisto.live_player.prepareAsync();
	}
	
	/** Listener for the live player in any activity other than LiveStream. Starts it playing or displays an error message. */
	static OnPreparedListener livePreparedListenerOther = new OnPreparedListener()
	{
		@Override
		public void onPrepared(MediaPlayer arg0) {
			if(!pd.isShowing())
				return;
			pd.cancel();
			try {
				Callisto.live_player.start();
				Callisto.live_isPlaying = true;
			}
			catch(Exception e)
			{
				LiveStream.dg.show();
			}
		}
	};
	
	/** Listener for the live player in only the LiveStream activity. Starts it playing or displays an error message. */
	private OnPreparedListener livePreparedListener = new OnPreparedListener()
	{
		@Override
		public void onPrepared(MediaPlayer arg0) {
			if(!pd.isShowing())
				return;
			pd.cancel();
			try {
				Callisto.live_player.start();
				update();
				Callisto.live_isPlaying = true;
				bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
				Intent notificationIntent = new Intent(LiveStream.this, LiveStream.class);
				PendingIntent contentIntent = PendingIntent.getActivity(LiveStream.this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		    	Callisto.notification_playing = new Notification(R.drawable.callisto, Callisto.RESOURCES.getString(R.string.playing), System.currentTimeMillis());
				Callisto.notification_playing.flags = Notification.FLAG_ONGOING_EVENT;
		       	Callisto.notification_playing.setLatestEventInfo(LiveStream.this, liveTitle,  "JB Radio", contentIntent);
			       	
		       	NotificationManager mNotificationManager =  (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		       	mNotificationManager.notify(Callisto.NOTIFICATION_ID, Callisto.notification_playing);
			}
			catch(Exception e)
			{
				dg.show();
				e.printStackTrace();
			}
		}
	};
	
	/** Listener for the play button. Duh! */
	private OnClickListener playButton = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Log.d("LiveStream:playButton", "Clicked play button");
			if(!Callisto.playerInfo.isPaused)
			{
				Callisto.mplayer.pause();
				Callisto.playerInfo.isPaused = true;
			}
			
			if(Callisto.live_player == null || !Callisto.live_isPlaying)
			{
				Log.d("LiveStream:playButton", "Live player does not exist, creating it.");
				if(Callisto.mplayer!=null)
					Callisto.mplayer.reset();
				Callisto.mplayer=null;
				liveInit();
				Callisto.live_player.setOnPreparedListener(livePreparedListener);
					try {
				Callisto.live_player.setDataSource(live_url);
				livePrepare(LiveStream.this);
				} catch (Exception e) {
					dg.show();
					sendErrorReport("EXCEPTION");
					e.printStackTrace();
				}
			}
			else
			{
				Log.d("LiveStream:playButton", "Live player does exist.");
				if(Callisto.live_isPlaying)
				{
					Log.d("LiveStream:playButton", "Pausing.");
					bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_play_lg));
					Callisto.live_player.pause();
				}
				else
				{
					Log.d("LiveStream:playButton", "Playing.");
					bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
					Callisto.live_player.start();
				}
				Callisto.live_isPlaying = !Callisto.live_isPlaying;
			}
			Log.d("LiveStream:playButton", "Done");
		}
	};
	
	/** Sends an error report to the folks at Qweex. COMPLETELY anonymous. The only information that is sent is the version of Callisto and the version of Android. */
	public static void sendErrorReport(String msg)
	{
		String errorReport = LiveStream.errorReportURL + "?id=Callisto&v=" + Callisto.appVersion + "&err=" + android.os.Build.VERSION.RELEASE + "_" + msg;
		
		
		HttpClient httpClient = new DefaultHttpClient();
	    HttpContext localContext = new BasicHttpContext();
	    HttpGet httpGet = new HttpGet(errorReport);
	    try {
	    httpClient.execute(httpGet, localContext);
	    }catch(Exception e){}
	}
}