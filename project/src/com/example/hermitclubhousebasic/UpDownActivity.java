package com.example.hermitclubhousebasic;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class UpDownActivity extends ActionBarActivity {

	//Tag used for debug / log messages
	private static final String TAG = UpDownActivity.class.getSimpleName();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_up_down);
		
		//Add functionality for when the buttons are pressed
		//upButton
		Button upButton = (Button) findViewById(R.id.up_button);
		upButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v){
				sendMessage("up");
			}
		});
		
		//downButton
		Button downButton = (Button) findViewById(R.id.down_button);
		downButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v){
				sendMessage("down");
			}
		});
		Log.d(TAG, "Finished onCreate");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		Log.d(TAG, "Inside onCreateOptionsMenu");
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
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
