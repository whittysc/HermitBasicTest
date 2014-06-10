package com.example.hermitclubhousebasic;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

public class UpDownActivity extends ActionBarActivity {

	//Tag used for debug / log messages
	private static final String TAG = UpDownActivity.class.getSimpleName();
	
	// Media router members
	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;
	private CastDevice mSelectedDevice;
	
	// Connection / API Client / Communication Members
	private Cast.Listener mCastListener;
	private ConnectionFailedListener mConnectionFailedListener;
	private ConnectionCallbacks mConnectionCallbacks;
	private GoogleApiClient mApiClient;
	private ClientReceiverChannel mClientReceiverChannel;
	private String mSessionId;
	private boolean mApplicationStarted;
	private boolean mWaitingForReconnect;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_up_down);
		
		//Add the ability to get the text from the textbox
		final EditText messageToServer = (EditText) findViewById(R.id.messageToServer);
		
		//Add functionality for when the buttons are pressed
		//upButton
		Button upButton = (Button) findViewById(R.id.up_button);
		upButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v){
				notifyButtonPressed("up", messageToServer.getText().toString());
			}
		});
		
		//downButton
		Button downButton = (Button) findViewById(R.id.down_button);
		downButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v){
				notifyButtonPressed("down", messageToServer.getText().toString());
			}
		});
		
		// Configure the Cast device discovery
		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = new MediaRouteSelector.Builder()
			.addControlCategory(
					CastMediaControlIntent.categoryForCast(getResources()
							.getString(R.string.app_id))).build();
		mMediaRouterCallback = new MyMediaRouterCallback();
		Log.d(TAG, "Finished onCreate");
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		// Start media router discovery
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, 
				MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
	}
	
	@Override
	protected void onPause(){
		if (isFinishing()){
			// End media router discovery
			mMediaRouter.removeCallback(mMediaRouterCallback);	
		}
		super.onPause();
	}
	
	@Override
	public void onDestroy(){
		teardown();
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		Log.d(TAG, "Inside onCreateOptionsMenu");
		getMenuInflater().inflate(R.menu.main, menu);
		
		//Get the button
		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
				.getActionProvider(mediaRouteMenuItem);
		//Hook up the button to the selector for device discovery
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		return true;
	}
	
	/**
	 * Callback for MediaRouter events
	 */
	private class MyMediaRouterCallback extends MediaRouter.Callback {
		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo info){
			Log.d(TAG, "onRouteSelected: info="+info);
			Toast.makeText(UpDownActivity.this, "Selected route to: "+info.getName(), Toast.LENGTH_SHORT).show();
			
			//Handle the user route selection.
			mSelectedDevice = CastDevice.getFromBundle(info.getExtras());	
			launchServer();
		}
		
		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo info) {
			Log.d(TAG, "onRouteUnselected: info="+info);
			Toast.makeText(UpDownActivity.this, "Deselected route to: "+info.getName(), Toast.LENGTH_SHORT).show();
			teardown();
			mSelectedDevice = null;
		}
	}
	
	/**
	 * Start the receiver app
	 */
	private void launchServer(){
		try{
			mCastListener = new Cast.Listener(){
				@Override
				public void onApplicationDisconnected(int errorCode){
					Log.d(TAG, "Application has stopped");
					teardown();
				}
			};
			
			//Connect to Google Play Services to hook up to the Cast device
			mConnectionCallbacks = new ConnectionCallbacks();
			mConnectionFailedListener = new ConnectionFailedListener();
			Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
					.builder(mSelectedDevice, mCastListener);
			mApiClient = new GoogleApiClient.Builder(this)
				.addApi(Cast.API, apiOptionsBuilder.build())
				.addConnectionCallbacks(mConnectionCallbacks)
				.addOnConnectionFailedListener(mConnectionFailedListener)
				.build();
			mApiClient.connect();
		} catch (Exception e){
			Log.e(TAG, "Failed launchServer", e);
		}
	}
	
	/*
	 * Google Play Services callbacks
	 */
	private class ConnectionFailedListener implements
		GoogleApiClient.OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(ConnectionResult result){
			Log.e(TAG, "onConnectionFailed");
			teardown();
		}
	}
	
	private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
		@Override
		public void onConnected(Bundle connectionHint){
			Log.d(TAG, "onConnected");
			if (mApiClient == null){
				//We got disconnected while this runnable was
				//pending execution
				return;
			}
			
			try {
				if (mWaitingForReconnect){
					mWaitingForReconnect = false;
					
					/// Check if the receiver app is still running
					if ((connectionHint != null)
							&& connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)){
						Log.d(TAG, "App is no longer running");
						teardown();
					} else {
						//Reset the channel as the client receiver
						try {
							Cast.CastApi.setMessageReceivedCallbacks(
									mApiClient,
									mClientReceiverChannel.getNamespace(),
									mClientReceiverChannel);
						} catch (IOException e){
							Log.e(TAG, "Exception while creating channel", e);
						}
					}
					
				} else {
					// Launch the server app
					Cast.CastApi.launchApplication(mApiClient, getString(R.string.app_id), false)
						.setResultCallback(new MyResultCallback());
				}
				
			} catch (Exception e){
				Log.e(TAG, "Failed to launch application", e);
			}
		}
		
		@Override
		public void onConnectionSuspended(int cause) {
			Log.d(TAG, "onConnectionSuspended");
			mWaitingForReconnect = true;
		}
	}
	
	private class MyResultCallback implements ResultCallback<Cast.ApplicationConnectionResult> {
		@Override
		public void onResult(ApplicationConnectionResult result){
			Status status = result.getStatus();
			Log.d(TAG, "ApplicationConnectionResultCallback.onResult: statusCode="+status.getStatusCode());
			if (status.isSuccess()){
				ApplicationMetadata applicationMetaData = result.getApplicationMetadata();
				mSessionId = result.getSessionId();
				String applicationStatus = result.getApplicationStatus();
				boolean wasLaunched = result.getWasLaunched();
				Log.d(TAG, "application name: " + applicationMetaData.getName()
						+ ", status: " + applicationStatus
						+ ", sessionId: " + mSessionId
						+ ", wasLaunched: " + wasLaunched);
				mApplicationStarted = true;
				
				//Create the ClientReceiverChannel and set the callbacks with the API
				mClientReceiverChannel = new ClientReceiverChannel();
				try {
					Cast.CastApi.setMessageReceivedCallbacks(
							mApiClient,
							mClientReceiverChannel.getNamespace(),
							mClientReceiverChannel);
				} catch (IOException e) {
					Log.e(TAG, "Exception while creating channel", e);
				}
			} else {
				Log.e(TAG, "applciation could not launch");
				teardown();
			}
		}
	}
	
	/**
	 * Custom message channel
	 */
	class ClientReceiverChannel implements MessageReceivedCallback {

		@Override
		public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
			Log.d(TAG, "onMessageReceived: namespace="+namespace+" message="+message);
		}
		
		/*
		 * @return custom namespace
		 */
		public String getNamespace() {
			return getString(R.string.namespace);
		}
		
	}
	
	private void teardown(){
		//Destroy all the connection objects
		Log.d(TAG, "Tearing down the connection objects!");
		if (mApiClient != null){
			if (mApplicationStarted){
				if (mApiClient.isConnected()){
					try {
						Cast.CastApi.stopApplication(mApiClient, mSessionId);
						if (mClientReceiverChannel != null){
							Cast.CastApi.removeMessageReceivedCallbacks(
									mApiClient, 
									mClientReceiverChannel.getNamespace());
							mClientReceiverChannel = null;
						}
					} catch (IOException e){
						Log.e(TAG, "Exception while removing receiver channel", e);
					}
					mApiClient.disconnect();
				}
				mApplicationStarted = false;
			}
			mApiClient = null;
		}
		mSelectedDevice = null;
	}
	
	/**
	 * Notify the server that you've pressed a button and whatever text was
	 * in the text box at the time.
	 */
	private void notifyButtonPressed(String buttonPressed, String textMessage){
		if (mApiClient != null && mClientReceiverChannel != null){
			try {
				JSONObject payload = new JSONObject();
				payload.put(getString(R.string.BUTTON_PRESSED), buttonPressed);
				payload.put(getString(R.string.TEXT_FIELD), textMessage);
				sendJSONToServer(payload);
			} catch (JSONException j){
				Log.e(TAG, "Exception while converting to JSON", j);
			}
		} else {
			Toast.makeText(UpDownActivity.this, "Connect to Chromecast first, dipshit!", Toast.LENGTH_SHORT).show();
		}
	}
	
	/*
	 * Send a JSON Object to the server
	 */
	private void sendJSONToServer(JSONObject payload){
		//Piggyback on sending strings to the server and trust that they'll process it correctly
		sendStringToServer(payload.toString());
	}
	
	/*
	 * Send a string to the server
	 */
	private void sendStringToServer(String message){
		Log.d(TAG, "Sending message to server...");
		try {
			Cast.CastApi.sendMessage(mApiClient,
					mClientReceiverChannel.getNamespace(), message)
					.setResultCallback(new SendMessageResultCallback(message));
		} catch (Exception e){
			Log.e(TAG, "Exception while sending message", e);
		}
	}
	
	/*
	 * Callback class for reacting after a message is sent to the server
	 */
	class SendMessageResultCallback implements ResultCallback<Status> {
		String mMessage;
		
		SendMessageResultCallback(String message){
			mMessage = message;
		}
		
		@Override
		public void onResult(Status result){
			if (!result.isSuccess()) {
				Log.e(TAG, "Message \""+mMessage+"\" failed to send");
			} else {
				Log.d(TAG, "Message \""+mMessage+"\" successfully sent!");
			}
		}
	}
}
