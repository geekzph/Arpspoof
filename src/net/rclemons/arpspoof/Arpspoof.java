/*    Arpspoof.java is the starting activity
    Copyright (C) 2011 Robbie Clemons <robclemons@gmail.com>

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA. */


package net.rclemons.arpspoof;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

public class Arpspoof extends Activity {
	/** Called when the activity is first created. */
	public static final int BUFFER_SIZE = 4096;
	private static final int WAIT = 1000;
	private static final int UNROOTED_ALERT = 0;
	private static final int LICENSE_ALERT = 1;
	private static final String TAG = "Arpspoof.main";
	private static final String FILENAME = "arpspoof";
	private static final String BINPATH = "/data/local/bin/";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		File localBin = getFileStreamPath(FILENAME);
		if(localBin.exists() == false)
			extractBinary();			

		/*Implements the Begin Spoofing button*/
		final Button startButton = (Button) findViewById(R.id.start);
		startButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				WifiManager wManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
				WifiInfo wInfo = wManager.getConnectionInfo();

				//Check to see if we're connected to wifi
				if( wInfo.getIpAddress() != 0) {
					final EditText gateway = (EditText) findViewById(R.id.gateway);
					String ipAddress = gateway.getText().toString();
					//If nothing was entered for the ip address use the gateway
					if(ipAddress.trim().equals(""))
						ipAddress = Formatter.formatIpAddress(wManager.getDhcpInfo().gateway);

					Intent intent = new Intent(v.getContext(), SpoofingActivity.class);
					//Add data necessary for running the program
					Bundle mBundle = new Bundle();
					mBundle.putString("gateway", ipAddress);
					mBundle.putString("localBin", getFileStreamPath(FILENAME).toString());
					intent.putExtras(mBundle);
					if(RootAccess.isGranted())
						startActivity(intent);
					else
						showDialog(UNROOTED_ALERT);
				}
				else {
					CharSequence text = "Must be connected to wireless network.";
					Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
				}
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.copy_exe:
			installBinary();
			return true;
		case R.id.viewLicense:
			showDialog(LICENSE_ALERT);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch(id) {
		case UNROOTED_ALERT:
			builder.setMessage(R.string.rootError);
			builder.setCancelable(false);
			builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					finish();
				}
			});
			dialog = builder.create();
			break;
		case LICENSE_ALERT:
			final InputStream licenseIS = getResources().openRawResource(R.raw.copying);
			final BufferedReader licenseBR = new BufferedReader(new InputStreamReader(licenseIS));
			StringBuilder license = new StringBuilder();
			try {
				String line = licenseBR.readLine();
				do {
					license.append(line + "\n");
					line = licenseBR.readLine();
				}while(line != null);

				licenseBR.close();
			} catch (IOException e) {
				Log.e(TAG, "error reading license file", e);
				try {
					licenseBR.close();
				} catch (IOException e1) {
					// swallow error
				}
			}
			builder.setTitle("COPYING");
			builder.setMessage(license);
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	private void extractBinary() {
		/*extracts the binary from the apk and makes it executable.  
		 * If any step fails and the function continues to run everything should be cleaned up*/
		if(RootAccess.isGranted()) {
			final InputStream arpBin = getResources().openRawResource(R.raw.arpspoof);
			FileOutputStream out = null;
			boolean success = true;
			final byte[] buff = new byte[BUFFER_SIZE];
			try {
				out = openFileOutput(FILENAME, Context.MODE_PRIVATE);
				while(arpBin.read(buff) > 0)
					out.write(buff);
			} catch (FileNotFoundException e) {
				Log.e(TAG, FILENAME + "wasn't found", e);
				success = false;
			} catch (IOException e) {
				Log.e(TAG, "couldn't extract executable", e);
				success = false;
			} finally {
				try {
					out.close();
				} catch (IOException e) {
					// swallow error
				}
			}
			try {
				ExecuteCommand ec = new ExecuteCommand("chmod +x " + getFileStreamPath(FILENAME));
				ec.start();
				ec.join();
			} catch (IOException e) {
				Log.e(TAG, "error running chmod on local file", e);
				success = false;
			} catch (InterruptedException e) {
				Log.i(TAG, "thread running chmod was interrupted");
				success = false;
			} finally {
				if(!success)
					getFileStreamPath(FILENAME).delete();
			}
		} else
			showDialog(UNROOTED_ALERT);

	}

	private void installBinary() {
		/*copies local binary to a directory that is easier to use from a terminal*/
		String command = new String();
		command += "mkdir " + BINPATH + ';';
		command += "cp " + getFileStreamPath(FILENAME) + " " + BINPATH + FILENAME + ';';
		command += "chmod +x " + BINPATH + FILENAME;
		if(RootAccess.isGranted()) {
			try {
				ExecuteCommand ec = new ExecuteCommand(command);
				ec.start();
				ec.join(WAIT);
			} catch (IOException e) {
				Log.e(TAG, "error running install commands", e);
			} catch (InterruptedException e) {
				Log.i(TAG, "thread running install commands was interrupted");
			}
		} else
			showDialog(UNROOTED_ALERT);
	}   
}