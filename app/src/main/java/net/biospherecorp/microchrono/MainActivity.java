package net.biospherecorp.microchrono;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	private static WeakReference<LiquidButton> LIQUID_BUTTON;
	private FloatingActionButton startButton;
	private FloatingActionButton settingButton;
	private static WeakReference<TextView> TEXT_TIME;

	private static final int DEFAULT_TIME_MN = 3;
	private static int VALUE_IN_ARRAY_TIME = 2;
	private static int COUNT_MINUTE = DEFAULT_TIME_MN;
	private static float TIME_IN_SEC;

	private Thread _thread;
	private Handler _handler;
	private Snackbar _snackBar;

	private static boolean IS_RUNNING = false;
	private static boolean IS_LIQUID_POURING = false;

	private static int COLOR_PRIMARY, COLOR_SECONDARY;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		COLOR_PRIMARY = getResources().getColor(R.color.colorPrimary);
		COLOR_SECONDARY = getResources().getColor(R.color.colorAccent);

		startButton = (FloatingActionButton) findViewById(R.id.start_button);
		settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
		settingButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));

		final TextView pressStart = (TextView) findViewById(R.id.pressStartButton);
		pressStart.setText("Press Start");

		TEXT_TIME = new WeakReference<>((TextView) findViewById(R.id.text_time));
		TEXT_TIME.get().setText(DEFAULT_TIME_MN + " mn");

		LIQUID_BUTTON = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));

		_handler = new mHandler();

		settingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				showNumberPicker();
			}
		});

		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				if (!IS_RUNNING && !IS_LIQUID_POURING){

					IS_RUNNING = true;
					TIME_IN_SEC = COUNT_MINUTE * 60f;

					startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_SECONDARY));
					startButton.setImageResource(android.R.drawable.ic_delete);

					settingButton.setEnabled(false);

					pressStart.setText("");
					TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

					LIQUID_BUTTON.get().setFillAfter(true);
					LIQUID_BUTTON.get().startPour();
					IS_LIQUID_POURING = true;

					_thread = new Thread(new ChronoMinute());
					_thread.start();

				}else{

					IS_RUNNING = false;

					LIQUID_BUTTON.get().finishPour();

					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					_snackBar = Snackbar.make(view, "Please Wait...", Snackbar.LENGTH_INDEFINITE);
					_snackBar.show();

					TEXT_TIME.get().setText("");
				}
			}
		});

		LIQUID_BUTTON.get().setPourFinishListener(new LiquidButton.PourFinishListener() {
			@Override
			public void onPourFinish() {
				IS_LIQUID_POURING = false;

				startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
				startButton.setImageResource(android.R.drawable.ic_media_play);

				settingButton.setEnabled(true);

				_snackBar.dismiss();
				pressStart.setText("Press Start");
			}

			@Override
			public void onProgressUpdate(float progress) {}
		});
	}


	private void showNumberPicker(){

		final String[] TIME_ARRAY = new String[99];

		// fill up the array used by the settings number picker
		for (int i = 0; i < 99; i++){
			TIME_ARRAY[i] = String.valueOf(i + 1);
		}

		final AlertDialog.Builder adb = new AlertDialog.Builder(this);

		adb.setTitle("Set the time");
//		adb.setMessage("Some Message to display");

		final NumberPicker np = new NumberPicker(this);

		// to make sure the soft keyboard doesn't pop up
		np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

		// values to use
		np.setDisplayedValues(TIME_ARRAY);

		// set the min index in the Wheel
		np.setMinValue(0);

		// set the max index in the Wheel
		np.setMaxValue(TIME_ARRAY.length -1);

		// some default value position from the value set of values to display
		np.setValue(VALUE_IN_ARRAY_TIME);

		// does the wheel wrap around
		np.setWrapSelectorWheel(true);

		adb.setPositiveButton("Set", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				VALUE_IN_ARRAY_TIME = np.getValue();

				COUNT_MINUTE = Integer.valueOf(TIME_ARRAY[VALUE_IN_ARRAY_TIME]);
				TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

				dialogInterface.dismiss();
			}
		});

		adb.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				dialogInterface.dismiss();
			}
		});

		final FrameLayout parent = new FrameLayout(this);

		parent.addView(np, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER));

		adb.setView(parent);
		adb.show();
	}




	private class ChronoMinute implements Runnable {

		float lastValue = TIME_IN_SEC;

		@Override
		public void run() {

			while(IS_RUNNING && !_thread.isInterrupted()){

				lastValue -= 1;

				Log.w("lastValue : ", ""+lastValue);

				try {
					Thread.sleep(1000); // sleep for 1 seconds
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (lastValue > 0){

					Message message = Message.obtain();
					message.arg1 = (int)lastValue;
					_handler.sendMessage(message);
				}else{

					Message message = Message.obtain();
					message.arg1 = -1;
					_handler.sendMessage(message);
				}
			}

			_thread.interrupt();
		}
	}

	private static class mHandler extends Handler {

		String updateValue;

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			if (msg.arg1 == -1){

				IS_RUNNING = false;
				TEXT_TIME.get().setText("");
				LIQUID_BUTTON.get().finishPour();

			}else{

				float progress = msg.arg1 / TIME_IN_SEC;
				LIQUID_BUTTON.get().changeProgress(1 - progress);

				if (msg.arg1 > 60){

					if(msg.arg1 % 60 == 0){
						COUNT_MINUTE -= 1;
					}

					updateValue = COUNT_MINUTE + " mn";
				}else{

					updateValue = msg.arg1 + " s";
				}

				if (IS_RUNNING){
					TEXT_TIME.get().setText(String.valueOf(updateValue));
				}else{
					TEXT_TIME.get().setText("");
				}
			}
		}
	}
}
