package com.dan.eo1;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    public int per_page = 500; //500 max
    public int interval = 5;
    public int tempid = 0;
    private int currentPosition = 0;
    private Handler handler = new Handler();
    private ImageView imageView;
    private VideoView videoView;
    private String apikey = "";
    private String userid = "";
    private int displayOption = 0;
    private int startQuietHour = -1;
    private int endQuietHour = -1;
    private List<FlickrPhoto> flickrPhotos;
    private boolean isInQuietHours = false;
    private String customTag = "";
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private float lastLightLevel;
    private int currentScreenBrightness;
    private boolean slideshowpaused = false;
    private ProgressBar progress;
    boolean screenon = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File cacheDir = new File(getCacheDir(), "picasso-cache");
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) {
                file.delete();
            }
        }

        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
        userid = settings.getString("userid", "");
        apikey = settings.getString("apikey", "");
        displayOption = settings.getInt("displayOption", 0);
        startQuietHour = settings.getInt("startQuietHour", -1);
        endQuietHour = settings.getInt("endQuietHour", -1);
        customTag = settings.getString("customTag", "");
        interval = settings.getInt("interval", 5);

        if (displayOption != 2) customTag = "";

        if (userid.isEmpty() || apikey.isEmpty()) {
            showSetupDialog();
        }

        imageView = findViewById(R.id.imageView);
        videoView = findViewById(R.id.videoView);
        progress = findViewById(R.id.progressBar);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (Math.abs(event.values[0] - lastLightLevel) >= 10.0f) {
                    adjustScreenBrightness(event.values[0]);
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        Log.e("hi", "registered light sensor");
        mSensorManager.registerListener(listener, mLightSensor, SensorManager.SENSOR_DELAY_UI);

        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("MSG_RECEIVED"));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!userid.isEmpty() && !apikey.isEmpty()) {
            loadImagesFromFlickr();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_C) {
            showSetupDialog();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_A) {
            Toast.makeText(MainActivity.this, "sensor = " + lastLightLevel, Toast.LENGTH_SHORT).show();
        }

        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            progress.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.INVISIBLE);
            videoView.setVisibility(View.INVISIBLE);
            slideshowpaused = false;
            showNextImage();
        }

        if (keyCode == 132) {
            //top button pushed
            WindowManager.LayoutParams params = getWindow().getAttributes();
            if (screenon) {
                params.screenBrightness = 0;
                screenon = false;
            } else {
                params.screenBrightness = 10;
                screenon = true;
            }
            getWindow().setAttributes(params);
        }

        return super.onKeyDown(keyCode, event);
    }

    private void showSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customLayout = getLayoutInflater().inflate(R.layout.options, null);
        builder.setView(customLayout);

        final EditText userIdEditText = customLayout.findViewById(R.id.editTextUserId);
        final EditText apiKeyEditText = customLayout.findViewById(R.id.editTextApiKey);
        final Spinner startHourSpinner = customLayout.findViewById(R.id.startHourSpinner);
        final Spinner endHourSpinner = customLayout.findViewById(R.id.endHourSpinner);
        final Button btnLoadConfig = customLayout.findViewById(R.id.btnLoadConfig);
        final EditText editTextCustomTag = customLayout.findViewById(R.id.editTextCustomTag);
        final EditText editTextInterval = customLayout.findViewById(R.id.editTextInterval);

        userIdEditText.setText(userid);
        apiKeyEditText.setText(apikey);
        editTextCustomTag.setText(customTag);
        editTextInterval.setText(String.valueOf(interval));

        RadioGroup optionsRadioGroup = customLayout.findViewById(R.id.optionsRadioGroup);
        optionsRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioOption3) {
                    editTextCustomTag.setVisibility(View.VISIBLE);
                    editTextCustomTag.setText(customTag);
                } else {
                    editTextCustomTag.setVisibility(View.GONE);
                    customTag = ""; // Clear the customTag when not applicable
                }
            }
        });

        editTextCustomTag.setVisibility(View.GONE);
        if (displayOption == 0) ((RadioButton) customLayout.findViewById(R.id.radioOption1)).setChecked(true);
        if (displayOption == 1) ((RadioButton) customLayout.findViewById(R.id.radioOption2)).setChecked(true);
        if (displayOption == 2) {
            ((RadioButton) customLayout.findViewById(R.id.radioOption3)).setChecked(true);
            editTextCustomTag.setVisibility(View.VISIBLE);
            editTextCustomTag.setText(customTag);
        }

        // Set up the Spinners for start and end hour
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        ArrayAdapter<String> hourAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        startHourSpinner.setAdapter(hourAdapter);
        if (startQuietHour != -1) startHourSpinner.setSelection(startQuietHour);
        endHourSpinner.setAdapter(hourAdapter);
        if (endQuietHour != -1) endHourSpinner.setSelection(endQuietHour);

        btnLoadConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, "config.txt");
                if (!file.exists()) {
                    Toast.makeText(MainActivity.this, "Can't find config.txt", Toast.LENGTH_SHORT).show();
                } else {
                    StringBuilder sb = new StringBuilder();
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    userIdEditText.setText(sb.toString().split("\n")[0]);
                    apiKeyEditText.setText(sb.toString().split("\n")[1]);
                }
            }
        });

        builder.setTitle("Setup")
                .setCancelable(false)
                .setView(customLayout)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        userid = userIdEditText.getText().toString().trim();
                        apikey = apiKeyEditText.getText().toString().trim();
                        displayOption = getSelectedOptionIndex(optionsRadioGroup);
                        startQuietHour = Integer.parseInt(startHourSpinner.getSelectedItem().toString());
                        endQuietHour = Integer.parseInt(endHourSpinner.getSelectedItem().toString());
                        customTag = editTextCustomTag.getText().toString().trim();
                        interval = Integer.parseInt(editTextInterval.getText().toString().trim());

                        if (!userid.isEmpty() && !apikey.isEmpty()) {
                            SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString("userid", userid);
                            editor.putString("apikey", apikey);
                            editor.putInt("displayOption", displayOption);
                            editor.putInt("startQuietHour", startQuietHour);
                            editor.putInt("endQuietHour", endQuietHour);
                            editor.putString("customTag", customTag);
                            editor.putInt("interval", interval);
                            editor.apply();

                            Toast.makeText(MainActivity.this, "Saved!  Hit 'C' to come back here later.", Toast.LENGTH_SHORT).show();

                            loadImagesFromFlickr();
                        } else {
                            Toast.makeText(MainActivity.this, "Please enter User ID and API Key", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.show();
    }


    private void startSlideshow() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Calendar calendar = Calendar.getInstance();
                int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
                int normalizedStart = (startQuietHour + 24) % 24;
                int normalizedEnd = (endQuietHour + 24) % 24;
                if ((currentHour >= normalizedStart && currentHour < normalizedEnd) ||
                   (normalizedStart > normalizedEnd && (currentHour >= normalizedStart || currentHour < normalizedEnd))) {
                    if (!isInQuietHours) {
                        //entering quiet, turn off screen
                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        params.screenBrightness = 0;
                        getWindow().setAttributes(params);
                        videoView.setVisibility(View.GONE);
                        imageView.setVisibility(View.GONE);

                        isInQuietHours = true;
                    }
                } else {
                    if (isInQuietHours) {
                        //exiting quiet, turn on screen
                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        params.screenBrightness = 1f;
                        getWindow().setAttributes(params);

                        isInQuietHours = false;
                    }
                    showNextImage();
                }
                handler.postDelayed(this, 60000 * interval);
            }
        }, 60000 * interval);

        showNextImage();
    }

    private void showNextImage() {
        if (flickrPhotos != null && !flickrPhotos.isEmpty() && slideshowpaused==false) {
            if (currentPosition >= flickrPhotos.size()) {
                loadImagesFromFlickr(); return;
            }

            try {
                String mediatype = flickrPhotos.get(currentPosition).getMedia();

                if (!mediatype.equals("video")) {
                    videoView.setVisibility(View.INVISIBLE);
                    imageView.setVisibility(View.VISIBLE);

                    if (flickrPhotos.get(currentPosition).getUrlO() == null) {
                        currentPosition++;
                        showNextImage();
                        return;
                    } else {
                        String imageUrl = flickrPhotos.get(currentPosition).getUrlO().toString().replace("_o", "_k");
                        Picasso.get().load(imageUrl).fit().centerInside().into(imageView); //Picasso.get().load(imageUrl).fit().centerCrop().into(imageView);
                        progress.setVisibility(View.INVISIBLE);
                    }

                } else {
                    imageView.setVisibility(View.INVISIBLE);

                    MediaController mediaController = new MediaController(this);
                    mediaController.setAnchorView(videoView);
                    mediaController.setVisibility(View.INVISIBLE);
                    videoView.setMediaController(mediaController);

                    String url = "";
                    if (flickrPhotos.get(currentPosition).getOriginalSecret() == null)
                        url = "https://www.flickr.com/photos/" + userid + "/" + flickrPhotos.get(currentPosition).getId() + "/play/720p/" + flickrPhotos.get(currentPosition).getSecret();
                    else
                        url = "https://www.flickr.com/photos/" + userid + "/" + flickrPhotos.get(currentPosition).getId() + "/play/orig/" + flickrPhotos.get(currentPosition).getOriginalSecret();

                    new DownloadVideoTask().execute(url);
                }
                currentPosition++;
            } catch (Exception ex) {
                progress.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "shownextimage err > " + ex.getMessage().toString(), Toast.LENGTH_SHORT).show();
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showNextImage();
                    }
                }, 10000);
            }
        }
    }

    private void loadImagesFromFlickr() {
        progress.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);

        if (isNetworkAvailable()) {
            Toast.makeText(MainActivity.this, "IP = " + getIPAddress(), Toast.LENGTH_LONG).show();

            Intent serviceIntent = new Intent(this, MyMessageService.class);
            startService(serviceIntent);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.flickr.com/services/rest/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(new okhttp3.OkHttpClient())
                    .build();

            FlickrApiService apiService = retrofit.create(FlickrApiService.class);
            Call<FlickrApiResponse> call;

            if (displayOption == 0)
                call = apiService.getPublicPhotos(apikey, userid, per_page, "media,url_o");
            else
                call = apiService.searchPhotos(apikey, "", per_page, (customTag.equals("") ? "electricobjectslives": customTag), "media,url_o");
            call.enqueue(new Callback<FlickrApiResponse>() {
                @Override
                public void onResponse(Call<FlickrApiResponse> call, Response<FlickrApiResponse> response) {
                    if (response.isSuccessful()) {
                        FlickrApiResponse apiResponse = response.body();
                        if (apiResponse != null) {
                            try {
                                flickrPhotos = apiResponse.getPhotos().getPhotoList();
                                if (flickrPhotos.size() == 0) {
                                    customTag = ""; loadImagesFromFlickr(); return;
                                }
                                currentPosition = 0;
                                Collections.shuffle(flickrPhotos);
                                startSlideshow();
                            } catch (Exception ex) {
                                Toast.makeText(MainActivity.this, "Flickr failure, check API key", Toast.LENGTH_SHORT).show();
                                showSetupDialog();
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Call<FlickrApiResponse> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Flickr failure", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadImagesFromFlickr(); // Retry loading images after delay
                }
            }, 10000);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private View getLabel(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        return textView;
    }

    private void adjustScreenBrightness(float lightValue){
        if (!isInQuietHours) {
            // Determine the desired brightness range
            float maxBrightness = 1.0f; // Maximum brightness value (0 to 1)
            float minBrightness = 0.0f; // Minimum brightness value (0 to 1)

            // Map the light sensor value (0 to 25) to the desired brightness range (0 to 1)
            float brightness = (lightValue / 35.0f) * (maxBrightness - minBrightness) + minBrightness;

            // Make sure brightness is within the valid range
            brightness = Math.min(Math.max(brightness, minBrightness), maxBrightness);

            // Apply the brightness setting to the screen
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = brightness;
            getWindow().setAttributes(layoutParams);
        }
        lastLightLevel = lightValue;
    }

    private int getSelectedOptionIndex(RadioGroup radioGroup) {
        int checkedRadioButtonId = radioGroup.getCheckedRadioButtonId();
        View checkedRadioButton = radioGroup.findViewById(checkedRadioButtonId);
        return radioGroup.indexOfChild(checkedRadioButton);
    }

    private String getIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getAddress().length == 4) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class DownloadVideoTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String videoUrl = params[0];
            try {
                // Download the video from the URL
                URL url = new URL(videoUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                File tempFile = new File(getCacheDir(), "temp" + tempid + ".mp4");
                FileOutputStream outputStream = new FileOutputStream(tempFile);

                tempid++;
                if (tempid == 5) tempid=0;

                InputStream inputStream = connection.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                return tempFile.getPath();
            } catch (IOException e) {
                return "ERR: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String file) {
            if (!file.startsWith("ERR")) {
                videoView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.INVISIBLE);
                videoView.setVideoPath(file);

                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.setLooping(true);
                        videoView.start();
                    }
                });
                videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        progress.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "mediaplayer ERR> ", Toast.LENGTH_SHORT).show();
                        showNextImage();
                        return true;
                    }
                });
            } else {
                progress.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "ERR> " + file, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals("MSG_RECEIVED")) {
                    String type = intent.getStringExtra("type");

                    progress.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.INVISIBLE);
                    videoView.setVisibility(View.INVISIBLE);

                    slideshowpaused = true;

                    if (isInQuietHours) {
                        isInQuietHours = false;
                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        params.screenBrightness = 1f;
                        getWindow().setAttributes(params);
                    }

                    if (type.equals("image") || type.equals("video")) {
                        Retrofit retrofit = new Retrofit.Builder()
                                    .baseUrl("https://api.flickr.com/services/rest/")
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .client(new okhttp3.OkHttpClient())
                                    .build();
                        FlickrApiService apiService = retrofit.create(FlickrApiService.class);
                            Call<FlickrGetSizesResponse> call = apiService.getSizes(apikey, intent.getStringExtra("imageid"));
                            call.enqueue(new Callback<FlickrGetSizesResponse>() {
                                @Override
                                public void onResponse(Call<FlickrGetSizesResponse> call, Response<FlickrGetSizesResponse> response) {
                                    if (response.isSuccessful()) {
                                        FlickrGetSizesResponse flickrSizesResponse = response.body();
                                        if (flickrSizesResponse != null) {
                                            List<FlickrGetSizesResponse.FlickrImageSize> imageSizes = flickrSizesResponse.getSizes().getImageSizes();
                                            for (FlickrGetSizesResponse.FlickrImageSize size : imageSizes) {
                                                String label = size.getLabel();
                                                String imageUrl = size.getSourceUrl();
                                                if (type.equals("image") && label.equals("Original")) {
                                                    loadImage(imageUrl);
                                                    return;
                                                }
                                                if (type.equals("video") && label.equals("720p")) {
                                                    loadVideo(imageUrl);
                                                    return;
                                                }
                                            }
                                            //couldn't find 720p, get 360p
                                            for (FlickrGetSizesResponse.FlickrImageSize size : imageSizes) {
                                                String label = size.getLabel();
                                                if (type.equals("video") && label.equals("360p")) {
                                                    loadVideo(size.getSourceUrl());
                                                    return;
                                                }
                                            }
                                            //couldn't find original image, find Large or Large 1600
                                            if (type.equals("image")) {
                                                for (int i= imageSizes.size() - 1; i >= 0; i--) {
                                                    String label = imageSizes.get(i).getLabel();
                                                    if (label.equals("Large") || label.equals("Large 1600")) {
                                                        loadImage(imageSizes.get(i).getSourceUrl());
                                                        return;
                                                    }
                                                }
                                            }
                                            Toast.makeText(MainActivity.this, "No source found.", Toast.LENGTH_SHORT).show();
                                            slideshowpaused = false;
                                            showNextImage();
                                        }
                                    }
                                }

                                @Override
                                public void onFailure(Call<FlickrGetSizesResponse> call, Throwable t) {
                                    Toast.makeText(MainActivity.this, "Flickr failure", Toast.LENGTH_SHORT).show();
                                    slideshowpaused = false;
                                    showNextImage();
                                }
                            });
                    }

                    if (type.equals("resume")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        slideshowpaused = false;
                        showNextImage();
                    }

                    if (type.equals("tag")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        slideshowpaused = false;

                        customTag = intent.getStringExtra("tag");
                        if (customTag.equals("")) {
                            SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                            displayOption = settings.getInt("displayOption", 0);
                            customTag = settings.getString("customTag", "");
                        } else {
                            displayOption = 2;
                        }
                        loadImagesFromFlickr();
                    }

                }
            }
        }
    };

    public void loadVideo(String url) {
        MediaController mediaController = new MediaController(MainActivity.this);
        mediaController.setAnchorView(videoView);
        mediaController.setVisibility(View.INVISIBLE);

        videoView.setMediaController(mediaController);

        new DownloadVideoTask().execute(url);
    }

    public void loadImage(String url) {
        videoView.setVisibility(View.INVISIBLE);
        Picasso.get().load(url).fit().centerInside().into(imageView);
        progress.setVisibility(View.INVISIBLE);
        imageView.setVisibility(View.VISIBLE);
    }

}