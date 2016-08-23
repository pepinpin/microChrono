package net.biospherecorp.microchrono;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	// the notification Time
	private static final int NOTIFICATION_TIME = 2500; // in ms

	// the default time for the timer
	private static final int DEFAULT_TIME_MN = 1;

	// this fields need to be static to be usable by the handler
	//
	// Putting a class needing the Context in a HardReference static
	// variable will leak the context, hence the use of a WeakReference
	private static WeakReference<LiquidButton> LIQUID_BUTTON;
	private static WeakReference<TextView> TEXT_TIME;

	// set the variable used to count the minutes
	private static int COUNT_MINUTE;

	// variable to hold the time in seconds
	private static float TIME_IN_SEC;

	// is the timer actually running ?
	private static boolean IS_RUNNING = false;


	// the wakelock, to keep the app running even if
	// the device goes to sleep mode
	private PowerManager.WakeLock _wl;

	// the position of the default time value in the array
	private int _valueInArrayTime = 3;

	// the secondary textview
	private TextView _secondaryTextView;

	// has the cancel button been pressed
	private boolean _isCanceledByUser = false;

	// The colors used by the buttons
	private int _colorPrimary, _colorSecondary;

	// the floating Action buttons
	private FloatingActionButton _startButton, _settingButton;

	// the thread ans the handler
	private Thread _thread;
	private Handler _handler;

	// the snackBar used when the timer is canceled by the user
	private Snackbar _snackBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// lock the screen orientation when the app is launched
		// (to compensate for the artifact that appears with
		// the liquid button on orientation change :/ )
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		_wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "The WakeLock");

		// get the textView showing the time
		TEXT_TIME = new WeakReference<>((TextView) findViewById(R.id.text_time));

		// set the time with the default value
		TEXT_TIME.get().setText(DEFAULT_TIME_MN + " mn");

		// get the liquidButton
		LIQUID_BUTTON = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));

		// say that the button stays filled up after the animation is complete
		LIQUID_BUTTON.get().setFillAfter(true);


		COUNT_MINUTE = DEFAULT_TIME_MN;

		// get the second textView
		_secondaryTextView = (TextView) findViewById(R.id.text_secondary);

		// get the colors
		_colorPrimary = getResources().getColor(R.color.colorPrimary);
		_colorSecondary = getResources().getColor(R.color.colorAccent);

		// get the buttons from the view
		_startButton = (FloatingActionButton) findViewById(R.id.start_button);
		_settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		// set the buttons background colors
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
		_settingButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));

		// instantiate the handler
		_handler = new mHandler();

		// the setting section
		_settingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// show the alertDialog &
				// the number picker
				_showNumberPicker();
			}
		});


		// the start button
		_startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// if the timer AND the liquid animations aren't running
				if (!IS_RUNNING){

					// say that's running
					IS_RUNNING = true;

					// store the conversion of minute to seconds
					TIME_IN_SEC = COUNT_MINUTE * 60f;

					// initialize the Buttons
					_initButtons();

					// initialize the TextViews
					_initTextViews();

					// dim the screen backlight down
					_lightScreenDown();

					// clear the notifications
					NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
					manager.cancelAll();

					// start the timer's thread
					_thread = new Thread(new ChronoMinute());
					_thread.start();

				}else if (!_isCanceledByUser){

					// only true when this button is pressed
					// while a timer is in progress
					_isCanceledByUser = true;

					// if the thread is still alive, stop it
					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					// turn the screen back light to 80%
					_lightScreenUp();

					// reset the buttons and textViews to original state
					_hideButtonsAndTextViews();

					// trigger the "finish pouring" animation
					LIQUID_BUTTON.get().finishPour();

					// display a snackBar asking the user to wait for the end of the animation
					_snackBar = Snackbar.make(view, R.string.wait_message, Snackbar.LENGTH_INDEFINITE);
					_snackBar.show();
				}
			}
		});



		// set a listener on the liquid button
		LIQUID_BUTTON.get().setPourFinishListener(new LiquidButton.PourFinishListener() {

			@Override
			public void onPourFinish() { // when the pouring animation is finished

				// change the start button background color AND image
				_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
				_startButton.setImageResource(android.R.drawable.ic_media_play);

				// if the snackBar is visible, dismiss it
				if (_snackBar != null){
					_snackBar.dismiss();
				}

				// show the description textViews
				_secondaryTextView.setVisibility(View.VISIBLE);

				// show the buttons
				_startButton.setVisibility(View.VISIBLE);
				_settingButton.setVisibility(View.VISIBLE);

				// re enabled the settings button
				_settingButton.setEnabled(true);

				// it's not running anymore
				IS_RUNNING = false;
			}

			@Override
			public void onProgressUpdate(float progress) {

				if (progress >= 1f){

					if (!_isCanceledByUser){

						// Change the textView (cannot be done from the thread)
						_secondaryTextView.setText(R.string.notification_text);

						Thread notifyThread = new Thread(new NotifyTimeIsUp());
						notifyThread.start();

					}else{

						// reset the variable to false
						_isCanceledByUser = false;

						// display the "Press Start" text
						_secondaryTextView.setText(R.string.press_start);

						// turn the screen backlight up
						_lightScreenUp();
					}
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onPostResume();

		// set the flag to avoid the device to go to sleep mode
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);


		// set the flag to deal with the lockscreen
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
	}

	@Override
	protected void onPause() {
		super.onPause();

		// clear the flag that avoids the device to go to sleep mode
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);


		// clear the flag that deals with the lockscreen
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);


		// display a notification indicating the state of the app (... in progress...)
		//
		// if timer is running
		if(IS_RUNNING){

			Intent intent = new Intent(MainActivity.this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			// prepare and display the notification
			NotificationCompat.Builder notification =
					new NotificationCompat.Builder(MainActivity.this)
							.setSmallIcon(R.drawable.ic_notification_microchrono)
							.setContentTitle(getResources().getString(R.string.app_name))
							.setContentText(getString(R.string.notification_text))
							.setContentIntent(pendingIntent)
							.setAutoCancel(true);

			NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.notify(1, notification.build());
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// release the wakelock
		if (_wl != null && _wl.isHeld()){
			_wl.release();
		}

		// interrupt the thread
		if (_thread != null && !_thread.isInterrupted()){
			_thread.interrupt();
		}

		// clear the notifications
		NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		manager.cancelAll();
	}


	private void _initButtons(){

		// change the image and the background color of the _startButton
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorSecondary));
		_startButton.setImageResource(android.R.drawable.ic_delete);

		// hide the settings button
		_settingButton.setVisibility(View.INVISIBLE);

		// start the pouring animation
		LIQUID_BUTTON.get().startPour();
	}

	private void _initTextViews(){

		// show the main TextView
		TEXT_TIME.get().setVisibility(View.VISIBLE);
		// set the main textView
		TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

		// hide the secondary textView
		_secondaryTextView.setVisibility(View.INVISIBLE);
	}

	private void _hideButtonsAndTextViews() {

		// hide the main TextView
		TEXT_TIME.get().setVisibility(View.INVISIBLE);

		// hide the description TextView
		_secondaryTextView.setVisibility(View.INVISIBLE);

		// hide the buttons
		_startButton.setVisibility(View.INVISIBLE);
		_settingButton.setVisibility(View.INVISIBLE);
	}

	// turn the screen brightness up
	private void _lightScreenUp(){
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = -1f;
		getWindow().setAttributes(layout);
	}

	// dim the screen brightness down
	private void _lightScreenDown(){
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = 0.2f;
		getWindow().setAttributes(layout);
	}


	// Displays the alertDialog used by the Settings section
	private void _showNumberPicker(){

		// the array holding the usable values
		final String[] TIME_ARRAY = new String[102];

		// add some info about egg cooking
		TIME_ARRAY[0] = getString(R.string.boiled_egg_cooking_time);
		TIME_ARRAY[1] = getString(R.string.soft_boiled_egg_cooking_time);
		TIME_ARRAY[2] = getString(R.string.hard_boiled_egg_cooking_time);

		// fill up the array used by the settings number picker (from 1 to 99)
		for (int i = 3; i <= 101; i++){
			TIME_ARRAY[i] = String.valueOf(i - 2); // to deduce the value from the index
		}

		final AlertDialog.Builder adb = new AlertDialog.Builder(this);
		final NumberPicker np = new NumberPicker(this);

		// set the title
		adb.setTitle(R.string.settings_title);

		// to make sure the soft keyboard doesn't pop up
		np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

		// values to use
		np.setDisplayedValues(TIME_ARRAY);

		// set the min index in the Wheel
		np.setMinValue(0);

		// set the max index in the Wheel
		np.setMaxValue(TIME_ARRAY.length -1);

		// some default value
		np.setValue(_valueInArrayTime);

		// does the wheel wrap around
		np.setWrapSelectorWheel(true);

		// set the "Set" button
		adb.setPositiveButton(R.string.settings_set, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				// store the value chosen (the position in the TIME_ARRAY)
				_valueInArrayTime = np.getValue();

				// check if it's a egg cooking time
				switch (_valueInArrayTime){
					case 0:
						COUNT_MINUTE = 3;
						break;
					case 1:
						COUNT_MINUTE = 5;
						break;
					case 2:
						COUNT_MINUTE = 10;
						break;

					// if not
					default:
						// store the chosen value (the proper value in mn)
						COUNT_MINUTE = Integer.valueOf(TIME_ARRAY[_valueInArrayTime]);
				}

				// display the new value in the main TextView
				TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

				// dismiss this AlertDialog
				dialogInterface.dismiss();
			}
		});

		// set the "Cancel" button
		adb.setNeutralButton(R.string.settings_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				// dismiss this AlertDialog
				dialogInterface.dismiss();
			}
		});

		// this listener is set to allow proper navigation on TVs (with Dpad)
		adb.setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {

				// if key is released (action up)
				if (keyEvent.getAction() == KeyEvent.ACTION_UP){

					// check fot the keyCode
					switch(keyEvent.getKeyCode()){

						// if it's up
						case KeyEvent.KEYCODE_DPAD_UP:
							// scroll the wheel upwards
							np.setValue(np.getValue() - 1);
							return true;
						// if it's down
						case KeyEvent.KEYCODE_DPAD_DOWN:
							// scroll the wheel downwards
							np.setValue(np.getValue() + 1);
							return true;
						default:
							return false;
					}
				}

				return false;
			}
		});

		// instantiate a new layout for the AlertDialog
		final FrameLayout parent = new FrameLayout(this);

		// set it up with the number picker
		parent.addView(np, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER));

		// add the newly create layout to the AlertDialog
		adb.setView(parent);

		// show the alertDialog
		adb.show();
	}


	private class NotifyTimeIsUp implements Runnable{

		@Override
		public void run() {

		// VIBRATE
			// get the vibrator
			Vibrator mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			mVibrator.vibrate(NOTIFICATION_TIME);


		// NOTIFICATION
			Intent intent = new Intent(MainActivity.this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			NotificationCompat.Builder notification =
					new NotificationCompat.Builder(MainActivity.this)
							.setSmallIcon(R.drawable.ic_notification_microchrono)
							.setContentTitle(getResources().getString(R.string.app_name))
							.setContentText(getString(R.string.notification_text))
							.setContentIntent(pendingIntent)
							.setAutoCancel(true);

			NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.notify(1, notification.build());


		// PLAY SOUND
			MediaPlayer player = MediaPlayer.create(MainActivity.this,
					R.raw.kitchen_timer_ringtone);
			player.setVolume(0.8f, 0.8f);
			player.start();

			try {
				Thread.sleep(NOTIFICATION_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			player.stop();
			player.reset();
			player.release();
		}
	}


	// the runnable used by the thread
	private class ChronoMinute implements Runnable {

		// store the seconds
		private int _seconds = (int)TIME_IN_SEC;

		// the message needed for the inter thread communication
		private Message _message;

		@Override
		public void run() {

			_wl.acquire();

			// while the thread is running
			while(IS_RUNNING && !_thread.isInterrupted()){

				// decrement the seconds by 1
				_seconds -= 1;

				// get a new message from the pool
				_message = Message.obtain();

				// debug only
//				Log.w("seconds : ", ""+ seconds);

				try {
					Thread.sleep(1000); // sleep for 1 seconds
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (_seconds > 0){
					// send the last value
					_message.arg1 = _seconds;
				}else{
					// sends -1 ( means error or end)
					_message.arg1 = -1;
				}

				//send the message
				_handler.sendMessage(_message);
			}

			// stop the thread
			_thread.interrupt();

			// release the wakelock
			_wl.release();
		}
	}



	// the handler (handles communication between threads)
	private static class mHandler extends Handler {

		private float _progress;
		private String _valueToDisplay;

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			// if the message = -1, stop the timer
			if (msg.arg1 == -1){

				// hide the TextView
				TEXT_TIME.get().setVisibility(View.INVISIBLE);

				// start the "finishPour" animation
				LIQUID_BUTTON.get().finishPour();

			}else{

				// calculate the progress and store it as a float (1f = 100%)
				_progress = msg.arg1 / TIME_IN_SEC;

				// change the progress of the liquidButton
				LIQUID_BUTTON.get().changeProgress(1 - _progress);

				// if the seconds count is > 60
				if (msg.arg1 > 60){

					// calculate if there is a minute change
					if(msg.arg1 % 60 == 0){

						// if there is, decrement the minute count
						COUNT_MINUTE -= 1;
					}

					// store the value
					_valueToDisplay = COUNT_MINUTE + " mn";
				}else{
					// if the seconds count is < 60, store the time in seconds
					_valueToDisplay = msg.arg1 + " s";
				}

				// if the timer is still running (not interrupted)
				if (IS_RUNNING){
					// set the main TextView with the value
					TEXT_TIME.get().setText(_valueToDisplay);
				}else{
					// otherwise, hide the TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);
				}
			}
		}
	}
}
