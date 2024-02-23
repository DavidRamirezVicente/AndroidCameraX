package com.example.androidcamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.video.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoActivity extends AppCompatActivity {
    private ExecutorService service;
    private Recording recording = null;
    private ImageCapture imageCapture = null;
    private VideoCapture<Recorder> videoCapture = null;
    private ImageButton capture, toggleFlash, flipCamera, photo;
    private PreviewView previewView;
    private ImageView minatura;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private Uri lastPhotoUri;



    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted && ActivityCompat.checkSelfPermission(VideoActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera(cameraFacing);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_layout);

        service = Executors.newSingleThreadExecutor();

        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.flash);
        flipCamera = findViewById(R.id.rotate);
        previewView = findViewById(R.id.preview);
        photo = findViewById(R.id.photo);
        minatura = findViewById(R.id.imageView2);
        minatura.setVisibility(View.GONE);
        capture.setOnClickListener(view -> checkAndCaptureVideo());
        photo.setOnClickListener(view -> capturePhoto());
        minatura.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (minatura.getTag() != null) {
                    Uri imageUri = (Uri) minatura.getTag();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(imageUri, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } else {
                    Toast.makeText(VideoActivity.this, "No hay imagen disponible", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (hasCameraPermission()) {
            startCamera(cameraFacing);
        } else {
            requestCameraPermission();
        }

        flipCamera.setOnClickListener(view -> {

            cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera(cameraFacing);
        });
    }

    private void checkAndCaptureVideo() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }

        if (!hasAudioPermission()) {
            requestAudioPermission();
            return;
        }

        if (!hasWriteExternalStoragePermission() && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestWriteExternalStoragePermission();
            return;
        }

        captureVideo();
    }

    private boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWriteExternalStoragePermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void requestAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void requestWriteExternalStoragePermission() {
        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void captureVideo() {
        capture.setImageResource(R.drawable.baseline_stop_circle_24);
        Recording recording1 = recording;
        if (recording1 != null) {
            recording1.stop();
            recording = null;
            return;
        }
        String name = new SimpleDateFormat("yyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Recorder");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI).
                setContentValues(contentValues).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recording = videoCapture.getOutput().prepareRecording(VideoActivity.this, options).withAudioEnabled().start(ContextCompat.getMainExecutor(VideoActivity.this),
                new Consumer<VideoRecordEvent>() {
                    @Override
                    public void accept(VideoRecordEvent videoRecordEvent) {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            capture.setImageResource(R.drawable.baseline_stop_circle_24);
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            if (((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                String msg = "Video Capture Successful";
                                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_SHORT).show();
                            } else {
                                recording.close();
                                recording = null;
                                String msg = "Error:" + ((VideoRecordEvent.Finalize) videoRecordEvent).hasError();
                                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                            capture.setImageResource(R.drawable.baseline_fiber_manual_record_24);
                        }
                    }
                });
    }

    private void startCamera(int cameraFacing) {
        ListenableFuture<ProcessCameraProvider> processCameraProviderListenableFuture = ProcessCameraProvider.getInstance(VideoActivity.this);
        processCameraProviderListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider provider = processCameraProviderListenableFuture.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    Recorder recorder = new Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                            .build();
                    videoCapture = VideoCapture.withOutput(recorder);
                    imageCapture = new ImageCapture.Builder()
                            .setTargetRotation(previewView.getDisplay().getRotation())
                            .build();
                    provider.unbindAll();

                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();
                    Camera camera = provider.bindToLifecycle(VideoActivity.this, cameraSelector, preview, videoCapture, imageCapture);

                    toggleFlash.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            toggleFlash(camera);

                        }
                    });
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(VideoActivity.this));
        if (lastPhotoUri != null) {
            minatura.setVisibility(View.VISIBLE);
            minatura.setImageURI(lastPhotoUri);
            minatura.setTag(lastPhotoUri);
        } else {
            minatura.setVisibility(View.GONE);
        }
    }

    private void toggleFlash(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                toggleFlash.setImageResource(R.drawable.baseline_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.baseline_flash_on_24);
            }
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VideoActivity.this, "Flash is not available", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void capturePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Error: ImageCapture no está disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSSS", Locale.getDefault()).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "CameraX");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        minatura.setVisibility(View.VISIBLE);
                        Toast.makeText(VideoActivity.this, "Foto guardada exitosamente", Toast.LENGTH_SHORT).show();
                        Uri saveURI = outputFileResults.getSavedUri();
                        minatura.setImageURI(saveURI);
                        minatura.setTag(saveURI);
                        lastPhotoUri = saveURI;
                    }


                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(VideoActivity.this, "Error al guardar la foto: ", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (service != null) {
            service.shutdown();
        }
    }
}
