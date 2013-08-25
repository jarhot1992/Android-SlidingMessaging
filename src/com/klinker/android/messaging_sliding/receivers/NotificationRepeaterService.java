package com.klinker.android.messaging_sliding.receivers;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.preference.PreferenceManager;

public class NotificationRepeaterService extends IntentService {

	public NotificationRepeaterService() {
		super("notification repeater service");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (sharedPrefs.getBoolean("wake_screen", false))
		{
			PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            @SuppressWarnings("deprecation")
			final WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
            wakeLock.acquire(Long.parseLong(sharedPrefs.getString("screen_timeout", "5"))*1000);
		}
		
		AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
            	if (sharedPrefs.getBoolean("vibrate", true))
		        {
		        	Vibrator vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		        	
		        	if (!sharedPrefs.getBoolean("custom_vibrate_pattern", false))
		        	{
			        	String vibPat = sharedPrefs.getString("vibrate_pattern", "2short");
			        	
			        	if (vibPat.equals("short"))
			        	{
			        		long[] pattern = {0L, 400L};
			        		vibrator.vibrate(pattern, -1);
			        	} else if (vibPat.equals("long"))
			        	{
			        		long[] pattern = {0L, 800L};
			        		vibrator.vibrate(pattern, -1);
			        	} else if (vibPat.equals("2short"))
			        	{
			        		long[] pattern = {0L, 400L, 100L, 400L};
			        		vibrator.vibrate(pattern, -1);
			        	} else if (vibPat.equals("2long"))
			        	{
			        		long[] pattern = {0L, 800L, 200L, 800L};
			        		vibrator.vibrate(pattern, -1);
			        	} else if (vibPat.equals("3short"))
			        	{
			        		long[] pattern = {0L, 400L, 100L, 400L, 100L, 400L};
			        		vibrator.vibrate(pattern, -1);
			        	} else if (vibPat.equals("3long"))
			        	{
			        		long[] pattern = {0L, 800L, 200L, 800L, 200L, 800L};
			        		vibrator.vibrate(pattern, -1);
			        	}
		        	} else
		        	{
		        		try
		        		{
			        		String[] vibPat = sharedPrefs.getString("set_custom_vibrate_pattern", "0, 100, 100, 100").split(", ");
			        		long[] pattern = new long[vibPat.length];
			        		
			        		for (int i = 0; i < vibPat.length; i++)
			        		{
			        			pattern[i] = Long.parseLong(vibPat[i]);
			        		}
			        		
			        		vibrator.vibrate(pattern, -1);
		        		} catch (Exception e)
		        		{
		        			
		        		}
		        	}
		        }
            	
                break;
            case AudioManager.RINGER_MODE_NORMAL:
            	try
            	{
	            	Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	            	
	            	try
			        {
			        	notification = (Uri.parse(sharedPrefs.getString("ringtone", "null")));
			        } catch(Exception e)
			        {
			        	notification = (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
			        }
	            	
	            	Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
	            	r.play();
	            	
	            	if (sharedPrefs.getBoolean("vibrate", true))
			        {
			        	Vibrator vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
			        	
			        	if (!sharedPrefs.getBoolean("custom_vibrate_pattern", false))
			        	{
				        	String vibPat = sharedPrefs.getString("vibrate_pattern", "2short");
				        	
				        	if (vibPat.equals("short"))
				        	{
				        		long[] pattern = {0L, 400L};
				        		vibrator.vibrate(pattern, -1);
				        	} else if (vibPat.equals("long"))
				        	{
				        		long[] pattern = {0L, 800L};
				        		vibrator.vibrate(pattern, -1);
				        	} else if (vibPat.equals("2short"))
				        	{
				        		long[] pattern = {0L, 400L, 100L, 400L};
				        		vibrator.vibrate(pattern, -1);
				        	} else if (vibPat.equals("2long"))
				        	{
				        		long[] pattern = {0L, 800L, 200L, 800L};
				        		vibrator.vibrate(pattern, -1);
				        	} else if (vibPat.equals("3short"))
				        	{
				        		long[] pattern = {0L, 400L, 100L, 400L, 100L, 400L};
				        		vibrator.vibrate(pattern, -1);
				        	} else if (vibPat.equals("3long"))
				        	{
				        		long[] pattern = {0L, 800L, 200L, 800L, 200L, 800L};
				        		vibrator.vibrate(pattern, -1);
				        	}
			        	} else
			        	{
			        		try
			        		{
				        		String[] vibPat = sharedPrefs.getString("set_custom_vibrate_pattern", "0, 100, 100, 100").split(", ");
				        		long[] pattern = new long[vibPat.length];
				        		
				        		for (int i = 0; i < vibPat.length; i++)
				        		{
				        			pattern[i] = Long.parseLong(vibPat[i]);
				        		}
				        		
				        		vibrator.vibrate(pattern, -1);
			        		} catch (Exception e)
			        		{
			        			
			        		}
			        	}
			        }
            	} catch (Exception e)
            	{
            		
            	}
            	
                break;
        }
			
        stopSelf();
        	
	}
}
