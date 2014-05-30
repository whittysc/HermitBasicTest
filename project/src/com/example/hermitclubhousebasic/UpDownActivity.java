package com.example.hermitclubhousebasic;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class UpDownActivity extends Activity {

	//Tag used for debug / log messages
	private static final String TAG = UpDownActivity.class.getSimpleName();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_up_down);
		
		//Add functionality for when the buttons are pressed
		Button upButton = (Button) findViewById(R.id.up_button);
		upButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v){
				sendMessage("up");
			}
		});
	}
	
	/**
	 * Send a message to the receiver.
	 * Note: We'll proxy this for now by just toasting to the screen
	 */
	private void sendMessage(String message){
		Log.d(TAG, "Sending message: "+message);
		Toast.makeText(UpDownActivity.this, message, Toast.LENGTH_SHORT).show();
	}
}
