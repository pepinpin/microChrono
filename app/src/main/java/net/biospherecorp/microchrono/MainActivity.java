package net.biospherecorp.microchrono;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	private static WeakReference<LiquidButton> liquidButton;
	private FloatingActionButton startButton;
	private FloatingActionButton settingButton;
	private static WeakReference<TextView> textTime;

	private static final int DEFAULT_TIME_MN = 3;
	private static int COUNT_MINUTE;
	private static float DEFAULT_TIME_SEC = DEFAULT_TIME_MN * 60f;

	private Thread thread;
	private Handler handler;

	private static boolean isRunning = false;
	private static boolean isLiquidPouring = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		startButton = (FloatingActionButton) findViewById(R.id.start_button);
		settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		final TextView pressStart = (TextView) findViewById(R.id.pressStartButton);
		pressStart.setText("Press Start");

		textTime = new WeakReference<>((TextView) findViewById(R.id.text_time));
		textTime.get().setText(DEFAULT_TIME_MN + " mn");

		liquidButton = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));

		handler = new mHandler();

		settingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(MainActivity.this, "Show Settings", Toast.LENGTH_SHORT).show();
			}
		});

		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				if (!isRunning && !isLiquidPouring){

					isRunning = true;
					COUNT_MINUTE = DEFAULT_TIME_MN;

					pressStart.setText("");
					textTime.get().setText(DEFAULT_TIME_MN + " mn");

					liquidButton.get().setFillAfter(true);
					liquidButton.get().startPour();
					isLiquidPouring = true;

					thread = new Thread(new ChronoMinute());
					thread.start();

				}else{

					isRunning = false;

					liquidButton.get().finishPour();

					if (!thread.isInterrupted()){
						thread.interrupt();
					}

					pressStart.setText("Please wait...");
					textTime.get().setText("");
				}
			}
		});

		liquidButton.get().setPourFinishListener(new LiquidButton.PourFinishListener() {
			@Override
			public void onPourFinish() {
				isLiquidPouring = false;

				pressStart.setText("Press Start");
			}

			@Override
			public void onProgressUpdate(float progress) {}
		});
	}




	private class ChronoMinute implements Runnable {

		float lastValue = DEFAULT_TIME_SEC;

		@Override
		public void run() {

			while(isRunning && !thread.isInterrupted()){

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
					handler.sendMessage(message);
				}else{

					Message message = Message.obtain();
					message.arg1 = -1;
					handler.sendMessage(message);
				}
			}

			thread.interrupt();
		}
	}

	private static class mHandler extends Handler {

		String updateValue;

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			if (msg.arg1 == -1){

				isRunning = false;
				textTime.get().setText("");
				liquidButton.get().finishPour();

			}else{

				float progress = msg.arg1 / DEFAULT_TIME_SEC;
				liquidButton.get().changeProgress(1 - progress);

				if (msg.arg1 > 60){

					if(msg.arg1 % 60 == 0){
						COUNT_MINUTE -= 1;
					}

					updateValue = COUNT_MINUTE + " mn";
				}else{

					updateValue = msg.arg1 + " s";
				}

				if (isRunning){
					textTime.get().setText(String.valueOf(updateValue));
				}else{
					textTime.get().setText("");
				}
			}
		}
	}
}
